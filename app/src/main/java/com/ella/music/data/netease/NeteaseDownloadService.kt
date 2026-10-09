package com.ella.music.data.netease

import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.io.IOException

internal fun detectNeteaseAudioFormat(prefix: ByteArray): Pair<String, String> = when {
    prefix.take(4).toByteArray().toString(Charsets.US_ASCII) == "fLaC" -> "flac" to "audio/flac"
    prefix.size >= 8 && prefix.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" -> "m4a" to "audio/mp4"
    prefix.take(3).toByteArray().toString(Charsets.US_ASCII) == "ID3" ||
        (prefix.size >= 2 && prefix[0].toInt() and 255 == 255 && prefix[1].toInt() and 224 == 224) -> "mp3" to "audio/mpeg"
    else -> throw IOException("Unsupported audio format")
}

internal object NeteaseOfflineDownloads {
    const val RELATIVE_PATH = "Music/Halcyon/NcmOfflineSongs/"
    val path: String get() = File(Environment.getExternalStorageDirectory(), RELATIVE_PATH).path.trimEnd('/')
    val revision = MutableStateFlow(0L)
    fun songs(context: Context): List<Song> {
        val columns = arrayOf("_id", "title", "artist", "album", "album_id", "duration", "_data", "_display_name", "_size", "mime_type")
        return buildList {
            context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
                "_data LIKE ?", arrayOf("$path/%"), "date_added DESC")?.use { c ->
                while (c.moveToNext()) add(Song(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(),
                    c.getString(3).orEmpty(), c.getLong(4), c.getLong(5), c.getString(6).orEmpty(),
                    c.getString(7).orEmpty(), fileSize = c.getLong(8), mimeType = c.getString(9).orEmpty()))
            }
        }
    }
}
