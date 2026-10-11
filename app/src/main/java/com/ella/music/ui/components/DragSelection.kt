package com.ella.music.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.math.abs

/** One gesture preserves earlier picks and restores them when its range is pulled back. */
internal class DragSelectionRange<T : Any>(orderedIds: List<T>, private val before: Set<T>, anchor: T) {
    private val ids = orderedIds.distinct()
    private val indexes = ids.withIndex().associate { it.value to it.index }
    private val anchorIndex = checkNotNull(indexes[anchor])
    private val selecting = anchor !in before
    private var lastIndex = -1
    fun update(target: T): Set<T>? {
        val index = indexes[target] ?: return null
        if (index == lastIndex) return null
        lastIndex = index
        val range = ids.subList(minOf(anchorIndex, index), maxOf(anchorIndex, index) + 1)
        return if (selecting) before + range else before - range.toSet()
    }
}

internal fun dragSelectionScrollSpeed(y: Float, top: Float, bottom: Float, edge: Float, maxSpeed: Float): Float {
    val zone = edge.coerceAtMost((bottom - top).coerceAtLeast(0f) / 2f)
    if (zone <= 0f) return 0f
    val amount = when {
        y < top + zone -> -((top + zone - y) / zone).coerceIn(0f, 1f)
        y > bottom - zone -> ((y - bottom + zone) / zone).coerceIn(0f, 1f)
        else -> 0f
    }
    return amount * abs(amount) * maxSpeed
}

private data class DragSelectionItem(val key: Any, val left: Float, val top: Float, val width: Float, val height: Float)
private data class DragSelectionViewport(val top: Float, val bottom: Float, val items: List<DragSelectionItem>)

@Composable
fun <T : Any> Modifier.dragSelectList(state: LazyListState, selection: LibrarySelectionState<T>,
    orderedIds: List<T>, itemIdsByKey: Map<Any, List<T>>? = null, excludedEndWidth: Dp = 0.dp): Modifier = dragSelectList(
    state, selection.selectionMode, orderedIds, selection.selectedIds,
    onSelectionChange = { selection.selectedIds = it; selection.rangeAnchorId = null; selection.rangeTargetId = null },
    onToggle = selection::toggleSelection, itemIdsByKey = itemIdsByKey, excludedEndWidth = excludedEndWidth)

@Composable
fun <T : Any> Modifier.dragSelectGrid(state: LazyGridState, selection: LibrarySelectionState<T>,
    orderedIds: List<T>, itemIdsByKey: Map<Any, List<T>>? = null): Modifier = dragSelectGrid(
    state, selection.selectionMode, orderedIds, selection.selectedIds,
    onSelectionChange = { selection.selectedIds = it; selection.rangeAnchorId = null; selection.rangeTargetId = null },
    onToggle = selection::toggleSelection, itemIdsByKey = itemIdsByKey)

@Composable
fun <T : Any> Modifier.dragSelectList(state: LazyListState, enabled: Boolean, orderedIds: List<T>,
    selectedIds: Set<T>, onSelectionChange: (Set<T>) -> Unit, onToggle: ((T) -> Unit)? = null,
    itemIdsByKey: Map<Any, List<T>>? = null, columns: Int = 1, excludedEndWidth: Dp = 0.dp,
    excludedStartWidth: Dp = 0.dp, onDragEnd: ((Set<T>) -> Unit)? = null): Modifier = dragSelection(
    state, enabled, orderedIds, selectedIds, onSelectionChange, onToggle, itemIdsByKey, columns, excludedEndWidth, excludedStartWidth, onDragEnd,
    viewport = { width -> state.layoutInfo.let { info ->
        DragSelectionViewport(0f, (info.viewportSize.height - info.afterContentPadding).coerceAtLeast(info.viewportSize.height / 2).toFloat(),
            info.visibleItemsInfo.map { DragSelectionItem(it.key, 0f, it.offset.toFloat(), width, it.size.toFloat()) })
    } }, scroll = { state.scrollBy(it) })

@Composable
fun <T : Any> Modifier.dragSelectGrid(state: LazyGridState, enabled: Boolean, orderedIds: List<T>,
    selectedIds: Set<T>, onSelectionChange: (Set<T>) -> Unit, onToggle: ((T) -> Unit)? = null,
    itemIdsByKey: Map<Any, List<T>>? = null): Modifier = dragSelection(
    state, enabled, orderedIds, selectedIds, onSelectionChange, onToggle, itemIdsByKey, 1, 0.dp, 0.dp, null,
    viewport = { _ -> state.layoutInfo.let { info ->
        DragSelectionViewport(0f, (info.viewportSize.height - info.afterContentPadding).coerceAtLeast(info.viewportSize.height / 2).toFloat(),
            info.visibleItemsInfo.map { DragSelectionItem(it.key, it.offset.x.toFloat(), it.offset.y.toFloat(),
                it.size.width.toFloat(), it.size.height.toFloat()) })
    } }, scroll = { state.scrollBy(it) })

