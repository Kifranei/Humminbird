package com.ella.music.data.smb

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow

/** A session credential lives in encrypted no-backup storage, like Last.fm authorization. */
internal class SmbPasswordStore private constructor(context: Context, private val source: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "${source}_password.enc"))
    private val mutablePassword = MutableStateFlow(read())
    val password get() = mutablePassword.value

    @Synchronized fun setPassword(input: String) {
        val password = input
        require(password.length <= 8192)
        if (password.isEmpty()) file.delete() else {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val encrypted = cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val stream = file.startWrite()
            try {
                stream.write(Base64.encode(encrypted, Base64.NO_WRAP))
                file.finishWrite(stream)
            } catch (error: Throwable) { file.failWrite(stream); throw error }
        }
        mutablePassword.value = password
    }

    private fun read(): String = runCatching {
        if (!file.baseFile.isFile) return ""
        val bytes = Base64.decode(file.openRead().use { it.readBytes() }, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }.getOrDefault("")

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }

    private val keyAlias get() = "halcyon_${source}_password_v1"

    companion object {
        private val instances = mutableMapOf<String, SmbPasswordStore>()
        @Synchronized fun getInstance(context: Context, source: String = "smb"): SmbPasswordStore {
            require(source in setOf("smb", "ftp", "sftp"))
            return instances.getOrPut(source) { SmbPasswordStore(context.applicationContext, source) }
        }
    }
}
