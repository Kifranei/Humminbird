@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ella.music.ui.settings

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.LocalInBottomSheet
import com.ella.music.ui.components.LocalSettingsCardFrosting
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.player.DesktopLyricService
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LyricFontScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val legacyWesternFontName by settingsManager.lyricWesternFontName.collectAsState(initial = "")
    val legacyWesternFontPath by settingsManager.lyricWesternFontPath.collectAsState(initial = "")
    val legacyCjkFontName by settingsManager.lyricCjkFontName.collectAsState(initial = "")
    val legacyCjkFontPath by settingsManager.lyricCjkFontPath.collectAsState(initial = "")
    val globalWesternFontName by settingsManager.globalWesternFontName.collectAsState(initial = "")
    val globalWesternFontPath by settingsManager.globalWesternFontPath.collectAsState(initial = "")
    val globalCjkFontName by settingsManager.globalCjkFontName.collectAsState(initial = "")
    val globalCjkFontPath by settingsManager.globalCjkFontPath.collectAsState(initial = "")
    val originalWesternFontName by settingsManager.lyricOriginalWesternFontName.collectAsState(initial = "")
    val originalWesternFontPath by settingsManager.lyricOriginalWesternFontPath.collectAsState(initial = "")
    val originalCjkFontName by settingsManager.lyricOriginalCjkFontName.collectAsState(initial = "")
    val originalCjkFontPath by settingsManager.lyricOriginalCjkFontPath.collectAsState(initial = "")
    val translationWesternFontName by settingsManager.lyricTranslationWesternFontName.collectAsState(initial = "")
    val translationWesternFontPath by settingsManager.lyricTranslationWesternFontPath.collectAsState(initial = "")
    val translationCjkFontName by settingsManager.lyricTranslationCjkFontName.collectAsState(initial = "")
    val translationCjkFontPath by settingsManager.lyricTranslationCjkFontPath.collectAsState(initial = "")
    val lyricFontWeight by settingsManager.lyricFontWeight.collectAsState(initial = 800)
    val lyricFontItalic by settingsManager.lyricFontItalic.collectAsState(initial = false)
    val lyricShareUseLyricFont by settingsManager.lyricShareUseLyricFont.collectAsState(initial = true)
    val lyricFontApplyToPage by settingsManager.lyricFontApplyToPage.collectAsState(initial = true)
    val lyricFontApplyToDesktop by settingsManager.lyricFontApplyToDesktop.collectAsState(initial = true)
    var fonts by remember { mutableStateOf<List<FontChoice>>(emptyList()) }
    var systemFonts by remember { mutableStateOf<List<FontChoice>>(emptyList()) }
    var showSystemFontPicker by remember { mutableStateOf(false) }
    var activeTarget by remember { mutableStateOf(LyricFontTarget.GlobalWestern) }
    val pageBackground = com.ella.music.ui.components.ellaPageBackground()
    val selectedFontPath = when (activeTarget) {
        LyricFontTarget.GlobalWestern -> globalWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.GlobalCjk -> globalCjkFontPath
        LyricFontTarget.OriginalWestern -> originalWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.OriginalCjk -> originalCjkFontPath.ifBlank { legacyCjkFontPath }
        LyricFontTarget.TranslationWestern -> translationWesternFontPath.ifBlank { legacyWesternFontPath }
        LyricFontTarget.TranslationCjk -> translationCjkFontPath.ifBlank { legacyCjkFontPath }
    }
    val currentSystemFont = remember(selectedFontPath, systemFonts) {
        systemFonts.firstOrNull { it.path == selectedFontPath }
    }
    val systemDefaultTitle = stringResource(R.string.settings_lyric_font_system_default)
    val globalWesternDisplayName = lyricFontChoiceLabel(
        globalWesternFontPath.ifBlank { legacyWesternFontPath },
        globalWesternFontName.ifBlank { legacyWesternFontName },
        systemDefaultTitle
    )
    val globalCjkDisplayName = lyricFontChoiceLabel(globalCjkFontPath, globalCjkFontName, systemDefaultTitle)
    val originalWesternDisplayName = lyricFontChoiceLabel(
        originalWesternFontPath.ifBlank { legacyWesternFontPath },
        originalWesternFontName.ifBlank { legacyWesternFontName },
        systemDefaultTitle
    )
    val originalCjkDisplayName = lyricFontChoiceLabel(
        originalCjkFontPath.ifBlank { legacyCjkFontPath },
        originalCjkFontName.ifBlank { legacyCjkFontName },
        systemDefaultTitle
    )
    val translationWesternDisplayName = lyricFontChoiceLabel(
        translationWesternFontPath.ifBlank { legacyWesternFontPath },
        translationWesternFontName.ifBlank { legacyWesternFontName },
        systemDefaultTitle
    )
    val translationCjkDisplayName = lyricFontChoiceLabel(
        translationCjkFontPath.ifBlank { legacyCjkFontPath },
        translationCjkFontName.ifBlank { legacyCjkFontName },
        systemDefaultTitle
    )
    val listState = rememberSettingsLazyListState("settings_lyric_font")

    suspend fun applyFont(font: FontChoice) {
        when (activeTarget) {
            LyricFontTarget.GlobalWestern -> settingsManager.setGlobalFont(font.name, font.path, globalCjkFontName, globalCjkFontPath)
            LyricFontTarget.GlobalCjk -> settingsManager.setGlobalFont(globalWesternFontName, globalWesternFontPath, font.name, font.path)
            LyricFontTarget.OriginalWestern -> settingsManager.setLyricOriginalFont(font.name, font.path, originalCjkFontName, originalCjkFontPath)
            LyricFontTarget.OriginalCjk -> settingsManager.setLyricOriginalFont(originalWesternFontName, originalWesternFontPath, font.name, font.path)
            LyricFontTarget.TranslationWestern -> settingsManager.setLyricTranslationFont(font.name, font.path, translationCjkFontName, translationCjkFontPath)
            LyricFontTarget.TranslationCjk -> settingsManager.setLyricTranslationFont(translationWesternFontName, translationWesternFontPath, font.name, font.path)
        }
        notifyDesktopLyricFontChanged(context, settingsManager)
    }

    LaunchedEffect(Unit) {
        fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
        systemFonts = withContext(Dispatchers.IO) { collectSystemFontChoices(context) }
    }
    LaunchedEffect(lyricFontItalic) {
        if (lyricFontItalic) settingsManager.setLyricFontItalic(false)
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { copyImportedFont(context, uri) }
            }.onSuccess { font ->
                applyFont(font)
                fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_lyric_font_applied, font.name),
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure {
                Toast.makeText(context, context.getString(R.string.settings_lyric_font_import_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }

    var currentTab by remember { mutableStateOf(LyricFontTab.Global) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_font_screen_title),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(onClick = { importLauncher.launch(SUPPORTED_FONT_MIME_TYPES) }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.settings_lyric_font_import),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            color = Color.Transparent
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LyricFontTab.entries.forEach { tab ->
                val isSelected = currentTab == tab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            color = if (isSelected) MiuixTheme.colorScheme.surface else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            currentTab = tab
                            when (tab) {
                                LyricFontTab.Global -> if (activeTarget != LyricFontTarget.GlobalWestern && activeTarget != LyricFontTarget.GlobalCjk) {
                                    activeTarget = LyricFontTarget.GlobalWestern
                                }
                                LyricFontTab.Original -> if (activeTarget != LyricFontTarget.OriginalWestern && activeTarget != LyricFontTarget.OriginalCjk) {
                                    activeTarget = LyricFontTarget.OriginalWestern
                                }
                                LyricFontTab.Translation -> if (activeTarget != LyricFontTarget.TranslationWestern && activeTarget != LyricFontTarget.TranslationCjk) {
                                    activeTarget = LyricFontTarget.TranslationWestern
                                }
                                LyricFontTab.Apply -> {}
                            }
                        }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(tab.titleRes),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            contentPadding = WindowInsets.navigationBars
                .asPaddingValues()
                .let { PaddingValues(bottom = it.calculateBottomPadding() + 96.dp) }
        ) {
            when (currentTab) {
                LyricFontTab.Global -> {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        LyricFontWeightCard(
                            westernFontPath = globalWesternFontPath.ifBlank { legacyWesternFontPath },
                            cjkFontPath = globalCjkFontPath,
                            lyricFontWeight = lyricFontWeight,
                            onWeightChange = { weight ->
                                scope.launch {
                                    settingsManager.setLyricFontWeight(weight)
                                    notifyDesktopLyricFontChanged(context, settingsManager)
                                }
                            }
                        )
                        val westernPath = globalWesternFontPath.ifBlank { legacyWesternFontPath }
                        val westernOptions = remember(fonts, westernPath, globalWesternDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = westernPath,
                                currentName = globalWesternDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        val cjkPath = globalCjkFontPath
                        val cjkOptions = remember(fonts, cjkPath, globalCjkDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = cjkPath,
                                currentName = globalCjkDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
                            Column {
                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_global_western) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_global_western),
                                    summary = globalWesternDisplayName,
                                    items = westernOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(westernOptions, westernPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = westernOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setGlobalFont(chosen.name, chosen.path, globalCjkFontName, globalCjkFontPath)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_global_cjk) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_global_cjk),
                                    summary = globalCjkDisplayName,
                                    items = cjkOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(cjkOptions, cjkPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = cjkOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setGlobalFont(globalWesternFontName, globalWesternFontPath, chosen.name, chosen.path)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                LyricFontTab.Original -> {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        LyricFontWeightCard(
                            westernFontPath = originalWesternFontPath.ifBlank { legacyWesternFontPath },
                            cjkFontPath = originalCjkFontPath.ifBlank { legacyCjkFontPath },
                            lyricFontWeight = lyricFontWeight,
                            onWeightChange = { weight ->
                                scope.launch {
                                    settingsManager.setLyricFontWeight(weight)
                                    notifyDesktopLyricFontChanged(context, settingsManager)
                                }
                            }
                        )
                        val westernPath = originalWesternFontPath.ifBlank { legacyWesternFontPath }
                        val westernOptions = remember(fonts, westernPath, originalWesternDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = westernPath,
                                currentName = originalWesternDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        val cjkPath = originalCjkFontPath.ifBlank { legacyCjkFontPath }
                        val cjkOptions = remember(fonts, cjkPath, originalCjkDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = cjkPath,
                                currentName = originalCjkDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
                            Column {
                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_original_western) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_original_western),
                                    summary = originalWesternDisplayName,
                                    items = westernOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(westernOptions, westernPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = westernOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setLyricOriginalFont(chosen.name, chosen.path, originalCjkFontName, originalCjkFontPath)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_original_cjk) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_original_cjk),
                                    summary = originalCjkDisplayName,
                                    items = cjkOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(cjkOptions, cjkPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = cjkOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setLyricOriginalFont(originalWesternFontName, originalWesternFontPath, chosen.name, chosen.path)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                LyricFontTab.Translation -> {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        LyricFontWeightCard(
                            westernFontPath = translationWesternFontPath.ifBlank { legacyWesternFontPath },
                            cjkFontPath = translationCjkFontPath.ifBlank { legacyCjkFontPath },
                            lyricFontWeight = lyricFontWeight,
                            onWeightChange = { weight ->
                                scope.launch {
                                    settingsManager.setLyricFontWeight(weight)
                                    notifyDesktopLyricFontChanged(context, settingsManager)
                                }
                            }
                        )
                        val westernPath = translationWesternFontPath.ifBlank { legacyWesternFontPath }
                        val westernOptions = remember(fonts, westernPath, translationWesternDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = westernPath,
                                currentName = translationWesternDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        val cjkPath = translationCjkFontPath.ifBlank { legacyCjkFontPath }
                        val cjkOptions = remember(fonts, cjkPath, translationCjkDisplayName, systemDefaultTitle) {
                            buildFontDropdownOptions(
                                fonts = fonts,
                                currentPath = cjkPath,
                                currentName = translationCjkDisplayName,
                                defaultTitle = systemDefaultTitle,
                                systemDefaultTitle = systemDefaultTitle
                            )
                        }
                        SettingsCardGroup(modifier = Modifier.padding(vertical = 4.dp)) {
                            Column {
                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_translation_western) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_translation_western),
                                    summary = translationWesternDisplayName,
                                    items = westernOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(westernOptions, westernPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = westernOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setLyricTranslationFont(chosen.name, chosen.path, translationCjkFontName, translationCjkFontPath)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                                // search-anchor:start
                                SettingsSearchAnchor(R.string.settings_font_translation_cjk) {
                                WindowSpinnerPreference(
                                    title = stringResource(R.string.settings_font_translation_cjk),
                                    summary = translationCjkDisplayName,
                                    items = cjkOptions.map { DropdownItem(title = it.name) },
                                    selectedIndex = resolveFontDropdownSelectedIndex(cjkOptions, cjkPath, systemDefaultTitle),
                                    onSelectedIndexChange = { index ->
                                        val chosen = cjkOptions.getOrNull(index) ?: return@WindowSpinnerPreference
                                        scope.launch {
                                            settingsManager.setLyricTranslationFont(translationWesternFontName, translationWesternFontPath, chosen.name, chosen.path)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                        }
                                    }
                                )
                                } // search-anchor:end

                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                LyricFontTab.Apply -> {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingsCardGroup {
                            // search-anchor:start
                            SettingsSearchAnchor(R.string.settings_lyric_share_use_lyric_font) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_lyric_share_use_lyric_font),
                                summary = stringResource(R.string.settings_lyric_share_use_lyric_font_summary),
                                checked = lyricShareUseLyricFont,
                                onCheckedChange = { value ->
                                    scope.launch { settingsManager.setLyricShareUseLyricFont(value) }
                                }
                            )
                            } // search-anchor:end

                            // search-anchor:start
                            SettingsSearchAnchor(R.string.settings_lyric_font_apply_to_page) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_lyric_font_apply_to_page),
                                summary = stringResource(R.string.settings_lyric_font_apply_to_page_summary),
                                checked = lyricFontApplyToPage,
                                onCheckedChange = { value ->
                                    scope.launch { settingsManager.setLyricFontApplyToPage(value) }
                                }
                            )
                            } // search-anchor:end

                            // search-anchor:start
                            SettingsSearchAnchor(R.string.settings_lyric_font_apply_to_desktop) {
                            SwitchPreference(
                                title = stringResource(R.string.settings_lyric_font_apply_to_desktop),
                                summary = stringResource(R.string.settings_lyric_font_apply_to_desktop_summary),
                                checked = lyricFontApplyToDesktop,
                                onCheckedChange = { value ->
                                    scope.launch {
                                        settingsManager.setLyricFontApplyToDesktop(value)
                                        notifyDesktopLyricFontChanged(context, settingsManager)
                                    }
                                }
                            )
                            } // search-anchor:end

                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        SystemFontEntryCard(
                            currentSystemFontName = currentSystemFont?.name,
                            currentSystemFontPath = currentSystemFont?.path,
                            currentWeight = lyricFontWeight,
                            onClick = { showSystemFontPicker = true }
                        )
                        val importedFonts = remember(fonts) { fonts.filter { it.sourceRank == FONT_SOURCE_IMPORTED } }
                        if (importedFonts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.settings_lyric_font_source_imported),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                            )
                            importedFonts.forEach { font ->
                                FontChoiceItem(
                                    font = font,
                                    currentWeight = lyricFontWeight,
                                    italic = false,
                                    selected = false,
                                    onClick = {},
                                    onDelete = {
                                        scope.launch {
                                            val deleted = withContext(Dispatchers.IO) { deleteImportedFont(font) }
                                            clearImportedFontReferences(settingsManager, font.path)
                                            notifyDesktopLyricFontChanged(context, settingsManager)
                                            fonts = withContext(Dispatchers.IO) { collectFontChoices(context) }
                                            Toast.makeText(
                                                context,
                                                if (deleted) context.getString(R.string.settings_lyric_font_deleted)
                                                else context.getString(R.string.settings_lyric_font_delete_failed),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showSystemFontPicker) {
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.settings_font_system_pick_title),
            onDismissRequest = { showSystemFontPicker = false }
        ) {
            CompositionLocalProvider(
                LocalSettingsCardFrosting provides null,
                LocalInBottomSheet provides true
            ) {
                if (systemFonts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_font_system_empty),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(18.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .heightIn(max = 480.dp)
                    ) {
                        items(systemFonts, key = { it.path }) { font ->
                            FontChoiceItem(
                                font = font,
                                currentWeight = lyricFontWeight,
                                italic = false,
                                selected = selectedFontPath == font.path,
                                onClick = {
                                    scope.launch {
                                        applyFont(font)
                                        showSystemFontPicker = false
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_lyric_font_applied, font.name),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class FontDropdownOption(
    val name: String,
    val path: String
)

internal fun lyricFontChoiceLabel(path: String, storedName: String, systemDefaultTitle: String): String {
    if (path.isBlank() || path == SYSTEM_FONT_PATH || isRemovedBundledFontPath(path)) return systemDefaultTitle
    return storedName.ifBlank { systemDefaultTitle }
}

internal fun isRemovedBundledFontPath(path: String): Boolean {
    val fileName = path.replace('\\', '/').substringAfterLast('/')
    return fileName.equals("Inter-Bold.ttf", ignoreCase = true) ||
        fileName.equals("MiSans-Bold.ttf", ignoreCase = true) ||
        fileName.equals("MiSansVF.ttf", ignoreCase = true)
}

internal fun buildFontDropdownOptions(
    fonts: List<FontChoice>,
    currentPath: String,
    currentName: String,
    defaultTitle: String,
    systemDefaultTitle: String = defaultTitle
): List<FontDropdownOption> {
    val result = mutableListOf(FontDropdownOption(systemDefaultTitle, SYSTEM_FONT_PATH))
    fonts.forEach { font ->
        if (font.path == SYSTEM_FONT_PATH ||
            font.path.isBlank() ||
            isRemovedBundledFontPath(font.path) ||
            font.name.equals(systemDefaultTitle, ignoreCase = true) ||
            font.name.equals(defaultTitle, ignoreCase = true)
        ) {
            return@forEach
        }
        result.add(FontDropdownOption(font.name, font.path))
    }
    val effectivePath = when {
        currentPath.isBlank() || isRemovedBundledFontPath(currentPath) -> SYSTEM_FONT_PATH
        else -> currentPath
    }
    if (result.none { it.path == effectivePath }) {
        val displayName = lyricFontChoiceLabel(effectivePath, currentName, systemDefaultTitle)
        if (result.none { it.name.equals(displayName, ignoreCase = true) && it.path == effectivePath }) {
            result.add(FontDropdownOption(displayName, effectivePath))
        }
    }
    return result
}

internal fun resolveFontDropdownSelectedIndex(
    options: List<FontDropdownOption>,
    currentPath: String,
    defaultTitle: String
): Int {
    if (currentPath.isBlank() || currentPath == SYSTEM_FONT_PATH || isRemovedBundledFontPath(currentPath)) {
        val systemIndex = options.indexOfFirst { it.path == SYSTEM_FONT_PATH || it.name.equals(defaultTitle, ignoreCase = true) }
        return if (systemIndex >= 0) systemIndex else 0
    }
    val exactMatch = options.indexOfFirst { it.path == currentPath }
    if (exactMatch >= 0) return exactMatch
    return 0
}

private enum class LyricFontTab(val titleRes: Int) {
    Global(R.string.settings_font_tab_global),
    Original(R.string.settings_font_tab_original),
    Translation(R.string.settings_font_tab_translation),
    Apply(R.string.settings_font_tab_apply)
}

private enum class LyricFontTarget {
    GlobalWestern,
    GlobalCjk,
    OriginalWestern,
    OriginalCjk,
    TranslationWestern,
    TranslationCjk
}

private suspend fun notifyDesktopLyricFontChanged(
    context: Context,
    settingsManager: SettingsManager
) {
    if (!settingsManager.desktopLyricEnabled.first()) return
    context.startService(
        Intent(context, DesktopLyricService::class.java)
            .setAction(DesktopLyricService.ACTION_APPLY_SETTINGS)
    )
}

private suspend fun clearImportedFontReferences(settingsManager: SettingsManager, path: String) {
    // Clear only the slot holding the deleted file. Empty slots deliberately fall back through
    // the migration/default chain, so a deleted imported font never leaves a broken Typeface.
    if (settingsManager.globalWesternFontPath.first() == path) {
        settingsManager.setGlobalFont("", "", settingsManager.globalCjkFontName.first(), settingsManager.globalCjkFontPath.first())
    }
    if (settingsManager.globalCjkFontPath.first() == path) {
        settingsManager.setGlobalFont(settingsManager.globalWesternFontName.first(), settingsManager.globalWesternFontPath.first(), "", "")
    }
    if (settingsManager.lyricOriginalWesternFontPath.first() == path) {
        settingsManager.setLyricOriginalFont("", "", settingsManager.lyricOriginalCjkFontName.first(), settingsManager.lyricOriginalCjkFontPath.first())
    }
    if (settingsManager.lyricOriginalCjkFontPath.first() == path) {
        settingsManager.setLyricOriginalFont(settingsManager.lyricOriginalWesternFontName.first(), settingsManager.lyricOriginalWesternFontPath.first(), "", "")
    }
    if (settingsManager.lyricTranslationWesternFontPath.first() == path) {
        settingsManager.setLyricTranslationFont("", "", settingsManager.lyricTranslationCjkFontName.first(), settingsManager.lyricTranslationCjkFontPath.first())
    }
    if (settingsManager.lyricTranslationCjkFontPath.first() == path) {
        settingsManager.setLyricTranslationFont(settingsManager.lyricTranslationWesternFontName.first(), settingsManager.lyricTranslationWesternFontPath.first(), "", "")
    }
}