@Composable
private fun <T : Any> Modifier.dragSelection(state: Any, enabled: Boolean, orderedIds: List<T>,
    selectedIds: Set<T>, onSelectionChange: (Set<T>) -> Unit, onToggle: ((T) -> Unit)?,
    itemIdsByKey: Map<Any, List<T>>?, columns: Int, excludedEndWidth: Dp, excludedStartWidth: Dp,
    onDragEnd: ((Set<T>) -> Unit)?,
    viewport: (Float) -> DragSelectionViewport, scroll: suspend (Float) -> Float): Modifier {
    if (!enabled) return this
    val latestIds = rememberUpdatedState(orderedIds)
    val latestSelected = rememberUpdatedState(selectedIds)
    val latestChange = rememberUpdatedState(onSelectionChange)
    val latestToggle = rememberUpdatedState(onToggle)
    val latestEnd = rememberUpdatedState(onDragEnd)
    val idsByKey = itemIdsByKey ?: remember(orderedIds) { orderedIds.associate { (it as Any) to listOf(it) } }
    val latestKeys = rememberUpdatedState(idsByKey)
    val latestViewport = rememberUpdatedState(viewport)
    val latestScroll = rememberUpdatedState(scroll)
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val edge = with(density) { 64.dp.toPx() }
    val maxSpeed = with(density) { 900.dp.toPx() }
    val excludedEndPx = with(density) { excludedEndWidth.toPx() }
    val excludedStartPx = with(density) { excludedStartWidth.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return pointerInput(state, enabled, columns, excludedEndPx, excludedStartPx, rtl) {
        coroutineScope {
            val scope = this
            fun itemAt(position: Offset, clampToViewport: Boolean = false): T? {
                val view = latestViewport.value(size.width.toFloat())
                if (view.bottom <= view.top) return null
                val y = if (clampToViewport) position.y.coerceIn(view.top + 0.5f, view.bottom - 0.5f) else position.y
                val candidates = view.items.filter {
                    position.x >= it.left && position.x < it.left + it.width && it.key in latestKeys.value
                }
                val item = candidates.firstOrNull { y >= it.top && y < it.top + it.height }
                    ?: if (clampToViewport) candidates.minByOrNull {
                        when { y < it.top -> it.top - y; y > it.top + it.height -> y - it.top - it.height; else -> 0f }
                    } else null
                if (item == null) return null
                val ids = latestKeys.value[item.key] ?: return null
                val physicalColumn = (((position.x - item.left) / item.width.coerceAtLeast(1f)) * columns).toInt()
                val column = if (rtl && columns > 1) columns - 1 - physicalColumn else physicalColumn
                return ids.getOrNull(column)
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (if (rtl) down.position.x < excludedEndPx else down.position.x > size.width - excludedEndPx) return@awaitEachGesture
                if (if (rtl) down.position.x > size.width - excludedStartPx else down.position.x < excludedStartPx) return@awaitEachGesture
                val anchor = itemAt(down.position) ?: return@awaitEachGesture
                if (anchor !in latestIds.value) return@awaitEachGesture
                // Own selection taps too, so the row's combinedClickable cannot toggle a hold twice.
                // Movement before the hold is left unconsumed for normal list scrolling.
                down.consume()
                val beforeHold = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    var result: androidx.compose.ui.input.pointer.PointerInputChange? = null
                    while (result == null) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            ?: return@withTimeoutOrNull down
                        if (!change.pressed || (change.position - down.position).getDistance() > viewConfiguration.touchSlop) result = change
                    }
                    result
                }
                if (beforeHold != null) {
                    if (!beforeHold.pressed && (beforeHold.position - down.position).getDistance() <= viewConfiguration.touchSlop) {
                        beforeHold.consume()
                        latestToggle.value?.invoke(anchor) ?: latestChange.value(
                            if (anchor in latestSelected.value) latestSelected.value - anchor else latestSelected.value + anchor)
                    }
                    return@awaitEachGesture
                }
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                val range = DragSelectionRange(latestIds.value, latestSelected.value, anchor)
                var lastSelection = latestSelected.value
                fun selectTo(id: T) { range.update(id)?.let { lastSelection = it; latestChange.value(it) } }
                selectTo(anchor)
                var position = down.position
                var active = true
                val autoScroll = scope.launch {
                    var lastFrame = 0L
                    while (active) {
                        val view = latestViewport.value(size.width.toFloat())
                        val speed = dragSelectionScrollSpeed(position.y, view.top, view.bottom, edge, maxSpeed)
                        if (speed == 0f) { lastFrame = 0L; delay(20); continue }
                        val frame = withFrameNanos { it }
                        val seconds = if (lastFrame == 0L) 0.016f else ((frame - lastFrame) / 1_000_000_000f).coerceAtMost(0.05f)
                        lastFrame = frame
                        if (speed != 0f && latestScroll.value(speed * seconds) != 0f) {
                            itemAt(position, true)?.let(::selectTo)
                        }
                    }
                }
                try {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        position = change.position
                        change.consume()
                        itemAt(position, true)?.let(::selectTo)
                    }
                } finally {
                    active = false
                    autoScroll.cancel()
                    latestEnd.value?.invoke(lastSelection)
                }
            }
        }
    }
}
