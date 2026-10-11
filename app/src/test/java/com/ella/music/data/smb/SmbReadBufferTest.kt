package com.ella.music.data.smb

import org.junit.Assert.*
import org.junit.Test

class SmbReadBufferTest {
    @Test fun smallExtractorReadsUseBoundedReadAheadAndRespectFinalRange() {
        val bytes = ByteArray(1024 * 1024 + 29) { (it % 251).toByte() }
        val reader = SmbReadBuffer()
        var requests = 0
        var position = 0L
        val result = java.io.ByteArrayOutputStream()
        val small = ByteArray(1024)
        while (position < bytes.size) {
            val count = reader.read(small, 0, small.size, position, bytes.size - position) { target, at, amount ->
                requests++
                assertTrue(amount <= 256 * 1024)
                bytes.copyInto(target, 0, at.toInt(), at.toInt() + amount)
                amount
            }
            result.write(small, 0, count); position += count
        }
        assertArrayEquals(bytes, result.toByteArray())
        assertEquals("Small reads must not become one network request each", 5, requests)
        assertEquals(-1, reader.read(small, 0, 1, position, 0) { _, _, _ -> error("EOF must not read the network") })
    }

    @Test fun seekingAndReopeningNeverReuseWrongBytes() {
        val bytes = ByteArray(1024) { (it % 251).toByte() }
        val reader = SmbReadBuffer(128)
        val destination = ByteArray(24)
        var calls = 0
        val source = { target: ByteArray, at: Long, count: Int ->
            calls++; bytes.copyInto(target, 0, at.toInt(), at.toInt() + count); count
        }
        for (position in listOf(0L, 900L, 16L, 32L)) {
            val count = reader.read(destination, 4, 20, position, 1024 - position, source)
            assertEquals(20, count)
            assertArrayEquals(bytes.copyOfRange(position.toInt(), position.toInt() + 20), destination.copyOfRange(4, 24))
        }
        assertEquals(3, calls)
        reader.clear()
        reader.read(destination, 0, 20, 32, 40, source)
        assertEquals(4, calls)
    }
}
