package com.ella.music.ui.player

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Native image filters share the layer's input image, never recorded child RenderNodes.
 * Keep the viewport mask outside the perspective plane without recording drawContent into
 * layers that can become descendants of that same plane when its modifier is toggled. */
internal fun Modifier.miniLyricsEdgeFeather(
    edgeHeight: Dp = 28.dp,
    blurRadius: Dp = 2.8.dp,
    topEdgeHeight: Dp = edgeHeight,
    bottomEdgeHeight: Dp = edgeHeight
): Modifier = composed {
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val topPx = with(density) { topEdgeHeight.toPx() }
    val bottomPx = with(density) { bottomEdgeHeight.toPx() }
    val blurPx = with(density) { blurRadius.toPx() }.coerceAtLeast(0.01f)
    val effect = remember(viewport, topPx, bottomPx, blurPx) {
        if (Build.VERSION.SDK_INT >= 31 && viewport.height > 0)
            createLyricEdgeRenderEffect(viewport.height.toFloat(), topPx, bottomPx, blurPx).asComposeRenderEffect()
        else null
    }
    clipToBounds().onSizeChanged { viewport = it }.graphicsLayer {
        compositingStrategy = CompositingStrategy.Offscreen
        renderEffect = effect
    }.drawWithCache {
        val stops = lyricEdgeMaskStops(size.height, topPx, bottomPx, soft = false, linearFallback = true)
        val fallback = Brush.verticalGradient(colorStops = stops.map { (at, alpha) -> at to Color.White.copy(alpha = alpha) }.toTypedArray())
        onDrawWithContent {
            drawContent()
            // RenderEffect needs HWUI. Software captures and Android 10/11 retain alpha feathering.
            if (Build.VERSION.SDK_INT < 31 || !drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
                drawRect(fallback, blendMode = BlendMode.DstIn)
            }
        }
    }
}

internal fun lyricEdgeMaskStops(height: Float, topPx: Float, bottomPx: Float,
    soft: Boolean, linearFallback: Boolean = false): List<Pair<Float, Float>> {
    fun fraction(px: Float) = (px / height.coerceAtLeast(1f)).coerceIn(0f, 0.24f)
    val top = fraction(topPx)
    val bottom = fraction(bottomPx)
    fun alpha(t: Float): Float {
        if (linearFallback) return t
        val fade = t * t * (3f - 2f * t)
        return if (soft) fade * (1f - fade) else fade * fade
    }
    val solid = if (soft && !linearFallback) 0f else 1f
    return buildList {
        if (top > 0f) for (step in 0..8) { val t = step / 8f; add(top * t to alpha(t)) }
        else add(0f to solid)
        if (bottom > 0f) for (step in 8 downTo 0) { val t = step / 8f; add(1f - bottom * t to alpha(t)) }
        else add(1f to solid)
    }
}

@RequiresApi(31)
private fun createLyricEdgeRenderEffect(height: Float, topPx: Float, bottomPx: Float, radius: Float): android.graphics.RenderEffect {
    fun mask(soft: Boolean): android.graphics.RenderEffect {
        val stops = lyricEdgeMaskStops(height, topPx, bottomPx, soft)
        val colors = stops.map { android.graphics.Color.argb((it.second * 255f).roundToInt(), 255, 255, 255) }.toIntArray()
        val shader = android.graphics.LinearGradient(0f, 0f, 0f, height, colors,
            stops.map { it.first }.toFloatArray(), android.graphics.Shader.TileMode.CLAMP)
        return android.graphics.RenderEffect.createShaderEffect(shader)
    }
    val input = android.graphics.RenderEffect.createOffsetEffect(0f, 0f)
    val blurred = android.graphics.RenderEffect.createBlurEffect(radius, radius, android.graphics.Shader.TileMode.DECAL)
    val crispEdges = android.graphics.RenderEffect.createBlendModeEffect(input, mask(false), android.graphics.BlendMode.DST_IN)
    val blurredEdges = android.graphics.RenderEffect.createBlendModeEffect(blurred, mask(true), android.graphics.BlendMode.DST_IN)
    // Weighted crisp + blurred alpha is exactly fade; the middle keeps its original pixels.
    return android.graphics.RenderEffect.createBlendModeEffect(crispEdges, blurredEdges, android.graphics.BlendMode.PLUS)
}
