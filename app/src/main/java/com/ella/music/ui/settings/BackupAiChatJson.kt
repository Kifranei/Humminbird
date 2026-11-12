package com.ella.music.ui.settings

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun exportAiChatBackupJson(context: Context): JSONObject {
    val dir = aiChatSessionsDir(context)
    val sessions = JSONArray()
    if (dir.exists()) {
        dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) && it.name != "index.json" }
            ?.sortedBy { it.name }
            ?.forEach { file ->
                runCatching {
                    sessions.put(
                        JSONObject()
                            .put("fileName", file.name)
                            .put("messages", JSONArray(file.readText(Charsets.UTF_8)))
                    )
                }
            }
    }
    val index = runCatching {
        val file = File(dir, "index.json")
        if (file.exists()) JSONArray(file.readText(Charsets.UTF_8)) else JSONArray()
    }.getOrDefault(JSONArray())
    return JSONObject()
        .put("version", 1)
        .put("index", index)
        .put("sessions", sessions)
}

internal fun restoreAiChatBackupJson(context: Context, payload: JSONObject) {
    val dir = aiChatSessionsDir(context).apply { mkdirs() }
    dir.listFiles()
        ?.filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
        ?.forEach { it.delete() }

    val index = payload.optJSONArray("index") ?: JSONArray()
    File(dir, "index.json").writeText(index.toString(), Charsets.UTF_8)

    val sessions = payload.optJSONArray("sessions") ?: JSONArray()
    for (i in 0 until sessions.length()) {
        val item = sessions.optJSONObject(i) ?: continue
        val fileName = item.optString("fileName").takeIf { it.isSafeAiChatBackupFileName() } ?: continue
        val messages = item.optJSONArray("messages") ?: continue
        File(dir, fileName).writeText(messages.toString(), Charsets.UTF_8)
    }
}

private fun aiChatSessionsDir(context: Context): File =
    File(context.filesDir, "ai_chat_sessions")

private fun String.isSafeAiChatBackupFileName(): Boolean =
    matches(Regex("""[A-Za-z0-9._-]+\.json""")) && this != "index.json"
