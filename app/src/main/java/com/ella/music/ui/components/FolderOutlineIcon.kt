package com.ella.music.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Folder

@Composable
fun FolderOutlineIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = MiuixIcons.Regular.Folder,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
