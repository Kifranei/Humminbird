package com.ella.music.ui.poster

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.*
import kotlin.random.Random

private data class CinematicWord(val line: LyricLine, val glyphs: List<PosterTimedGlyph>,
    val offsets: List<Float>, val width: Float)
private data class CinematicLine(val source: LyricLine, val words: List<CinematicWord>)

/** Native scene compositions informed by Folia's Tempera, Lumiere, Claddagh and Sonnet.
 * Layouts and glyph metrics are cached; only drawing reads the live playback clock. */
@Composable
internal fun PosterCinematicScene(
    style: PosterLyricStyle, lyrics: List<LyricLine>, currentIndex: Int, position: State<Long>, time: State<Float>,
    fallbackTitle: String, showTranslation: Boolean, showPronunciation: Boolean,
    fontFamily: FontFamily?, translationFontFamily: FontFamily?, fontWeight: FontWeight, fontScale: Float,
    modifier: Modifier = Modifier
) {
    val resolvedTypeface by LocalFontFamilyResolver.current.resolve(fontFamily, fontWeight)
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val fontPx = with(density) {
        (minOf(viewport.width * 0.065f, viewport.height * 0.155f).coerceAtLeast(1f) / this.density).sp.toPx()
    } * fontScale
    val paint = remember(resolvedTypeface, fontPx) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resolvedTypeface as? Typeface ?: Typeface.DEFAULT
        textSize = fontPx
        textAlign = Paint.Align.LEFT
    } }
    val active = lyrics.getOrNull(currentIndex)
    val window = remember(lyrics, currentIndex, paint, style) {
        (currentIndex - (if (style == PosterLyricStyle.Glow) 6 else 1)..currentIndex + 1).mapNotNull { index ->
            val line = lyrics.getOrNull(index) ?: return@mapNotNull null
            val words = posterLyricWords(line, lyrics.getOrNull(index + 1)).map { token ->
                val glyphs = posterTimedGlyphs(token)
                var prefix = ""
                val offsets = mutableListOf(0f)
                glyphs.forEach { prefix += it.text; offsets += paint.measureText(prefix) }
                CinematicWord(token, glyphs, offsets, offsets.last())
            }
            CinematicLine(line, words)
        }
    }
    val primary = window.firstOrNull { it.source == active }
    val collage = remember(primary, viewport, fontPx, style) {
        posterTypeLayout(primary?.words?.map { it.width }.orEmpty(), fontPx * 1.12f,
            viewport.width.toFloat(), viewport.height * 0.65f, active?.text?.hashCode() ?: 0,
            sonnet = style == PosterLyricStyle.Sonnet)
    }
    val particles = remember { Random(615).let { r -> List(100) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Box(modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        Canvas(Modifier.fillMaxSize().testTag("theater-current-lyric")) {
            val seconds = position.value / 1000f
            val elapsed = if (active == null) seconds else (position.value - active.timeMs) / 1000f
            val seed = active?.text?.hashCode() ?: fallbackTitle.hashCode()
            cinematicBackdrop(style, seconds, seed, particles, paint, primary?.source?.text.orEmpty())
            val native = drawContext.canvas.nativeCanvas
            if (primary == null) {
                paint.color = Color.White.copy(alpha = 0.8f).toArgb()
                paint.style = Paint.Style.FILL; paint.clearShadowLayer()
                val fit = minOf(1f, size.width * 0.8f / paint.measureText(fallbackTitle).coerceAtLeast(1f))
                native.save(); native.translate(size.width / 2, size.height / 2); native.scale(fit, fit)
                native.drawText(fallbackTitle, -paint.measureText(fallbackTitle) / 2, 0f, paint); native.restore()
            } else when (style) {
                PosterLyricStyle.Orbit -> drawCinematicRing(window, primary, position.value, paint)
                PosterLyricStyle.Glow -> drawCinematicGlow(window, primary, collage, position.value, seconds, paint, showPronunciation)
                else -> {
                    // Slow camera travel connects scenes, while each glyph has its own entrance.
                    val zoom = 1.02f + sin(elapsed * 0.23f) * 0.025f
                    val passes = if (style == PosterLyricStyle.ColorField) 2 else 1
                    val inkRegion = if (passes == 2) cinematicInkRegion(size, seconds, seed) else null
                    repeat(passes) { pass ->
                    native.save()
                    // Clip in stage coordinates before applying ANY text/camera transform.
                    // Inversion follows the rendered paper pixels, including partial glyphs.
                    if (inkRegion != null) {
                        if (pass == 1) native.clipPath(inkRegion) else native.clipOutPath(inkRegion)
                    }
                    native.translate(size.width / 2, size.height * 0.51f)
                    native.scale(zoom, zoom)
                    primary.words.forEachIndexed { i, word ->
                        val box = collage.getOrNull(i) ?: return@forEachIndexed
                        val uprightColumn = box.vertical && word.line.text.any { ch ->
                            Character.UnicodeScript.of(ch.code) in listOf(Character.UnicodeScript.HAN,
                                Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA)
                        }
                        native.save(); native.translate(box.x, box.y); native.rotate(if (uprightColumn) 0f else box.rotation)
                        native.scale(box.scale, box.scale)
                        val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
                        word.glyphs.forEachIndexed { g, glyph ->
                            val advance = word.offsets[g + 1] - word.offsets[g]
                            val entrance = smooth(((position.value - glyph.startMs + 450f) / 450f).coerceIn(0f, 1f))
                            val motion = (1f - entrance) * fontPx * 0.9f
                            val direction = (i + g) % 4
                            val x = (if (uprightColumn) -advance / 2f else word.offsets[g] - word.width / 2f) +
                                when (direction) { 0 -> -motion; 1 -> motion; else -> 0f }
                            val y = baseline + (if (uprightColumn) word.offsets[g] + advance / 2f - word.width / 2f else 0f) +
                                when (direction) { 2 -> motion; 3 -> -motion; else -> 0f }
                            native.save()
                            if (style == PosterLyricStyle.Sonnet) {
                                native.rotate((1f - entrance) * if (i % 2 == 0) 14f else -14f, x + advance / 2f, y - fontPx / 2f)
                            }
                            val color = if (pass == 1) Color(0xFF090909) else Color.White
                            drawLitGlyph(native, paint, glyph, x, y, advance, position.value, color,
                                unlit = if (style == PosterLyricStyle.ColorField) 0.45f else 0.27f,
                                opacity = entrance, glow = if (style == PosterLyricStyle.Sonnet) fontPx * 0.12f else 0f)
                            native.restore()
                        }
                        if (pass == 0 && showPronunciation && !word.line.pronunciation.isNullOrBlank()) {
                            drawReading(native, paint, word.line.pronunciation, -word.width / 2f, -fontPx * 0.67f)
                        }
                        native.restore()
                    }
                    native.restore()
                    }
                }
            }
        }
        // Keep secondary text readable and stationary while the primary scene fills the stage.
        val previous = lyrics.getOrNull(currentIndex - 1)
        if (style != PosterLyricStyle.Orbit && previous != null) {
            Text(previous.text, color = Color.White.copy(alpha = 0.54f), fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp * fontScale, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.74f).padding(top = with(density) { (viewport.height * 0.14f).toDp() }))
        }
        if (active != null) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f)
            .padding(bottom = with(density) { (viewport.height * 0.11f).toDp() }), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showPronunciation && active.pronunciationWords.isEmpty() && !active.pronunciation.isNullOrBlank())
                Text(active.pronunciation, color = Color.White.copy(alpha = 0.42f), fontFamily = translationFontFamily,
                    fontSize = 13.sp * fontScale, maxLines = 2, textAlign = TextAlign.Center)
            if (showTranslation && !active.translation.isNullOrBlank())
                Text(active.translation, color = Color.White.copy(alpha = 0.58f), fontFamily = translationFontFamily,
                    fontSize = 16.sp * fontScale, maxLines = 2, textAlign = TextAlign.Center)
            if (!active.backgroundText.isNullOrBlank())
                Text(active.backgroundText, color = Color.White.copy(alpha = 0.36f), fontFamily = fontFamily,
                    fontSize = 12.sp * fontScale, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

private fun smooth(value: Float) = value * value * (3f - 2f * value)

/** Same stage geometry as the paper artwork. Select the more distant ink/paper tone,
 * as Folia's backdrop filter does, rather than coloring a whole glyph from its center. */
internal fun cinematicInkRegion(size: Size, seconds: Float, seed: Int): android.graphics.Path {
    val w = size.width; val h = size.height
    val travel = sin(seconds * 0.17f) * h * 0.035f
    fun band(degrees: Float, y: Float, height: Float): android.graphics.Path = android.graphics.Path().apply {
        addRect(-w * 0.4f, y, w * 1.4f, y + height, android.graphics.Path.Direction.CW)
        transform(android.graphics.Matrix().apply { setRotate(degrees, w / 2f, h * 0.48f) })
    }
    return when (Math.floorMod(seed, 4)) {
        // Each translucent band alone is dark. Only their overlap crosses the tone midpoint.
        0 -> band(23f, h * 0.35f + travel, h * 0.24f).apply {
            op(band(-23f, h * 0.35f - travel, h * 0.24f), android.graphics.Path.Op.INTERSECT)
        }
        1 -> android.graphics.Path().apply {
            addRect(0f, h * 0.47f + travel, w, h * 1.47f + travel, android.graphics.Path.Direction.CW)
        }
        // The remaining artwork stays below mid-gray after alpha compositing.
        else -> android.graphics.Path()
    }
}

private fun drawReading(canvas: android.graphics.Canvas, paint: Paint, text: String, x: Float, y: Float) {
    val size = paint.textSize
    paint.clearShadowLayer(); paint.style = Paint.Style.FILL
    paint.textSize = size * 0.27f; paint.color = Color.White.copy(alpha = 0.55f).toArgb()
    canvas.drawText(text, x, y, paint); paint.textSize = size
}

internal fun drawLitGlyph(canvas: android.graphics.Canvas, paint: Paint, glyph: PosterTimedGlyph,
    x: Float, baseline: Float, advance: Float, position: Long, color: Color, unlit: Float,
    opacity: Float = 1f, glow: Float = 0f, outline: Boolean = false
) {
    val progress = posterGlyphProgress(glyph, position)
    paint.clearShadowLayer()
    paint.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
    paint.strokeWidth = paint.textSize * 0.009f
    paint.color = color.copy(alpha = (unlit * opacity).coerceIn(0f, 1f)).toArgb()
    canvas.drawText(glyph.text, x, baseline, paint)
    if (progress <= 0f || glyph.text.isBlank()) return
    val padding = paint.textSize * 0.15f + glow
    val left = x - padding; val right = x + advance + padding
    val top = baseline + paint.fontMetrics.top - padding
    val bottom = baseline + paint.fontMetrics.bottom + padding
    // A bounded glyph layer keeps the sweep soft without allocating a full-stage bitmap.
    if (progress < 1f) canvas.saveLayer(left, top, right, bottom, null) else canvas.save()
    paint.style = Paint.Style.FILL
    paint.color = color.copy(alpha = opacity.coerceIn(0f, 1f)).toArgb()
    if (glow > 0f) paint.setShadowLayer(glow, 0f, 0f, color.copy(alpha = opacity * 0.7f).toArgb())
    canvas.drawText(glyph.text, x, baseline, paint)
    paint.clearShadowLayer()
    if (progress < 1f) {
        val feather = minOf(advance * 0.32f, paint.textSize * 0.14f).coerceAtLeast(1f)
        val edge = x - feather + (advance + feather * 2f) * progress
        paint.shader = android.graphics.LinearGradient(edge - feather, 0f, edge + feather, 0f,
            intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT), null, android.graphics.Shader.TileMode.CLAMP)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(left, top, right, bottom, paint)
        paint.xfermode = null; paint.shader = null
    }
    canvas.restore()

}

