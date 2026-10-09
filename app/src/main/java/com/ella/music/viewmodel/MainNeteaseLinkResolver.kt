package com.ella.music.viewmodel

import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MainNeteaseLinkResolver(
    private val repository: MusicRepository,
    private val songsForArtist: (String) -> List<Song>,
    private val songsForAlbum: (Long) -> List<Song>
) {
    suspend fun artistUrlForArtist(artistName: String): String? = withContext(Dispatchers.IO) {
        val targetNames = splitArtistNames(artistName)
            .ifEmpty { listOf(artistName.trim()) }
            .filter { it.isNotBlank() }
        val targetKeys = targetNames.map { it.tagIdentityKey() }.toSet()
        val matchedArtist = songsForArtist(artistName).asSequence()
            .take(80)
            .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
            .flatMap { it.artists.asSequence() }
            .firstOrNull { artist ->
                artist.id.isNotBlank() && artist.name.tagIdentityKey() in targetKeys
            }
            ?: songsForArtist(artistName).asSequence()
                .take(80)
                .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
                .flatMap { it.artists.asSequence() }
                .firstOrNull { artist ->
                    artist.id.isNotBlank() && targetNames.any { target ->
                        artist.name.equals(target, ignoreCase = true) ||
                            (artist.name.length >= 3 && target.contains(artist.name, ignoreCase = true)) ||
                            (target.length >= 3 && artist.name.contains(target, ignoreCase = true))
                    }
                }
        matchedArtist?.id?.let(::neteaseArtistUrl)
    }

    suspend fun albumUrlForAlbum(albumId: Long): String? = withContext(Dispatchers.IO) {
        songsForAlbum(albumId).asSequence()
            .take(40)
            .mapNotNull { song -> decodeNeteaseKey(repository.getSongTagInfo(song).neteaseKey) }
            .firstOrNull { it.albumId.isNotBlank() }
            ?.albumId
            ?.let(::neteaseAlbumUrl)
    }
}
