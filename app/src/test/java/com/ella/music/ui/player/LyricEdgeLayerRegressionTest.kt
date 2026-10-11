package com.ella.music.ui.player

import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 31, 35], application = Application::class, qualifiers = "w412dp-h915dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricEdgeLayerRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun perspectiveCanToggleRepeatedlyWithTheViewportFeatherAndNestedChildLayers() {
        val perspective = mutableStateOf(false)
        val alignment = mutableIntStateOf(SettingsManager.PLAYER_LYRIC_ALIGN_LEFT)
        val angle = mutableIntStateOf(25)
        compose.setContent {
            Box(Modifier.size(300.dp, 400.dp).background(Color.Black).testTag("lyrics")) {
                Canvas(Modifier.size(300.dp, 400.dp).miniLyricsEdgeFeather(topEdgeHeight = 16.dp, bottomEdgeHeight = 16.dp)
                    .playerLyricPerspective(perspective.value, angle.intValue, alignment.intValue)) {
                    for (row in 0..10) drawRect(Color.White,
                        topLeft = androidx.compose.ui.geometry.Offset(10.dp.toPx(), row * 38.dp.toPx()),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.7f, 12.dp.toPx()))
                }
            }
        }
        repeat(12) { i ->
            compose.runOnIdle {
                perspective.value = i % 2 == 0
                alignment.intValue = i % 3
                angle.intValue = if (i % 2 == 0) 35 else 15
            }
            val bitmap = compose.onNodeWithTag("lyrics").captureToImage().asAndroidBitmap()
            assertTrue(bitmap.width > 0 && bitmap.height > 0)
            var bright = 0
            for (y in 0 until bitmap.height step 8) for (x in 0 until bitmap.width step 8) {
                if ((bitmap.getPixel(x, y) and 0x00ffffff) > 0x00cccccc) bright++
            }
            assertTrue("The lyrics must remain visible after toggle $i", bright > 20)
        }
    }

    @Test fun nativeImageFilterMasksLeaveTheMiddleIntactAndBlendOnlyTheEdges() {
        val crisp = lyricEdgeMaskStops(400f, 16f, 0f, soft = false)
        val soft = lyricEdgeMaskStops(400f, 16f, 0f, soft = true)
        assertEquals(crisp.map { it.first }, soft.map { it.first })
        assertEquals(0f, crisp.first().second + soft.first().second, 0f)
        assertEquals(1f, crisp[8].second, 0f)
        assertEquals(0f, soft[8].second, 0f)
        assertEquals(1f, crisp.last().second, 0f)
        assertEquals(0f, soft.last().second, 0f)
        assertTrue((crisp zip soft).all { (c, s) -> c.second + s.second in 0f..1f })
    }
}
