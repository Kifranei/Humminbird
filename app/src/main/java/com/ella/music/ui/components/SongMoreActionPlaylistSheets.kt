package com.ella.music.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.viewmodel.MainViewModel

@Composable
internal fun SongMorePlaylistActionSheets(
    context: Context,
    mainViewModel: MainViewModel,
    playlists: List<UserPlaylist>,
    playlistSong: Song?,
    onPlaylistSongChange: (Song?) -> Unit,
    createPlaylistSong: Song?,
    onCreatePlaylistSongChange: (Song?) -> Unit,
    addToPlaylistTitle: String
) {
    playlistSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = addToPlaylistTitle,
            onDismissRequest = { onPlaylistSongChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists.sortedWith(
                    compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                        .thenByDescending { it.createdAt }
                ),
                songsToAdd = listOf(song),
                onDismiss = { onPlaylistSongChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongChange(song)
                    onPlaylistSongChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(song), appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistSongChange(null)
                }
            )
        }
    }

    createPlaylistSong?.let { song ->
        CreatePlaylistAndAddSheet(
            onDismiss = { onCreatePlaylistSongChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, listOf(song))
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlist_named, playlist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    onCreatePlaylistSongChange(null)
                }
            }
        )
    }
}
