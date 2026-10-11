package com.ella.music.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Matches the close / centered title / save header of the home module ordering sheet. */
@Composable
fun SettingsEditSheet(
    show: Boolean,
    title: String,
    subtitle: String? = null,
    saveEnabled: Boolean = true,
    closeEnabled: Boolean = true,
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!show) return
    EllaMiuixBottomSheet(show = true, title = null, enableNestedScroll = false,
        onDismissRequest = { if (closeEnabled) onDismissRequest() }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (closeEnabled) onDismissRequest() }, enabled = closeEnabled) {
                    Icon(MiuixIcons.Regular.Close, stringResource(R.string.common_cancel),
                        Modifier.size(24.dp), tint = MiuixTheme.colorScheme.onSurface)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MiuixTheme.colorScheme.onSurface)
                    if (!subtitle.isNullOrBlank()) Text(subtitle, fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(onClick = onSave, enabled = saveEnabled) {
                    Icon(MiuixIcons.Regular.Ok, stringResource(R.string.common_save), Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.primary.copy(alpha = if (saveEnabled) 1f else .35f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
        }
    }
}