private fun ringGlyphs(line: CinematicLine, paint: Paint): List<Pair<PosterTimedGlyph, Float>> {
    val glyphs = posterTimedGlyphs(line.source)
    return glyphs.map { it to paint.measureText(it.text) }
}

private fun DrawScope.drawCinematicRing(window: List<CinematicLine>, active: CinematicLine, position: Long, paint: Paint) {
    val radius = min(size.width * 0.47f, size.height * 0.77f)
    val canvas = drawContext.canvas.nativeCanvas
    val activeGlyphs = ringGlyphs(active, paint)
    val advances = activeGlyphs.map { it.second + paint.textSize * 0.10f }
    val total = advances.sum()
    val angularFit = minOf(1f, radius * 4.1f / total.coerceAtLeast(1f))
    var sung = 0f
    activeGlyphs.forEachIndexed { i, (glyph, _) -> sung += advances[i] * posterGlyphProgress(glyph, position) }
    // The current sung position stays close to the foreground focus as the sentence rotates.
    val focus = (sung - total / 2f) / radius.coerceAtLeast(1f) * angularFit
    data class DrawGlyph(val glyph: PosterTimedGlyph, val width: Float, val pose: PosterRingGlyph, val active: Boolean)
    val draws = mutableListOf<DrawGlyph>()
    window.filter { it.source.timeMs >= active.source.timeMs }.forEach { line ->
        val current = line === active
        val glyphs = ringGlyphs(line, paint)
        val span = glyphs.sumOf { (it.second + paint.textSize * 0.1f).toDouble() }.toFloat()
        val fit = minOf(1f, radius * 4.1f / span.coerceAtLeast(1f))
        var offset = -span / 2f
        glyphs.forEach { (glyph, width) ->
            val angle = (offset + width / 2f) / radius.coerceAtLeast(1f) * fit -
                (if (current) focus else focus * 0.3f) + if (current) 0f else PI.toFloat()
            draws += DrawGlyph(glyph, width, posterRingGlyph(angle, radius), current)
            offset += width + paint.textSize * 0.1f
        }
    }
    draws.sortedBy { it.pose.depth }.forEach { draw ->
        val p = draw.pose
        canvas.save(); canvas.translate(size.width / 2f + p.x, size.height * 0.48f + p.y)
        canvas.rotate(p.rotation); canvas.scale(p.scale * angularFit, p.scale * angularFit)
        val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
        drawLitGlyph(canvas, paint, draw.glyph, -draw.width / 2, baseline, draw.width, position,
            Color.White, 0.45f, opacity = p.alpha * if (draw.active) 1f else 0.45f,
            glow = if (draw.active && abs(p.rotation + 45f) < 8f) paint.textSize * 0.10f else 0f)
        canvas.restore()
    }
}

