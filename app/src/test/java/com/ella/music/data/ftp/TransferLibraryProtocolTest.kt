package com.ella.music.data.ftp

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.ella.music.data.SettingsManager
import com.ella.music.data.remote.RemoteFileLibraryEntry
import com.ella.music.data.remote.scanRemoteFileLibrary
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.repository.RemoteAudioCache
import com.ella.music.data.smb.SmbPasswordStore
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.player.playbackUri
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TransferLibraryProtocolTest {
    @Test fun remotePathsRoundTripUnicodeAndRejectTraversalAndControlCommands() {
        for (scheme in listOf("ftp", "ftps", "sftp")) {
            val root = TransferLocation.parse("$scheme://server:2222/Music")
            val song = root.child("测试 + 曲目.mp3")
            assertEquals(song, TransferLocation.parse(song.url()))
            assertTrue(song.within(root))
            assertFalse(root.copy(host = "other").child("song.mp3").within(root))
            assertFalse(root.copy(segments = listOf("Music2")).child("song.mp3").within(root))
            for (suffix in listOf("%2e%2e/a.mp3", "a%2fb.mp3", "a%5cb.mp3", "a%0d%0aDELE.mp3")) {
                assertThrows(IllegalArgumentException::class.java) { TransferLocation.parse("$scheme://server/Music/$suffix") }
            }
        }
        assertThrows(IllegalArgumentException::class.java) { TransferLocation.parse("ftp://user:secret@server/Music") }
    }
    @Test fun ftpSupportsListingNestedLibrariesSeekingTagsAndOfflineCache() = runBlocking {
        exercise("ftp", "HALCYON_TEST_FTP_ROOT")
    }
    @Test fun sftpRequiresFingerprintConfirmationAndRejectsAChangedServerKey() = runBlocking {
        exercise("sftp", "HALCYON_TEST_SFTP_ROOT")
    }
    @Test fun explicitFtpsAuthenticatesTheCertificateAndEncryptsTheDataChannel() = runBlocking {
        val store = System.getenv("HALCYON_TEST_FTPS_TRUSTSTORE").orEmpty()
        val url = System.getenv("HALCYON_TEST_FTPS_ROOT").orEmpty()
        assumeTrue("Set the local FTPS fixture and trust store", store.isNotBlank() && url.isNotBlank())
        val root = TransferLocation.parse(url)
        // The local self-signed certificate is initially untrusted.
        assertThrows(IOException::class.java) { transferSession(TransferAccount(root, "listener", "fixture-password")).use { it.list(root) } }
        val properties = listOf("javax.net.ssl.trustStore", "javax.net.ssl.trustStorePassword", "javax.net.ssl.trustStoreType")
        val previous = properties.associateWith { System.getProperty(it) }
        try {
            System.setProperty(properties[0], store)
            System.setProperty(properties[1], "fixture-password")
            System.setProperty(properties[2], "PKCS12")
            exercise("ftp", "HALCYON_TEST_FTPS_ROOT")
        } finally {
            previous.forEach { (key, value) -> if (value == null) System.clearProperty(key) else System.setProperty(key, value) }
        }
    }
    private suspend fun exercise(source: String, variable: String) {
        val url = System.getenv(variable).orEmpty()
        assumeTrue("Set $variable to a local protocol fixture", url.isNotBlank())
        val context = RuntimeEnvironment.getApplication()
        val root = TransferLocation.parse(url)
        var account = TransferAccount(root, "listener", "fixture-password")
        if (source == "sftp") {
            val pending = assertThrows(SftpHostKeyException::class.java) { transferSession(account).use { it.list(root) } }
            assertFalse(pending.changed)
            assertTrue(pending.fingerprint.startsWith("SHA256:"))
            account = account.copy(hostKey = pending.fingerprint)
            val changed = assertThrows(SftpHostKeyException::class.java) {
                transferSession(account.copy(hostKey = "SHA256:wrong")).use { it.list(root) }
            }
            assertTrue(changed.changed)
        }
        assertThrows(IOException::class.java) {
            transferSession(account.copy(password = "incorrect")).use { it.list(root) }
        }
        val entries = transferSession(account).use { session ->
            val entries = session.list(root)
            assertTrue(entries.any { it.name == "Album" && it.directory })
            val library = scanRemoteFileLibrary(root.url(), { url ->
                session.list(TransferLocation.parse(url)).map { entry ->
                    RemoteFileLibraryEntry(entry.location.url(), entry.directory,
                        entry.takeIf { !it.directory && WebDavClient.isAudioFile(it.name) }?.toSong())
                }
            })
            assertEquals(2, library.size)
            assertTrue(library.any { it.path.contains("/Album/") && it.fileName == "测试 + 歌曲.mp3" })
            entries
        }
        val song = entries.first { it.name == "First.mp3" }.toSong()
        val settings = SettingsManager.getInstance(context)
        settings.setTransferLibraryConfig(source, TransferLibraryConfig(root.url(), account.username, account.hostKey))
        // JVM tests do not provide AndroidKeyStore. Supply the decrypted credential state;
        // the protocol, repository, metadata and playback use the production credential reader.
        val passwordStore = SmbPasswordStore.getInstance(context, source)
        val passwordState = SmbPasswordStore::class.java.getDeclaredField("mutablePassword").apply { isAccessible = true }
            .get(passwordStore) as kotlinx.coroutines.flow.MutableStateFlow<String>
        passwordState.value = account.password
        val repository = MusicRepository.getInstance(context)
        assertEquals(2, repository.loadRemoteLibrary(source, true).total)
        repository.clearInMemoryLibrary()
        assertEquals(2, repository.loadRemoteLibrary(source, false).total)
        val expected = ByteArray(512) { ((it + 128) % 256).toByte() }
        TransferLibraryDataSource(context).use { data ->
            assertEquals(512L, data.open(DataSpec.Builder().setUri(Uri.parse(song.path)).setPosition(128).setLength(512).build()))
            val buffer = ByteArray(512)
            var offset = 0
            while (offset < buffer.size) {
                val count = data.read(buffer, offset, buffer.size - offset)
                assertTrue(count > 0)
                offset += count
            }
            assertArrayEquals(expected, buffer)
            assertEquals(C.RESULT_END_OF_INPUT, data.read(ByteArray(8), 0, 8))
        }
        assertThrows(IOException::class.java) {
            TransferLibraryDataSource(context).use { it.open(DataSpec.Builder().setUri(Uri.parse(song.path)).setPosition(8193).build()) }
        }
        withContext(Dispatchers.IO) { assertNull("Directory browsing is cache-only", TransferMetadataCache.metadataFile(context, song)) }
        val metadata = withContext(Dispatchers.IO) { requireNotNull(TransferMetadataCache.metadataFile(context, song, allowNetwork = true)) }
        assertEquals(8192L, metadata.length())
        RemoteAudioCache.init(context)
        withContext(Dispatchers.IO) { RemoteAudioCache.cacheSongs(listOf(song), this).join() }
        val cached = requireNotNull(RemoteAudioCache.localFileIfCached(song))
        assertArrayEquals(expected, cached.readBytes().copyOfRange(128, 640))
        assertEquals("file", song.playbackUri().scheme)
        val backup = settings.exportSettingsJson()
        settings.setTransferLibraryConfig(source, TransferLibraryConfig(root.child("Album").url(), "other", ""))
        settings.restoreSettingsJson(backup)
        assertEquals(root.url(), settings.transferLibraryConfig(source).first().url)
    }
}

private inline fun <T> TransferLibraryDataSource.use(block: (TransferLibraryDataSource) -> T): T =
    try { block(this) } finally { close() }
