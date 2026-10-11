package com.ella.music.ui.poster

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A bounded field of repeating, fully packed mosaic blocks. Coordinates are in dp. */
internal data class PosterRect(val x: Float, val y: Float, val width: Float, val height: Float) {
    val right get() = x + width
    val bottom get() = y + height
    val centerX get() = x + width / 2f
    val centerY get() = y + height / 2f
    fun intersects(other: PosterRect) = x < other.right && right > other.x && y < other.bottom && bottom > other.y
}

private data class PosterSlot(val x: Int, val y: Int, val width: Int, val height: Int)

internal data class PosterPlacement(val index: Int, val rect: PosterRect, val key: String)

internal enum class PosterDirection { Left, Right, Up, Down }

/** Search adjacent mosaic blocks rather than materializing or scanning the whole library. */
internal fun nextPosterPlacement(from: PosterPlacement, geometry: PosterWallGeometry, direction: PosterDirection): PosterPlacement? {
    val reach = geometry.cell * 12f
    val horizontal = direction == PosterDirection.Left || direction == PosterDirection.Right
    val sign = if (direction == PosterDirection.Left || direction == PosterDirection.Up) -1f else 1f
    val candidates = geometry.visiblePosters(PosterRect(from.rect.centerX - reach, from.rect.centerY - reach,
        reach * 2f, reach * 2f), overscan = 0f)
    fun forward(rect: PosterRect) = sign * if (horizontal) rect.centerX - from.rect.centerX else rect.centerY - from.rect.centerY
    fun sideways(rect: PosterRect) = kotlin.math.abs(if (horizontal) rect.centerY - from.rect.centerY else rect.centerX - from.rect.centerX)
    fun inBeam(rect: PosterRect) = if (horizontal) rect.y < from.rect.bottom && rect.bottom > from.rect.y
        else rect.x < from.rect.right && rect.right > from.rect.x
    return candidates.filter { forward(it.rect) > 0.1f }.minWithOrNull(
        compareBy<PosterPlacement> { if (inBeam(it.rect)) 0 else 1 }
            .thenBy { forward(it.rect) + sideways(it.rect) * 0.5f }
            .thenBy { sideways(it.rect) })
}

// One six-by-four block; every cell is used exactly once. Small, portrait, landscape and
// square posters share one rhythm without sorting the user's queue by artwork or size.
private val slots = listOf(
    PosterSlot(0, 0, 2, 2), PosterSlot(2, 0, 1, 1), PosterSlot(3, 0, 1, 2),
    PosterSlot(4, 0, 2, 1), PosterSlot(2, 1, 1, 1), PosterSlot(4, 1, 1, 1),
    PosterSlot(5, 1, 1, 2), PosterSlot(0, 2, 1, 2), PosterSlot(1, 2, 2, 1),
    PosterSlot(3, 2, 2, 2), PosterSlot(1, 3, 2, 1), PosterSlot(5, 3, 1, 1)
)

