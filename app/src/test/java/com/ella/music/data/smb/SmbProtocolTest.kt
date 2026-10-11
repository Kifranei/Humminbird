package com.ella.music.data.smb

import android.app.Application
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.RemoteAudioCache
import com.ella.music.player.playbackUri
import com.ella.music.ui.folder.toSong
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SmbProtocolTest {
    @Test fun chineseShareAndSigningRequiredServerSupportWindowsStyleAccounts() {
        val url = System.getenv("HALCYON_TEST_SMB_SIGNING_ROOT").orEmpty()
        assumeTrue("Set HALCYON_TEST_SMB_SIGNING_ROOT to a signing-required fixture", url.isNotBlank())
        val original = SmbLocation.parse(url)
        val root = original.copy(share = "音乐")
        SmbSession(SmbAccount(root, "DESKTOP\\listener", "fixture-password", protocol = SmbProtocol.Smb2)).use { session ->
            val entries = session.list(root)
            assertTrue(entries.any { it.name == "测试 + 音乐.wav" })
            val file = entries.first { it.name == "First.wav" }
            session.open(file.location).use { source ->
                val header = ByteArray(4)
                assertEquals(4, source.read(header, 0, 0, 4))
                assertEquals("RIFF", header.toString(Charsets.US_ASCII))
            }
        }
        val rejected = org.junit.Assert.assertThrows(SmbConnectionException::class.java) {
            SmbSession(SmbAccount(root, "listener", "incorrect-password")).use { it.list(root) }
        }
        assertEquals("authenticate", rejected.stage)
        assertEquals("STATUS_LOGON_FAILURE", smbFailureCode(rejected))
    }
    @Test fun windowsAccountNamesPreservePasswordsAndExplicitDomains() {
        val root = SmbLocation.parse("smb://server/音乐")
        val account = SmbAccount(root, "DESKTOP\\listener", " password ")
        assertEquals("listener", account.authentication().username)
        assertEquals("DESKTOP", account.authentication().domain)
        assertArrayEquals(" password ".toCharArray(), account.authentication().password)
        assertEquals("CUSTOM", account.copy(domain = "CUSTOM").authentication().domain)
        assertEquals("email@example.com", account.copy(username = "email@example.com").authentication().username)
    }
    @Test fun locationRoundTripsUnicodeAndNeverLetsAFileEscapeTheConfiguredShare() {
        val root = SmbLocation.parse("smb://server/Music/Album")
        val child = root.child("测试 + 曲目.wav")
        assertEquals(child, SmbLocation.parse(child.url()))
        assertTrue(child.within(root))
        assertFalse(SmbLocation.parse("smb://server/Music/Album2/song.wav").within(root))
        assertFalse(SmbLocation.parse("smb://other/Music/Album/song.wav").within(root))
        for (invalid in listOf("smb://user:password@server/Music", "smb://server/Music/%2e%2e/song.wav",
            "smb://server/Music/a%2fb.wav", "smb://server/Music/a%5cb.wav", "smb://server/", "https://server/Music")) {
            assertThrows(IllegalArgumentException::class.java) { SmbLocation.parse(invalid) }
        }
    }
    @Test fun realSmb2ShareSupportsBrowsingByteRangePlaybackAndOfflineCache() = runBlocking {
        val url = System.getenv("HALCYON_TEST_SMB_ROOT").orEmpty()
        assumeTrue("Set HALCYON_TEST_SMB_ROOT to a local SMB fixture", url.isNotBlank())
        val context = RuntimeEnvironment.getApplication()
        val root = SmbLocation.parse(url)
        val entries = SmbSession(SmbAccount(root, "", "")).use { session ->
            val result = session.list(root)
            assertTrue(result.any { it.name == "Album" && it.directory })
            assertTrue(result.any { it.name == "测试 + 音乐.wav" })
            result
        }
        SmbSession(SmbAccount(root, "listener", "fixture-password")).use { authenticated ->
            assertTrue(authenticated.list(root).any { it.name == "First.wav" })
        }
        val entry = entries.first { it.name == "First.wav" }
        val settings = SettingsManager.getInstance(context)
        settings.setSmbConfig(root.url(), "", "")
        val repository = com.ella.music.data.repository.MusicRepository.getInstance(context)
        val summary = repository.loadRemoteLibrary(SettingsManager.LIBRARY_SOURCE_SMB, forceRefresh = true)
        assertTrue(summary.total > 1)
        assertTrue(repository.songs.value.any { it.path.endsWith("/Album/second.mp3") })
        assertTrue(repository.songs.value.any { it.path == entry.location.url() })
        repository.clearInMemoryLibrary()
        repository.loadRemoteLibrary(SettingsManager.LIBRARY_SOURCE_SMB, forceRefresh = false)
        assertEquals(summary.total, repository.songs.value.size)
        val expected = ByteArray(512) { ((it + 128 - 44) % 256).toByte() }
        SmbDataSource(context).use { source ->
            val spec = DataSpec.Builder().setUri(Uri.parse(entry.location.url())).setPosition(128).setLength(512).build()
            assertEquals(512L, source.open(spec))
            assertEquals(0, source.read(ByteArray(1), 0, 0))
            val actual = ByteArray(512)
            var offset = 0
            while (offset < actual.size) {
                val count = source.read(actual, offset, actual.size - offset)
                assertTrue(count > 0)
                offset += count
            }
            assertArrayEquals(expected, actual)
            assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(8), 0, 8))
        }
        RemoteAudioCache.init(context)
        val song = entry.toSong()
        assertNull("Browsing must not fetch remote audio metadata", SmbMetadataCache.metadataFile(context, song))
        val metadata = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            requireNotNull(SmbMetadataCache.metadataFile(context, song, allowNetwork = true))
        }
        assertEquals(entry.bytes, metadata.length())
        assertEquals("RIFF", metadata.inputStream().use { it.readNBytes(4).toString(Charsets.US_ASCII) })
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val flac = entries.first { it.name == "LongMetadata.flac" }.toSong()
            val prefix = requireNotNull(SmbMetadataCache.metadataFile(context, flac, allowNetwork = true))
            assertEquals(5L * 1024 * 1024 + 46, prefix.length())
            assertEquals(prefix.length(), com.ella.music.data.webdav.flacMetadataEnd(prefix))
            // Large unsupported files and incomplete FLAC metadata must never be passed to
            // native tag readers as zero-filled sparse audio.
            assertNull(SmbMetadataCache.metadataFile(context, entries.first { it.name == "IncompleteMetadata.flac" }.toSong(), allowNetwork = true))
            assertNull(SmbMetadataCache.metadataFile(context, entries.first { it.name == "Large.wav" }.toSong(), allowNetwork = true))
        }
        RemoteAudioCache.cacheSongs(listOf(song), this).join()
        assertEquals(1, RemoteAudioCache.progress.value.completed)
        assertEquals(0, RemoteAudioCache.progress.value.failed)
        val cached = requireNotNull(RemoteAudioCache.localFileIfCached(song))
        assertEquals(entry.bytes, cached.length())
        assertArrayEquals(expected, cached.readBytes().copyOfRange(128, 640))
        assertEquals("file", song.playbackUri().scheme)
    }
}

private inline fun <T> SmbDataSource.use(block: (SmbDataSource) -> T): T = try { block(this) } finally { close() }
