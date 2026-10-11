package com.ella.music.ui.components

import android.app.Application
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], application = Application::class, qualifiers = "w412dp-h915dp")
@OptIn(ExperimentalFoundationApi::class)
class DragSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun selectionRowInvertsOnlyItsVisibleScopeAndCanInvertBack() {
        val selection = LibrarySelectionState<Int>().apply { selectionMode = true; selectedIds = setOf(1, 99) }
        compose.setContent {
            top.yukonga.miuix.kmp.theme.MiuixTheme {
                SongSelectionActionRow(selection.selectedIds.size, 3, false, false,
                    onRangeSelect = {}, onSelectAll = { selection.toggleSelectAll(listOf(1, 2, 3)) },
                    onInvertSelection = { selection.invertSelection(listOf(1, 2, 3)) })
            }
        }
        val label = org.robolectric.RuntimeEnvironment.getApplication().getString(com.ella.music.R.string.common_invert_selection)
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(setOf(2, 3, 99), selection.selectedIds) }
        compose.onNodeWithText(label).performClick()
        compose.runOnIdle { assertEquals(setOf(1, 99), selection.selectedIds) }
    }

    @Test fun rangeRestoresEarlierPicksOnReversalAndSelectedAnchorRemovesOnlyItsRange() {
        val add = DragSelectionRange((0..10).toList(), setOf(1, 8), 3)
        assertEquals(setOf(1, 3, 4, 5, 6, 8), add.update(6))
        assertEquals(setOf(1, 3, 4, 8), add.update(4))
        assertNull(add.update(4))
        val remove = DragSelectionRange((0..10).toList(), setOf(1, 3, 4, 5, 8), 3)
        assertEquals(setOf(1, 8), remove.update(5))
        assertEquals(setOf(1, 4, 5, 8), remove.update(3))
        assertEquals(0f, dragSelectionScrollSpeed(150f, 0f, 300f, 64f, 900f), 0f)
        assertTrue(dragSelectionScrollSpeed(290f, 0f, 300f, 64f, 900f) > dragSelectionScrollSpeed(250f, 0f, 300f, 64f, 900f))
        assertEquals(-900f, dragSelectionScrollSpeed(-10f, 0f, 300f, 64f, 900f), 0f)
    }

    @Test fun holdSelectsRowsWithoutDoubleTogglingAndShortMovementStillScrolls() {
        val selection = LibrarySelectionState<Int>().apply { selectionMode = true; selectedIds = setOf(18) }
        var childHolds = 0
        lateinit var state: androidx.compose.foundation.lazy.LazyListState
        val ids = (0..40).toList()
        compose.setContent {
            state = rememberLazyListState()
            LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(400.dp).testTag("list")
                .dragSelectList(state, selection, ids)) {
                item(key = "header") { BasicText("Header", Modifier.height(40.dp)) }
                items(ids, key = { it }) { id ->
                    BasicText("$id", Modifier.fillMaxWidth().height(50.dp)
                        .combinedClickable(onClick = { selection.toggleSelection(id) }, onLongClick = { childHolds++; selection.toggleSelection(id) }))
                }
            }
        }
        compose.onNodeWithTag("list").performTouchInput { down(Offset(width * 0.5f, height * 0.30f)) }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithTag("list").performTouchInput { moveTo(Offset(width * 0.5f, height * 0.55f), delayMillis = 100); up() }
        compose.runOnIdle { assertEquals(setOf(1, 2, 3, 18), selection.selectedIds); assertEquals(0, childHolds) }
        compose.onNodeWithTag("list").performTouchInput { click(Offset(width * 0.5f, height * 0.16f)) }
        compose.runOnIdle { assertTrue(0 in selection.selectedIds) }
        compose.onNodeWithTag("list").performTouchInput { click(Offset(width * 0.5f, height * 0.16f)) }
        compose.runOnIdle { assertFalse(0 in selection.selectedIds) }
        val before = selection.selectedIds
        compose.onNodeWithTag("list").performTouchInput { swipeUp(durationMillis = 200) }
        compose.runOnIdle { assertEquals(before, selection.selectedIds); assertTrue(state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0) }
    }

    @Test fun groupedSongRowsHitTheActualColumnIncludingRtl() {
        val picked = mutableStateOf(setOf(9))
        val ids = (0..9).toList()
        val rows = ids.chunked(2)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                val state = rememberLazyListState()
                LazyColumn(state = state, modifier = Modifier.fillMaxWidth().height(400.dp).testTag("grid-rows")
                    .dragSelectList(state, true, ids, picked.value, { picked.value = it },
                        itemIdsByKey = rows.associate { (it.first() as Any) to it }, columns = 2)) {
                    items(rows, key = { it.first() }) { row ->
                        Row(Modifier.fillMaxWidth().height(80.dp)) { row.forEach { BasicText("$it", Modifier.weight(1f)) } }
                    }
                }
            }
        }
        compose.onNodeWithTag("grid-rows").performTouchInput { down(Offset(width * 0.25f, height * 0.30f)) }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithTag("grid-rows").performTouchInput { moveTo(Offset(width * 0.75f, height * 0.50f), delayMillis = 100); up() }
        compose.runOnIdle { assertEquals(setOf(3, 4, 9), picked.value) }
    }

    @Test fun gridAndHeldEdgeScrollSelectItemsOutsideTheInitialViewport() {
        val selection = LibrarySelectionState<Int>().apply { selectionMode = true }
        lateinit var state: androidx.compose.foundation.lazy.grid.LazyGridState
        val ids = (0..99).toList()
        compose.setContent {
            state = rememberLazyGridState()
            LazyVerticalGrid(GridCells.Fixed(2), state = state,
                modifier = Modifier.fillMaxWidth().height(400.dp).testTag("grid").dragSelectGrid(state, selection, ids)) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) { BasicText("Header", Modifier.height(60.dp)) }
                items(ids, key = { it }) { BasicText("$it", Modifier.height(80.dp)) }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("grid").performTouchInput { down(Offset(width * 0.75f, height * 0.30f)) }
        compose.mainClock.advanceTimeBy(650)
        compose.onNodeWithTag("grid").performTouchInput { moveTo(Offset(width * 0.75f, height * 0.98f), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertTrue(state.firstVisibleItemIndex >= 2); assertTrue(selection.selectedIds.any { it >= 10 }) }
        compose.onNodeWithTag("grid").performTouchInput { moveTo(Offset(width * 0.25f, height * 0.01f), delayMillis = 100) }
        compose.mainClock.advanceTimeBy(1800)
        compose.runOnIdle { assertEquals(0, state.firstVisibleItemIndex); assertTrue(0 in selection.selectedIds) }
        compose.onNodeWithTag("grid").performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }
}
