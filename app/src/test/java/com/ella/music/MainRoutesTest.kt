package com.ella.music

import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRoutesTest {
    @Test fun searchReturnsToItsOpeningPageForDockAndPagerNavigation() {
        val library = Screen.Library.route
        val artists = Screen.Artist.createRoute(fromDock = true)
        val search = Screen.LibrarySearch.createRoute()
        val state = MainPageState(library).apply { routes = listOf(Screen.Home.route, library, artists, search) }
        assertTrue(state.select(search))
        assertEquals(library, state.searchReturnRoute)
        assertTrue(state.exitSearch())
        assertEquals(library, state.selectedRoute)
        state.settle(artists)
        state.settle(search)
        assertEquals(artists, state.searchReturnRoute)
        val restored = MainPageState(state.selectedRoute, state.searchReturnRoute).apply { routes = state.routes }
        assertTrue(restored.exitSearch())
        assertEquals(artists, restored.selectedRoute)
        assertFalse(restored.exitSearch())
    }
    @Test fun folderPlaylistDockRouteIsSelectableAndKeepsBrowsingBackgrounds() {
        assertEquals(Screen.FolderPlaylists.route, Screen.FolderPlaylists.route.toCurrentTabRoute())
        assertTrue(Screen.FolderPlaylists.route.isBottomDockRoute())
        for (route in listOf(Screen.Album.createRoute(true), Screen.Artist.createRoute(true),
            Screen.Folder.createRoute(true), Screen.MetadataCategory.createRoute("genre", true))) {
            assertTrue(com.ella.music.ui.components.supportsNowPlayingFlowBackground(route))
        }
        assertFalse(com.ella.music.ui.components.supportsNowPlayingFlowBackground(Screen.MetadataCategoryDetail.createRoute("genre", "Jazz")))
    }
    @Test
    fun dockSettingsPatternResolvesToFirstLevelTab() {
        val route = resolvedCurrentRoute(
            destinationRoute = Screen.Settings.route,
            fromDock = true
        )
        assertEquals(Screen.Settings.createRoute(fromDock = true), route)
        assertTrue(route.isBottomDockRoute())
        assertEquals(Screen.Settings.createRoute(fromDock = true), route.toCurrentTabRoute())
    }

    @Test
    fun settingsOpenedWithoutDockStaysSecondary() {
        val route = resolvedCurrentRoute(
            destinationRoute = Screen.Settings.route,
            fromDock = false
        )
        assertEquals(Screen.Settings.createRoute(fromDock = false), route)
        assertFalse(route.isBottomDockRoute())
        assertEquals(null, route.toCurrentTabRoute())
    }

    @Test
    fun backFromArtistRestoresPlayerWhenPreviousPageIsTheSavedRoute() {
        val library = navRouteIdentity(Screen.Library.route) { null }
        assertTrue(shouldRestorePlayerOnBack(library, library))
        assertFalse(shouldRestorePlayerOnBack(library, Screen.ArtistDetail.route))
        assertFalse(shouldRestorePlayerOnBack(null, library))
        assertFalse(playerDismissBackEnabled(playerVisible = true, restorePlayerOnBack = true))
        assertTrue(playerDismissBackEnabled(playerVisible = true, restorePlayerOnBack = false))
    }

    @Test
    fun settingsGraphIncludesMainPageAndSubpages() {
        assertTrue(Screen.Settings.createRoute(fromDock = true).isSettingsGraphRoute())
        assertTrue(Screen.Settings.createRoute(fromDock = false).isSettingsGraphRoute())
        assertTrue(Screen.SettingsDetail.createRoute().isSettingsGraphRoute())
        assertTrue(Screen.LyricSettings.createRoute().isSettingsGraphRoute())
        assertTrue(Screen.AppearanceSubpage.createRoute("player").isSettingsGraphRoute())
        assertTrue(Screen.ScanSettings.createRoute(fromDock = true).isSettingsGraphRoute())
        assertTrue(Screen.SettingsWizard.route.isSettingsGraphRoute())
        assertTrue(Screen.SettingsMaintenance.route.isSettingsGraphRoute())
        assertTrue(Screen.NavidromeServerSettings.route.isSettingsGraphRoute())
        assertTrue(Screen.RemoteServerEditor.createRoute(com.ella.music.data.remote.RemoteMusicProvider.Navidrome).isSettingsGraphRoute())
        assertTrue(Screen.RemoteServerEditor.createRoute(com.ella.music.data.remote.RemoteMusicProvider.Navidrome, "server123").isSettingsGraphRoute())
        assertTrue(Screen.LxSourceSettings.route.isSettingsGraphRoute())
        assertTrue(Screen.PlayerShortcutSettings.createRoute().isSettingsGraphRoute())
        assertTrue(Screen.WebDav.route.isSettingsGraphRoute())
        assertFalse(Screen.Library.route.isSettingsGraphRoute())
        assertFalse(Screen.Home.route.isSettingsGraphRoute())
        assertFalse(Screen.AlbumDetail.createRoute(11L).isSettingsGraphRoute())
    }

    @Test
    fun rawSettingsTemplateIsNotADockTabUntilFromDockIsFilled() {
        assertFalse(Screen.Settings.route.isBottomDockRoute())
        assertEquals(null, Screen.Settings.route.toCurrentTabRoute())
    }

    @Test
    fun libraryAnalysisIsAUsableDockDestination() {
        assertTrue(Screen.LibraryAnalysis.route.isBottomDockRoute())
        assertEquals(Screen.LibraryAnalysis.route, Screen.LibraryAnalysis.route.toCurrentTabRoute())
    }

    @Test
    fun settingsChildNeverRestoresAsBottomDockState() {
        assertFalse(
            shouldRestoreBottomDockState(
                Screen.Settings.createRoute(fromDock = true),
                Screen.SettingsDetail.createRoute()
            )
        )
        assertTrue(shouldRestoreBottomDockState(Screen.Library.route, Screen.Home.route))
    }

    @Test
    fun settingsDockPopsSourceStackedOnSettings() {
        assertTrue(
            shouldPopStackedSourceToDockSettings(
                dockRoute = Screen.Settings.createRoute(fromDock = true),
                currentRoute = Screen.Library.route,
                previousRoute = Screen.AppearanceSubpage.createRoute("player")
            )
        )
        assertFalse(
            shouldPopStackedSourceToDockSettings(
                dockRoute = Screen.Settings.createRoute(fromDock = true),
                currentRoute = Screen.Library.route,
                previousRoute = Screen.Home.route
            )
        )
        assertFalse(
            shouldPopStackedSourceToDockSettings(
                dockRoute = Screen.Home.route,
                currentRoute = Screen.Library.route,
                previousRoute = Screen.AppearanceSubpage.createRoute("player")
            )
        )
    }
}
