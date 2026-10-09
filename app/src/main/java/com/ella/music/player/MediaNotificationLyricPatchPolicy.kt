package com.ella.music.player

internal data class MediaNotificationLyricPayload(
    val text: String?,
    val secondaryText: String?
)

internal data class MediaNotificationLyricPatchState(
    val songKey: String? = null,
    val payload: MediaNotificationLyricPayload? = null,
    val lastPatchElapsedMs: Long = Long.MIN_VALUE,
    val suppressUntilElapsedMs: Long = 0L
)

internal object MediaNotificationLyricPatchPolicy {
    const val MIN_PATCH_INTERVAL_MS = 750L
    const val SONG_CHANGE_SUPPRESSION_MS = 900L

    fun onSongChanged(songKey: String?, nowMs: Long): MediaNotificationLyricPatchState =
        MediaNotificationLyricPatchState(
            songKey = songKey,
            suppressUntilElapsedMs = nowMs + SONG_CHANGE_SUPPRESSION_MS
        )

    fun onCleared(): MediaNotificationLyricPatchState =
        MediaNotificationLyricPatchState()

    fun onPatched(
        songKey: String?,
        payload: MediaNotificationLyricPayload?,
        nowMs: Long
    ): MediaNotificationLyricPatchState =
        MediaNotificationLyricPatchState(
            songKey = songKey,
            payload = payload,
            lastPatchElapsedMs = nowMs
        )

    fun actionFor(
        state: MediaNotificationLyricPatchState,
        songKey: String,
        payload: MediaNotificationLyricPayload,
        nowMs: Long,
        force: Boolean = false
    ): MediaNotificationLyricPatchDecision {
        if (payload.text.isNullOrBlank()) {
            return if (state.payload != null || state.songKey == songKey) {
                MediaNotificationLyricPatchDecision(MediaNotificationLyricPatchAction.RestoreSongMetadata)
            } else {
                MediaNotificationLyricPatchDecision(MediaNotificationLyricPatchAction.Skip)
            }
        }

        if (!force && state.songKey == songKey && state.payload == payload) {
            return MediaNotificationLyricPatchDecision(MediaNotificationLyricPatchAction.Skip)
        }

        val suppressedForMs = state.suppressUntilElapsedMs - nowMs
        if (suppressedForMs > 0L) {
            return MediaNotificationLyricPatchDecision(
                action = MediaNotificationLyricPatchAction.Defer,
                retryAfterMs = suppressedForMs
            )
        }

        val throttledForMs = if (state.lastPatchElapsedMs == Long.MIN_VALUE) {
            0L
        } else {
            MIN_PATCH_INTERVAL_MS - (nowMs - state.lastPatchElapsedMs)
        }
        if (throttledForMs > 0L) {
            return MediaNotificationLyricPatchDecision(
                action = MediaNotificationLyricPatchAction.Defer,
                retryAfterMs = throttledForMs
            )
        }

        return MediaNotificationLyricPatchDecision(MediaNotificationLyricPatchAction.Patch)
    }
}

internal data class MediaNotificationLyricPatchDecision(
    val action: MediaNotificationLyricPatchAction,
    val retryAfterMs: Long = 0L
)

internal enum class MediaNotificationLyricPatchAction {
    Patch,
    Defer,
    Skip,
    RestoreSongMetadata
}
