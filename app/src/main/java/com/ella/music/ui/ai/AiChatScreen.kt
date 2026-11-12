package com.ella.music.ui.ai

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import java.util.UUID

// ── Composable ──

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AiChatScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val background = ellaPageBackground()

    // ── Multi-session state ──
    var sessionIndex by remember { mutableStateOf(loadSessionIndex(context)) }
    var currentSessionId by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf(emptyList<AiChatMessage>()) }
    var showDeleteDialog by remember { mutableStateOf<AiChatSessionMeta?>(null) }

    // Initialize: load or create first session
    LaunchedEffect(Unit) {
        if (sessionIndex.isEmpty()) {
            val id = UUID.randomUUID().toString()
            val meta = AiChatSessionMeta(id, defaultSessionTitle(context), System.currentTimeMillis())
            sessionIndex = listOf(meta)
            currentSessionId = id
            messages = emptyList()
            saveSessionIndex(context, sessionIndex)
        } else {
            val first = sessionIndex.first()
            currentSessionId = first.id
            messages = loadSessionMessages(context, first.id)
        }
    }

    // Save messages when they change
    LaunchedEffect(messages, currentSessionId) {
        currentSessionId?.let { id ->
            saveSessionMessages(context, id, messages)
            // Update session title from first user message
            val firstUserMsg = messages.firstOrNull { it.role == AiChatRole.User && !it.loading }
            if (firstUserMsg != null) {
                val newTitle = firstUserMsg.text.take(20).replace("\n", " ")
                val updatedIndex = sessionIndex.map {
                    if (it.id == id && it.title.isDefaultAiChatSessionTitle()) it.copy(title = newTitle) else it
                }
                if (updatedIndex != sessionIndex) {
                    sessionIndex = updatedIndex
                    saveSessionIndex(context, sessionIndex)
                }
            }
        }
    }

    fun switchSession(sessionId: String) {
        if (sessionId == currentSessionId) return
        currentSessionId = sessionId
        messages = loadSessionMessages(context, sessionId)
    }

    fun createNewSession() {
        val id = UUID.randomUUID().toString()
        val meta = AiChatSessionMeta(id, defaultSessionTitle(context), System.currentTimeMillis())
        sessionIndex = listOf(meta) + sessionIndex
        currentSessionId = id
        messages = emptyList()
        saveSessionIndex(context, sessionIndex)
    }

    fun deleteSession(meta: AiChatSessionMeta) {
        deleteSession(context, meta.id)
        sessionIndex = sessionIndex.filter { it.id != meta.id }
        saveSessionIndex(context, sessionIndex)
        if (currentSessionId == meta.id) {
            val next = sessionIndex.firstOrNull()
            currentSessionId = next?.id
            messages = next?.let { loadSessionMessages(context, it.id) } ?: emptyList()
        }
    }

    // ── Actions ──

    fun playSongs(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerViewModel.setPlaylist(songs, 0)
        onNavigateToPlayer()
    }

    fun playSingleSong(song: Song) {
        playerViewModel.setPlaylist(listOf(song), 0)
        onNavigateToPlayer()
    }

    fun addSongsToQueue(songs: List<Song>) {
        if (songs.isEmpty()) return
        playerViewModel.addToPlaylist(songs)
        Toast.makeText(context, context.getString(R.string.ai_chat_added_to_queue, songs.size), Toast.LENGTH_SHORT).show()
    }

    fun createPlaylistFromSongs(songs: List<Song>, playlistName: String = "") {
        if (songs.isEmpty()) return
        val name = playlistName.ifBlank { context.getString(R.string.ai_chat_playlist_name) }
        mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
            mainViewModel.addSongsToPlaylist(playlist.id, songs, appendToEnd = true)
            Toast.makeText(
                context,
                context.getString(R.string.ai_chat_playlist_created, playlist.name, songs.size),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isBlank() || sending) return
        input = ""
        val pending = AiChatMessage(role = AiChatRole.Assistant, text = context.getString(R.string.ai_chat_thinking), songs = emptyList(), loading = true)
        messages = messages + AiChatMessage(role = AiChatRole.User, text = text, songs = emptyList()) + pending
        sending = true
        scope.launch {
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
            val history = messages.dropLast(1).filter { !it.loading }.flatMap { msg ->
                val role = if (msg.role == AiChatRole.User) "user" else "assistant"
                listOf(role to msg.text)
            }
            runCatching { mainViewModel.chatWithOpenAiLibraryAssistant(text, conversationHistory = history) }
                .onSuccess { result ->
                    messages = messages.dropLast(1) + AiChatMessage(
                        role = AiChatRole.Assistant,
                        text = result.answer,
                        songs = result.songs,
                        playlistName = result.playlistName
                    )
                }
                .onFailure { error ->
                    messages = messages.dropLast(1) + AiChatMessage(
                        role = AiChatRole.Assistant,
                        text = context.getString(R.string.ai_chat_failed, error.message ?: context.getString(R.string.common_unknown)),
                        songs = emptyList()
                    )
                    Toast.makeText(context, error.message ?: context.getString(R.string.common_unknown), Toast.LENGTH_LONG).show()
                }
            sending = false
            listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0))
        }
    }

    // ── Delete confirmation dialog ──
    showDeleteDialog?.let { meta ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.ai_chat_delete_session),
            message = stringResource(R.string.ai_chat_delete_session_confirm),
            onDismiss = { showDeleteDialog = null },
            onConfirm = {
                deleteSession(meta)
                showDeleteDialog = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        AiChatTopBar(onBack = onBack)
        AiChatSessionStrip(
            sessions = sessionIndex,
            currentSessionId = currentSessionId,
            onSessionClick = ::switchSession,
            onSessionLongClick = { showDeleteDialog = it },
            onCreateSession = ::createNewSession,
        )

        // ── Messages + Input (Box layout to handle mini player overlay) ──
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 124.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages) { message ->
                    AiChatBubble(
                        message = message,
                        onPlaySongs = { playSongs(message.songs) },
                        onPlaySingleSong = { playSingleSong(it) },
                        onAddSongsToQueue = { addSongsToQueue(message.songs) },
                        onCreatePlaylist = { createPlaylistFromSongs(message.songs, message.playlistName) }
                    )
                }
            }

            if (messages.isEmpty()) {
                AiChatEmptyState(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 34.dp, vertical = 80.dp)
                )
            }

            // ── Input bar (fixed to the bottom; IME only moves this layer) ──
            AiChatInputBar(
                input = input,
                sending = sending,
                onInputChange = { input = it },
                onSend = ::send,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            )
        } // end Box
    }
}
