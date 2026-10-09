package com.ella.music.ui.components

import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import org.junit.Assert.assertEquals
import org.junit.Test

class AddToPlaylistSortTest {
    @Test
    fun customSortUsesThePlaylistListCustomOrderInsteadOfCreationTime() {
        val favorites = playlist(FAVORITES_PLAYLIST_ID, "Favorites", 99)
        val newest = playlist("newest", "Newest", 30)
        val middle = playlist("middle", "Middle", 20)
        val oldest = playlist("oldest", "Oldest", 10)

        val sorted = listOf(favorites, newest, middle, oldest).sortedForAddToPlaylist(
            mode = AddPlaylistSortMode.Custom,
            customOrderIds = listOf("oldest", "newest", "middle")
        )

        assertEquals(
            listOf(FAVORITES_PLAYLIST_ID, "oldest", "newest", "middle"),
            sorted.map(UserPlaylist::id)
        )
    }

    private fun playlist(id: String, name: String, createdAt: Long) = UserPlaylist(
        id = id,
        name = name,
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
