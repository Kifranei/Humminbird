package com.ella.music.ui.components

import android.content.Context
import android.widget.Toast
import com.ella.music.R
import com.ella.music.data.model.UserPlaylist
import com.ella.music.viewmodel.MainViewModel

internal fun MainViewModel.createPlaylistOrShowDuplicateToast(
    context: Context,
    name: String,
    onCreated: (UserPlaylist) -> Unit
) {
    if (name.isBlank()) return
    createPlaylist(name) { playlist ->
        if (playlist == null) {
            Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
        } else {
            onCreated(playlist)
        }
    }
}