private fun DrawScope.drawCinematicGlow(window: List<CinematicLine>, active: CinematicLine,
    boxes: List<PosterTypeBox>, position: Long, seconds: Float, paint: Paint, readings: Boolean
) {
    val canvas = drawContext.canvas.nativeCanvas
    val warm = Color(0xFFFFF4D8)
    val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    // Finite, low-contrast word echoes follow the swaying light field. Never retain
    // expired glyphs at a fixed point or move the main sung text into that decoration.
    window.forEach { line -> line.words.forEachIndexed { index, word ->
        val pose = posterGlowEchoPose(word.glyphs.firstOrNull()?.startMs ?: line.source.timeMs,
            position, word.line.text.hashCode() xor line.source.timeMs.hashCode() xor index * 131)
        if (pose.alpha <= 0.001f) return@forEachIndexed
        canvas.save(); canvas.translate(size.width * pose.x, size.height * pose.y)
        canvas.rotate(pose.rotation); canvas.scale(pose.scale, pose.scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = paint.textSize * 0.009f
        paint.clearShadowLayer(); paint.color = warm.copy(alpha = pose.alpha).toArgb()
        word.glyphs.forEachIndexed { glyphIndex, glyph ->
            val angle = glyphIndex * 2.39996f + index
            val drift = paint.textSize * pose.spread
            canvas.save()
            canvas.translate(word.offsets[glyphIndex] - word.width / 2f + cos(angle) * drift,
                sin(angle) * drift)
            canvas.rotate(sin(angle) * pose.spread * 12f)
            canvas.drawText(glyph.text, 0f, baseline, paint); canvas.restore()
        }
        canvas.restore()
    } }
    active.words.forEachIndexed { i, word ->
        val box = boxes.getOrNull(i) ?: return@forEachIndexed
        word.glyphs.forEachIndexed { g, glyph ->
            val x0 = size.width / 2f + box.x + (word.offsets[g] - word.width / 2f) * box.scale
            val y0 = size.height * 0.51f + box.y
            val seed = (glyph.text.hashCode() xor glyph.startMs.hashCode()).toFloat()
            val incoming = (1f - smooth(((position - glyph.startMs + 650f) / 650f).coerceIn(0f, 1f)))
            val drift = incoming * 0.7f
            val dx = sin(seed) * size.width * 0.10f * drift
            val dy = -size.height * (0.06f + abs(cos(seed)) * 0.16f) * drift
            val x = x0 + dx + sin(seconds * 0.7f + g) * paint.textSize * 0.018f
            val y = y0 + dy + cos(seconds * 0.6f + i) * paint.textSize * 0.014f
            val progress = posterGlyphProgress(glyph, position)
            if (progress > 0f) {
                val trail = Path().apply {
                    moveTo(x + paint.textSize * 0.15f, y - paint.textSize * 0.3f)
                    cubicTo(x - paint.textSize * 0.1f, y + paint.textSize * 0.4f,
                        x0 + sin(seed) * paint.textSize * 0.5f, y0 + paint.textSize, x0, y0 + paint.textSize * 1.8f)
                }
                drawPath(trail, warm.copy(alpha = 0.10f * incoming), style = Stroke(0.7f))
                val flash = (1f - (position - glyph.startMs).coerceAtLeast(0) / 230f).coerceIn(0f, 1f)
                if (flash > 0f) cinematicSpark(Offset(x + paint.textSize * 0.2f, y), paint.textSize * 0.20f * flash, warm, flash)
            }
            canvas.save(); canvas.translate(x, y); canvas.rotate(box.rotation)
            canvas.scale(box.scale, box.scale)
            val advance = word.offsets[g + 1] - word.offsets[g]
            drawLitGlyph(canvas, paint, glyph, 0f, baseline, advance, position, warm,
                unlit = 0.18f, opacity = 1f, glow = paint.textSize * 0.22f, outline = true)
            if (readings && g == 0 && !word.line.pronunciation.isNullOrBlank()) drawReading(canvas, paint, word.line.pronunciation, 0f, -paint.textSize * 0.67f)
            canvas.restore()
        }
    }
}

private fun DrawScope.cinematicSpark(at: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.45f), Color.Transparent), at, radius * 4f), radius * 4f, at)
    drawLine(color.copy(alpha = alpha * 0.6f), at - Offset(radius * 2f, 0f), at + Offset(radius * 2f, 0f), 0.8f)
    drawLine(color.copy(alpha = alpha), at - Offset(0f, radius), at + Offset(0f, radius), 0.8f)
    drawCircle(color.copy(alpha = alpha), radius * 0.1f, at)
}

