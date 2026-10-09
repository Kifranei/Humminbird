package com.ella.music.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlaybackAudioSession {
    private val _audioSessionId = MutableStateFlow(-1)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    fun update(id: Int) {
        _audioSessionId.value = id
    }

    fun clear() {
        _audioSessionId.value = -1
    }
}
