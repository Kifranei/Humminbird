package com.ella.music.ui.components

import com.ella.music.ui.listmodel.SortDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortDropdownMenuTest {
    private enum class Mode { NameAsc, NameDesc, DurationAsc, DurationDesc }

    @Test
    fun everySortFieldKeepsBothDirectionalActionsAvailable() {
        val items = directionalSortModeDropdownItems(
            fields = listOf(
                DirectionalSortModeField("Name", Mode.NameAsc, Mode.NameDesc),
                DirectionalSortModeField("Duration", Mode.DurationAsc, Mode.DurationDesc)
            ),
            selectedMode = Mode.NameAsc,
            onSelect = {}
        )

        assertEquals(2, items.size)
        assertTrue(items[0].selected)
        assertEquals(SortDirection.Ascending, items[0].direction)
        items.forEach { item ->
            assertNotNull(item.onSelectAscending)
            assertNotNull(item.onSelectDescending)
        }
    }

    @Test
    fun selectedDescendingDirectionIsReportedForItsOwnField() {
        val items = directionalSortModeDropdownItems(
            fields = listOf(
                DirectionalSortModeField("Name", Mode.NameAsc, Mode.NameDesc),
                DirectionalSortModeField("Duration", Mode.DurationAsc, Mode.DurationDesc)
            ),
            selectedMode = Mode.DurationDesc,
            onSelect = {}
        )

        assertTrue(items[1].selected)
        assertEquals(SortDirection.Descending, items[1].direction)
        assertEquals(SortDirection.Ascending, items[0].direction)
    }
}
