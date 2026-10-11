package com.ella.music.ui.poster

import org.json.JSONArray

/** Saved identities survive presentation shuffles, filtering and changing queue positions. */
internal data class PosterStackLayout(val groups: List<List<String>>) {
    private val membership = buildMap { groups.forEachIndexed { index, group -> group.forEach { put(it, index) } } }

    fun members(key: String): List<String> = membership[key]?.let(groups::get).orEmpty()

    /** Members are ordered from bottom to top; insert immediately beside the chosen layer. */
    fun move(key: String, targetKey: String, before: Boolean = false): PosterStackLayout? {
        val from = membership[key] ?: return null
        val target = membership[targetKey] ?: return null
        if (key == targetKey || (from != target && groups[target].size >= MAX_POSTER_STACK_SIZE)) return null
        val next = groups.mapIndexed { index, group ->
            val remaining = if (index == from) group - key else group
            if (index == target) remaining.toMutableList().apply {
                add(indexOf(targetKey) + if (before) 0 else 1, key)
            } else remaining
        }.filter(List<String>::isNotEmpty)
        return if (next == groups) null else PosterStackLayout(next)
    }

    fun rearranged(seed: Int): PosterStackLayout {
        if (groups.size < 2) return this
        val shuffled = groups.shuffled(kotlin.random.Random(seed))
        return copy(groups = if (shuffled != groups) shuffled else groups.drop(1) + groups.take(1))
    }

    fun indices(items: List<PosterWallItem>): List<List<Int>> {
        val indexByKey = items.withIndex().associate { it.value.stackKey to it.index }
        return groups.map { group -> group.mapNotNull(indexByKey::get) }.filter(List<Int>::isNotEmpty)
    }

    fun encode(): String = JSONArray().apply {
        groups.forEach { group -> put(JSONArray(group)) }
    }.toString()

    companion object {
        fun resolve(items: List<PosterWallItem>, saved: String): PosterStackLayout {
            val remaining = items.mapTo(linkedSetOf()) { it.stackKey }
            val groups = mutableListOf<List<String>>()
            // Never let a malformed backup lose songs or duplicate them between stacks.
            val array = runCatching { JSONArray(saved) }.getOrNull()
            if (array != null) for (i in 0 until array.length()) {
                val group = array.optJSONArray(i) ?: continue
                val keys = buildList {
                    for (j in 0 until group.length()) {
                        val key = group.optString(j)
                        if (key in remaining && size < MAX_POSTER_STACK_SIZE) {
                            add(key); remaining.remove(key)
                        }
                    }
                }
                if (keys.isNotEmpty()) groups += keys
            }
            groups += remaining.toList().chunked(3)
            return PosterStackLayout(groups)
        }
    }
}

internal const val MAX_POSTER_STACK_SIZE = 10
