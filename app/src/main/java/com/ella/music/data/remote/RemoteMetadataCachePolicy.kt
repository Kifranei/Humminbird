package com.ella.music.data.remote

import java.io.File

/** Indexing never downloads metadata. Selected tracks retain only a small recent window cache. */
internal object RemoteMetadataCachePolicy {
    const val MAX_WINDOW_BYTES = 32L * 1024 * 1024
    const val MAX_CACHE_BYTES = 64L * 1024 * 1024
    const val MAX_WINDOWS = 3

    @Synchronized
    fun trim(directory: File, keep: File? = null) {
        val files = directory.listFiles().orEmpty().filter {
            it.isFile && it != keep && it.extension !in setOf("partial", "tmp", "flac-meta")
        }.sortedByDescending { it.lastModified() }
        var bytes = keep?.length() ?: 0L
        var count = if (keep == null) 0 else 1
        for (file in files) {
            if (count >= MAX_WINDOWS || bytes + file.length() > MAX_CACHE_BYTES) {
                file.delete(); File(directory, "${file.name}.flac-meta").delete()
            } else { bytes += file.length(); count++ }
        }
        directory.listFiles().orEmpty().filter { it.isFile && it.extension == "flac-meta" }.forEach { marker ->
            if (!File(directory, marker.name.removeSuffix(".flac-meta")).isFile) marker.delete()
        }
    }
}
