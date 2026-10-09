package com.ella.music.plugin.source

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginTagMetadataMapperTest {

    @Test
    fun mapsSearchResultAndSupplementaryLyricsTagsToEditableAudioTags() {
        val hit = PluginSearchHit(
            sourceId = "source",
            sourceName = "Source",
            song = PluginSongSearchResult(
                id = "song",
                pluginId = "plugin",
                pluginName = "Plugin",
                title = "Matched title",
                artist = "Matched artist",
                album = "Matched album",
                date = "2024-02-03",
                trackNumber = "03/12",
                fields = mapOf("album_artist" to "Matched album artist")
            )
        )

        val tags = hit.toAudioTagInfo(
            mapOf(
                "genre" to "Pop",
                "discNumber" to "2/2",
                "composer" to "Composer",
                "lyricist" to "Lyricist"
            )
        )

        assertEquals("Matched title", tags.title)
        assertEquals("Matched artist", tags.artist)
        assertEquals("Matched album", tags.album)
        assertEquals("Matched album artist", tags.albumArtist)
        assertEquals("2024-02-03", tags.year)
        assertEquals(3, tags.trackNumber)
        assertEquals(2, tags.discNumber)
        assertEquals("Pop", tags.genre)
        assertEquals("Composer", tags.composer)
        assertEquals("Lyricist", tags.lyricist)
    }

    @Test
    fun fallsBackToLyricPayloadMetadataWhenSearchResultOmitsIt() {
        val hit = PluginSearchHit(
            sourceId = "source",
            sourceName = "Source",
            song = PluginSongSearchResult(
                id = "song",
                pluginId = "plugin",
                pluginName = "Plugin"
            )
        )

        val tags = hit.toAudioTagInfo(
            mapOf(
                "ti" to "Payload title",
                "ar" to "Payload artist",
                "al" to "Payload album",
                "date" to "2005",
                "track" to "8"
            )
        )

        assertEquals("Payload title", tags.title)
        assertEquals("Payload artist", tags.artist)
        assertEquals("Payload album", tags.album)
        assertEquals("2005", tags.year)
        assertEquals(8, tags.trackNumber)
    }
}
