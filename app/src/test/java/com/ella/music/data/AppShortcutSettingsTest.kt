package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppShortcutSettingsTest {

    @Test
    fun shortcutOrderDropsUnknownIdsDuplicatesAndRespectsTheFiveItemLimit() {
        assertEquals(
            listOf(
                SettingsManager.APP_SHORTCUT_SEARCH,
                SettingsManager.APP_SHORTCUT_YEARS,
                SettingsManager.APP_SHORTCUT_GENRES,
                SettingsManager.APP_SHORTCUT_ALBUMS,
                SettingsManager.APP_SHORTCUT_ARTISTS
            ),
            SettingsManager.normalizeAppShortcutOrder(
                "search,years,missing,genres,search,albums,artists,playlists"
            )
        )
    }

    @Test
    fun shortcutOrderAcceptsAnExplicitEmptySelection() {
        assertEquals(emptyList<String>(), SettingsManager.normalizeAppShortcutOrder(""))
    }
}
