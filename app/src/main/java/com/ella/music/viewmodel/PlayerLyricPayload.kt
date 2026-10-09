package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine

internal fun List<LyricLine>.bluetoothPayloadAt(
    index: Int,
    includeTranslation: Boolean,
    includePronunciation: Boolean
): Pair<String, String?>? {
    return lyricPayloadAt(
        index = index,
        includeTranslation = includeTranslation,
        includePronunciation = includePronunciation
    )
}

internal fun List<LyricLine>.lyricPayloadAt(
    index: Int,
    includeTranslation: Boolean,
    includePronunciation: Boolean = false
): Pair<String, String?>? {
    val line = getOrNull(index) ?: return null
    val text = line.text.cleanBluetoothLyricText() ?: return null
    val directTranslation = when {
        includePronunciation -> line.pronunciation?.cleanBluetoothLyricText()
        else -> line.secondaryLyricText(includeTranslation)?.cleanBluetoothLyricText()
    }

    if (!includeTranslation && !includePronunciation) return text to null

    if (includePronunciation && directTranslation != null) {
        return text to directTranslation
    }

    if (directTranslation != null) {
        return orderBluetoothLyricPair(text, directTranslation, preferFirstAsPrimary = true)
    }

    return text to null
}

private fun LyricLine?.secondaryLyricText(includeTranslation: Boolean): String? {
    if (!includeTranslation) return null
    return this?.translation
        ?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
        ?: this?.backgroundTranslation?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
}

private fun orderBluetoothLyricPair(
    first: String,
    second: String,
    preferFirstAsPrimary: Boolean
): Pair<String, String> {
    val firstLooksTranslated = first.looksLikeChineseTranslationOf(second)
    val secondLooksTranslated = second.looksLikeChineseTranslationOf(first)
    return when {
        firstLooksTranslated && !secondLooksTranslated -> second to first
        secondLooksTranslated && !firstLooksTranslated -> first to second
        preferFirstAsPrimary -> first to second
        else -> second to first
    }
}

private fun String.cleanBluetoothLyricText(): String? =
    trim().takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }

private fun String.looksLikeChineseTranslationOf(other: String): Boolean =
    hasCjkOrHangul() && other.hasLatinLetter()

private fun String.hasLatinLetter(): Boolean =
    any { it in 'A'..'Z' || it in 'a'..'z' }

private fun String.hasCjkOrHangul(): Boolean =
    any { char ->
        Character.UnicodeBlock.of(char) in setOf(
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES,
            Character.UnicodeBlock.HANGUL_JAMO,
            Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
        )
    }

private fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true
    return content.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮') ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}
