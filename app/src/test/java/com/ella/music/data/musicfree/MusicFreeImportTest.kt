package com.ella.music.data.musicfree

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import android.app.Application
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import okhttp3.Request
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import com.sun.net.httpserver.HttpServer
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ella.music.player.toMediaItemExtras
import java.io.IOException
import okhttp3.OkHttpClient

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(UnstableApi::class)
class MusicFreeImportTest {
    @Test fun acceptsAPlatformWithoutAnyBuiltInProviderMapping() {
        val (name, script) = MusicFreePluginService().importPluginScript(
            "module.exports = { platform: 'New Provider', async search(query,page,type) { return {data:[],isEnd:true}; } };",
            allowRuntimeInspect = false
        )
        assertEquals("New Provider", name)
        assertTrue(script.contains("search"))
    }
    @Test fun normalizesDefaultExports() {
        val (_, script) = MusicFreePluginService().importPluginScript(
            "export default { platform: 'Example', async search(query) { return {data:[]}; } };",
            allowRuntimeInspect = false
        )
        assertTrue(script.startsWith("module.exports = "))
    }
    @Test(expected = IllegalStateException::class)
    fun rejectsScriptsWithoutMusicCapabilities() {
        MusicFreePluginService().importPluginScript(
            "module.exports = { platform: 'Not a music plugin', version: '1.0.0', author: 'Example' };",
            allowRuntimeInspect = false
        )
    }

    @Test fun preservesMusicFreePageDataAndTheExplicitEndFlag() {
        val data = JSONArray((1..20).map { JSONObject().put("id", it) })
        val first = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", data).put("isEnd", false))
        assertEquals(20, first.data.length())
        assertEquals(false, first.isEnd)
        val last = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", data).put("isEnd", true))
        assertEquals(20, last.data.length())
        assertEquals(true, last.isEnd)
    }

    @Test fun pluginsWithoutAnEndFlagCanContinueUntilAnEmptyPage() {
        val page = MusicFreeRawSearchPage.fromJson(JSONObject().put("data", JSONArray("[{\"id\":1}]")))
        assertEquals(1, page.data.length())
        assertNull(page.isEnd)
        val empty = MusicFreeRawSearchPage.fromJson(JSONObject())
        assertEquals(0, empty.data.length())
        assertNull(empty.isEnd)
    }

    @Test fun aSearchPreviewUrlDoesNotBypassThePluginsMediaResolver() = runBlocking {
        var resolverCalls = 0
        val track = trackWithPreview()
        val source = source()
        val service = MusicFreePluginService(
            RuntimeEnvironment.getApplication(),
            { plugin, rawJson, _ ->
                resolverCalls++
                assertEquals(source, plugin)
                assertEquals(track.rawJson, rawJson)
                JSONObject().put("url", "https://audio.example.test/resolved.flac")
            },
            { _, _ -> JSONObject().put("rawLrc", "[00:01.00]Resolved lyrics") }
        )

        val song = service.resolvePlayableSong(track, source)

        assertEquals(1, resolverCalls)
        assertEquals("https://audio.example.test/resolved.flac", song.path)
        assertEquals("audio/flac", song.mimeType)
        assertEquals("[00:01.00]Resolved lyrics", song.onlineLyrics)
    }

