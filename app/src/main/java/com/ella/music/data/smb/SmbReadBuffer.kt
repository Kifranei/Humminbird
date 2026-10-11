package com.ella.music.data.smb

import java.io.EOFException

/** Read ahead in bounded blocks; Media3's small extractor reads must not each be an SMB RPC.
 * Keeps file offsets explicit so a seek cannot reuse bytes from another position. */
internal class SmbReadBuffer(private val capacity: Int = 256 * 1024) {
    private val bytes = ByteArray(capacity)
    private var start = -1L
    private var size = 0

    fun clear() { start = -1L; size = 0 }

    fun read(destination: ByteArray, offset: Int, length: Int, position: Long, remaining: Long,
        readAt: (ByteArray, Long, Int) -> Int): Int {
        if (length == 0) return 0
        if (remaining <= 0L) return -1
        if (position < start || position >= start + size) {
            start = position
            size = readAt(bytes, position, minOf(capacity.toLong(), remaining).toInt())
            if (size <= 0) throw EOFException("SMB file ended before the declared audio length")
        }
        val bufferOffset = (position - start).toInt()
        val count = minOf(length, size - bufferOffset, remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        bytes.copyInto(destination, offset, bufferOffset, bufferOffset + count)
        return count
    }
}
