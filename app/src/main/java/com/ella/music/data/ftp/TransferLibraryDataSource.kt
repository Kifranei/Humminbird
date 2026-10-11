package com.ella.music.data.ftp

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.ella.music.data.SettingsManager
import com.ella.music.data.smb.SmbPasswordStore
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal fun String.isTransferLibraryUrl(): Boolean = startsWith("ftp://", true) || startsWith("ftps://", true) || startsWith("sftp://", true)
internal suspend fun transferAccount(context: Context, source: String): TransferAccount {
    val settings = SettingsManager.getInstance(context)
    val config = settings.transferLibraryConfig(source).first()
    val root = TransferLocation.parse(config.url)
    require((source == "sftp") == (root.scheme == "sftp"))
    return TransferAccount(root, config.username, SmbPasswordStore.getInstance(context, source).password, config.hostKey)
}

internal class TransferLibraryDataSource(private val context: Context,
    private val accountOverride: TransferAccount? = null) : BaseDataSource(true) {
    private var session: TransferSession? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var remaining = 0L
    private var opened = false
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            val location = TransferLocation.parse(dataSpec.uri.toString())
            val account = accountOverride ?: runBlocking(Dispatchers.IO) {
                transferAccount(context, if (location.scheme == "sftp") "sftp" else "ftp")
            }
            require(location.within(account.root)) { "Remote audio is outside the configured music library" }
            val connection = transferSession(account).also { session = it }
            val size = connection.size(location)
            if (dataSpec.position > size) throw IOException("Remote audio seek exceeds file length")
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) size - dataSpec.position else minOf(dataSpec.length, size - dataSpec.position)
            if (remaining > 0) input = com.ella.music.data.remote.bufferedRemoteInput(connection.read(location, dataSpec.position), remaining)
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) { close(); throw IOException("Unable to open remote audio: ${error.message.orEmpty()}", error) }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val read = requireNotNull(input).read(buffer, offset, minOf(length.toLong(), remaining).toInt())
        if (read < 0) throw IOException("Remote audio ended before the expected file length")
        if (read > 0) { remaining -= read; bytesTransferred(read) }
        return read
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        runCatching { input?.close() }; input = null
        session?.close(); session = null; uri = null
        if (opened) { opened = false; transferEnded() }
    }
}

internal class TransferLibraryDataSourceFactory(private val context: Context, private val fallback: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = object : DataSource {
        private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()
        private var active: DataSource? = null
        override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) { listeners += listener; active?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.toString().isTransferLibraryUrl()) TransferLibraryDataSource(context) else fallback.createDataSource()
            active = source
            listeners.forEach(source::addTransferListener)
            return source.open(dataSpec)
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int) = requireNotNull(active).read(buffer, offset, length)
        override fun getUri() = active?.uri
        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders.orEmpty()
        override fun close() { active?.close(); active = null }
    }
}
