package com.ella.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixListItem

@Composable
internal fun WebDavBackupPickerDialog(
    backupFiles: List<WebDavItem>,
    onDismissRequest: () -> Unit,
    onFileSelected: (WebDavItem) -> Unit
) {
    EllaMiuixDialog(
        show = true,
        title = stringResource(R.string.settings_backup_webdav_download),
        onDismissRequest = onDismissRequest
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            backupFiles.forEach { item ->
                EllaMiuixListItem(
                    title = item.name,
                    summary = item.name.toBackupDisplayName(),
                    onClick = { onFileSelected(item) }
                )
            }
        }
    }
}
