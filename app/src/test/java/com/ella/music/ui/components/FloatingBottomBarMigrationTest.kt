package com.ella.music.ui.components

import android.app.Application
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class, qualifiers = "w412dp-h915dp")
class FloatingBottomBarMigrationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun tapsCommitOnceAndDragsChooseTheTargetWithTheRetainedContentApi() {
        val selected = mutableIntStateOf(0)
        val commits = mutableListOf<Int>()
        compose.setContent {
            MiuixTheme {
                val activate = { index: Int -> commits += index; selected.intValue = index }
                FloatingBottomBar(selectedIndex = { selected.intValue }, onSelected = activate,
                    backdrop = null, tabsCount = 3, modifier = Modifier.testTag("bar")) {
                    repeat(3) { i -> FloatingBottomBarItem({ activate(i) }, Modifier.testTag("tab-$i")) { BasicText("Tab $i") } }
                }
            }
        }
        compose.onNodeWithTag("tab-1").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(1), commits); assertEquals(1, selected.intValue) }
        compose.onNodeWithTag("bar").performTouchInput { swipeLeft(startX = width * 0.50f, endX = width * 0.08f) }
        compose.runOnIdle { assertEquals(0, selected.intValue); assertEquals(listOf(1, 0), commits) }
    }
}
