package com.ella.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTitlePresentationTest {
    @Test
    fun stripsBracketedExplicitMarkerAndAddsAdvisoryFlag() {
        val presentation = "Song Name (Explicit)".toSongTitlePresentation()

        assertEquals("Song Name", presentation.text)
        assertTrue(presentation.isExplicit)
    }

    @Test
    fun stripsSeparatorExplicitMarkerAndKeepsTitleSpacingClean() {
        val presentation = "Song Name - Explicit".toSongTitlePresentation()

        assertEquals("Song Name", presentation.text)
        assertTrue(presentation.isExplicit)
    }

    @Test
    fun doesNotTreatExplicitlyAsAnExplicitMarker() {
        val presentation = "Explicitly Yours".toSongTitlePresentation()

        assertEquals("Explicitly Yours", presentation.text)
        assertFalse(presentation.isExplicit)
    }

    @Test
    fun leavesOrdinaryTitlesUntouched() {
        val presentation = "Song Name".toSongTitlePresentation()

        assertEquals("Song Name", presentation.text)
        assertFalse(presentation.isExplicit)
    }
}
