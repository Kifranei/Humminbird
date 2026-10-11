package com.ella.music.ui.category

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.metadataCategoryEditKey
import com.ella.music.ui.components.SettingsEditSheet
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MetadataCategoryBatchSheet(type: String, category: String?, mainViewModel: MainViewModel, onDismiss: () -> Unit) {
    val editor = mainViewModel.metadataBatchEditor
    val state by editor.state.collectAsState()
    val active = state?.takeIf { it.type == type }
    val target = active?.category ?: category ?: return
    var value by rememberSaveable(type, target) { mutableStateOf(target) }
    val total = remember(type, target) { mainViewModel.getSongsForMetadataCategory(type, target).size }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        editor.answerPermission(it.resultCode == Activity.RESULT_OK)
    }
    // This key survives progress emissions; the same permission request is never launched twice.
    LaunchedEffect(active?.permission) {
        editor.takePermissionLaunch()?.let { error ->
            runCatching { launcher.launch(IntentSenderRequest.Builder(error.intentSender).build()) }
                .onFailure { editor.answerPermission(false) }
        }
    }
    val running = active?.running == true
    val finished = active != null && !running
    val subtitle = when {
        finished -> stringResource(R.string.category_batch_result, active.succeeded, active.failed, active.skipped)
        running -> stringResource(R.string.category_batch_progress, active.processed, active.total)
        else -> stringResource(R.string.category_batch_description, total, metadataCategoryEditKey(type).orEmpty())
    }
    SettingsEditSheet(true, stringResource(R.string.category_batch_edit), subtitle,
        saveEnabled = !running && (finished || value.isNotBlank() && (type != "year" || value.matches(Regex("\\d{4}")))),
        closeEnabled = !running, onDismissRequest = { editor.dismiss(); onDismiss() }, onSave = {
            if (finished) { editor.dismiss(); onDismiss() }
            else editor.start(type, target, value.trim(), mainViewModel.getSongsForMetadataCategory(type, target))
        }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (active == null) {
                TextField(value, { value = it }, label = stringResource(R.string.category_batch_value),
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.category_batch_write_hint), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            } else Text(active.value, color = MiuixTheme.colorScheme.onSurface)
        }
    }
}