    @Test fun explicitResolverFailureDoesNotFallBackToTheSearchPreview() = runBlocking {
        val service = MusicFreePluginService(
            RuntimeEnvironment.getApplication(),
            { _, _, _ -> throw IllegalStateException("Source unavailable") },
            { _, _ -> error("Failed source must not load lyrics") }
        )

        val failure = runCatching { service.resolvePlayableSong(trackWithPreview(), source()) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals("Source unavailable", failure?.message)
    }

    @Test fun emptyResolverResultDoesNotFallBackToTheSearchPreview() = runBlocking {
        val service = MusicFreePluginService(
            RuntimeEnvironment.getApplication(),
            { _, _, _ -> JSONObject() },
            { _, _ -> error("Empty source must not load lyrics") }
        )

        val failure = runCatching { service.resolvePlayableSong(trackWithPreview(), source()) }.exceptionOrNull()

        assertEquals("插件没有返回播放地址", failure?.message)
    }

    @Test fun hlsResolverResultKeepsAnAdaptiveMimeType() = runBlocking {
        val service = MusicFreePluginService(
            RuntimeEnvironment.getApplication(),
            { _, _, _ -> JSONObject().put("url", "https://audio.example.test/master.m3u8?token=fixture") },
            { _, _ -> JSONObject() }
        )
        assertEquals(MimeTypes.APPLICATION_M3U8, service.resolvePlayableSong(trackWithPreview(), source()).mimeType)
    }

    @Test fun sourceAndOptionalLyricCancellationArePropagated() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val cancelledSource = MusicFreePluginService(
            context,
            { _, _, _ -> throw CancellationException("Source cancelled") },
            { _, _ -> JSONObject() }
        )
        val sourceFailure = runCatching {
            cancelledSource.resolvePlayableSong(trackWithPreview(), source())
        }.exceptionOrNull()
        assertTrue(sourceFailure is CancellationException)

        val cancelledLyric = MusicFreePluginService(
            context,
            { _, _, _ -> JSONObject().put("url", "https://audio.example.test/resolved.mp3") },
            { _, _ -> throw CancellationException("Lyric cancelled") }
        )
        val lyricFailure = runCatching {
            cancelledLyric.resolvePlayableSong(trackWithPreview(), source())
        }.exceptionOrNull()
        assertTrue(lyricFailure is CancellationException)
    }

