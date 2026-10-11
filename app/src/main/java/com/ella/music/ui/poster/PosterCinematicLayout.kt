package com.ella.music.ui.poster

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.*

internal data class PosterTimedGlyph(val text: String, val startMs: Long, val endMs: Long, val word: Int)

/** Keep the source's sung intervals, whitespace, punctuation and grapheme clusters. */
internal fun posterTimedGlyphs(line: LyricLine, next: LyricLine? = null): List<PosterTimedGlyph> {
    val words = line.words.ifEmpty {
        if (line.text.isBlank() && line.backgroundWords.isNotEmpty()) line.backgroundWords
        else listOf(LyricWord(line.text.ifBlank { line.backgroundText.orEmpty() }, line.timeMs, line.primaryEndMs(next)))
    }
    val glyphs = buildList {
        words.forEachIndexed { index, word ->
            val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(word.text) }
            val boundaries = mutableListOf<Int>()
            var boundary = iterator.first()
            while (boundary != BreakIterator.DONE) { boundaries += boundary; boundary = iterator.next() }
            val count = (boundaries.size - 1).coerceAtLeast(1)
            val duration = (word.endMs - word.startMs).coerceAtLeast(0)
            for (i in 0 until boundaries.size - 1) add(PosterTimedGlyph(
                word.text.substring(boundaries[i], boundaries[i + 1]),
                word.startMs + duration * i / count, word.startMs + duration * (i + 1) / count, index))
        }
    }
    // Some lyric formats store word timing without the spaces present in the full line.
    // Recover only whitespace, without guessing word boundaries or altering sung intervals.
    val text = line.text.ifBlank { line.backgroundText.orEmpty() }
    if (text.filterNot(Char::isWhitespace) != glyphs.joinToString("") { it.text }.filterNot(Char::isWhitespace)) return glyphs
    var cursor = 0
    return buildList {
        glyphs.filterNot { it.text.isBlank() }.forEach { glyph ->
            val start = cursor
            while (cursor < text.length && text[cursor].isWhitespace()) cursor++
            if (cursor > start) add(PosterTimedGlyph(text.substring(start, cursor), glyph.startMs, glyph.startMs, glyph.word))
            add(glyph)
            cursor += glyph.text.length
        }
        if (cursor < text.length) glyphs.lastOrNull()?.let { last ->
            add(PosterTimedGlyph(text.substring(cursor), last.endMs, last.endMs, last.word))
        }
    }
}

