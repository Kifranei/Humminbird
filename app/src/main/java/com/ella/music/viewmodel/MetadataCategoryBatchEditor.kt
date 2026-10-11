package com.ella.music.viewmodel

import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.metadataCategoryEditKey
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MetadataBatchEditState(
    val type: String,
    val category: String,
    val value: String,
    val total: Int,
    val processed: Int = 0,
    val succeeded: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0,
    val running: Boolean = true,
    val permission: WritePermissionRequiredException? = null
)

/** Lives with the ViewModel: rotating or recreating the sheet cannot repeat file writes. */
class MetadataCategoryBatchEditor(
    private val scope: CoroutineScope,
    private val write: suspend (Song, String, String) -> Result<Song>,
    private val publish: suspend (List<Song>) -> Unit
) {
    private val mutableState = MutableStateFlow<MetadataBatchEditState?>(null)
    val state = mutableState.asStateFlow()
    private var permissionReply: CompletableDeferred<Boolean>? = null
    private var permissionLaunched = false

    fun start(type: String, category: String, value: String, songs: List<Song>) {
        if (mutableState.value?.running == true) return
        val key = metadataCategoryEditKey(type) ?: return
        val targets = songs.distinctBy { it.playlistIdentityKey() }
        mutableState.value = MetadataBatchEditState(type, category, value, targets.size)
        scope.launch {
            val edited = mutableListOf<Song>()
            try {
                for (song in targets) {
                    val writable = song.path.startsWith("/") || song.path.startsWith("content://") || song.path.startsWith("file://")
                    if (!writable) {
                        update { it.copy(processed = it.processed + 1, skipped = it.skipped + 1) }
                        continue
                    }
                    var result = safelyWrite(song, key, value)
                    val permission = result.exceptionOrNull() as? WritePermissionRequiredException
                    if (permission != null) {
                        val reply = CompletableDeferred<Boolean>()
                        permissionReply = reply
                        permissionLaunched = false
                        update { it.copy(permission = permission) }
                        val allowed = reply.await()
                        permissionReply = null
                        update { it.copy(permission = null) }
                        if (allowed) result = safelyWrite(song, key, value)
                    }
                    result.getOrNull()?.let(edited::add)
                    update { it.copy(processed = it.processed + 1,
                        succeeded = it.succeeded + if (result.isSuccess) 1 else 0,
                        failed = it.failed + if (result.isFailure) 1 else 0) }
                }
            } finally {
                try { withContext(NonCancellable) { publish(edited) } }
                finally {
                    permissionReply = null
                    update { it.copy(running = false, permission = null) }
                }
            }
        }
    }

    fun answerPermission(allowed: Boolean) { permissionReply?.complete(allowed) }
    fun takePermissionLaunch(): WritePermissionRequiredException? {
        val permission = mutableState.value?.permission ?: return null
        if (permissionLaunched) return null
        permissionLaunched = true
        return permission
    }
    fun dismiss() { if (mutableState.value?.running != true) mutableState.value = null }
    private fun update(transform: (MetadataBatchEditState) -> MetadataBatchEditState) {
        mutableState.value?.let { mutableState.value = transform(it) }
    }
    private suspend fun safelyWrite(song: Song, key: String, value: String): Result<Song> = try {
        write(song, key, value)
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { Result.failure(error) }
}