internal class PosterWallGeometry(val count: Int, val cell: Float = 124f, val gap: Float = 10f, val infinite: Boolean = false, val stacked: Boolean = false,
    stackGroups: List<List<Int>>? = null, private val stackKeys: List<String> = emptyList()) {
    private val groups = if (stacked) stackGroups ?: (0 until count).toList().chunked(3) else emptyList()
    private val groupOf = IntArray(count)
    private val depthOf = IntArray(count)
    init {
        if (stacked) {
            require(groups.flatten().sorted() == (0 until count).toList())
            require(groups.all { it.size in 1..MAX_POSTER_STACK_SIZE })
            groups.forEachIndexed { group, indices -> indices.forEachIndexed { depth, index ->
                groupOf[index] = group; depthOf[index] = depth
            } }
        }
    }
    fun groupIndices(index: Int): List<Int> = if (stacked) groups[groupOf[index]] else listOf(index)
    fun depth(index: Int): Int = if (stacked) depthOf[index] else 0
    private val rotations = FloatArray(count) { Float.NaN }
    fun rotation(index: Int): Float {
        if (!rotations[index].isNaN()) return rotations[index]
        val angle = if (!stacked || groupIndices(index).size == 1) 0f else {
            val seed = (stackKeys.getOrNull(index) ?: index.toString()).hashCode()
            kotlin.random.Random(seed xor 0x4215).nextFloat() * 26f - 13f
        }
        rotations[index] = angle
        return angle
    }
    private fun blockIndices(block: Int): List<Int> = if (stacked) {
        (block * 4 until min(block * 4 + 4, groups.size)).flatMap { groups[it] }
    } else (block * slots.size until min((block + 1) * slots.size, count)).toList()
    val blockCount = if (stacked) (groups.size + 3) / 4 else (count.coerceAtLeast(0) + slots.size - 1) / slots.size
    val blockColumns = ceil(sqrt(blockCount.coerceAtLeast(1).toDouble())).toInt().coerceIn(1, 4)
    private val blockRows = (blockCount + blockColumns - 1) / blockColumns
    private val step = cell + gap
    private val stackPitch = step * 2.70f
    private val blockWidth = if (stacked) stackPitch * 2 else step * 6
    private val blockHeight = if (stacked) stackPitch * 2 else step * 4
    // Reserve room for jittered covers if a future pitch reduction crosses block boundaries.
    private val overhang = if (stacked) max(0f, step * 2.64f - gap - stackPitch) else 0f
    val repeatWidth = blockColumns * blockWidth
    val repeatHeight = blockRows.coerceAtLeast(1) * blockHeight
    // Tight bounds also keep a single-song or short queue centred rather than leaving a mostly
    // empty six-column canvas. Only the partial last block needs inspection.
    val width: Float
    val height: Float

    private val rectangles = arrayOfNulls<PosterRect>(count)
    init {
        val tail = (max(0, blockCount - blockColumns) until blockCount).flatMap(::blockIndices).map(::rect)
        width = max(if (blockCount > blockColumns) blockColumns * blockWidth + overhang - (if (stacked) 0f else gap) else 0f, tail.maxOfOrNull { it.right } ?: 0f)
        height = max(if (stacked && blockRows > 1) blockRows * blockHeight + overhang else 0f, tail.maxOfOrNull { it.bottom } ?: 0f)
    }

    fun rect(index: Int): PosterRect = rectangles[index] ?: calculateRect(index).also { rectangles[index] = it }
    private fun calculateRect(index: Int): PosterRect {
        require(index in 0 until count)
        val block = if (stacked) groupOf[index] / 4 else index / slots.size
        val local = if (stacked) stackRect(groupOf[index] % 4, index) else localRect(index % slots.size)
        return local.copy(x = local.x + (block % blockColumns) * blockWidth,
            y = local.y + (block / blockColumns) * blockHeight)
    }

    private fun localRect(index: Int): PosterRect {
        val slot = slots[index]
        return PosterRect(slot.x * step, slot.y * step, slot.width * step - gap, slot.height * step - gap)
    }

    private fun stackRect(group: Int, index: Int): PosterRect {
        val random = kotlin.random.Random((stackKeys.getOrNull(index) ?: index.toString()).hashCode())
        val scattered = groupIndices(index).size > 1
        // Stable irregular offsets and angles give larger piles a loosely stacked book feel.
        val dx = if (scattered) random.nextFloat() * step * 0.30f else 0f
        val dy = if (scattered) random.nextFloat() * step * 0.30f else 0f
        return PosterRect((group % 2) * stackPitch + step * 0.24f + dx,
            (group / 2) * stackPitch + step * 0.24f + dy, step * 2.1f - gap, step * 2.1f - gap)
    }

    /** Visits blocks in the viewport, never scans the library. Overscan warms the next covers. */
    fun visibleIndices(bounds: PosterRect, overscan: Float = step): List<Int> {
        if (count <= 0 || bounds.width <= 0 || bounds.height <= 0) return emptyList()
        val expanded = PosterRect(bounds.x - overscan, bounds.y - overscan, bounds.width + overscan * 2, bounds.height + overscan * 2)
        val firstColumn = max(0, floor((expanded.x - overhang) / blockWidth).toInt())
        val lastColumn = min(blockColumns - 1, floor(expanded.right / blockWidth).toInt())
        val firstRow = max(0, floor((expanded.y - overhang) / blockHeight).toInt())
        val lastRow = min(blockRows - 1, floor(expanded.bottom / blockHeight).toInt())
        return buildList {
            for (row in firstRow..lastRow) for (column in firstColumn..lastColumn) {
                val block = row * blockColumns + column
                for (index in blockIndices(block)) {
                    if (rect(index).intersects(expanded)) add(index)
                }
            }
        }
    }

    /** Repeated cells have unique spatial keys, but keep the original song/queue index. */
    fun visiblePosters(bounds: PosterRect, overscan: Float = step): List<PosterPlacement> {
        if (!infinite) return visibleIndices(bounds, overscan).map { PosterPlacement(it, rect(it), "$it") }
        if (count <= 0 || bounds.width <= 0 || bounds.height <= 0) return emptyList()
        val expanded = PosterRect(bounds.x - overscan, bounds.y - overscan, bounds.width + overscan * 2, bounds.height + overscan * 2)
        return buildList {
            for (row in floor((expanded.y - overhang) / blockHeight).toInt()..floor(expanded.bottom / blockHeight).toInt()) {
                for (column in floor((expanded.x - overhang) / blockWidth).toInt()..floor(expanded.right / blockWidth).toInt()) {
                    val block = Math.floorMod(row, blockRows) * blockColumns + Math.floorMod(column, blockColumns)
                    if (stacked) {
                        repeat(4) { localGroup ->
                            val group = (block * 4 + localGroup) % groups.size
                            groups[group].forEachIndexed { depth, index ->
                                val local = stackRect(localGroup, index)
                                val posterRect = local.copy(x = column * blockWidth + local.x, y = row * blockHeight + local.y)
                                if (posterRect.intersects(expanded)) add(PosterPlacement(index, posterRect, "$row:$column:$localGroup:$depth"))
                            }
                        }
                    } else slots.forEachIndexed { slotIndex, _ ->
                        val local = localRect(slotIndex)
                        val posterRect = local.copy(x = column * blockWidth + local.x, y = row * blockHeight + local.y)
                        if (posterRect.intersects(expanded)) {
                            add(PosterPlacement((block * slots.size + slotIndex) % count, posterRect, "$row:$column:$slotIndex"))
                        }
                    }
                }
            }
        }
    }

    fun stackGroupKey(placement: PosterPlacement): String = if (infinite) {
        placement.key.substringBeforeLast(':')
    } else groupOf[placement.index].toString()

    fun nearestRect(index: Int, bounds: PosterRect): PosterRect {
        val rect = rect(index)
        if (!infinite) return rect
        return rect.copy(
            x = rect.x + kotlin.math.round((bounds.centerX - rect.centerX) / repeatWidth) * repeatWidth,
            y = rect.y + kotlin.math.round((bounds.centerY - rect.centerY) / repeatHeight) * repeatHeight
        )
    }
}

