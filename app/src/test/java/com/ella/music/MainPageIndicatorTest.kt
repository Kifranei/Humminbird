package com.ella.music

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.ella.music.ui.components.FloatingBottomBar
import com.ella.music.ui.components.FloatingBottomBarItem
import com.ella.music.ui.components.LocalFloatingBottomBarIsDragging
import com.ella.music.ui.components.LocalFloatingBottomBarActiveIndex
import com.ella.music.ui.navigation.NavigationBackScope
import com.ella.music.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class, qualifiers = "w412dp-h915dp")
class MainPageIndicatorTest {
    @get:Rule val compose = createComposeRule()

    @Test fun pageSwipeSettlesWithoutPressAnimationAndBarDragNavigatesOnlyAfterRelease() {
        val routes = listOf(Screen.Home.route, Screen.Library.route, Screen.Settings.route)
        val state = MainPageState(routes.first()).apply { this.routes = routes }
        var visualIndex = -1
        var trackingPageSwipe = true
        var pressDuringPageSwipe = false
        compose.setContent {
            MiuixTheme {
                Column(Modifier.fillMaxSize()) {
                    NavigationBackScope {
                        MainPagePager(state, routes, true, Modifier.weight(1f).testTag("pager")) { BasicText(it) }
                    }
                    FloatingBottomBar(selectedIndex = { routes.indexOf(state.selectedRoute) },
                        onSelected = { state.select(routes[it]) }, backdrop = null, tabsCount = 3,
                        modifier = Modifier.testTag("bar")) {
                        val active = LocalFloatingBottomBarActiveIndex.current
                        val dragging = LocalFloatingBottomBarIsDragging.current
                        SideEffect {
                            visualIndex = active
                            if (trackingPageSwipe && dragging) pressDuringPageSwipe = true
                        }
                        routes.forEach { route -> FloatingBottomBarItem({ state.select(route) }) { BasicText(route) } }
                    }
                }
            }
        }
        compose.onNodeWithTag("pager").performTouchInput {
            down(Offset(width * 0.85f, height * 0.5f))
            moveTo(Offset(width * 0.25f, height * 0.5f), delayMillis = 300)
        }
        compose.runOnIdle {
            assertEquals(0, visualIndex)
            assertEquals(routes[0], state.selectedRoute)
            assertEquals(0, state.requestVersion)
        }
        compose.onNodeWithTag("pager").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(routes[1], state.selectedRoute)
            assertEquals(1, visualIndex)
            assertFalse("Page navigation must not synthesize a bar press", pressDuringPageSwipe)
            trackingPageSwipe = false
        }
        compose.onNodeWithTag("bar").performTouchInput {
            down(Offset(width * 0.50f, height * 0.5f))
            moveTo(Offset(width * 0.90f, height * 0.5f), delayMillis = 300)
        }
        compose.runOnIdle {
            assertEquals(2, visualIndex)
            assertEquals(routes[1], state.selectedRoute)
            assertEquals(0, state.requestVersion)
        }
        compose.onNodeWithTag("bar").performTouchInput { up() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(routes[2], state.selectedRoute)
            assertEquals(1, state.requestVersion)
            assertEquals(2, visualIndex)
        }
    }
}
