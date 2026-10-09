package com.ella.music.ui.components

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.flow.first

/** Opens the spectrum app chosen in settings. The old built-in viewer is gone. */
internal object SpectrumViewerLauncher {
    const val ASPECT_PRO = "aspect_pro"
    const val KASPEK = "kaspek"
    const val HEARUSY = "hearusy"

    suspend fun openSelected(context: Context, song: Song) {
        when (SettingsManager.getInstance(context).spectrumViewerId.first()) {
            KASPEK -> openSongSpectrumWithKaspek(context, song)
            HEARUSY -> openSongSpectrumWithHearusy(context, song)
            else -> openSongSpectrumWithAspectPro(context, song)
        }
    }
}