internal data class PosterCamera(val x: Float = 20f, val y: Float = 24f, val scale: Float = 0.72f) {
    fun bounds(viewWidth: Float, viewHeight: Float) = PosterRect(-x / scale, -y / scale, viewWidth / scale, viewHeight / scale)

    fun clamped(geometry: PosterWallGeometry, viewWidth: Float, viewHeight: Float): PosterCamera {
        if (geometry.infinite && geometry.count > 0) {
            // Rebase by complete repeating fields before Float precision can erode during long
            // browsing sessions. The picture on either side of the rebase is identical.
            fun wrap(offset: Float, period: Float): Float {
                val phase = offset % (period * scale)
                return if (phase > 0f) phase - period * scale else phase
            }
            return copy(x = wrap(x, geometry.repeatWidth), y = wrap(y, geometry.repeatHeight))
        }
        fun clampAxis(offset: Float, extent: Float, viewport: Float): Float {
            val size = extent * scale
            return if (size + 40f < viewport) (viewport - size) / 2f
            else offset.coerceIn(viewport - size - 24f, 24f)
        }
        return copy(x = clampAxis(x, geometry.width, viewWidth), y = clampAxis(y, geometry.height, viewHeight))
    }

    fun transformed(panX: Float, panY: Float, zoom: Float, focusX: Float, focusY: Float): PosterCamera {
        val nextScale = (scale * zoom).coerceIn(0.45f, 1.5f)
        val ratio = nextScale / scale
        return PosterCamera(focusX - (focusX - x) * ratio + panX, focusY - (focusY - y) * ratio + panY, nextScale)
    }

    fun centeredOn(rect: PosterRect, viewWidth: Float, viewHeight: Float) =
        copy(x = viewWidth / 2f - rect.centerX * scale, y = viewHeight / 2f - rect.centerY * scale)
}