internal fun posterGlyphProgress(glyph: PosterTimedGlyph, position: Long): Float = when {
    position < glyph.startMs -> 0f
    position >= glyph.endMs -> 1f
    else -> ((position - glyph.startMs).toFloat() / (glyph.endMs - glyph.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
}

internal data class PosterRingGlyph(val x: Float, val y: Float, val scale: Float, val alpha: Float, val rotation: Float, val depth: Float)

/** A flattened ring around an anti-diagonal, with its normal axis running at +45 degrees.
 * Glyph tangents remain readable on the rear side; the sung glyph passes through the focus. */
internal fun posterRingGlyph(angle: Float, radius: Float): PosterRingGlyph {
    val depth = (cos(angle) + 1f) * 0.5f
    val spacing = 0.35f + 0.65f * depth.pow(1.2f)
    val x = sin(angle) * radius * spacing
    val y = cos(angle) * radius * 0.09f
    val diagonal = sqrt(0.5f)
    val tangent = atan2(-sin(angle) * 0.09f, cos(angle)) * 180f / PI.toFloat() - 45f
    var readable = tangent
    while (readable > 90f) readable -= 180f
    while (readable < -90f) readable += 180f
    val focus = (1f - (abs(angle) / 0.75f).coerceIn(0f, 1f)).pow(1.8f)
    return PosterRingGlyph((x + y) * diagonal, (y - x) * diagonal,
        0.54f + depth * 0.20f + focus * 0.52f,
        0.16f + depth * 0.25f + focus * 0.59f, readable, depth)
}

internal data class PosterTypeBox(val x: Float, val y: Float, val width: Float, val height: Float,
    val scale: Float = 1f, val rotation: Float = 0f, val vertical: Boolean = false)

/** Sized by visual words, rather than distributing individual letters over a uniform grid.
 * All words shrink together when a long line needs more room; ordering is never changed. */
internal fun posterTypeLayout(widths: List<Float>, fontHeight: Float, width: Float, height: Float,
    seed: Int, sonnet: Boolean): List<PosterTypeBox> {
    if (widths.isEmpty() || width <= 0 || height <= 0) return emptyList()
    val variant = Math.floorMod(seed, if (sonnet) 5 else 4)
    val hero = widths.indices.maxBy { widths[it] }
    val vertical = false // Keep sung lines readable; ornamentation can carry vertical motion.
    fun place(fit: Float): List<PosterTypeBox> {
        val scales = widths.indices.map { (if (it == hero) if (sonnet) 1.75f else 1.55f else 0.80f) * fit }
        val limit = width * 0.84f
        val gap = fontHeight * 0.14f * fit
        val rows = mutableListOf<MutableList<Int>>()
        var used = 0f
        widths.indices.forEach { i ->
            val advance = if (vertical) fontHeight * scales[i] else widths[i] * scales[i]
            if (rows.isEmpty() || (used + advance + gap > limit && rows.last().isNotEmpty())) {
                rows += mutableListOf<Int>(); used = 0f
            }
            rows.last() += i; used += advance + gap
        }
        val rowHeights = rows.map { row -> row.maxOf { i -> if (vertical) widths[i] * scales[i] else fontHeight * scales[i] } }
        val totalHeight = rowHeights.sum() + gap * (rows.size - 1)
        var top = -totalHeight / 2f
        val placed = arrayOfNulls<PosterTypeBox>(widths.size)
        rows.forEachIndexed { r, row ->
            val advances = row.map { i -> if (vertical) fontHeight * scales[i] else widths[i] * scales[i] }
            val rowWidth = advances.sum() + gap * (row.size - 1)
            var left = -rowWidth / 2f
            row.forEachIndexed { c, i ->
                val advance = advances[c]
                val rotation = when {
                    vertical -> 90f
                    sonnet && variant == 2 -> -10f
                    sonnet && variant == 3 -> if (i == hero) -7f else 7f
                    !sonnet && variant == 2 -> -8f
                    else -> 0f
                }
                placed[i] = PosterTypeBox(left + advance / 2f, top + rowHeights[r] / 2f,
                    advance, if (vertical) widths[i] * scales[i] else fontHeight * scales[i], scales[i], rotation, vertical)
                left += advance + gap
            }
            top += rowHeights[r] + gap
        }
        return placed.map { checkNotNull(it) }
    }
    for (fit in listOf(1f, 0.9f, 0.8f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f, 0.2f)) {
        val boxes = place(fit)
        if (boxes.all { box ->
            val angle = box.rotation * PI.toFloat() / 180f
            val rawW = if (box.vertical) box.height else box.width
            val rawH = if (box.vertical) box.width else box.height
            val halfW = (abs(cos(angle)) * rawW + abs(sin(angle)) * rawH) / 2f
            val halfH = (abs(sin(angle)) * rawW + abs(cos(angle)) * rawH) / 2f
            abs(box.x) + halfW <= width * 0.48f && abs(box.y) + halfH <= height * 0.45f
        }) return boxes
    }
    return place(0.15f)
}

internal data class PosterGlowEchoPose(val x: Float, val y: Float, val rotation: Float,
    val scale: Float, val alpha: Float, val spread: Float)

/** Decorative word echoes travel through the light field and disappear; the sung line stays readable. */
internal fun posterGlowEchoPose(birthMs: Long, positionMs: Long, seed: Int): PosterGlowEchoPose {
    val age = (positionMs - birthMs) / 1000f
    if (age < 0f || age >= 7f) return PosterGlowEchoPose(0.5f, 0f, 0f, 1f, 0f, 0f)
    fun fraction(salt: Int): Float {
        var mixed = seed xor salt
        mixed = (mixed xor (mixed ushr 16)) * 0x45d9f3b
        mixed = mixed xor (mixed ushr 16)
        return (mixed.toLong() and 0xffff).toFloat() / 65535f
    }
    fun ease(value: Float): Float { val v = value.coerceIn(0f, 1f); return v * v * (3f - 2f * v) }
    val seconds = positionMs / 1000f
    val swing = sin(seconds * 0.19f) * 0.28f
    val distance = 0.08f + age * (0.065f + fraction(71) * 0.030f)
    val across = (fraction(113) - 0.5f) * (0.28f + distance * 0.50f)
    val alpha = ease(age / 0.9f) * (1f - ease((age - 4.5f) / 2.5f))
    return PosterGlowEchoPose(0.5f + sin(swing) * distance + cos(swing) * across,
        0.06f + cos(swing) * distance - sin(swing) * across,
        (fraction(157) - 0.5f) * 24f + (fraction(191) - 0.5f) * age * 2f,
        1.10f + fraction(223) * 0.70f, alpha * 0.13f, age * age * 0.008f)
}
