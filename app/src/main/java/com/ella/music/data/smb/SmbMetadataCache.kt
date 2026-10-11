package com.ella.music.data.smb

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

/** Complete small files, contiguous FLAC tags, and safe sparse windows for remote metadata. */
internal object SmbMetadataCache {
    private const val FULL_FILE_LIMIT = 6L * 1024 * 1024
    private const val FLAC_PREFIX_LIMIT = 32L * 1024 * 1024
    private const val SPARSE_FILE_LIMIT = 32L * 1024 * 1024
    private val locks = Array(16) { Any() }
    /** Also trims windows made by older versions; index-only browsing doesn't need them all. */
    fun trim(context: Context, keep: File? = null) {
        com.ella.music.data.remote.RemoteMetadataCachePolicy.trim(File(context.cacheDir, "smb_metadata"), keep)
    }
    fun metadataFile(context: Context, song: Song, allowNetwork: Boolean = false): File? {
        val key = MessageDigest.getInstance("SHA-256").digest(
            "v2|${song.path}|${song.fileSize}|${song.dateModified}".toByteArray(Charsets.UTF_8)
        ).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "smb_metadata")
        val extension = song.fileName.substringAfterLast('.', "audio").takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) } ?: "audio"
        val target = File(directory, "$key.$extension")
        if (target.isFile && target.length() > 0) return target
        // Synchronous callers on the UI thread get the ordinary fallback; background artwork
        // and metadata loaders prepare the window and then use the existing tag reader.
        if (!allowNetwork || Looper.myLooper() == Looper.getMainLooper()) return null
        return synchronized(locks[Math.floorMod(key.hashCode(), locks.size)]) {
            if (target.isFile && target.length() > 0) return@synchronized target
            val temporary = File(directory, "$key.partial")
            try {
                check(directory.isDirectory || directory.mkdirs())
                val account = runBlocking(Dispatchers.IO) { smbAccount(context) }
                SmbSession(account).use { session -> session.open(SmbLocation.parse(song.path)).use { source ->
                    val length = source.fileInformation.standardInformation.endOfFile
                    check(length > 0)
                    val complete = length <= FULL_FILE_LIMIT
                    val flac = song.isLikelyFlacAudio()
                    if (!complete && !flac && (!song.supportsSparseWebDavMetadataWindow() || length > SPARSE_FILE_LIMIT)) {
                        return@synchronized null
                    }
                    RandomAccessFile(temporary, "rw").use { output ->
                        output.setLength(0)
                        val buffer = ByteArray(64 * 1024)
                        fun copyWindow(start: Long, count: Long) {
                            var position = start
                            val end = start + count
                            output.seek(start)
                            while (position < end) {
                                val read = source.read(buffer, position, 0, minOf(buffer.size.toLong(), end - position).toInt())
                                check(read > 0)
                                output.write(buffer, 0, read)
                                position += read
                            }
                        }
                        when {
                            complete -> copyWindow(0, length)
                            flac -> {
                                // Zero-filled FLAC audio can be interpreted as corrupt metadata by
                                // native tag readers. Grow a contiguous prefix until every tag and
                                // embedded picture is present, using the existing WebDAV validator.
                                var end = minOf(length, 64L * 1024)
                                copyWindow(0, end)
                                var metadataEnd = flacMetadataEnd(temporary)
                                while (metadataEnd == null && end < minOf(length, FLAC_PREFIX_LIMIT)) {
                                    val nextEnd = minOf(length, FLAC_PREFIX_LIMIT, end * 2)
                                    copyWindow(end, nextEnd - end)
                                    end = nextEnd
                                    metadataEnd = flacMetadataEnd(temporary)
                                }
                                output.setLength(checkNotNull(metadataEnd))
                            }
                            else -> {
                                output.setLength(length)
                                val head = minOf(length, 4L * 1024 * 1024)
                                copyWindow(0, head)
                                val tailStart = maxOf(head, length - 2L * 1024 * 1024)
                                if (tailStart < length) copyWindow(tailStart, length - tailStart)
                            }
                        }
                    }
                } }
                check(temporary.renameTo(target))
                trim(context, target)
                target
            } catch (_: Exception) { temporary.delete(); null }
        }
    }
}
