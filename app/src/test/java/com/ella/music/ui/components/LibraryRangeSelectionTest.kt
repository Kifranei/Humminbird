package com.ella.music.ui.components

import org.junit.Assert.*
import org.junit.Test

class LibraryRangeSelectionTest {
    @org.junit.Test fun inversionPreservesOtherScopesAndRespectsPickerLimits() {
        val state = LibrarySelectionState<Int>().apply {
            selectionMode = true; selectedIds = setOf(1, 99); rangeAnchorId = 1; rangeTargetId = 3
        }
        state.invertSelection(listOf(1, 2, 2, 3))
        org.junit.Assert.assertEquals(setOf(2, 3, 99), state.selectedIds)
        org.junit.Assert.assertNull(state.rangeAnchorId)
        org.junit.Assert.assertNull(state.rangeTargetId)
        state.invertSelection(listOf(1, 2, 3))
        org.junit.Assert.assertEquals(setOf(1, 99), state.selectedIds)
        org.junit.Assert.assertTrue(state.selectionMode)
        org.junit.Assert.assertEquals(setOf(99, 2), invertedSelection(listOf(1, 2, 3, 4), setOf(1, 99), 2))
        state.selectedIds = setOf(1, 2, 3)
        state.invertSelection(listOf(1, 2, 3))
        org.junit.Assert.assertTrue(state.selectedIds.isEmpty() && state.selectionMode)
    }

    @Test fun rangePinsInDisplayOrderIncludingPreviouslySelectedEndpoints() {
        val ids = ('A'..'G').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        state.toggleSelection('B')
        state.toggleSelection('E')
        state.applyRangeSelection(ids, indexes)
        assertEquals("BCDE".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun ordinaryMultiSelectionStillPinsInTapOrder() {
        val state = LibrarySelectionState<Char>()
        "BECD".forEach(state::toggleSelection)
        assertEquals("BECD".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun reverseRangeKeepsOutsideSelectionsInTheirOriginalOrder() {
        val ids = ('A'..'G').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        "GEB".forEach(state::toggleSelection)
        state.applyRangeSelection(ids, indexes)
        assertEquals("GBCDE".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun rangesUseLastTwoManualTapsAndResetAfterEachRange() {
        val ids = ('A'..'N').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        state.toggleSelection('B')
        state.toggleSelection('E')
        state.applyRangeSelection(ids, indexes)
        state.toggleSelection('H')
        assertFalse(state.isRangeSelectionAvailable(indexes))
        state.toggleSelection('I')
        assertTrue(state.isRangeSelectionAvailable(indexes))
        state.toggleSelection('L')
        state.toggleSelection('N')
        state.applyRangeSelection(ids, indexes)
        assertEquals("BCDEHILMN".toSet(), state.selectedIds)
        assertFalse(state.isRangeSelectionAvailable(indexes))
    }

    @Test fun deselectingAnchorDoesNotReuseAnOlderCompletedRange() {
        val state = LibrarySelectionState<Char>()
        state.selectedIds = setOf('B', 'C', 'D')
        state.toggleSelection('H')
        state.toggleSelection('H')
        state.toggleSelection('L')
        assertNull(state.rangeTargetId)
    }
}
