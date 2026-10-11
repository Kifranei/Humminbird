package com.ella.music

import android.app.Application
import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ella.music.ui.navigation.NavigationBackScope
import com.ella.music.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MainPageBackNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var nav: NavHostController
    private lateinit var state: MainPageState
    private lateinit var back: OnBackPressedDispatcher
    private val localMenu = mutableStateOf(false)
    private var exits = 0
    private var hiddenPageBacks = 0
    private var menuBacks = 0
    private val search = Screen.LibrarySearch.createRoute()

    private fun show(hiddenHome: Boolean = false) {
        compose.setContent {
            nav = rememberNavController()
            back = checkNotNull(LocalOnBackPressedDispatcherOwner.current).onBackPressedDispatcher
            state = rememberMainPageState(Screen.Library.route)
            val routes = (if (hiddenHome) emptyList() else listOf(Screen.Home.route)) + listOf(Screen.Library.route, search)
            state.routes = routes
            DisposableEffect(nav) {
                MainPageNavigation.register(nav, state)
                onDispose { MainPageNavigation.unregister(nav) }
            }
            BackHandler { exits++ }
            NavigationBackScope {
                NavHost(nav, MAIN_PAGES_ROUTE) {
                    composable(MAIN_PAGES_ROUTE) {
                        MainPagePager(state, routes, false, onBackToHome = {
                            if (!MainPageNavigation.exitSearch(nav)) MainPageNavigation.select(nav, Screen.Home.route)
                        }) { route ->
                            BackHandler(enabled = route.isSearchRoute() || localMenu.value) {
                                if (localMenu.value) { menuBacks++; localMenu.value = false }
                                else MainPageNavigation.exitSearch(nav)
                            }
                            Box(Modifier.fillMaxSize())
                        }
                    }
                    composable(Screen.Home.route) { Box(Modifier.fillMaxSize()) }
                    composable("detail") { BackHandler { nav.popBackStack() }; Box(Modifier.fillMaxSize()) }
                    composable("stacked_search") { BackHandler { nav.popBackStack() }; Box(Modifier.fillMaxSize()) }
                }
                // A composed neighbouring tab must not be able to steal the current tab's Back.
                MainPageBackScope(active = false) { BackHandler { hiddenPageBacks++ } }
            }
        }
        compose.waitForIdle()
    }

    private fun pressBack() { compose.runOnIdle { back.onBackPressed() }; compose.waitForIdle() }

    @Test fun tabRootReturnsHomeOnFirstBackAndHomeExitsOnTheNext() {
        show()
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Home.route, state.selectedRoute); assertEquals(0, exits); assertEquals(0, hiddenPageBacks) }
        pressBack()
        compose.runOnIdle { assertEquals(1, exits); assertEquals(0, hiddenPageBacks) }
    }

    @Test fun dockSearchReturnsToLibraryAndAnImmediateSecondBackCannotRestoreOldSearch() {
        show()
        compose.runOnIdle { MainPageNavigation.select(nav, search) }
        compose.waitForIdle()
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Library.route, state.selectedRoute); assertEquals(0, exits) }
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Home.route, state.selectedRoute); assertEquals(0, exits) }
    }

    @Test fun localMenusHavePriorityAndStackedSearchPopsItsActualSource() {
        show()
        compose.runOnIdle { localMenu.value = true }
        pressBack()
        compose.runOnIdle { assertEquals(1, menuBacks); assertEquals(Screen.Library.route, state.selectedRoute) }
        compose.runOnIdle { nav.navigate("detail"); nav.navigate("stacked_search") }
        compose.waitForIdle()
        pressBack()
        compose.runOnIdle { assertEquals("detail", nav.currentDestination?.route) }
        pressBack()
        compose.runOnIdle { assertEquals(MAIN_PAGES_ROUTE, nav.currentDestination?.route); assertEquals(Screen.Library.route, state.selectedRoute) }
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Home.route, state.selectedRoute); assertEquals(0, hiddenPageBacks) }
    }

    @Test fun homeRemainsTheRootDestinationWhenItsDockButtonIsHidden() {
        show(hiddenHome = true)
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Home.route, nav.currentDestination?.route); assertNull(nav.previousBackStackEntry) }
        compose.runOnIdle { MainPageNavigation.select(nav, search) }
        compose.waitForIdle()
        pressBack()
        compose.runOnIdle { assertEquals(Screen.Home.route, nav.currentDestination?.route); assertNull(nav.previousBackStackEntry); assertEquals(0, exits) }
        pressBack()
        compose.runOnIdle { assertEquals(1, exits) }
    }

    @Test fun pendingNavigationCannotBeOverwrittenByAnOlderSettledPagerFrame() {
        assertFalse(canSettleMainPage(false, false, requestedVersion = 2, settledVersion = 1))
        assertFalse(canSettleMainPage(false, true, requestedVersion = 2, settledVersion = 2))
        assertTrue(canSettleMainPage(false, false, requestedVersion = 2, settledVersion = 2))
    }
}
