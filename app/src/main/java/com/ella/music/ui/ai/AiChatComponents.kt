package com.ella.music.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixSurfaceCard
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AiChatBubble(
    message: AiChatMessage,
    onPlaySongs: () -> Unit,
    onPlaySingleSong: (Song) -> Unit,
    onAddSongsToQueue: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val isUser = message.role == AiChatRole.User
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        EllaMiuixSurfaceCard(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.84f else 0.96f),
            color = if (isUser) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
            } else {
                MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
            }
        ) {
            AiMarkdownText(
                text = message.text,
                color = MiuixTheme.colorScheme.onSurface
            )
            if (message.songs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                message.songs.take(5).forEach { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${song.title} · ${song.artist}",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onPlaySingleSong(song) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Play,
                                contentDescription = "Play",
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Button(onClick = onPlaySongs) {
                        Text(
                            stringResource(R.string.ai_chat_play_songs, message.songs.size),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = onAddSongsToQueue) {
                        Text(
                            stringResource(R.string.ai_chat_add_to_queue),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(onClick = onCreatePlaylist) {
                        Text(
                            stringResource(R.string.ai_chat_create_playlist),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiMarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val lines = text.trim().lines().ifEmpty { listOf(text) }
        lines.forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                return@forEach
            }
            val trimmed = line.trimStart()
            val headingLevel = trimmed.takeWhile { it == '#' }.length.takeIf { it in 1..3 && trimmed.getOrNull(it) == ' ' }
            val bullet = trimmed.removePrefix("- ").takeIf { trimmed.startsWith("- ") }
                ?: trimmed.removePrefix("* ").takeIf { trimmed.startsWith("* ") }
            val numbered = Regex("""^(\d+)[.)]\s+(.+)$""").find(trimmed)

            when {
                headingLevel != null -> {
                    Text(
                        text = inlineAiMarkdown(trimmed.drop(headingLevel + 1), MiuixTheme.colorScheme.primary),
                        fontSize = when (headingLevel) {
                            1 -> 18.sp
                            2 -> 16.sp
                            else -> 15.sp
                        },
                        lineHeight = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                bullet != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("•", fontSize = 14.sp, color = color.copy(alpha = 0.78f))
                        Text(
                            text = inlineAiMarkdown(bullet, MiuixTheme.colorScheme.primary),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                numbered != null -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${numbered.groupValues[1]}.", fontSize = 14.sp, color = color.copy(alpha = 0.78f))
                        Text(
                            text = inlineAiMarkdown(numbered.groupValues[2], MiuixTheme.colorScheme.primary),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = color,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                else -> {
                    Text(
                        text = inlineAiMarkdown(trimmed, MiuixTheme.colorScheme.primary),
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = color,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun inlineAiMarkdown(text: String, accent: Color) = buildAnnotatedString {
    val pattern = Regex("""(\*\*[^*]+\*\*|`[^`]+`)""")
    var cursor = 0
    pattern.findAll(text).forEach { match ->
        append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(token.removeSurrounding("**"))
            }
            token.startsWith("`") -> withStyle(SpanStyle(color = accent, background = accent.copy(alpha = 0.10f))) {
                append(token.removeSurrounding("`"))
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    append(text.substring(cursor))
}
