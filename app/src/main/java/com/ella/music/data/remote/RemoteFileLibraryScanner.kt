package com.ella.music.data.remote

import com.ella.music.data.model.Song
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class RemoteFileLibraryEntry(val url: String, val directory: Boolean, val song: Song? = null)

/** Index directories once, and publish songs in batches without waiting for embedded artwork. */
internal suspend fun scanRemoteFileLibrary(
    root: String,
    list: suspend (String) -> List<RemoteFileLibraryEntry>,
    onSongs: (List<Song>) -> Unit = {},
    maxDirectories: Int = 10_000
): List<Song> {
    val pending = ArrayDeque<String>().apply { add(root) }
    val visited = HashSet<String>()
    val songs = LinkedHashMap<String, Song>()
    var publishedSize = 0
    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val directory = pending.removeFirst()
        if (!visited.add(directory.trimEnd('/'))) continue
        if (visited.size > maxDirectories) throw IOException("Remote music library contains too many directories")
        for (entry in list(directory)) {
            currentCoroutineContext().ensureActive()
            if (entry.directory) {
                if (entry.url.trimEnd('/') !in visited) pending.add(entry.url)
            } else if (entry.song != null) {
                songs[entry.song.path] = entry.song
            }
        }
        if (songs.size > publishedSize && (publishedSize == 0 || songs.size - publishedSize >= 64)) {
            onSongs(songs.values.toList())
            publishedSize = songs.size
        }
    }
    return songs.values.toList().also { onSongs(it) }
}
