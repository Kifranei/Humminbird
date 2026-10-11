package com.ella.music.ui.poster

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.ella.music.data.model.Song
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w412dp-h915dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterWallInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun stackedLongPressMovesOneCoverWithoutOpeningMoreOrPanningTheWall() {
        val state = PosterWallState()
        val items = (0 until 6).map { PosterWallItem(Song(it.toLong(), "Track $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it") }
        val itemState = mutableStateOf(items)
        val geometry = PosterWallGeometry(6, cell = 70f, stacked = true)
        var moved: Pair<Int, Int>? = null
        var more = 0
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(412.dp, 680.dp)) {
                    PosterWallCanvas(itemState.value, geometry, state, -1, artwork = { _, modifier, _ -> Box(modifier.background(Color.DarkGray)) },
                        scope = rememberCoroutineScope(), onExpand = { _, _ -> }, onMore = { more++ },
                        onMoveStack = { from, to, _ -> moved = from to to })
                }
            }
        }
        compose.waitForIdle()
        val before = state.camera
        val startRect = geometry.rect(2)
        val endRect = geometry.rect(5)
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput {
            val start = Offset((before.x + startRect.centerX * before.scale) * density, (before.y + startRect.centerY * before.scale) * density)
            val end = Offset((before.x + endRect.centerX * before.scale) * density, (before.y + endRect.centerY * before.scale) * density)
            down(start)
            advanceEventTime(700)
            moveTo(start)
        }
        // Remote metadata hydration may finish while a cover is being held.
        compose.runOnIdle { itemState.value = items.map { it.copy(song = it.song.copy(artist = "Hydrated Artist")) } }
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput {
            val start = Offset((before.x + startRect.centerX * before.scale) * density, (before.y + startRect.centerY * before.scale) * density)
            val end = Offset((before.x + endRect.centerX * before.scale) * density, (before.y + endRect.centerY * before.scale) * density)
            repeat(12) { moveTo(start + (end - start) * ((it + 1) / 12f), delayMillis = 24) }
            up()
        }
        compose.runOnIdle {
            assertEquals(0, more)
            assertEquals(2, moved?.first)
            assertTrue(moved?.second in 3..5)
            assertEquals(before, state.camera)
        }
    }

    @Test fun stackedExpandedCardSwipesOnceAndKeepsItsExpandedFrameForTheNextSong() {
        val songs = (1..3).map { Song(it.toLong(), listOf("First", "Second", "Third")[it - 1], "Artist", "Album", 0, 1000, "/$it", "$it") }
        val poster = mutableStateOf(ExpandedPoster(songs[0], 0,
            PosterRect(30f, 50f, 120f, 120f), stackKey = "1"))
        val artworkMounts = mutableMapOf<Long, Int>()
        var directions = listOf<Int>()
        val closed = mutableStateOf(false)
        var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null
        compose.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
            MiuixTheme {
                Box(Modifier.size(412.dp, 680.dp).testTag("deck-test-host")) {
                if (!closed.value) PosterExpandedCard(poster.value, PosterRect(0f, 0f, 412f, 680f), true,
                    artwork = { song, modifier ->
                        androidx.compose.runtime.DisposableEffect(song.id) {
                            artworkMounts[song.id] = artworkMounts.getOrDefault(song.id, 0) + 1
                            onDispose { }
                        }
                        Box(modifier.background(Color.hsl(song.id * 70f, 0.4f, 0.3f)))
                    },
                    lyrics = { modifier -> Box(modifier) { BasicText("Shared mini lyrics", Modifier.testTag("card-lyrics")) } },
                    seekBar = { BasicText("Glow progress", Modifier.testTag("card-progress")) },
                    controls = { PosterPlayControls(false, {}, {}, {}, showSkipButtons = false) },
                    onPlay = {}, onMore = {}, onClosed = { closed.value = true },
                    onStackSwipe = { direction ->
                        directions = directions + direction
                        val index = Math.floorMod(poster.value.sourceIndex + direction, songs.size)
                        poster.value = poster.value.copy(song = songs[index], sourceIndex = index, stackKey = "${index + 1}")
                    }, stackPosition = "${poster.value.sourceIndex + 1} / ${songs.size}",
                    stackSongs = songs, stackIndex = poster.value.sourceIndex)
                }
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithText("First").assertCountEquals(1)
        compose.onNodeWithText(org.robolectric.RuntimeEnvironment.getApplication().getString(com.ella.music.R.string.poster_wall_current) + " · 1 / 3").assertExists()
        compose.onNodeWithTag("card-lyrics").assertExists()
        compose.onNodeWithTag("card-progress").assertExists()
        compose.onNodeWithTag("poster-expanded-rear-1").assertExists()
        compose.onNodeWithTag("poster-expanded-rear-2").assertExists()
        compose.onNodeWithText("下一首").assertDoesNotExist()
        val frame = compose.onNodeWithTag("poster-expanded-card").fetchSemanticsNode().boundsInRoot
        System.getenv("HALCYON_POSTER_DECK_CAPTURE")?.takeIf(String::isNotBlank)?.let { path ->
            val bitmap = compose.onNodeWithTag("deck-test-host").captureToImage().asAndroidBitmap()
            java.io.File(path).also { it.parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("poster-expanded-card").performTouchInput {
            swipe(Offset(width * 0.8f, height * 0.4f), Offset(width * 0.2f, height * 0.4f), durationMillis = 250)
        }
        compose.runOnIdle { assertEquals(listOf(1), directions) }
        compose.onAllNodesWithText("Second").assertCountEquals(1)
        assertEquals(frame, compose.onNodeWithTag("poster-expanded-card").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("poster-expanded-card").performTouchInput {
            swipe(Offset(width * 0.2f, height * 0.4f), Offset(width * 0.8f, height * 0.4f), durationMillis = 250)
        }
        compose.runOnIdle { assertEquals(listOf(1, -1), directions) }
        compose.runOnIdle {
            assertEquals(mapOf(1L to 1, 2L to 1, 3L to 1), artworkMounts)
        }
        compose.onNodeWithText("First").assertExists()
        compose.runOnIdle { checkNotNull(backDispatcher).onBackPressed() }
        compose.onNodeWithTag("poster-expanded-card").assertDoesNotExist()
    }

    @Test fun exposedLowerCoverCanBeRaisedThenInsertedInsideTheSameFullStack() {
        val state = PosterWallState()
        val items = (0 until 10).map { PosterWallItem(Song(it.toLong(), "Layer $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it") }
        val layout = mutableStateOf(PosterStackLayout(listOf(items.map { it.stackKey })))
        var more = 0
        var opened = 0
        var move: Triple<Int, Int, Boolean>? = null
        fun geometry() = PosterWallGeometry(10, stacked = true, stackGroups = layout.value.indices(items))
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(412.dp, 680.dp)) {
                    val field = remember(layout.value) { geometry() }
                    PosterWallCanvas(items, field, state, 0, artwork = { _, modifier, _ -> Box(modifier.background(Color.DarkGray)) },
                        scope = rememberCoroutineScope(), onExpand = { _, _ -> opened++ }, onMore = { more++ },
                        onMoveStack = { from, to, before ->
                            move = Triple(from, to, before)
                            layout.value.move("$from", "$to", before)?.let { layout.value = it }
                        })
                }
            }
        }
        compose.waitForIdle()
        val initial = geometry()
        val (source, point) = (0..8).firstNotNullOf { index -> exposedPoint(initial, index)?.let { index to it } }
        val before = state.camera
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput {
            val screen = (point * before.scale + Offset(before.x, before.y)) * density
            down(screen); advanceEventTime(700); moveTo(screen)
        }
        compose.onNodeWithText("Layer $source").assertExists()
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput { up() }
        compose.runOnIdle {
            assertEquals(Triple(source, 9, false), move)
            assertEquals("$source", layout.value.groups.single().last())
            assertEquals(10, layout.value.groups.single().size)
        }
        val reordered = geometry()
        val target = (0..9).filter { it != source }.firstNotNullOf { index ->
            exposedPoint(reordered, index, lowerHalf = true)?.let { index to it }
        }
        val start = checkNotNull(exposedPoint(reordered, source))
        val camera = state.camera
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput {
            val from = (start * camera.scale + Offset(camera.x, camera.y)) * density
            val to = (target.second * camera.scale + Offset(camera.x, camera.y)) * density
            down(from); advanceEventTime(700); moveTo(from)
            repeat(12) { moveTo(from + (to - from) * ((it + 1) / 12f), delayMillis = 24) }
            up()
        }
        compose.runOnIdle {
            assertEquals(Triple(source, target.first, true), move)
            val group = layout.value.groups.single()
            assertEquals(group.indexOf("${target.first}") - 1, group.indexOf("$source"))
            assertEquals(10, group.size)
            assertEquals(0, more)
            assertEquals(0, opened)
            assertEquals(layout.value, PosterStackLayout.resolve(items.reversed(), layout.value.encode()))
        }
    }

    /** Find a genuinely exposed pixel of a rotated layer, including overlap with neighbors. */
    private fun exposedPoint(geometry: PosterWallGeometry, index: Int, lowerHalf: Boolean = false): Offset? {
        fun rotate(point: Offset, angle: Float): Offset {
            val radians = Math.toRadians(angle.toDouble())
            return Offset((point.x * kotlin.math.cos(radians) - point.y * kotlin.math.sin(radians)).toFloat(),
                (point.x * kotlin.math.sin(radians) + point.y * kotlin.math.cos(radians)).toFloat())
        }
        fun level(i: Int) = if (geometry.groupIndices(i).last() == i) 20f else geometry.depth(i) / 10f
        val rect = geometry.rect(index)
        val center = Offset(rect.centerX, rect.centerY)
        for (y in 7 until rect.height.toInt() - 7 step 6) for (x in 7 until rect.width.toInt() - 7 step 6) {
            if (lowerHalf && y < rect.height / 2f + 8f) continue
            val point = center + rotate(Offset(x - rect.width / 2f, y - rect.height / 2f), geometry.rotation(index))
            val top = (0 until geometry.count).filter { candidate ->
                val cover = geometry.rect(candidate)
                val local = rotate(point - Offset(cover.centerX, cover.centerY), -geometry.rotation(candidate))
                kotlin.math.abs(local.x) < cover.width / 2f && kotlin.math.abs(local.y) < cover.height / 2f
            }.maxWithOrNull(compareBy<Int> { level(it) }.thenBy { it })
            if (top == index) return point
        }
        return null
    }

    @Test fun overlappingStacksLetTouchOpenTheTopPosterAndRemoteSelectOtherLayers() {
        val state = PosterWallState()
        val items = (0 until 60).map { PosterWallItem(Song(it.toLong(), "Track $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it") }
        val geometry = PosterWallGeometry(60, stacked = true, stackGroups = listOf((0..9).toList()) + (10 until 60).toList().chunked(3))
        var opened = -1
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(412.dp, 680.dp).background(PosterInk)) {
                    PosterWallCanvas(items, geometry, state, -1,
                        artwork = { song, modifier, _ -> Box(modifier.background(Color.hsl((song.id * 41 % 360).toFloat(), 0.45f, 0.35f))) },
                        scope = rememberCoroutineScope(), onExpand = { index, _ -> opened = index }, onMore = {})
                }
            }
        }
        compose.waitForIdle()
        val rect = geometry.rect(9)
        val camera = state.camera
        compose.onNodeWithTag("poster-wall-canvas").performTouchInput {
            click(Offset((camera.x + rect.centerX * camera.scale) * density, (camera.y + rect.centerY * camera.scale) * density))
        }
        compose.runOnIdle { assertEquals(9, opened) }
        System.getenv("HALCYON_POSTER_CAPTURE")?.takeIf(String::isNotBlank)?.let { path ->
            val bitmap = compose.onNodeWithTag("poster-wall-canvas").captureToImage().asAndroidBitmap()
            java.io.File(path).also { it.parentFile?.mkdirs() }.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithTag("poster-wall-canvas").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster-wall-canvas").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue(opened in 0 until items.size) }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-television-xxhdpi")
    fun searchKeepsKeyboardFocusAndFilteredPostersRemainReachable() {
        var opened = -1
        val state = PosterWallState()
        val items = (0 until 100).map {
            PosterWallItem(Song(it.toLong(), "Track $it", "Artist", "Album", 0, 1000, "/$it", "$it"), it, "$it")
        }
        val geometry = PosterWallGeometry(items.size)
        compose.setContent {
            val input = LocalInputModeManager.current
            val searchFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { input.requestInputMode(InputMode.Keyboard); searchFocus.requestFocus() }
            MiuixTheme {
                Column(Modifier.size(412.dp, 680.dp)) {
                    BasicText("Search", Modifier.testTag("search-focus").focusRequester(searchFocus).focusable())
                    // The backdrop and poster layout overlap, as they do in the actual screen.
                    Box(Modifier.weight(1f)) {
                    PosterWallCanvas(items, geometry, state, 0,
                        artwork = { _, modifier, _ -> Box(modifier.background(Color.DarkGray)) },
                        scope = rememberCoroutineScope(), onExpand = { index, _ -> opened = index }, onMore = {}, searching = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("search-focus").assertIsFocused()
        compose.onNodeWithTag("poster-wall-canvas").performSemanticsAction(SemanticsActions.RequestFocus)
        compose.onNodeWithTag("poster-wall-canvas").performKeyInput { pressKey(Key.DirectionRight); pressKey(Key.Enter) }
        compose.runOnIdle { assertTrue(opened >= 0) }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp-television-xxhdpi")
    fun remoteArrowsRevealOffscreenPostersAndConfirmAndMenuTargetTheSelection() {
        val state = PosterWallState()
        var opened = -1
        var more = -1L
        var openedRect: PosterRect? = null
        setWall(state, onMore = { more = it.id }) { index, rect -> opened = index; openedRect = rect }
        compose.waitForIdle()
        val before = state.camera
        compose.onNodeWithTag("wall").performKeyInput {
            repeat(15) { pressKey(Key.DirectionDown) }
            pressKey(Key.DirectionRight)
            pressKey(Key.Enter)
        }
        compose.runOnIdle {
            assertTrue(opened >= 0)
            assertNotEquals(before, state.camera)
            val rect = checkNotNull(openedRect)
            assertTrue(rect.centerX in 0f..state.viewport.x && rect.centerY in 0f..state.viewport.y)
        }
        compose.onNodeWithTag("wall").performKeyInput { pressKey(Key.Menu) }
        compose.runOnIdle { assertEquals(opened.toLong(), more) }
    }

    @Test fun tappingAPosterExpandsItWithoutChangingTheCamera() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        compose.waitForIdle()
        val original = state.camera
        compose.onNodeWithText("Track 0").performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(0, expanded); assertEquals(original, state.camera) }
    }

    @Test fun draggingOverACardPansWithoutAccidentallyPlayingOrExpanding() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        val before = state.camera
        compose.onNodeWithTag("wall").performTouchInput { swipe(center, center + Offset(-220f, -300f), durationMillis = 400) }
        compose.runOnIdle { assertTrue(state.camera != before); assertEquals(-1, expanded); state.stop() }
    }

    @Test fun portraitAndLandscapeCamerasKeepNearbyPostersReachable() {
        val state = PosterWallState()
        setWall(state) { _, _ -> }
        compose.waitForIdle()
        compose.runOnIdle {
            val geometry = PosterWallGeometry(10000)
            state.resize(840f, 360f, geometry)
            state.transform(Offset(-3000f, -2000f), 1.8f, Offset(420f, 180f), geometry)
            val nearby = geometry.visibleIndices(state.camera.bounds(840f, 360f))
            assertTrue(nearby.isNotEmpty()); assertTrue(nearby.size < 150)
        }
    }

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun enteringInPortraitThenRotatingKeepsTheComposedPostersAndLayoutInSync() =
        checkRotation(DpSize(412.dp, 680.dp), DpSize(840.dp, 310.dp))

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun enteringInLandscapeThenRotatingKeepsTheComposedPostersAndLayoutInSync() =
        checkRotation(DpSize(840.dp, 310.dp), DpSize(412.dp, 680.dp))

    @Test
    @Config(qualifiers = "w915dp-h915dp-xxhdpi")
    fun rotatingAnInfiniteWallKeepsTheVisibleCellsAndLayoutInSync() =
        checkRotation(DpSize(412.dp, 680.dp), DpSize(840.dp, 310.dp), infinite = true)

    @Test fun repeatedPostersExpandTheOriginalQueueIndex() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state, infinite = true, count = 1) { index, _ -> expanded = index }
        compose.waitForIdle()
        val camera = state.camera
        val geometry = PosterWallGeometry(1, infinite = true)
        val rect = geometry.visiblePosters(camera.bounds(state.viewport.x, state.viewport.y)).first {
            camera.x + it.rect.centerX * camera.scale in 0f..state.viewport.x &&
                camera.y + it.rect.centerY * camera.scale in 0f..state.viewport.y
        }.rect
        compose.onNodeWithTag("wall").performTouchInput {
            val scale = this.width / state.viewport.x
            click(Offset((camera.x + rect.centerX * camera.scale) * scale, (camera.y + rect.centerY * camera.scale) * scale))
        }
        compose.runOnIdle { assertEquals(0, expanded) }
    }

    private fun checkRotation(initialSize: DpSize, rotatedSize: DpSize, infinite: Boolean = false) {
        val state = PosterWallState()
        val viewportSize = mutableStateOf(initialSize)
        var expanded = -1
        setWall(state, viewportSize, infinite) { index, _ -> expanded = index }
        compose.waitForIdle()
        // Retain the same composition and camera, as MainActivity does on orientation changes.
        // Changing only state.resize() misses the measure/place pass where the crash occurs.
        repeat(6) { turn ->
            val nextSize = if (turn % 2 == 0) rotatedSize else initialSize
            compose.runOnIdle { viewportSize.value = nextSize }
            compose.waitForIdle()
            compose.runOnIdle {
                assertEquals(Offset(nextSize.width.value, nextSize.height.value), state.viewport)
                assertTrue(PosterWallGeometry(10000, infinite = infinite).visiblePosters(state.camera.bounds(state.viewport.x, state.viewport.y)).isNotEmpty())
                assertEquals(-1, expanded)
            }
            compose.onNodeWithTag("wall").performTouchInput {
                swipe(center, center + Offset(-120f, -80f), durationMillis = 400)
            }
            compose.runOnIdle { state.stop() }
        }
    }

    @Test fun pinchingAcrossTwoClickablePostersZoomsInsteadOfExpandingEither() {
        val state = PosterWallState()
        var expanded = -1
        setWall(state) { index, _ -> expanded = index }
        val initialScale = state.camera.scale
        compose.onNodeWithTag("wall").performTouchInput {
            down(0, center - Offset(100f, 0f)); down(1, center + Offset(100f, 0f))
            moveTo(0, center - Offset(180f, 0f)); moveTo(1, center + Offset(180f, 0f))
            up(0); up(1)
        }
        compose.runOnIdle { assertTrue(state.camera.scale > initialScale); assertEquals(-1, expanded) }
    }

    @Test fun changingCoverResolutionInvalidatesOnlyItsArtworkCacheVariant() {
        val requested = androidx.compose.runtime.mutableIntStateOf(192)
        var resolvedWidth = 0
        val calls = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val song = Song(101, "Variant cover", "Artist", "Album", 0, 100000, "/music/variant.flac", "variant.flac")
        compose.setContent {
            val size = requested.intValue
            val state = com.ella.music.ui.components.rememberSongArtworkState(song, null,
                loadCoverArt = { calls.add(size); Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888) },
                usage = com.ella.music.ui.components.ArtworkUsage.LibraryGrid, cacheVariant = "poster-test:$size")
            androidx.compose.runtime.SideEffect { resolvedWidth = (state.model as? Bitmap)?.width ?: 0 }
            androidx.compose.foundation.text.BasicText("Cover ${ (state.model as? Bitmap)?.width ?: 0 }")
        }
        compose.waitUntil(5000) { resolvedWidth == 192 }
        compose.runOnIdle { requested.intValue = 512 }
        compose.waitForIdle()
        try { compose.waitUntil(5000) { resolvedWidth == 512 } }
        catch (failure: Throwable) { throw AssertionError("Resolved width $resolvedWidth; cover loads $calls", failure) }
    }

    private fun setWall(state: PosterWallState, viewportSize: State<DpSize>? = null, infinite: Boolean = false, count: Int = 10000, onMore: (Song) -> Unit = {}, onExpand: (Int, PosterRect) -> Unit) {
        val items = (0 until count).map { index ->
            val song = Song(id = index.toLong(), title = "Track $index", artist = "Artist ${index % 9}", album = "Album", albumId = 0, duration = 180000, path = "/music/$index.flac", fileName = "$index.flac")
            PosterWallItem(song, index, "$index")
        }
        val geometry = PosterWallGeometry(items.size, infinite = infinite)
        compose.setContent {
            MiuixTheme {
                Box(Modifier.size(viewportSize?.value ?: DpSize(412.dp, 680.dp)).background(PosterInk).testTag("wall")) {
                    PosterWallCanvas(items, geometry, state, 0, artwork = { song, modifier, _ ->
                        val hue = (song.id * 41 % 360).toFloat()
                        Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color.hsl(hue, 0.55f, 0.45f), Color.hsl((hue + 60) % 360, 0.65f, 0.17f)))))
                    }, scope = rememberCoroutineScope(), onExpand = onExpand, onMore = onMore)
                }
            }
        }
    }
}
