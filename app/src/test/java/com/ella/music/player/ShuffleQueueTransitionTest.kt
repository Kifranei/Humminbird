package com.ella.music.player

import android.app.Application
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.ella.music.data.model.Song
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ShuffleQueueTransitionTest {
    @Test fun repeatCallbackAfterAutomaticAdvanceDoesNotReplaceTheQueue() = withQueue { player, manager, controller ->
        // Replays the diagnostic: AUTO -> artwork PLAYLIST_CHANGED -> spurious REPEAT.
        player.advanceTo(1)
        drainUntil { controller.currentMediaItemIndex == 1 }
        val nextId = controller.currentMediaItem!!.mediaId
        val nextSong = manager.currentSong.value
        val patched = controller.currentMediaItem!!.buildUpon().setMediaMetadata(
            controller.mediaMetadata.buildUpon().setArtworkData(byteArrayOf(1, 2), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                .setExtras(android.os.Bundle(controller.mediaMetadata.extras).markMetadataOnlyPatch(PATCH_REASON_NOTIFICATION_ARTWORK))
                .build()
        ).build()
        player.patchItem(1, patched)
        shadowOf(Looper.getMainLooper()).idle()
        field<Player.Listener>(manager, "playerListener").onMediaItemTransition(
            controller.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("A metadata callback must not start a new shuffle round", 0, player.queueReplacements)
        assertEquals(nextId, controller.currentMediaItem!!.mediaId)
        assertEquals(nextSong, manager.currentSong.value)
        assertEquals(1, manager.currentQueueIndex.value)
    }

    @Test fun actualLastToFirstAutomaticTransitionStartsExactlyOneNewShuffleRound() = withQueue { player, manager, controller ->
        player.advanceTo(2)
        drainUntil { controller.currentMediaItemIndex == 2 }
        setField(manager, "pendingShuffleReorder", true)
        player.advanceTo(0)
        drainUntil { player.queueReplacements == 1 }
        shadowOf(Looper.getMainLooper()).idle()
        // Repeated forwarding callbacks from the same boundary cannot reshuffle again.
        field<Player.Listener>(manager, "playerListener").onMediaItemTransition(
            controller.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
        )
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, player.queueReplacements)
        assertEquals(0, manager.currentQueueIndex.value)
        assertEquals(false, field<Boolean>(manager, "pendingShuffleReorder"))
    }

    @Test fun repeatOnlyBoundaryFallbackStillStartsANewRoundOnce() = withQueue { player, manager, controller ->
        player.advanceTo(2)
        drainUntil { controller.currentMediaItemIndex == 2 }
        val listener = field<Player.Listener>(manager, "playerListener")
        controller.removeListener(listener)
        player.advanceTo(0)
        drainUntil { controller.currentMediaItemIndex == 0 }
        controller.addListener(listener)
        // Some session forwarding chains omit the discontinuity and only report REPEAT.
        repeat(2) {
            listener.onMediaItemTransition(controller.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            shadowOf(Looper.getMainLooper()).idle()
        }
        assertEquals(1, player.queueReplacements)
        assertEquals(0, manager.currentQueueIndex.value)
    }

    @Test fun posterSwipesPreservePauseWhileExplicitPlaybackStillStarts() = withQueue { player, manager, controller ->
        manager.playQueueIndex(1, startPlayback = false)
        drainUntil { controller.currentMediaItemIndex == 1 && !controller.playWhenReady }
        assertEquals(false, manager.playWhenReady.value)
        val queue = field<MutableList<Song>>(manager, "playlist").toList()
        field<MutableStateFlow<Boolean>>(manager, "_shuffleEnabled").value = false
        manager.setPlaylist(queue, 2, startPlayback = false)
        drainUntil { controller.currentMediaItemIndex == 2 && !controller.playWhenReady }
        assertEquals(false, manager.playWhenReady.value)
        manager.playQueueIndex(0)
        drainUntil { controller.currentMediaItemIndex == 0 && controller.playWhenReady }
        assertEquals(true, manager.playWhenReady.value)
    }

    private fun withQueue(block: (QueuePlayer, ExoPlayerManager, MediaController) -> Unit) {
        val context = RuntimeEnvironment.getApplication()
        val songs = (0..2).map { i -> Song(8500L + i, "Song $i", "Artist", "Album", 1L, 180_000L,
            "/music/$i.flac", "$i.flac") }
        val items = songs.mapIndexed { i, song -> MediaItem.Builder().setMediaId("queue:${song.id}:$i")
            .setUri(song.path).setMediaMetadata(MediaMetadata.Builder().setTitle(song.title)
                .setExtras(song.toMediaItemExtras()).build()).build() }
        val player = QueuePlayer(items)
        val session = MediaSession.Builder(context, player).build()
        val future = MediaController.Builder(context, session.token).buildAsync()
        drainUntil { future.isDone }
        val controller = future.get()
        val manager = ExoPlayerManager(context)
        try {
            drainUntil { field<CompletableDeferred<Unit>>(manager, "initialRestore").isCompleted }
            setField(manager, "mediaController", controller)
            setField(manager, "playlist", songs.toMutableList())
            field<MutableStateFlow<Boolean>>(manager, "_shuffleEnabled").value = true
            ExoPlayerManager::class.java.getDeclaredMethod("setupListener").apply { isAccessible = true }.invoke(manager)
            block(player, manager, controller)
        } finally {
            manager.disconnect()
            field<CoroutineScope>(manager, "commandScope").cancel()
            field<CoroutineScope>(manager, "persistenceScope").cancel()
            controller.release()
            session.release()
            player.release()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun drainUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
        while (!condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            check(System.nanoTime() < deadline) { "Timed out waiting for MediaController" }
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(target: Any, name: String): T = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(target) as T
    private fun setField(target: Any, name: String, value: Any) = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.set(target, value)

    private class QueuePlayer(items: List<MediaItem>) : SimpleBasePlayer(Looper.getMainLooper()) {
        var queueReplacements = 0
        private var snapshot = State.Builder().setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(items.map(::data)).setCurrentMediaItemIndex(0).setContentPositionMs(0)
            .setPlaybackState(Player.STATE_READY).setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setRepeatMode(Player.REPEAT_MODE_ALL).build()
        override fun getState(): State = snapshot
        fun advanceTo(index: Int) {
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(index).setContentPositionMs(0)
                .setPositionDiscontinuity(Player.DISCONTINUITY_REASON_AUTO_TRANSITION, 0).build()
            invalidateState()
            snapshot = snapshot.buildUpon().clearPositionDiscontinuity().build()
        }
        fun patchItem(index: Int, item: MediaItem) {
            snapshot = snapshot.buildUpon().setPlaylist(snapshot.playlist.mapIndexed { i, old -> if (i == index) data(item) else old }).build()
            invalidateState()
        }
        override fun handleSetMediaItems(items: List<MediaItem>, startIndex: Int, startPositionMs: Long): ListenableFuture<*> {
            queueReplacements++
            snapshot = snapshot.buildUpon().setPlaylist(items.map(::data)).setCurrentMediaItemIndex(startIndex)
                .setContentPositionMs(startPositionMs).build()
            return Futures.immediateVoidFuture()
        }
        override fun handleSetShuffleModeEnabled(enabled: Boolean): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
            snapshot = snapshot.buildUpon().setPlayWhenReady(playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST).build()
            return Futures.immediateVoidFuture()
        }
        override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
            snapshot = snapshot.buildUpon().setCurrentMediaItemIndex(mediaItemIndex).setContentPositionMs(positionMs).build()
            return Futures.immediateVoidFuture()
        }
        override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
            snapshot = snapshot.buildUpon().setRepeatMode(repeatMode).build()
            return Futures.immediateVoidFuture()
        }
        override fun handlePrepare(): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
        private fun data(item: MediaItem): MediaItemData = MediaItemData.Builder(item.mediaId).setMediaItem(item)
            .setDurationUs(180_000_000).build()
    }
}
