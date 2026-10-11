package com.ella.music.data.smb

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import com.ella.music.data.SettingsManager

internal suspend fun smbAccount(context: Context): SmbAccount {
    val settings = SettingsManager.getInstance(context)
    return SmbAccount(SmbLocation.parse(settings.smbUrl.first()), settings.smbUsername.first(),
        SmbPasswordStore.getInstance(context).password, settings.smbDomain.first(), SmbProtocol.fromId(settings.smbProtocol.first()))
}

internal class SmbDataSource(private val context: Context) : BaseDataSource(true) {
    private var session: SmbSession? = null
    private var file: com.hierynomus.smbj.share.File? = null
    private var uri: Uri? = null
    private var position = 0L
    private var remaining = 0L
    private var opened = false
    private val readBuffer = SmbReadBuffer()
    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        try {
            readBuffer.clear()
            val account = runBlocking(Dispatchers.IO) { smbAccount(context) }
            val next = SmbSession(account).also { session = it }
            val handle = next.open(SmbLocation.parse(dataSpec.uri.toString())).also { file = it }
            val length = handle.fileInformation.standardInformation.endOfFile
            if (dataSpec.position > length) throw IOException("SMB seek exceeds file length")
            position = dataSpec.position
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) length - position else minOf(dataSpec.length, length - position)
            uri = dataSpec.uri
            opened = true
            transferStarted(dataSpec)
            return remaining
        } catch (error: Exception) { close(); throw IOException("Unable to open SMB audio", error) }
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val read = try {
            readBuffer.read(buffer, offset, length, position, remaining) { destination, at, count ->
                requireNotNull(file).read(destination, at, 0, count)
            }
        } catch (error: Exception) { throw IOException("Unable to read SMB audio", error) }
        if (read <= 0) return C.RESULT_END_OF_INPUT
        position += read; remaining -= read
        bytesTransferred(read)
        return read
    }
    override fun getUri(): Uri? = uri
    override fun close() {
        readBuffer.clear()
        runCatching { file?.close() }; file = null
        session?.close(); session = null; uri = null
        if (opened) { opened = false; transferEnded() }
    }
}

internal class SmbDataSourceFactory(private val context: Context, private val fallback: DataSource.Factory) : DataSource.Factory {
    override fun createDataSource(): DataSource = RoutingDataSource(context, fallback)
    private class RoutingDataSource(private val context: Context, private val fallback: DataSource.Factory) : DataSource {
        private val listeners = mutableListOf<androidx.media3.datasource.TransferListener>()
        private var active: DataSource? = null
        override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) { listeners += listener; active?.addTransferListener(listener) }
        override fun open(dataSpec: DataSpec): Long {
            val source = if (dataSpec.uri.scheme.equals("smb", true)) SmbDataSource(context) else fallback.createDataSource()
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
