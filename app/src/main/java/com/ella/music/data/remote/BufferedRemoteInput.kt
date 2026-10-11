package com.ella.music.data.remote

import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.InputStream

/** FTP/SFTP streams already read sequentially. Buffer them without crossing the requested range. */
internal fun bufferedRemoteInput(input: InputStream, length: Long): InputStream {
    val bounded = object : FilterInputStream(input) {
        private var remaining = length
        override fun read(): Int {
            if (remaining == 0L) return -1
            val value = `in`.read()
            if (value >= 0) remaining--
            return value
        }
        override fun read(buffer: ByteArray, offset: Int, count: Int): Int {
            if (count == 0) return 0
            if (remaining == 0L) return -1
            val result = `in`.read(buffer, offset, minOf(count.toLong(), remaining).toInt())
            if (result > 0) remaining -= result
            return result
        }
    }
    return BufferedInputStream(bounded, 256 * 1024)
}
