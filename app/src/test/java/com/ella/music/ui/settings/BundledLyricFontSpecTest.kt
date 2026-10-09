package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledLyricFontSpecTest {
    @Test
    fun noLyricFontsAreBundled() {
        assertTrue(BUNDLED_FONT_SPECS.isEmpty())
    }

    @Test
    fun bundledFontDestinationsAreUnique() {
        assertEquals(BUNDLED_FONT_SPECS.size, BUNDLED_FONT_SPECS.map { it.fileName }.distinct().size)
        assertTrue(BUNDLED_FONT_SPECS.all { it.assetPath.startsWith("fonts/") })
    }

    @Test
    fun buildFontDropdownOptionsUsesSystemDefaultAndDropsRetiredBundledFonts() {
        val fonts = listOf(
            FontChoice("Inter Bold", "/app/files/lyric_builtin_fonts/Inter-Bold.ttf", "内置", 0),
            FontChoice("MiSans Bold", "/app/files/lyric_builtin_fonts/MiSans-Bold.ttf", "内置", 0),
            FontChoice("Custom Font", "/app/files/lyric_custom_fonts/Custom.ttf", "自定义", 1)
        )

        val options = buildFontDropdownOptions(
            fonts = fonts,
            currentPath = "",
            currentName = "Inter Bold",
            defaultTitle = "系统默认",
            systemDefaultTitle = "系统默认"
        )
        assertEquals(2, options.size)
        assertEquals("系统默认", options[0].name)
        assertEquals(SYSTEM_FONT_PATH, options[0].path)
        assertEquals("Custom Font", options[1].name)
        assertEquals("/app/files/lyric_custom_fonts/Custom.ttf", options[1].path)
        assertEquals(0, options.count { it.name == "Inter Bold" || it.name == "MiSans Bold" })
    }

    @Test
    fun resolveFontDropdownSelectedIndexMatchesCorrectly() {
        val options = listOf(
            FontDropdownOption("系统默认", SYSTEM_FONT_PATH),
            FontDropdownOption("Custom Font", "/app/files/lyric_custom_fonts/Custom.ttf")
        )

        assertEquals(0, resolveFontDropdownSelectedIndex(options, "", "系统默认"))
        assertEquals(0, resolveFontDropdownSelectedIndex(options, SYSTEM_FONT_PATH, "系统默认"))
        assertEquals(
            0,
            resolveFontDropdownSelectedIndex(
                options,
                "/data/user/0/com.ella.music/files/lyric_builtin_fonts/Inter-Bold.ttf",
                "系统默认"
            )
        )
        assertEquals(
            0,
            resolveFontDropdownSelectedIndex(options, "/app/files/lyric_builtin_fonts/MiSans-Bold.ttf", "系统默认")
        )
        assertEquals(1, resolveFontDropdownSelectedIndex(options, "/app/files/lyric_custom_fonts/Custom.ttf", "系统默认"))
        assertEquals("系统默认", lyricFontChoiceLabel("", "Inter Bold", "系统默认"))
        assertEquals("系统默认", lyricFontChoiceLabel("/files/MiSans-Bold.ttf", "MiSans Bold", "系统默认"))
        assertEquals("Custom Font", lyricFontChoiceLabel("/app/files/lyric_custom_fonts/Custom.ttf", "Custom Font", "系统默认"))
    }
}
