package com.ella.music.ui.player

import android.app.Application
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.SemanticsActions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.defaultTextStyles
import org.junit.Test
import androidx.navigation.compose.composable

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w840dp-h400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerScreenPageHostTest {
    @Test fun mainTabPagingKeepsOneNavigationEntryAndRestoresEachPagesSavedState() {
        lateinit var nav: androidx.navigation.NavHostController
        lateinit var state: com.ella.music.MainPageState
        val routes = listOf("home", "library", "settings?fromDock=true")
        val restoration = androidx.compose.ui.test.junit4.StateRestorationTester(compose)
        restoration.setContent {
            MiuixTheme {
                nav = androidx.navigation.compose.rememberNavController()
                state = com.ella.music.rememberMainPageState("home")
                state.routes = routes
                androidx.compose.runtime.DisposableEffect(nav) {
                    com.ella.music.MainPageNavigation.register(nav, state)
                    onDispose { com.ella.music.MainPageNavigation.unregister(nav) }
                }
                Box(Modifier.size(500.dp, 260.dp)) {
                    androidx.navigation.compose.NavHost(nav, com.ella.music.MAIN_PAGES_ROUTE) {
                        composable(com.ella.music.MAIN_PAGES_ROUTE) {
                            com.ella.music.MainPagePager(state, routes, true, Modifier.testTag("main-tabs")) { route ->
                                var count by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
                                androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(Color.DarkGray)) {
                                    top.yukonga.miuix.kmp.basic.Text("$route:$count", Modifier.testTag("tab-value"))
                                    top.yukonga.miuix.kmp.basic.Text("Increment", Modifier.clickable { count++ })
                                }
                            }
                        }
                        composable("detail") { top.yukonga.miuix.kmp.basic.Text("Detail") }
                    }
                }
            }
        }
        compose.waitForIdle()
        val entryId = nav.currentBackStackEntry!!.id
        compose.onNodeWithText("Increment").performClick()
        compose.onNodeWithText("home:1").assertExists()
        compose.runOnIdle { com.ella.music.MainPageNavigation.select(nav, routes[2]) }
        compose.waitForIdle()
        compose.onNodeWithText("settings?fromDock=true:0").assertExists()
        compose.runOnIdle {
            assertEquals(entryId, nav.currentBackStackEntry!!.id)
            assertNull(nav.previousBackStackEntry)
            com.ella.music.MainPageNavigation.select(nav, "home")
        }
        compose.waitForIdle()
        compose.onNodeWithText("home:1").assertExists()
        compose.onNodeWithTag("main-tabs").performTouchInput {
            swipeLeft(durationMillis = 350)
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals("library", state.selectedRoute)
            assertEquals(entryId, nav.currentBackStackEntry!!.id)
            nav.navigate("detail")
        }
        compose.waitForIdle()
        compose.onNodeWithText("Detail").assertExists()
        compose.runOnIdle { com.ella.music.MainPageNavigation.select(nav, "home") }
        compose.waitForIdle()
        compose.onNodeWithText("home:1").assertExists()
        compose.runOnIdle { assertEquals(entryId, nav.currentBackStackEntry!!.id); assertNull(nav.previousBackStackEntry) }
        compose.runOnIdle { com.ella.music.MainPageNavigation.select(nav, routes[2]) }
        compose.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText("settings?fromDock=true:0").assertExists()
        compose.runOnIdle { com.ella.music.MainPageNavigation.select(nav, "home") }
        compose.waitForIdle()
        compose.onNodeWithText("home:1").assertExists()
    }

    @Test fun audioOutputSectionTitlesAndValuesShareTheSettingsTextInset() {
        val rtl = mutableStateOf(false)
        compose.setContent {
            MiuixTheme {
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (rtl.value) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    androidx.compose.foundation.layout.Column(Modifier.size(500.dp, 200.dp).testTag("output-alignment")) {
                        AudioOutputInfoSection("Source") { AudioOutputInfoRow("File", "Example.m4a") }
                    }
                }
            }
        }
        for (direction in listOf(false, true)) {
            compose.runOnIdle { rtl.value = direction }
            compose.waitForIdle()
            val bounds = listOf("Source", "File", "Example.m4a").map { compose.onNodeWithText(it).fetchSemanticsNode().boundsInRoot }
            if (direction) {
                assertEquals(bounds[0].right, bounds[1].right, 0.1f)
                assertEquals(bounds[0].right, bounds[2].right, 0.1f)
            } else {
                assertEquals(bounds[0].left, bounds[1].left, 0.1f)
                assertEquals(bounds[0].left, bounds[2].left, 0.1f)
                val output = java.io.File("build/outputs/issues-710-714-20261011/audio-output-alignment.png")
                output.parentFile?.mkdirs()
                output.outputStream().use { compose.onNodeWithTag("output-alignment").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }

    @Test fun libraryMiniPlayersReplaceArtworkWhenSongsAreSelectedWithoutOpeningThePlayer() {
        fun cover(color: Int) = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
            .apply { eraseColor(color) }
        val covers = listOf(cover(android.graphics.Color.RED), cover(android.graphics.Color.GREEN), cover(android.graphics.Color.BLUE))
        val songs = (0..2).map { index ->
            com.ella.music.data.model.Song(992001L + index, "Selected ${index + 1}", "Artist", "Shared album", 41, 0,
                "/mini-$index.flac", "mini-$index.flac")
        }
        val selected = mutableStateOf(0)
        val release = java.util.concurrent.CountDownLatch(1)
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val loader: (com.ella.music.data.model.Song) -> android.graphics.Bitmap? = { song ->
            val index = songs.indexOf(song)
            if (index == 1) {
                started.set(true)
                release.await(15, java.util.concurrent.TimeUnit.SECONDS)
            }
            covers[index]
        }
        val tags = listOf("mini-square", "mini-circle", "mini-compact")
        com.ella.music.ui.components.clearArtworkModelMemoryCache()
        fun coloredPixels(tag: String, expected: Int): Int {
            val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return pixels.count { pixel ->
                kotlin.math.abs(android.graphics.Color.red(pixel) - android.graphics.Color.red(expected)) < 12 &&
                    kotlin.math.abs(android.graphics.Color.green(pixel) - android.graphics.Color.green(expected)) < 12 &&
                    kotlin.math.abs(android.graphics.Color.blue(pixel) - android.graphics.Color.blue(expected)) < 12
            }
        }
        try {
            compose.setContent {
                MiuixTheme {
                    androidx.compose.foundation.layout.Column(Modifier.size(500.dp, 260.dp).testTag("library-mini-players")) {
                        tags.forEachIndexed { index, tag ->
                            Box(Modifier.size(500.dp, 82.dp).testTag(tag)) {
                                if (index == 2) {
                                    com.ella.music.ui.components.CompactMiniPlayer(songs[selected.value], false,
                                        albumArtUri = android.net.Uri.parse("content://media/external/audio/albumart/41"),
                                        loadCoverArt = loader, onClick = {}, onPlayPause = {})
                                } else {
                                    com.ella.music.ui.components.MiniPlayer(songs[selected.value], false,
                                        isFloating = index == 1,
                                        albumArtUri = android.net.Uri.parse("content://media/external/audio/albumart/41"),
                                        loadCoverArt = loader, onClick = {}, onPlayPause = {})
                                }
                            }
                        }
                    }
                }
            }
            compose.waitUntil(5000) { tags.all { coloredPixels(it, android.graphics.Color.RED) > 400 } }
            compose.runOnIdle { selected.value = 1 }
            compose.waitForIdle()
            compose.waitUntil(5000) { started.get() }
            for (tag in tags) assertEquals("$tag must clear the previous cover while loading", 0,
                coloredPixels(tag, android.graphics.Color.RED))
            compose.runOnIdle { selected.value = 2 }
            compose.waitForIdle()
            for (tag in tags) {
                assertEquals("$tag must not retain the first cover", 0, coloredPixels(tag, android.graphics.Color.RED))
                assertEquals("$tag must not show the delayed second cover", 0, coloredPixels(tag, android.graphics.Color.GREEN))
            }
            release.countDown()
            compose.waitUntil(5000) { tags.all { coloredPixels(it, android.graphics.Color.BLUE) > 400 } }
            val output = java.io.File("build/outputs/issues-709-710-20261010/library-mini-player-artwork.png")
            output.parentFile?.mkdirs()
            output.outputStream().use {
                compose.onNodeWithTag("library-mini-players").captureToImage().asAndroidBitmap()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            compose.runOnIdle { selected.value = 0 }
            compose.waitUntil(5000) { tags.all { coloredPixels(it, android.graphics.Color.RED) > 400 } }
        } finally { release.countDown() }
    }

    @Test fun songArtworkNeverPublishesAPreviousSongDuringAnOutOfOrderLoad() {
        val first = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val second = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val third = android.graphics.Bitmap.createBitmap(8, 8, android.graphics.Bitmap.Config.ARGB_8888)
        val release = java.util.concurrent.CountDownLatch(1)
        val started = java.util.concurrent.atomic.AtomicBoolean(false)
        val requested = java.util.concurrent.ConcurrentLinkedQueue<String>()
        val song = mutableStateOf(com.ella.music.data.model.Song(991001, "First", "Artist", "Album", 0, 0, "/first.flac", "first.flac"))
        var model: Any? = null
        val mismatches = java.util.concurrent.ConcurrentLinkedQueue<String>()
        com.ella.music.ui.components.clearArtworkModelMemoryCache()
        try {
            compose.setContent {
                val artwork = com.ella.music.ui.components.rememberSongArtworkState(song.value, null,
                    loadCoverArt = { current -> requested.add(current.title); when (current.title) {
                        "First" -> first
                        "Second" -> { started.set(true); release.await(10, java.util.concurrent.TimeUnit.SECONDS); second }
                        else -> third
                    } }, usage = com.ella.music.ui.components.ArtworkUsage.MiniPlayer)
                androidx.compose.runtime.SideEffect {
                    model = artwork.model
                    if (song.value.title == "Third" && (model === first || model === second)) mismatches.add(song.value.title)
                }
                top.yukonga.miuix.kmp.basic.Text(song.value.title)
            }
            compose.waitUntil(5000) { model === first }
            compose.runOnIdle { song.value = song.value.copy(id = 991002, title = "Second", path = "/second.flac") }
            compose.onNodeWithText("Second").assertExists()
            try { compose.waitUntil(5000) { started.get() } } catch (error: Exception) { throw AssertionError("Requested artwork: $requested; modelIsFirst=${model === first}", error) }
            compose.runOnIdle { assertNull(model) }
            compose.runOnIdle { song.value = song.value.copy(id = 991003, title = "Third", path = "/third.flac") }
            compose.onNodeWithText("Third").assertExists()
            compose.runOnIdle { assertTrue(model == null || model === third) }
            release.countDown()
            compose.waitUntil(5000) { model === third }
            compose.runOnIdle { assertTrue(model === third); assertTrue(mismatches.isEmpty()) }
        } finally { release.countDown() }
    }

    @Test fun biographyUsesNativeSelectionAndFollowsTheThemeFont() {
        var root: android.view.View? = null
        compose.setContent {
            val defaults = defaultTextStyles()
            MiuixTheme(textStyles = defaults.copy(main = defaults.main.copy(fontFamily = FontFamily.Monospace))) {
                root = androidx.compose.ui.platform.LocalView.current
                com.ella.music.ui.components.SystemSelectableText("倉木麻衣 Biography", Color.White)
            }
        }
        compose.waitForIdle()
        fun find(view: android.view.View): android.widget.TextView? {
            if (view is android.widget.TextView) return view
            if (view is android.view.ViewGroup) for (index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        compose.runOnIdle {
            val text = requireNotNull(find(requireNotNull(root)))
            assertTrue(text.isTextSelectable)
            assertNull(text.customSelectionActionModeCallback)
            assertEquals(text.paint.measureText("WWWW"), text.paint.measureText("iiii"), 0.01f)
        }
    }

    @get:Rule val compose = createComposeRule()

    @Test
    @Config(qualifiers = "w840dp-h400dp-xxhdpi")
    fun landscapeClockFollowsThemeFontAndUsesBatteryStateIcons() {
        val family = mutableStateOf<FontFamily>(FontFamily.Monospace)
        val battery = mutableStateOf(ClockBattery(67, false))
        compose.setContent {
            val defaults = defaultTextStyles()
            MiuixTheme(textStyles = defaults.copy(main = defaults.main.copy(fontFamily = family.value))) {
                LandscapeClockContent("18:03", "October 6 Tuesday", "Slowly", Color.White, battery.value,
                    true, {}, {}, {}, {}, false, artwork = { Box(it.background(Color.DarkGray)) },
                    artist = "Fixture artist", modifier = Modifier.size(640.dp, 360.dp))
            }
        }
        fun checkFont() {
            compose.waitForIdle()
            for (tag in listOf("clock-time", "clock-date", "clock-title", "clock-artist", "clock-battery")) {
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertEquals("$tag must follow the global theme font", family.value, layouts.single().layoutInput.style.fontFamily)
                if (tag == "clock-time") {
                    val layout = layouts.single()
                    // Native paragraph widths can exceed their rounded IntSize by a fraction
                    // of a pixel; allow rounding while checking the entire clock fits.
                    assertTrue("Clock glyphs must fit the column",
                        layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.layoutInput.constraints.maxWidth + 1f)
                    val last = layout.getBoundingBox(layout.layoutInput.text.lastIndex)
                    assertTrue("Clock must fit: size=${layout.size}, last=$last, paragraph=${layout.multiParagraph.width}x${layout.multiParagraph.height}",
                        last.right <= layout.size.width + 1f && last.bottom <= layout.size.height + 1f)
                }
            }
        }
        checkFont()
        compose.onNodeWithTag("clock-battery-icon", useUnmergedTree = true).assertExists()
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_5, clockBatteryIcon(battery.value))
        compose.runOnIdle { family.value = FontFamily.Serif; battery.value = ClockBattery(67, true) }
        checkFont()
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_bolt, clockBatteryIcon(battery.value))
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_full, clockBatteryIcon(ClockBattery(100, false)))
        assertEquals(com.ella.music.R.drawable.ic_battery_android_frame_1, clockBatteryIcon(ClockBattery(0, false)))
        val image = compose.onNodeWithTag("landscape-cover-clock").captureToImage().asAndroidBitmap()
        val output = java.io.File("build/outputs/ruby-alignment/clock-font-battery.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun openingLyricsPaintsLyricsBeforeAndAfterTheResidentPagerIsAligned() {
        val pending = mutableStateOf<Int?>(PLAYER_PAGE_LYRICS)
        lateinit var scope: CoroutineScope
        lateinit var pager: androidx.compose.foundation.pager.PagerState
        compose.setContent {
            scope = rememberCoroutineScope()
            pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            Box(Modifier.size(640.dp, 360.dp).testTag("page-host")) {
                PlayerScreenPageHost(false, true, pager, true, {}, {}, {}, {},
                    coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                    lyricsPage = { _, _, _, _, modifier -> Box(modifier.background(Color.Green)) },
                    detailPage = { modifier -> Box(modifier.background(Color.Blue)) },
                    pendingEntryPage = pending.value)
            }
        }
        fun assertLyricsFrame() {
            compose.waitForIdle()
            val image = compose.onNodeWithTag("page-host").captureToImage().asAndroidBitmap()
            assertTrue(android.graphics.Color.green(image.getPixel(image.width / 2, image.height / 2)) > 245)
            assertTrue(android.graphics.Color.red(image.getPixel(image.width / 2, image.height / 2)) < 10)
        }
        assertLyricsFrame()
        compose.runOnIdle { scope.launch { pager.scrollToPage(PLAYER_PAGE_LYRICS); pending.value = null } }
        assertLyricsFrame()
        compose.runOnIdle {
            assertNull("The entry must finish aligning rather than remain on the temporary lyrics surface", pending.value)
            assertEquals(PLAYER_PAGE_LYRICS, pager.currentPage)
        }
    }
    @Test
    fun aDirectLyricsMorphDoesNotReplayTheResidentCoverArtwork() {
        val replay = mutableStateOf(true)
        compose.setContent {
            val artwork = rememberGraphicsLayer()
            val morph = remember { PlayerMorphState { 0.5f } }
            Box(Modifier.size(640.dp, 360.dp).testTag("lyrics-morph")
                .drawWithContent {
                    artwork.record { drawRect(Color.Red) }
                    val bounds = Rect(Offset.Zero, size)
                    morph.miniBounds = bounds
                    morph.miniArtwork = bounds
                    morph.artworkBounds = bounds
                    morph.artworkLayer = artwork
                    drawContent()
                }.playerMorphSurface(morph, false, replayArtwork = { replay.value })
                .background(Color.Green))
        }
        compose.waitForIdle()
        fun pixel() = compose.onNodeWithTag("lyrics-morph").captureToImage().asAndroidBitmap().let {
            it.getPixel(it.width / 2, it.height / 2)
        }
        assertTrue(android.graphics.Color.red(pixel()) > 245)
        compose.runOnIdle { replay.value = false }
        compose.waitForIdle()
        val lyricsPixel = pixel()
        assertTrue("The shared morph must retain the lyric page instead of overlaying its old cover",
            android.graphics.Color.green(lyricsPixel) > 245 && android.graphics.Color.red(lyricsPixel) < 10)
    }

    @Test
    fun immersiveEntryDoesNotWaitForAnAbsentPagerAndCanDismiss() {
        val showingLyrics = mutableStateOf(true)
        var aligned = false
        compose.setContent {
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            PlayerPagerEntryEffects(1, true, pager, PLAYER_PAGE_COVER) { aligned = true }
            Box(Modifier.size(640.dp, 360.dp).testTag("immersive-entry")) {
                PlayerScreenPageHost(true, showingLyrics.value, pager, true, {}, { showingLyrics.value = false }, {}, {},
                    coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                    lyricsPage = { dismiss, _, _, _, modifier ->
                        Box(modifier.background(Color.Green).testTag("immersive-lyrics")) {
                            androidx.compose.foundation.text.BasicText("Close", Modifier.testTag("dismiss-lyrics")
                                .clickable(onClick = dismiss))
                        }
                    },
                    detailPage = { modifier -> Box(modifier.background(Color.Blue)) })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { assertTrue("No pager layout exists in immersive mode", aligned) }
        compose.onNodeWithTag("dismiss-lyrics").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("immersive-lyrics").assertDoesNotExist()
    }

    @Test
    fun backAndDownwardSwipeDismissAResidentLyricsEntryAfterReopening() {
        val visible = mutableStateOf(true)
        val token = mutableStateOf(1)
        var dismissed = 0
        lateinit var dispatcher: OnBackPressedDispatcher
        var density = 1f
        compose.setContent {
            dispatcher = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            density = LocalDensity.current.density
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            var aligned by remember(token.value) { mutableStateOf(false) }
            PlayerPagerEntryEffects(token.value, false, pager, PLAYER_PAGE_LYRICS) { aligned = true }
            Box(Modifier.size(640.dp, 360.dp).testTag("resident-player")) {
                PlayerDismissMotionHost(token.value, {}, { dismissed++; visible.value = false },
                    modifier = Modifier.graphicsLayer { alpha = if (visible.value) 1f else 0f },
                    backEnabled = visible.value) {
                    PlayerScreenPageHost(false, true, pager, true, {}, {}, {}, {},
                        coverPage = { _, modifier -> Box(modifier.background(Color.Red)) },
                        lyricsPage = { _, _, _, _, modifier -> Box(modifier.background(Color.Green)) },
                        detailPage = { modifier -> Box(modifier.background(Color.Blue)) },
                        playerVisible = visible.value,
                        pendingEntryPage = PLAYER_PAGE_LYRICS.takeUnless { aligned })
                }
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, dismissed)
            assertFalse(visible.value)
            visible.value = true
            token.value++
        }
        compose.waitForIdle()
        compose.onNodeWithTag("resident-player").performTouchInput {
            swipe(Offset(width / 2f, 12f * density), Offset(width / 2f, height - 8f * density), 500)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(2, dismissed); assertFalse(visible.value) }
    }

    @Test
    fun aHiddenImmersiveLyricPageCannotInterceptBack() {
        var lyricsBackEnabled = true
        compose.setContent {
            val pager = rememberPagerState(initialPage = PLAYER_PAGE_COVER) { PLAYER_PAGE_COUNT }
            PlayerScreenPageHost(true, true, pager, true, {}, {}, {}, {},
                coverPage = { _, modifier -> Box(modifier) },
                lyricsPage = { _, _, backEnabled, _, modifier ->
                    lyricsBackEnabled = backEnabled
                    Box(modifier)
                }, detailPage = {}, playerVisible = false)
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(lyricsBackEnabled) }
    }

    @Test
    fun lyricsRendererIsPausedWhilePagerIsMoving() {
        assertTrue(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, false))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, true))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_COVER, PLAYER_PAGE_LYRICS, false))
    }

    @Test
    fun backIsNotInterceptedOnPagedLyrics() {
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_LYRICS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_DETAILS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_COVER))
        assertFalse(shouldInterceptPlayerPagerBack(false, PLAYER_PAGE_LYRICS))
    }
}
