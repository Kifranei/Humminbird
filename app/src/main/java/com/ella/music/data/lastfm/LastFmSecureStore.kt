package com.ella.music.data.lastfm

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import com.ella.music.data.AppLogStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Stores the Last.fm shared secret and session key outside DataStore and outside Android backup.
 *
 * Last.fm requires the application's shared secret when exchanging an auth token and when
 * scrobbling.  Keeping it in DataStore or a regular backed-up preference file would both expose
 * it in a backup and make accidental source leaks much more likely.  The payload is encrypted
 * with a non-exportable Android Keystore AES key and written to noBackupFilesDir.
 */
class LastFmSecureStore private constructor(private val context: Context) {
    private val encryptedFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val _credentials = MutableStateFlow(readCredentials())
    val credentials: StateFlow<LastFmCredentials> = _credentials.asStateFlow()

    @Synchronized
    fun updateAppCredentials(apiKey: String, sharedSecret: String) {
        val current = _credentials.value
        val appCredentialsChanged = current.apiKey.trim() != apiKey.trim() ||
            current.sharedSecret.trim() != sharedSecret.trim()
        save(
            current.copy(
                apiKey = apiKey.trim(),
                sharedSecret = sharedSecret.trim(),
                // A token/session can only belong to one Last.fm application.
                username = if (appCredentialsChanged) "" else current.username,
                sessionKey = if (appCredentialsChanged) "" else current.sessionKey,
                pendingToken = ""
            )
        )
    }

    @Synchronized
    fun setPendingToken(token: String) {
        save(_credentials.value.copy(pendingToken = token.trim()))
    }

    @Synchronized
    fun saveSession(session: LastFmSession) {
        save(
            _credentials.value.copy(
                username = session.username.trim(),
                sessionKey = session.sessionKey.trim(),
                pendingToken = ""
            )
        )
    }

    @Synchronized
    fun clearAuthorization() {
        save(_credentials.value.copy(username = "", sessionKey = "", pendingToken = ""))
    }

    @Synchronized
    fun clearAll() {
        _credentials.value = LastFmCredentials()
        encryptedFile.delete()
    }

    private fun save(value: LastFmCredentials) {
        _credentials.value = value
        if (
            value.apiKey.isBlank() && value.sharedSecret.isBlank() && value.username.isBlank() &&
            value.sessionKey.isBlank() && value.pendingToken.isBlank()
        ) {
            encryptedFile.delete()
            return
        }
        runCatching {
            val payload = JSONObject()
                .put("apiKey", value.apiKey)
                .put("sharedSecret", value.sharedSecret)
                .put("username", value.username)
                .put("sessionKey", value.sessionKey)
                .put("pendingToken", value.pendingToken)
            val stream = encryptedFile.startWrite()
            try {
                stream.write(encrypt(payload.toString()).toByteArray(Charsets.UTF_8))
                encryptedFile.finishWrite(stream)
            } catch (error: Throwable) {
                encryptedFile.failWrite(stream)
                throw error
            }
        }.onFailure { error ->
            AppLogStore.warn(context, "LastFmCredentials", "Failed to persist encrypted Last.fm credentials", error)
        }
    }

    private fun readCredentials(): LastFmCredentials {
        if (!encryptedFile.baseFile.exists()) return LastFmCredentials()
        return runCatching {
            val encrypted = encryptedFile.openRead().bufferedReader().use { it.readText() }
            val payload = JSONObject(decrypt(encrypted))
            LastFmCredentials(
                apiKey = payload.optString("apiKey"),
                sharedSecret = payload.optString("sharedSecret"),
                username = payload.optString("username"),
                sessionKey = payload.optString("sessionKey"),
                pendingToken = payload.optString("pendingToken")
            )
        }.getOrElse { error ->
            AppLogStore.warn(context, "LastFmCredentials", "Unable to read encrypted Last.fm credentials", error)
            LastFmCredentials()
        }
    }

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(cipher.iv.size + cipherText.size)
        cipher.iv.copyInto(packed, destinationOffset = 0)
        cipherText.copyInto(packed, destinationOffset = cipher.iv.size)
        return Base64.encodeToString(packed, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > GCM_IV_LENGTH_BYTES) { "Invalid encrypted Last.fm credential payload" }
        val iv = packed.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val cipherText = packed.copyOfRange(GCM_IV_LENGTH_BYTES, packed.size)
        val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), javax.crypto.spec.GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val FILE_NAME = "lastfm_credentials.enc"
        private const val KEY_ALIAS = "halcyon_lastfm_credentials_v1"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128

        @Volatile
        private var instance: LastFmSecureStore? = null

        fun getInstance(context: Context): LastFmSecureStore =
            instance ?: synchronized(this) {
                instance ?: LastFmSecureStore(context.applicationContext).also { instance = it }
            }
    }
}
