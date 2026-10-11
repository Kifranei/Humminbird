package com.ella.music.ui.components

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first

/** Opens the spectrum app chosen in settings. The old built-in viewer is gone. */
internal object SpectrumViewerLauncher {
    const val ASK_EACH_TIME = "ask_each_time"
    const val ASPECT_PRO = "aspect_pro"
    const val KASPEK = "kaspek"
    const val HEARUSY = "hearusy"

    val viewerIds = listOf(ASPECT_PRO, KASPEK, HEARUSY)

    suspend fun openSelected(context: Context, song: Song, onAskEachTime: (Song) -> Unit) {
        val selected = SettingsManager.getInstance(context).spectrumViewerId.first()
        if (selected == ASK_EACH_TIME || selected !in viewerIds) {
            onAskEachTime(song)
        } else {
            open(context, song, selected)
        }
    }

    fun open(context: Context, song: Song, viewerId: String) {
        when (viewerId) {
            KASPEK -> openSongSpectrumWithKaspek(context, song)
            HEARUSY -> openSongSpectrumWithHearusy(context, song)
            else -> openSongSpectrumWithAspectPro(context, song)
        }
    }
}
