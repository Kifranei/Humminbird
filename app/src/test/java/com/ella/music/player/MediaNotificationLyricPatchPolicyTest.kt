package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationLyricPatchPolicyTest {
    @Test
    fun sameLyricLineDoesNotPatchAgain() {
        val state = MediaNotificationLyricPatchPolicy.onPatched(
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line", "translation"),
            nowMs = 1_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line", "translation"),
            nowMs = 2_000L
        )

        assertEquals(MediaNotificationLyricPatchAction.Skip, decision.action)
    }

    @Test
    fun lyricPatchIsThrottledWithinMinInterval() {
        val state = MediaNotificationLyricPatchPolicy.onPatched(
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line 1", null),
            nowMs = 1_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line 2", null),
            nowMs = 1_300L
        )

        assertEquals(MediaNotificationLyricPatchAction.Defer, decision.action)
        assertTrue(decision.retryAfterMs > 0L)
    }

    @Test
    fun songChangeSuppressesImmediateLyricPatch() {
        val state = MediaNotificationLyricPatchPolicy.onSongChanged(
            songKey = "song-2",
            nowMs = 2_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-2",
            payload = MediaNotificationLyricPayload("first line", null),
            nowMs = 2_200L,
            force = true
        )

        assertEquals(MediaNotificationLyricPatchAction.Defer, decision.action)
    }

    @Test
    fun lyricPatchAllowedAfterSongChangeDelay() {
        val state = MediaNotificationLyricPatchPolicy.onSongChanged(
            songKey = "song-2",
            nowMs = 2_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-2",
            payload = MediaNotificationLyricPayload("first line", null),
            nowMs = 3_000L
        )

        assertEquals(MediaNotificationLyricPatchAction.Patch, decision.action)
    }

    @Test
    fun disablingNotificationLyricsRestoresSongMetadata() {
        val state = MediaNotificationLyricPatchPolicy.onPatched(
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line", null),
            nowMs = 1_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-1",
            payload = MediaNotificationLyricPayload(null, null),
            nowMs = 1_100L
        )

        assertEquals(MediaNotificationLyricPatchAction.RestoreSongMetadata, decision.action)
    }

    @Test
    fun metadataOnlyPatchStillIgnoredAsDisplayOnlySnapshot() {
        val currentSong = song(id = 1L, title = "Song", artist = "Artist", path = "/music/song.flac")
        val patchedSong = currentSong.copy(title = "Lyric line", artist = "Song · Artist")

        assertTrue(
            isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = true,
                snapshotSong = patchedSong,
                currentSong = currentSong
            )
        )
    }

    @Test
    fun artworkPatchDoesNotCauseLyricPatchLoop() {
        val state = MediaNotificationLyricPatchPolicy.onPatched(
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line", null),
            nowMs = 1_000L
        )

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = state,
            songKey = "song-1",
            payload = MediaNotificationLyricPayload("line", null),
            nowMs = 2_000L,
            force = false
        )

        assertEquals(MediaNotificationLyricPatchAction.Skip, decision.action)
    }

    private fun song(
        id: Long,
        title: String,
        artist: String,
        path: String
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = path.substringAfterLast('/')
    )
}
