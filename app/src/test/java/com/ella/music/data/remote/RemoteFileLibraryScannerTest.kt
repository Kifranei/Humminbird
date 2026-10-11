package com.ella.music.data.remote

import com.ella.music.data.model.Song
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RemoteFileLibraryScannerTest {
    @Test fun nestedSharesAreIndexedOnceAndPublishBeforeTheFinalDirectory() = runBlocking {
        val calls = mutableListOf<String>()
        val snapshots = mutableListOf<List<Song>>()
        val first = song("smb://server/music/first.flac")
        val second = song("smb://server/music/album/second.flac")
        val result = scanRemoteFileLibrary("smb://server/music/", list = { url ->
            calls += url
            if (url.trimEnd('/') == "smb://server/music") listOf(
                RemoteFileLibraryEntry(url, true), RemoteFileLibraryEntry("smb://server/music/album/", true),
                RemoteFileLibraryEntry(first.path, false, first), RemoteFileLibraryEntry(first.path, false, first)
            ) else {
                assertEquals(listOf(first), snapshots.first())
                listOf(RemoteFileLibraryEntry("smb://server/music/", true), RemoteFileLibraryEntry(second.path, false, second))
            }
        }, onSongs = { snapshots += it })
        assertEquals(listOf(first, second), result)
        assertEquals(2, calls.size)
        assertEquals(result, snapshots.last())
    }
    @Test fun largeLibrariesAreNotSilentlyTruncatedAtTwentyThousandSongs() = runBlocking {
        val entries = (1..20_001).map { RemoteFileLibraryEntry("https://server/music/$it.flac", false, song("https://server/music/$it.flac")) }
        assertEquals(20_001, scanRemoteFileLibrary("https://server/music/", list = { entries }).size)
    }
    @Test fun failedSubdirectoriesDoNotReturnAnApparentlyCompleteLibrary() = runBlocking {
        try {
            scanRemoteFileLibrary("https://server/music/", list = {
                if (it.endsWith("missing/")) throw IOException("Missing directory")
                listOf(RemoteFileLibraryEntry("https://server/music/missing/", true))
            })
            fail("A failed refresh must preserve the previous cache rather than return a truncated snapshot")
        } catch (expected: IOException) { assertEquals("Missing directory", expected.message) }
    }
    private fun song(path: String) = Song(path.hashCode().toLong(), "Song", "Artist", "Album", 1L, 0L,
        path, path.substringAfterLast('/'))
}
