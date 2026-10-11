package com.ella.music.data.ftp

import android.content.Context
import android.os.Looper
import com.ella.music.data.model.Song
import com.ella.music.data.repository.isLikelyFlacAudio
import com.ella.music.data.repository.supportsSparseWebDavMetadataWindow
import com.ella.music.data.webdav.flacMetadataEnd
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Reuses the same bounded native-tag input policy as SMB and WebDAV. */
internal object TransferMetadataCache {
    private val locks = Array(16) { Any() }
    fun metadataFile(context: Context, song: Song, allowNetwork: Boolean = false): File? {
        val key = MessageDigest.getInstance("SHA-256").digest(
            "v1|${song.path}|${song.fileSize}|${song.dateModified}".toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "ftp_metadata")
        val extension = song.fileName.substringAfterLast('.', "audio").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
        val target = File(directory, "$key.$extension")
        if (target.isFile && target.length() > 0) return target
        if (!allowNetwork || Looper.myLooper() == Looper.getMainLooper()) return null
        return synchronized(locks[Math.floorMod(key.hashCode(), locks.size)]) {
            if (target.isFile && target.length() > 0) return@synchronized target
            val temporary = File(directory, "$key.partial")
            try {
                check(directory.isDirectory || directory.mkdirs())
                val location = TransferLocation.parse(song.path)
                val account = runBlocking(Dispatchers.IO) { transferAccount(context, if (location.scheme == "sftp") "sftp" else "ftp") }
                transferSession(account).use { session ->
                    val length = session.size(location)
                    check(length > 0)
                    val complete = length <= 6L * 1024 * 1024
                    val flac = song.isLikelyFlacAudio()
                    if (!complete && !flac && (!song.supportsSparseWebDavMetadataWindow() || length > com.ella.music.data.remote.RemoteMetadataCachePolicy.MAX_WINDOW_BYTES))
                        return@synchronized null
                    RandomAccessFile(temporary, "rw").use { output ->
                        output.setLength(0)
                        val buffer = ByteArray(64 * 1024)
                        fun copyWindow(start: Long, count: Long) {
                            output.seek(start)
                            var remaining = count
                            session.read(location, start).use { input ->
                                while (remaining > 0) {
                                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                                    check(read > 0)
                                    output.write(buffer, 0, read)
                                    remaining -= read
                                }
                            }
                        }
                        when {
                            complete -> copyWindow(0, length)
                            flac -> {
                                var end = minOf(length, 64L * 1024)
                                copyWindow(0, end)
                                var metadataEnd = flacMetadataEnd(temporary)
                                while (metadataEnd == null && end < minOf(length, 32L * 1024 * 1024)) {
                                    val next = minOf(length, 32L * 1024 * 1024, end * 2)
                                    copyWindow(end, next - end)
                                    end = next
                                    metadataEnd = flacMetadataEnd(temporary)
                                }
                                output.setLength(checkNotNull(metadataEnd))
                            }
                            else -> {
                                output.setLength(length)
                                val head = minOf(length, 4L * 1024 * 1024)
                                copyWindow(0, head)
                                val tail = maxOf(head, length - 2L * 1024 * 1024)
                                if (tail < length) copyWindow(tail, length - tail)
                            }
                        }
                    }
                }
                check(temporary.renameTo(target))
                com.ella.music.data.remote.RemoteMetadataCachePolicy.trim(directory, target)
                target
            } catch (_: Exception) { temporary.delete(); null }
        }
    }
}
