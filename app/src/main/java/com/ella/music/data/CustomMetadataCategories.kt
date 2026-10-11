package com.ella.music.data

import com.ella.music.data.model.Song
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** Identity follows the tag key, so renaming a category preserves its order and pins. */
data class CustomMetadataCategory(val name: String, val tagKey: String) {
    val type: String get() = "tag_" + tagKey.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
}

fun customCategoryTagKey(type: String): String? {
    if (!type.startsWith("tag_")) return null
    return runCatching {
        val hex = type.removePrefix("tag_")
        require(hex.isNotEmpty() && hex.length % 2 == 0)
        String(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
            .takeIf { it.isNotBlank() && it.none(Char::isISOControl) }
    }.getOrNull()
}

internal fun normalizeCustomCategories(items: List<CustomMetadataCategory>): List<CustomMetadataCategory> =
    items.mapNotNull { item ->
        val name = item.name.trim()
        val key = item.tagKey.trim().uppercase(Locale.ROOT)
        if (name.isEmpty() || key.isEmpty() || name.any(Char::isISOControl) || key.any(Char::isISOControl)) null
        else CustomMetadataCategory(name, key)
    }.distinctBy { it.tagKey }

internal fun decodeCustomCategories(value: String): List<CustomMetadataCategory> = runCatching {
    val json = JSONArray(value)
    normalizeCustomCategories((0 until json.length()).mapNotNull { index ->
        val item = json.optJSONObject(index) ?: return@mapNotNull null
        CustomMetadataCategory(item.optString("name"), item.optString("tagKey"))
    })
}.getOrDefault(emptyList())

internal fun encodeCustomCategories(items: List<CustomMetadataCategory>): String = JSONArray().apply {
    normalizeCustomCategories(items).forEach { item ->
        put(JSONObject().put("name", item.name).put("tagKey", item.tagKey))
    }
}.toString()

/** Use the same matching in counts, cards, detail pages, and batch editing. */
fun Song.customCategoryValues(type: String): List<String> {
    val key = customCategoryTagKey(type) ?: return emptyList()
    val raw = customTags.entries.filter { it.key.equals(key, ignoreCase = true) }.flatMap { it.value }
    val fallback = when (key) {
        "TITLE" -> title
        "ARTIST" -> artist
        "ALBUM" -> album
        "ALBUMARTIST", "ALBUM ARTIST" -> albumArtist
        "COMPOSER" -> composer
        "ARRANGER" -> arranger
        "LYRICIST" -> lyricist
        "GENRE" -> genre
        "DATE", "YEAR" -> year
        else -> ""
    }
    val values = raw.ifEmpty { listOf(fallback) }
    return values.flatMap { value ->
        when (key) {
            "ARTIST", "ALBUMARTIST", "ALBUM ARTIST", "COMPOSER", "ARRANGER", "LYRICIST", "SONGWRITER", "SONGWRITERS" -> splitArtistNames(value)
            "GENRE" -> splitGenreNames(value)
            else -> value.split(';', '；').map(String::trim).filter(String::isNotEmpty)
        }
    }.distinctBy { it.tagIdentityKey() }
}

/** Lyrics remain in their existing cache rather than being duplicated in every library record. */
internal fun Map<String, List<String>>.libraryCustomTags(): Map<String, List<String>> = filterKeys { key ->
    val normalized = key.uppercase(Locale.ROOT)
    normalized.replace(" ", "") !in setOf("LYRICS", "LYRIC", "SYNCEDLYRICS", "UNSYNCEDLYRICS",
        "TTMLLYRICS", "TTMLLYRIC", "TTML", "USLT", "SYLT", "©LYR", "163KEY", "NETEASEKEY",
        "----:COM.APPLE.ITUNES:LYRICS")
}

fun metadataCategoryEditKey(type: String): String? = when (type) {
    "genre" -> "GENRE"
    "year" -> "DATE"
    else -> customCategoryTagKey(type)
}