    @Test fun allFourSelectedQualityTiersStillReachThePlugin() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val settings = SettingsManager.getInstance(context)
        val qualities = mutableListOf<String>()
        val service = MusicFreePluginService(
            context,
            { _, _, quality ->
                qualities += quality
                JSONObject().put("url", "https://audio.example.test/resolved.mp3")
            },
            { _, _ -> JSONObject() }
        )
        try {
            for (tier in listOf("128k", "320k", "flac", "flac24bit")) {
                settings.setOnlinePlaybackQuality(tier)
                service.resolvePlayableSong(trackWithPreview(), source())
            }
            assertEquals(listOf("standard", "high", "super", "super"), qualities)
        } finally {
            settings.setOnlinePlaybackQuality("auto")
        }
    }

    @Test fun streamHeadersOnlyApplyToTheMarkedStreamAndPreservePlayerRange() {
        val holder = MusicFreeStreamHeaders()
        val original = "https://audio.example.test/resolved.mp3"
        val marked = holder.register(original, JSONObject()
            .put("headers", JSONObject().put("Referer", "https://source.example.test/")
                .put("Authorization", "Bearer fixture").put("Range", "bytes=0-1"))
            .put("userAgent", "Fixture Player"))
        val scoped = holder.scopeRequest(Request.Builder().url(marked).header("Range", "bytes=100-").build())
        val request = holder.requestForHop(scoped)

        assertEquals(original, request.url.toString())
        assertEquals("https://source.example.test/", request.header("Referer"))
        assertEquals("Bearer fixture", request.header("Authorization"))
        assertEquals("Fixture Player", request.header("User-Agent"))
        assertEquals("bytes=100-", request.header("Range"))
        val otherSource = holder.requestForHop(holder.scopeRequest(Request.Builder().url(original).build()))
        assertNull(otherSource.header("Authorization"))
        assertNull(otherSource.header("Referer"))
    }

    @Test fun redirectedPluginCredentialsStayOnTheOriginalOrigin() {
        val holder = MusicFreeStreamHeaders()
        val marked = holder.register("https://audio.example.test/track.mp3", JSONObject()
            .put("headers", JSONObject().put("Authorization", "Bearer fixture")
                .put("Cookie", "fixture=1").put("X-Source-Key", "fixture"))
            .put("userAgent", "Fixture Player"))
        val first = holder.requestForHop(holder.scopeRequest(Request.Builder().url(marked).build()))
        val same = holder.requestForHop(first.newBuilder().url("https://audio.example.test/next.mp3").build())
        assertEquals("Bearer fixture", same.header("Authorization"))

        for (url in listOf("https://other.example.test/next.mp3", "https://audio.example.test:8443/next.mp3")) {
            val redirected = holder.requestForHop(first.newBuilder().url(url).build())
            assertNull(redirected.header("Authorization"))
            assertNull(redirected.header("Cookie"))
            assertNull(redirected.header("X-Source-Key"))
            assertNull(redirected.header("User-Agent"))
        }
        val downgrade = runCatching {
            holder.requestForHop(first.newBuilder().url("http://audio.example.test/next.mp3").build())
        }.exceptionOrNull()
        assertTrue(downgrade is java.io.IOException)
    }

    @Test fun identicalAudioUrlsFromDifferentSourcesKeepIndependentHeaders() {
        val holder = MusicFreeStreamHeaders()
        val url = "https://audio.example.test/track.mp3"
        fun sourceRequest(key: String): Request {
            val marked = holder.register(url, JSONObject()
                .put("headers", JSONObject().put("X-Source-Key", key)))
            return holder.requestForHop(holder.scopeRequest(Request.Builder().url(marked).build()))
        }
        assertEquals("first", sourceRequest("first").header("X-Source-Key"))
        assertEquals("second", sourceRequest("second").header("X-Source-Key"))
        assertEquals(url, holder.register(url, JSONObject()))
    }

    @Test fun queuedStreamHeadersSurviveAHolderReloadWithoutEnteringTheUrl() {
        val file = File.createTempFile("musicfree-stream-", ".json")
        // AtomicFile starts with an absent target on Android. Windows' File.renameTo cannot
        // replace the empty file allocated by createTempFile, unlike the Android filesystem.
        assertTrue(file.delete())
        try {
            val marked = MusicFreeStreamHeaders(file).register(
                "https://audio.example.test/track.mp3",
                JSONObject().put("headers", JSONObject().put("Authorization", "Bearer fixture"))
            )
            assertTrue(file.length() > 0L)
            assertFalse(marked.contains("fixture"))
            val restored = MusicFreeStreamHeaders(file)
            val request = restored.requestForHop(restored.scopeRequest(Request.Builder().url(marked).build()))
            assertEquals("https://audio.example.test/track.mp3", request.url.toString())
            assertEquals("Bearer fixture", request.header("Authorization"))
        } finally {
            file.delete()
            File(file.path + ".bak").delete()
            File(file.path + ".new").delete()
        }
    }

    @Test fun scopedHlsFactoryRetainsIdentityAndSendsHeadersToIndependentManifestAndSegmentRequests() {
        val seen = CopyOnWriteArrayList<Pair<String, String?>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seen += exchange.requestURI.path to exchange.requestHeaders.getFirst("X-Source-Key")
            val bytes = "fixture".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val context = RuntimeEnvironment.getApplication()
            val base = "http://127.0.0.1:${server.address.port}"
            val holder = MusicFreeStreamHeaders()
            val marked = holder.register("$base/master.m3u8", JSONObject()
                .put("headers", JSONObject().put("X-Source-Key", "fixture")))
            val original = MediaItem.Builder().setMediaId("musicfree:42").setUri(marked)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Fixture title")
                    .setExtras(Bundle().apply { putString("songIdentity", "musicfree:42") }).build())
                .build()
            val factory = MusicFreeMediaSourceFactory(
                context, DefaultMediaSourceFactory(DefaultDataSource.Factory(context)), OkHttpClient(), holder
            )
            val source = factory.createMediaSource(original)
            assertEquals("HlsMediaSource", source.javaClass.simpleName)
            assertEquals(marked, source.mediaItem.localConfiguration?.uri.toString())
            assertEquals("musicfree:42", source.mediaItem.mediaId)
            assertEquals("Fixture title", source.mediaItem.mediaMetadata.title)
            assertEquals("musicfree:42", source.mediaItem.mediaMetadata.extras?.getString("songIdentity"))
            // Crossfade and queue restoration recreate sources using the player's logical item.
            val reusedSource = factory.createMediaSource(source.mediaItem)
            assertEquals(original, reusedSource.mediaItem)
            val scoped = requireNotNull(factory.scopedSource(reusedSource.mediaItem))
            for (uri in listOf(marked, "$base/audio/segment.ts")) {
                val dataSource = scoped.dataSourceFactory.createDataSource()
                try {
                    dataSource.open(DataSpec(Uri.parse(uri)))
                    assertEquals(if (uri == marked) "$base/master.m3u8" else uri, dataSource.uri.toString())
                    val output = ByteArrayOutputStream()
                    val bytes = ByteArray(16)
                    while (true) {
                        val count = dataSource.read(bytes, 0, bytes.size)
                        if (count < 0) break
                        output.write(bytes, 0, count)
                    }
                    assertEquals("fixture", output.toString("UTF-8"))
                } finally { dataSource.close() }
            }
            assertEquals(listOf("/master.m3u8" to "fixture", "/audio/segment.ts" to "fixture"), seen.toList())
            val normal = original.buildUpon().setUri("$base/plain.mp3").setMimeType("audio/mpeg").build()
            assertNull(factory.scopedSource(normal))
            assertEquals(normal, factory.createMediaSource(normal).mediaItem)
        } finally { server.stop(0) }
    }

    @Test fun publicPluginStreamsUseTheirOwnRedirectTransportAndKeepPrivateLibrariesOnTheFallback() {
        val context = RuntimeEnvironment.getApplication()
        val seenAuth = CopyOnWriteArrayList<String?>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/redirect") { exchange ->
            seenAuth += exchange.requestHeaders.getFirst("Authorization")
            exchange.responseHeaders.set("Location", "/audio")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/audio") { exchange ->
            seenAuth += exchange.requestHeaders.getFirst("Authorization")
            val bytes = "audio fixture".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/redirect"
            val factory = MusicFreeMediaSourceFactory(context,
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context)), OkHttpClient(), MusicFreeStreamHeaders())
            for (source in listOf("qs", "sd", "wy", "musicfree:Test", "netease", "webdav")) {
                val extras = trackWithPreview().song.copy(path = url, onlineSource = source).toMediaItemExtras()
                if (source == "netease") extras.putBoolean(EXTRA_PLUGIN_TRANSPORT, true)
                val item = MediaItem.Builder().setUri(url)
                    .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()
                val scoped = factory.scopedSource(item)
                if (source == "webdav") {
                    assertNull(scoped)
                    continue
                }
                assertEquals(item, requireNotNull(scoped).item)
                val dataSource = scoped.dataSourceFactory.createDataSource()
                try {
                    dataSource.open(DataSpec(Uri.parse(url)))
                    val result = ByteArrayOutputStream()
                    val buffer = ByteArray(32)
                    while (true) {
                        val count = dataSource.read(buffer, 0, buffer.size)
                        if (count < 0) break
                        result.write(buffer, 0, count)
                    }
                    assertEquals("audio fixture", result.toString("UTF-8"))
                    assertEquals("/audio", dataSource.uri?.path)
                } finally { dataSource.close() }
                val local = item.buildUpon().setUri("file:///fixture/song.m4a").build()
                assertNull(factory.scopedSource(local))
                val credentialed = item.buildUpon().setUri("https://user:pass@audio.example.test/song.m4a").build()
                assertTrue(runCatching { factory.scopedSource(credentialed) }.exceptionOrNull() is IOException)
            }
            assertEquals(List(10) { null }, seenAuth.toList())
            val official = MediaItem.Builder().setUri(url).setMediaMetadata(MediaMetadata.Builder()
                .setExtras(trackWithPreview().song.copy(onlineSource = "netease").toMediaItemExtras()).build()).build()
            assertNull(factory.scopedSource(official))
        } finally { server.stop(0) }
    }


    private fun source() = MusicFreePluginConfig(
        id = "test-source", url = "", name = "Test Source",
        script = "module.exports={platform:'Test Source',getMediaSource:async()=>({url:'https://audio.example.test/resolved.mp3'})}"
    )

    private fun trackWithPreview(): MusicFreeOnlineSong {
        val preview = "https://audio.example.test/preview.mp3"
        return MusicFreeOnlineSong(
            Song(
                id = 42L, title = "Test Song", artist = "Artist", album = "Album", albumId = 0L,
                duration = 180_000L, path = preview, fileName = "Test Song.mp3",
                onlineSource = "musicfree:Test Source", onlineId = "42"
            ),
            pluginName = "Test Source",
            rawJson = JSONObject().put("id", "42").put("url", preview)
                .put("qualities", JSONArray(listOf("low", "standard", "high", "super"))).toString()
        )
    }
}
