package com.ella.music.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.Card
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.CustomMetadataCategory
import com.ella.music.data.SettingsManager
import com.ella.music.data.normalizeCustomCategories
import com.ella.music.ui.components.SettingsEditSheet
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CustomCategorySettings(settings: SettingsManager) {
    val saved by settings.customMetadataCategories.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var show by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(-1) }
    var draft by remember { mutableStateOf(emptyList<CustomMetadataCategory>()) }
    SettingsCardGroup {
        SettingsSearchAnchor(R.string.custom_categories_title) {
            ArrowPreference(title = stringResource(R.string.custom_categories_title),
                summary = saved.joinToString(" / ") { it.name }.ifBlank { stringResource(R.string.custom_categories_summary) },
                onClick = { draft = saved; show = true })
        }
    }
    SettingsEditSheet(show && !editing, stringResource(R.string.custom_categories_title),
        subtitle = stringResource(R.string.custom_categories_summary),
        onDismissRequest = { show = false }, onSave = {
            val result = draft
            scope.launch { settings.setCustomMetadataCategories(result) }
            show = false
        }) {
        Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            draft.forEachIndexed { position, category ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ArrowPreference(title = category.name, summary = category.tagKey, modifier = Modifier.weight(1f),
                            onClick = { index = position; editing = true })
                        IconButton(onClick = { draft = draft.filterIndexed { i, _ -> i != position } }) {
                            Icon(MiuixIcons.Regular.Delete, stringResource(R.string.common_delete), tint = MiuixTheme.colorScheme.error)
                        }
                    }
                }
            }
            ArrowPreference(title = stringResource(R.string.custom_category_add),
                summary = stringResource(R.string.custom_category_example), onClick = { index = -1; editing = true })
        }
    }
    if (editing) {
        val original = draft.getOrNull(index)
        var name by rememberSaveable(original) { mutableStateOf(original?.name.orEmpty()) }
        var tag by rememberSaveable(original) { mutableStateOf(original?.tagKey.orEmpty()) }
        val candidate = normalizeCustomCategories(listOf(CustomMetadataCategory(name, tag))).singleOrNull()
        val duplicate = candidate != null && draft.filterIndexed { i, _ -> i != index }.any { it.tagKey == candidate.tagKey }
        SettingsEditSheet(true, stringResource(R.string.custom_category_add),
            subtitle = stringResource(R.string.custom_category_example), saveEnabled = candidate != null && !duplicate,
            onDismissRequest = { editing = false }, onSave = {
                candidate?.let { item -> draft = draft.toMutableList().apply { if (index in indices) set(index, item) else add(item) } }
                editing = false
            }) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextField(name, { name = it }, label = stringResource(R.string.custom_category_name), singleLine = true, modifier = Modifier.fillMaxWidth())
                TextField(tag, { tag = it }, label = stringResource(R.string.custom_category_tag), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (duplicate) Text(stringResource(R.string.custom_category_duplicate), color = MiuixTheme.colorScheme.error)
            }
        }
    }
}
