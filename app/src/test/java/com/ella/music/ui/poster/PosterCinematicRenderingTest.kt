package com.ella.music.ui.poster

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosterCinematicRenderingTest {
    @Test fun glowWordEchoesUseBothSidesOfTheBeamAndExpireInsteadOfAccumulating() {
        val poses = (0..80).map { posterGlowEchoPose(1000, 3500, it * 193) }
        assertTrue(poses.any { it.x < 0.5f } && poses.any { it.x > 0.5f })
        assertTrue(poses.all { it.alpha in 0f..0.13f && it.x in 0.1f..0.9f })
        assertEquals(0f, posterGlowEchoPose(1000, 999, 17).alpha, 0f)
        assertEquals(0f, posterGlowEchoPose(1000, 8000, 17).alpha, 0f)
        assertEquals(0f, posterGlowEchoPose(1000, 60000, 17).alpha, 0f)
        assertTrue(posterGlowEchoPose(1000, 6000, 17).y > posterGlowEchoPose(1000, 2500, 17).y)
    }

    @Test fun timedSweepHasAFeatheredFrontAndNeverLightsFutureGlyphs() {
        val bitmap = Bitmap.createBitmap(160, 140, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 100f }
        val glyph = PosterTimedGlyph("■", 1000, 2000, 0)
        val width = paint.measureText(glyph.text)
        drawLitGlyph(canvas, paint, glyph, 20f, 105f, width, 1500, Color.White, 0f)
        val alphas = (20..(20 + width.toInt())).map { android.graphics.Color.alpha(bitmap.getPixel(it, 75)) }
        assertTrue("A sweep needs multiple intermediate alpha values", alphas.distinct().size > 10)
        assertTrue(alphas.any { it == 255 } && alphas.any { it == 0 })
        bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
        drawLitGlyph(canvas, paint, glyph, 20f, 105f, width, 999, Color.White, 0f)
        assertTrue((20..120).all { android.graphics.Color.alpha(bitmap.getPixel(it, 75)) == 0 })
    }

    @Test fun inkSelectionFollowsTheStageAcrossCameraAndWordTransforms() {
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = android.graphics.Color.WHITE }
        val region = cinematicInkRegion(Size(400f, 200f), 0f, 1)
        canvas.save(); canvas.clipOutPath(region)
        canvas.translate(200f, 100f); canvas.rotate(-17f); canvas.scale(1.3f, 1.3f)
        canvas.drawRect(-300f, -200f, 300f, 200f, paint); canvas.restore()
        canvas.save(); canvas.clipPath(region)
        canvas.translate(200f, 100f); canvas.rotate(-17f); canvas.scale(1.3f, 1.3f)
        paint.color = android.graphics.Color.BLACK
        canvas.drawRect(-300f, -200f, 300f, 200f, paint); canvas.restore()
        assertEquals(android.graphics.Color.WHITE, bitmap.getPixel(200, 80))
        assertEquals(android.graphics.Color.BLACK, bitmap.getPixel(200, 110))
        // Individually dark translucent bands must not turn type black.
        assertTrue(cinematicInkRegion(Size(400f, 200f), 0f, 2).isEmpty)
    }
}
