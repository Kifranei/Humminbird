package com.ella.music.ui.settings

import android.app.Application
import androidx.datastore.preferences.core.edit
import com.ella.music.data.dataStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.netease.NeteaseLinks
import com.ella.music.data.netease.NeteaseLinkTarget
import com.ella.music.data.netease.NeteaseLinkKind
import com.ella.music.ui.folder.FolderDisplaySettings
import com.ella.music.ui.folder.FolderDisplayStore
import com.ella.music.ui.about.UpdateChannelPreferences
import com.ella.music.MusicVideoCaptionStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsBackupRoundTripTest {
    @Before @After
    fun clearSettingsSingletonBetweenApplications() {
        SettingsManager::class.java.getDeclaredField("instance").apply { isAccessible = true }.set(null, null)
        FolderDisplayStore.state = null
    }
    @Test @Config(qualifiers = "sw800dp-w1280dp-h800dp-land")
    fun tabletDefaultsAndExplicitShortcutChoicesDeduplicateFeatureBlocks() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.dataStore.edit { it.clear() }
        val settings = SettingsManager.getInstance(context)
        val defaultShortcuts = settings.homeShortcutItems.first().split(',')
        assertEquals(8, defaultShortcuts.size)
        org.junit.Assert.assertTrue(defaultShortcuts.intersect(settings.homeFeatureItems.first().split(',').toSet()).isEmpty())
        settings.setHomeShortcutItems("artist,genre,year")
        settings.setHomeFeatureItems("artist,album,genre,year,composer")
        assertEquals("album,composer", settings.homeFeatureItems.first())
        val backup = settings.exportSettingsJson()
        settings.setHomeShortcutItems("album")
        settings.restoreSettingsJson(backup)
        assertEquals("artist,genre,year", settings.homeShortcutItems.first())
        assertEquals("album,composer", settings.homeFeatureItems.first())
        settings.setHomeShortcutItems("artist")
        assertEquals("album,genre,year,composer", settings.homeFeatureItems.first())
    }

    @Test fun customCategoryRenameDeleteAndLibraryOnlyRestoreMaintainHomeEntries() = runBlocking {
        val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
        val category = com.ella.music.data.CustomMetadataCategory("编曲家", "ARRANGER")
        settings.setCustomMetadataCategories(listOf(category))
        settings.setHomeFeatureItems("genre,${category.type},year")
        settings.setCustomMetadataCategories(listOf(category.copy(name = "Arrangers")))
        assertEquals("genre,${category.type},year", settings.homeFeatureItems.first())
        val backup = settings.exportSettingsJson().filterBackupSettings(setOf(BackupType.LibraryAndScan))
        assertEquals(BackupType.LibraryAndScan, "custom_metadata_categories".backupType())
        settings.setCustomMetadataCategories(emptyList())
        assertEquals("genre,year", settings.homeFeatureItems.first())
        settings.restoreSettingsJson(backup)
        assertEquals(listOf(category.copy(name = "Arrangers")), settings.customMetadataCategories.first())
        assertEquals("genre,year,${category.type}", settings.homeFeatureItems.first())
    }

    @Test fun legacySearchCategoriesEnableFolderPlaylistsAndRespectAnExplicitDisableAfterRestore() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        context.dataStore.edit { prefs ->
            prefs[SettingsManager.KEY_SEARCH_ALL_CATEGORY_TYPES] = "folder,genre"
            prefs.remove(SettingsManager.KEY_SEARCH_ALL_CATEGORY_TYPES_VERSION)
        }
        assertEquals(setOf("folder", "genre", "folder_playlist"), settings.searchAllCategoryTypes.first())
        settings.setSearchAllCategoryTypeEnabled("folder_playlist", false)
        assertEquals(setOf("folder", "genre"), settings.searchAllCategoryTypes.first())
        val backup = settings.exportSettingsJson()
        settings.setSearchAllCategoryTypeEnabled("folder_playlist", true)
        settings.restoreSettingsJson(backup)
        assertEquals(setOf("folder", "genre"), settings.searchAllCategoryTypes.first())
        settings.restoreSettingsJson(org.json.JSONObject().put("search_all_category_types", "folder"))
        assertEquals(setOf("folder", "folder_playlist"), settings.searchAllCategoryTypes.first())
    }

    @Test fun newExportStackAndSmbPreferencesSurviveSettingsRoundTrip() = runBlocking {
        val settings = SettingsManager.getInstance(RuntimeEnvironment.getApplication())
        settings.setArtistImageSaveLocation("content://artist-directory")
        settings.setMusicVideoDownloadLocation("folder")
        settings.setMusicVideoDownloadTemplate("{artist} - {title}_{quality}({mvId})")
        settings.setPosterWallStacked(true)
        settings.setPosterWallSource("library")
        val posters = (0 until 15).map { index ->
            com.ella.music.ui.poster.PosterWallItem(
                com.ella.music.data.model.Song(index.toLong(), "Song $index", "Artist", "Album", 0, 1000, "/$index", "$index"), index, "$index")
        }
        var layout = com.ella.music.ui.poster.PosterStackLayout.resolve(posters, "")
        for (key in (3..9).map(Int::toString)) layout = layout.move(key, "0")!!
        assertEquals(10, layout.members("0").size)
        assertEquals(null, layout.move("10", "0"))
        // A full group still accepts ordering changes, including insertion at the bottom.
        layout = layout.move("0", layout.members("0").last())!!
        assertEquals("0", layout.members("0").last())
        val bottom = layout.members("0").first()
        layout = layout.move("0", bottom, before = true)!!
        assertEquals(listOf("0", bottom), layout.members("0").take(2))
        layout = layout.move("2", "0")!!
        assertEquals(listOf("0", "2", bottom), layout.members("0").take(3))
        assertEquals(null, layout.move("2", "0"))
        assertEquals(15, layout.groups.flatten().distinct().size)
        assertEquals(null, layout.move("10", "0", before = true))
        settings.setPosterWallStackLayout("local:library", layout.encode())
        settings.setPosterWallStackLayout("smb:library", "[[\"smb-track\"]]")
        settings.setSpectrumViewerId("ask_each_time")
        settings.setSmbConfig("smb://server/Music", "listener", "WORKGROUP")
        settings.setSmbProtocol("smb3")
        val backup = settings.exportSettingsJson()
        settings.setArtistImageSaveLocation("default")
        settings.setMusicVideoDownloadLocation("default")
        settings.setMusicVideoDownloadTemplate("{title}")
        settings.setPosterWallStacked(false)
        settings.setPosterWallSource("queue")
        settings.setPosterWallStackLayout("local:library", "[]")
        settings.setSpectrumViewerId("aspect_pro")
        settings.setSmbConfig("", "", "")
        settings.setSmbProtocol("auto")
        settings.restoreSettingsJson(backup)
        assertEquals("content://artist-directory", settings.artistImageSaveLocation.first())
        assertEquals("folder", settings.musicVideoDownloadLocation.first())
        assertEquals("{artist} - {title}_{quality}({mvId})", settings.musicVideoDownloadTemplate.first())
        assertEquals(true, settings.posterWallStacked.first())
        assertEquals("library", settings.posterWallSource.first())
        val restored = org.json.JSONObject(settings.posterWallStackLayouts.first())
        val grouped = com.ella.music.ui.poster.PosterStackLayout.resolve(posters.reversed(), restored.getJSONArray("local:library").toString())
        assertEquals(layout, grouped)
        assertEquals(posters.map { it.stackKey }.toSet(), grouped.groups.flatten().toSet())
        assertEquals("smb-track", restored.getJSONArray("smb:library").getJSONArray(0).getString(0))
        assertEquals("ask_each_time", settings.spectrumViewerId.first())
        assertEquals("smb3", settings.smbProtocol.first())
        assertEquals("smb://server/Music", settings.smbUrl.first())
        assertEquals("listener", settings.smbUsername.first())
        assertEquals("WORKGROUP", settings.smbDomain.first())
        assertEquals(false, backup.has("smb_password"))
        val onlineBackup = backup.filterBackupSettings(setOf(BackupType.OnlineSources))
        assertEquals("smb://server/Music", onlineBackup.getString("smb_url"))
        assertEquals(false, backup.filterBackupSettings(setOf(BackupType.Personalization)).has("smb_url"))
    }
    @Test fun changingLastFmRegionPreservesMigratedSpotifyMarket() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager(context)
        context.dataStore.edit {
            it.remove(SettingsManager.KEY_ARTIST_SPOTIFY_REGION)
            it[SettingsManager.KEY_ARTIST_IMAGE_REGION] = "zh-hk"
        }
        assertEquals("HK", settings.artistSpotifyRegion.first())
        settings.setArtistImageRegion("ja")
        assertEquals("ja", settings.artistImageRegion.first())
        assertEquals("HK", settings.artistSpotifyRegion.first())
        settings.restoreSettingsJson(org.json.JSONObject().put("artist_image_region", "zh-tw"))
        assertEquals("zh", settings.artistImageRegion.first())
        assertEquals("TW", settings.artistSpotifyRegion.first())
    }
    @Test fun incompatibleValuesDoNotEraseCompatiblePreferences() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        val captions = context.getSharedPreferences("music_video_caption_preferences", 0)
        captions.edit().putFloat("font_size_sp", 28f).putLong("sync_bad", 1000L).apply()
        settings.setPlayerMiniLyricScale(137)
        settings.setPlayerActionMenuLayout("song_info,poster_wall;speed")
        settings.restoreSettingsJson(org.json.JSONObject()
            .put("player_mini_lyric_scale", "invalid")
            .put("player_action_menu_layout", org.json.JSONArray())
            .put("player_mini_lyric_primary_size", 28)
            .put("music_video_caption_settings_json", """{"font_size_sp":"invalid","bold":false,"sync_bad":1.5}"""))
        assertEquals(137, settings.playerMiniLyricScale.first())
        assertEquals("song_info,poster_wall;speed", settings.playerActionMenuLayout.first())
        assertEquals(28, settings.playerMiniLyricPrimarySize.first())
        assertEquals(28f, captions.getFloat("font_size_sp", 21f), 0f)
        assertEquals(false, captions.getBoolean("bold", true))
        assertEquals(1000L, captions.getLong("sync_bad", 0L))
    }
    @Test fun jsonRestoresActionOrderAndAllMiniLyricValues() = roundTrip(false)
    @Test fun zipRestoresActionOrderAndAllMiniLyricValues() = roundTrip(true)

    private fun roundTrip(zip: Boolean) = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        val layout = ActionMenuLayout(ActionMenuIds.playerActionMenuDefaults.reversed(), setOf(ActionMenuIds.SPEED)).serialize()
        settings.setPlayerActionMenuLayout(layout)
        settings.setPlayerMiniLyricScale(137)
        settings.setPlayerMiniLyricPrimarySize(28)
        settings.setPlayerMiniLyricSecondarySize(21)
        settings.setPlayerMiniLyricLineSpacing(24)
        settings.setPlayerMiniLyricTextAlign(2)
        settings.setReplayGainPreampDb(2.35f)
        settings.setAppleMusicLyricsWordLift(false)
        settings.setDesktopLyricWordLift(true)
        settings.setSpotifyCanvasEnabled(true)
        settings.setArtistImageRegion("zh")
        settings.setArtistSpotifyRegion("HK")
        FolderDisplayStore.update(context, FolderDisplaySettings(85, 35))
        UpdateChannelPreferences.setIncludesPrereleases(context, true)
        val captionPreferences = context.getSharedPreferences("music_video_caption_preferences", 0)
        val captionStyle = MusicVideoCaptionStyle(fontSizeSp = 28f, positionY = 0.68f, textColorArgb = 0xFFAABBCC.toInt())
        captionStyle.save(captionPreferences)
        captionPreferences.edit().putBoolean("captions_enabled", true).putLong("sync_abcdef", 2350L).apply()
        context.getSharedPreferences("playback_widget", 0).edit().putBoolean("safe_layout", true).apply()
        val custom = "intent://song/{id}#Intent;scheme=test;end"
        NeteaseLinks.update(context) { it.copy(target = NeteaseLinkTarget.Custom, custom = mapOf(NeteaseLinkKind.SongWiki to custom)) }
        val customCategories = listOf(com.ella.music.data.CustomMetadataCategory("编曲家", "ARRANGER"),
            com.ella.music.data.CustomMetadataCategory("情绪", "MOOD"))
        settings.setCustomMetadataCategories(customCategories)
        val customOrder = "${customCategories[1].type},genre,${customCategories[0].type}"
        settings.setHomeFeatureItems(customOrder)
        settings.setPinned("category:${customCategories[0].type}", "Artist A", true)
        val types = setOf(BackupType.Personalization, BackupType.OnlineSources, BackupType.LibraryAndScan)
        val file = if (zip) buildApplicationBackupZipFile(context, types) else {
            File.createTempFile("settings-backup-", ".json", context.cacheDir).apply {
                writeText(buildApplicationBackupJson(context, types).toString(), Charsets.UTF_8)
            }
        }
        try {
            settings.setCustomMetadataCategories(emptyList())
            settings.setHomeFeatureItems("genre")
            settings.setPinned("category:${customCategories[0].type}", "Artist A", false)
            settings.setPlayerActionMenuLayout("")
            settings.setPlayerMiniLyricScale(100)
            settings.setPlayerMiniLyricPrimarySize(19)
            settings.setPlayerMiniLyricSecondarySize(14)
            settings.setPlayerMiniLyricLineSpacing(7)
            settings.setPlayerMiniLyricTextAlign(0)
            settings.setReplayGainPreampDb(0f)
            settings.setDesktopLyricWordLift(false)
            settings.setSpotifyCanvasEnabled(false)
            settings.setArtistImageRegion("en")
            settings.setArtistSpotifyRegion("US")
            FolderDisplayStore.update(context, FolderDisplaySettings())
            UpdateChannelPreferences.setIncludesPrereleases(context, false)
            captionPreferences.edit().clear().apply()
            context.getSharedPreferences("playback_widget", 0).edit().putBoolean("safe_layout", false).apply()
            NeteaseLinks.update(context) { it.copy(target = NeteaseLinkTarget.Web, custom = emptyMap()) }
            val backup = readApplicationBackupFile(context, file)
            assertEquals("custom", backup.getJSONObject("settings").getString("netease_link_target"))
            restoreApplicationBackup(context, backup, types)
            assertEquals(customCategories, settings.customMetadataCategories.first())
            assertEquals(customOrder, settings.homeFeatureItems.first())
            assertEquals(listOf("Artist A"), settings.pinnedKeysFlow("category:${customCategories[0].type}").first())
            assertEquals(layout, settings.playerActionMenuLayout.first())
            assertEquals(137, settings.playerMiniLyricScale.first())
            assertEquals(28, settings.playerMiniLyricPrimarySize.first())
            assertEquals(21, settings.playerMiniLyricSecondarySize.first())
            assertEquals(24, settings.playerMiniLyricLineSpacing.first())
            assertEquals(2, settings.playerMiniLyricTextAlign.first())
            assertEquals(2.35f, settings.replayGainPreampDb.first(), 0.001f)
            assertEquals(false, settings.appleMusicLyricsWordLift.first())
            assertEquals(true, settings.desktopLyricWordLift.first())
            assertEquals(true, settings.spotifyCanvasEnabled.first())
            assertEquals("zh", settings.artistImageRegion.first())
            assertEquals("HK", settings.artistSpotifyRegion.first())
            assertEquals(FolderDisplaySettings(85, 35), FolderDisplayStore.get(context).value)
            assertEquals(true, UpdateChannelPreferences.includesPrereleases(context))
            assertEquals(captionStyle, MusicVideoCaptionStyle.load(captionPreferences))
            assertEquals(true, captionPreferences.getBoolean("captions_enabled", false))
            assertEquals(2350L, captionPreferences.getLong("sync_abcdef", 0L))
            assertEquals(true, context.getSharedPreferences("playback_widget", 0).getBoolean("safe_layout", false))
            assertEquals(NeteaseLinkTarget.Custom, NeteaseLinks.current(context).target)
            assertEquals(custom, NeteaseLinks.current(context).custom[NeteaseLinkKind.SongWiki])
        } finally { file.delete() }
    }
}
