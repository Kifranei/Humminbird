package com.ella.music.data

import org.json.JSONArray
import org.json.JSONObject

internal fun parseLxSourcesJson(
    json: String,
    defaultName: String
): List<LxSourceConfig> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            LxSourceConfig(
                id = item.optString("id"),
                url = item.optString("url"),
                name = item.optString("name").ifBlank { defaultName },
                script = item.optString("script")
            )
        }.filter { it.id.isNotBlank() && it.script.isNotBlank() }
    }.getOrDefault(emptyList())
}

internal fun List<LxSourceConfig>.toLxSourcesJson(): String {
    val array = JSONArray()
    forEach { source ->
        array.put(
            JSONObject()
                .put("id", source.id)
                .put("url", source.url)
                .put("name", source.name)
                .put("script", source.script)
        )
    }
    return array.toString()
}

internal fun String.toLxSourceId(script: String): String {
    val source = trim().ifBlank { script.take(64) }
    return "lx_${source.hashCode()}"
}
