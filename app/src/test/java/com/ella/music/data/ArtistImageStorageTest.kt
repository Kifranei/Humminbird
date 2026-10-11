package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistImageStorageTest {
    @Test fun savedArtistImagesOnlyAddSuffixesForCaseInsensitiveCollisions() {
        val names = mutableListOf<String>()
        val saved = listOf("LiSA", "LiSA", "LISA", "LISA").map { artist ->
            uniqueMediaExportName("$artist.png", names).also(names::add)
        }
        assertEquals(listOf("LiSA.png", "LiSA (1).png", "LISA (2).png", "LISA (3).png"), saved)
    }
    @Test fun mvTemplatesPreserveMetadataAndReplaceReservedFilenameCharacters() {
        assertEquals("Artist - Title_1080(42).mp4", musicVideoExportName("{artist} - {title}_{quality}({mvId})", "Artist", "Title", "Album", "1080", "42"))
        assertEquals("Title - Artist - Album.mp4", musicVideoExportName("{title} - {artist} - {album}", "Artist", "Title", "Album", "720", "42"))
        org.junit.Assert.assertTrue(sanitizeMediaExportName("歌".repeat(200)).toByteArray(Charsets.UTF_8).size <= 210)
        assertEquals("／＼：＊？｜〈〉'", sanitizeMediaExportName("/\\:*?|<>\""))
    }

    @Test fun spotifyUsesTheLargestAvailableImageInsteadOfResponseOrdering() {
        val json = """{"artists":{"items":[{"name":"LISA","images":[{"url":"https://image.test/small.jpg","width":160,"height":160},{"url":"https://image.test/large.jpg","width":640,"height":640}]}]}}"""
        assertEquals("https://image.test/large.jpg", ArtistImageRepository.parseSpotifyArtistImageUrl(json, "LISA"))
    }
    @Test fun differentArtistCasingReservesAnotherImageAndItsSourceSidecar() {
        assertEquals("LiSA (1).jpg", nextArtistImageFileName("LiSA", "jpg", listOf("LISA.jpg", "LISA.jpg.source")))
        assertEquals("LiSA (2).jpg", nextArtistImageFileName("LiSA", "jpg", listOf("LISA.jpg", "lisa (1).jpg.source")))
        val dir = java.nio.file.Files.createTempDirectory("artist-case-").toFile()
        try {
            java.io.File(dir, "LISA.jpg").writeText("first artist")
            java.io.File(dir, "LiSA (1).jpg").writeText("second artist")
            assertEquals(listOf("LISA.jpg"), matchingArtistImageFiles(dir, "LISA").map { it.name })
            assertEquals(listOf("LiSA (1).jpg"), matchingArtistImageFiles(dir, "LiSA").map { it.name })
            assertEquals("first artist", java.io.File(dir, "LISA.jpg").readText())
        } finally { dir.listFiles().orEmpty().forEach { it.delete() }; dir.delete() }
    }

    @Test fun imageProvidersPreferLargerVariantsAndRetainTheOriginalFallback() {
        val url = "https://lastfm.freetls.fastly.net/i/u/300x300/image.jpg"
        assertEquals(listOf("https://lastfm.freetls.fastly.net/i/u/770x0/image.jpg", url), preferredArtistImageUrls(SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM, url))
        val netease = "https://p1.music.126.net/cover.jpg?param=100y100"
        assertEquals(listOf("https://p1.music.126.net/cover.jpg?param=1000y1000", netease), preferredArtistImageUrls(SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE, netease))
    }
    @Test fun internalStorageNeverFallsBackToTheCoverFolder() {
        assertEquals("", artistImageDownloadFolder(ARTIST_IMAGE_INTERNAL_STORAGE, "content://covers"))
        assertEquals("content://covers", artistImageDownloadFolder("", "content://covers"))
        assertEquals("content://downloads", artistImageDownloadFolder("content://downloads", "content://covers"))
    }
}
