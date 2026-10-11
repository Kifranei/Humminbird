package com.ella.music.data.remote

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.RandomAccessFile
import org.junit.Assert.*
import org.junit.Test

class RemoteStreamPolicyTest {
    @Test fun smallStreamReadsAreBufferedAndNeverExceedTheRequestedRange() {
        val bytes = ByteArray(1024 * 1024) { (it % 251).toByte() }
        var requests = 0
        var consumed = 0
        val source = object : FilterInputStream(ByteArrayInputStream(bytes)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                requests++
                return `in`.read(buffer, offset, length).also { if (it > 0) consumed += it }
            }
        }
        val result = java.io.ByteArrayOutputStream()
        bufferedRemoteInput(source, 512 * 1024 + 3L).use { input ->
            val chunk = ByteArray(1024)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                result.write(chunk, 0, count)
            }
        }
        assertEquals(3, requests)
        assertEquals(512 * 1024 + 3, consumed)
        assertArrayEquals(bytes.copyOf(consumed), result.toByteArray())
    }

    @Test fun pruningKeepsRecentWindowsAndTheirMarkersWithoutTouchingLyricsOrOfflineAudio() {
        val directory = java.nio.file.Files.createTempDirectory("remote-window-policy").toFile()
        try {
            val lyrics = File(directory, "sidecar_lyrics").apply { mkdir() }
            File(lyrics, "song.lrc").writeText("[00:00]keep")
            val files = (0..3).map { i -> File(directory, "$i.flac").apply {
                RandomAccessFile(this, "rw").use { it.setLength(24L * 1024 * 1024) }
                setLastModified(1000L + i)
                File(directory, "$name.flac-meta").writeText("marker")
            } }
            RemoteMetadataCachePolicy.trim(directory, files[3])
            assertFalse(files[0].exists()); assertFalse(files[1].exists())
            assertTrue(files[2].exists()); assertTrue(files[3].exists())
            assertFalse(File(directory, "0.flac.flac-meta").exists())
            assertTrue(File(directory, "3.flac.flac-meta").exists())
            assertTrue(File(lyrics, "song.lrc").exists())
        } finally { directory.deleteRecursively() }
    }
}