private fun DrawScope.cinematicBackdrop(style: PosterLyricStyle, seconds: Float, seed: Int,
    particles: List<Triple<Float, Float, Float>>, paint: Paint, text: String
) {
    val w = size.width; val h = size.height
    val center = Offset(w * 0.5f, h * 0.48f)
    drawRect(Color(0xFF060709))
    when (style) {
        PosterLyricStyle.Orbit -> {
            val delta = Offset(h * 0.30f, h * 0.30f)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.38f), Color.Transparent),
                center - delta, center + delta), center - delta, center + delta, 1.4f)
            particles.take(20).forEachIndexed { i, p ->
                val at = Offset(p.first * w, p.second * h)
                rotate(seconds * 0.8f + i * 31f, at) {
                    val r = h * (0.025f + p.third * 0.065f)
                    val color = Color.White.copy(alpha = 0.045f)
                    when (i % 4) {
                        0 -> drawRect(color, at - Offset(r, r), Size(r * 2, r * 2))
                        1 -> drawCircle(color, r, at, style = Stroke(1f))
                        2 -> { drawLine(color, at - Offset(r, 0f), at + Offset(r, 0f), r * 0.6f)
                            drawLine(color, at - Offset(0f, r), at + Offset(0f, r), r * 0.6f) }
                        else -> drawPath(Path().apply { moveTo(at.x, at.y - r); lineTo(at.x + r, at.y + r); lineTo(at.x - r, at.y + r); close() }, color)
                    }
                }
            }
        }
        PosterLyricStyle.ColorField -> {
            val variant = Math.floorMod(seed, 4)
            val travel = sin(seconds * 0.17f) * h * 0.035f
            val paper = Color(0xFFB5B5B5)
            when (variant) {
                0 -> {
                    rotate(23f, center) { drawRect(paper.copy(alpha = 0.52f), Offset(-w * 0.4f, h * 0.35f + travel), Size(w * 1.8f, h * 0.24f)) }
                    rotate(-23f, center) { drawRect(paper.copy(alpha = 0.55f), Offset(-w * 0.4f, h * 0.35f - travel), Size(w * 1.8f, h * 0.24f)) }
                }
                1 -> {
                    drawRect(paper, Offset(0f, h * 0.47f + travel), Size(w, h))
                    drawRect(Color(0xFF333333), Offset(w * 0.72f, 0f), Size(w * 0.28f, h * 0.47f))
                }
                2 -> {
                    rotate(-17f, center) {
                        repeat(3) { i -> drawRect(paper.copy(alpha = 0.22f + i * 0.18f),
                            Offset(-w * 0.2f, h * (0.12f + i * 0.28f) + travel), Size(w * 1.4f, h * 0.18f)) }
                    }
                }
                else -> {
                    drawCircle(paper.copy(alpha = 0.48f), h * 0.45f, center + Offset(w * 0.13f, 0f))
                    drawCircle(Color(0xFF141414), h * 0.28f, center + Offset(w * 0.13f, 0f))
                    drawRect(paper.copy(alpha = 0.36f), Offset(0f, h * 0.32f), Size(w * 0.44f, h * 0.19f))
                }
            }
            // Screen-print grain, frame guides and diagonal hatching span the entire stage.
            for (row in 0 until 24) for (column in 0 until 48) {
                val p = particles[(row * 17 + column) % particles.size]
                drawCircle(Color.White.copy(alpha = 0.025f + p.third * 0.015f), 0.7f,
                    Offset((column + p.first) * w / 48, (row + p.second) * h / 24))
            }
            rotate(7f, center) {
                drawLine(Color.White.copy(alpha = 0.16f), Offset(w * 0.81f, -h), Offset(w * 0.81f, h * 2), 0.8f)
                drawLine(Color.White.copy(alpha = 0.16f), Offset(-w, h * 0.38f), Offset(w * 2, h * 0.38f), 0.8f)
                repeat(25) { i -> drawLine(Color.White.copy(alpha = 0.055f), Offset(i * w * 0.02f, 0f), Offset(i * w * 0.02f + h * 0.3f, h * 0.3f), 0.7f) }
            }
        }
        PosterLyricStyle.Glow -> {
            val warm = Color(0xFFFFE5AB)
            val light = Offset(w * (0.51f + sin(seconds * 0.13f) * 0.045f), h * 0.39f)
            drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.3f), warm.copy(alpha = 0.025f), Color.Transparent), light, h * 0.29f), h * 0.29f, light)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, warm.copy(alpha = 0.5f), Color.Transparent),
                Offset(0f, light.y), Offset(w, light.y)), Offset(0f, light.y), Offset(w, light.y), 1.0f)
            rotate(-17f + sin(seconds * 0.15f) * 5f, light) {
                repeat(7) { i ->
                    val rx = w * (0.1f + i * 0.037f); val ry = h * (0.035f + i * 0.012f)
                    drawOval(warm.copy(alpha = 0.075f), light - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(0.7f))
                }
                drawLine(warm.copy(alpha = 0.065f), light - Offset(w * 0.65f, 0f), light + Offset(w * 0.65f, 0f), h * 0.02f)
            }
            particles.forEachIndexed { i, p ->
                val at = Offset(p.first * w, ((p.second + seconds * (0.005f + p.third * 0.006f)) % 1f) * h)
                val alpha = 0.12f + (sin(seconds * 1.1f + i) + 1f) * 0.20f
                if (i % 4 == 0) cinematicSpark(at, h * (0.0015f + p.third * 0.004f), warm, alpha)
                else drawCircle(warm.copy(alpha = alpha), 0.7f + p.third * 0.7f, at)
            }
            cinematicSpark(light, h * 0.024f, Color(0xFFFFF8DE), 0.88f)
            for (x in listOf(w * 0.1f, w * 0.9f)) for (y in listOf(h * 0.12f, h * 0.86f)) {
                val sx = if (x < w / 2) 1 else -1; val sy = if (y < h / 2) 1 else -1
                drawLine(warm.copy(alpha = 0.26f), Offset(x, y), Offset(x + sx * h * 0.045f, y), 0.8f)
                drawLine(warm.copy(alpha = 0.26f), Offset(x, y), Offset(x, y + sy * h * 0.045f), 0.8f)
            }
        }
        PosterLyricStyle.Sonnet -> {
            val ink = Color.White.copy(alpha = 0.34f)
            val wheel = Offset(w * 0.39f, h * 0.51f)
            drawCircle(ink, h * 0.35f, wheel, style = Stroke(1.4f))
            rotate(seconds * 1.6f, wheel) {
                repeat(40) { i ->
                    val angle = i * PI.toFloat() / 20f
                    val from = wheel + Offset(cos(angle), sin(angle)) * h * 0.38f
                    val to = wheel + Offset(cos(angle), sin(angle)) * max(w, h) * 1.3f
                    drawLine(ink.copy(alpha = if (i % 4 == 0) 0.24f else 0.09f), from, to, 0.7f)
                }
            }
            particles.take(16).forEachIndexed { i, p ->
                val at = Offset(p.first * w, p.second * h)
                val r = h * (0.027f + p.third * 0.065f)
                rotate(i * 23f + sin(seconds * 0.21f + i) * 6f, at) {
                    when (i % 4) {
                        0 -> { repeat(7) { petal ->
                            val a = petal * PI.toFloat() * 2f / 7f
                            drawOval(ink, at + Offset(cos(a), sin(a)) * r * 0.6f - Offset(r * 0.3f, r * 0.3f), Size(r * 0.6f, r * 0.6f), style = Stroke(1.0f))
                        }; drawCircle(ink, r * 0.22f, at, style = Stroke(1f)) }
                        1 -> { drawCircle(ink, r, at, style = Stroke(1f)); drawCircle(ink, r * 0.2f, at)
                            drawLine(ink, at - Offset(r * 1.6f, 0f), at + Offset(r * 1.6f, 0f), 0.8f) }
                        2 -> { drawRect(ink, at - Offset(r, r), Size(r * 2, r * 2), style = Stroke(1f))
                            drawLine(ink, at - Offset(r * 0.5f, 0f), at + Offset(r * 0.5f, 0f), 0.7f) }
                        else -> { val star = Path().apply {
                            moveTo(at.x, at.y - r); quadraticTo(at.x + r * 0.18f, at.y - r * 0.18f, at.x + r, at.y)
                            quadraticTo(at.x + r * 0.18f, at.y + r * 0.18f, at.x, at.y + r)
                            quadraticTo(at.x - r * 0.18f, at.y + r * 0.18f, at.x - r, at.y)
                            quadraticTo(at.x - r * 0.18f, at.y - r * 0.18f, at.x, at.y - r); close()
                        }; drawPath(star, ink) }
                    }
                }
            }
            drawRect(Color.White.copy(alpha = 0.07f), Offset(0f, h * 0.33f), Size(w * 0.29f, h * 0.15f))
            repeat(18) { i -> drawLine(Color.White.copy(alpha = 0.14f), Offset(w * 0.015f * i, 0f), Offset(w * 0.015f * i + h * 0.30f, h * 0.31f), 0.8f) }
        }
        else -> Unit
    }
    if (style != PosterLyricStyle.Orbit && text.isNotBlank()) {
        val canvas = drawContext.canvas.nativeCanvas
        val original = paint.textSize
        paint.clearShadowLayer(); paint.style = Paint.Style.STROKE
        paint.strokeWidth = original * 0.012f; paint.color = Color.White.copy(alpha = 0.09f).toArgb()
        paint.textSize = original * if (style == PosterLyricStyle.Glow) 2.5f else 2f
        val words = text.split(' ').filter(String::isNotBlank)
        for (i in 0 until minOf(3, words.size)) {
            canvas.save(); canvas.translate(w * (0.25f + i * 0.2f), h * (0.3f + i * 0.22f))
            canvas.rotate(-18f + i * 12f)
            canvas.drawText(words[i], -paint.measureText(words[i]) / 2f, 0f, paint); canvas.restore()
        }
        paint.textSize = original; paint.style = Paint.Style.FILL
    }
}
