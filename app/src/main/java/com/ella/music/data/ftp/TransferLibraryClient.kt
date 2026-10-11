package com.ella.music.data.ftp

import com.ella.music.data.model.Song
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.Locale
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.json.JSONObject

internal data class TransferLocation(val scheme: String, val host: String, val port: Int, val segments: List<String>) {
    val path: String get() = "/" + segments.joinToString("/")
    fun url(): String = URI(scheme, null, host, if (port == defaultPort(scheme)) -1 else port, path, null, null).toASCIIString()
    fun child(name: String): TransferLocation {
        require(validSegment(name)) { "Invalid remote file name" }
        return copy(segments = segments + name)
    }
    fun within(root: TransferLocation): Boolean = scheme == root.scheme && host.equals(root.host, true) &&
        port == root.port && segments.take(root.segments.size) == root.segments && segments.size >= root.segments.size
    companion object {
        fun defaultPort(scheme: String) = if (scheme == "sftp") 22 else 21
        private fun validSegment(name: String) = name.isNotEmpty() && name !in listOf(".", "..") &&
            name.none { it == '/' || it == '\\' || it.code < 32 || it.code == 127 }
        fun parse(value: String): TransferLocation {
            val uri = URI(value.trim())
            val scheme = uri.scheme.orEmpty().lowercase(Locale.ROOT)
            require(scheme in setOf("ftp", "ftps", "sftp") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
                uri.rawQuery == null && uri.rawFragment == null) { "Use ftp://, ftps:// or sftp:// without credentials in the address" }
            val segments = uri.rawPath.orEmpty().split('/').filter(String::isNotEmpty)
                .map { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
            require(segments.all(::validSegment)) { "Invalid remote path" }
            val port = if (uri.port == -1) defaultPort(scheme) else uri.port
            require(port in 1..65535) { "Invalid remote port" }
            return TransferLocation(scheme, uri.host.lowercase(Locale.ROOT), port, segments)
        }
    }
}

internal data class TransferLibraryConfig(val url: String = "", val username: String = "", val hostKey: String = "") {
    fun json() = JSONObject().put("url", url).put("username", username).put("hostKey", hostKey).toString()
    companion object {
        fun parse(value: String): TransferLibraryConfig = runCatching {
            val json = JSONObject(value)
            TransferLibraryConfig(json.optString("url"), json.optString("username"), json.optString("hostKey"))
        }.getOrDefault(TransferLibraryConfig())
    }
}

internal data class TransferAccount(val root: TransferLocation, val username: String, val password: String, val hostKey: String = "")
internal data class TransferEntry(val location: TransferLocation, val directory: Boolean, val size: Long, val modified: Long) {
    val name get() = location.segments.lastOrNull().orEmpty()
    fun toSong(): Song {
        val path = location.url()
        val title = name.substringBeforeLast('.').ifBlank { name }
        val artist = title.substringBefore(" - ", "")
        val id = -(ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE).coerceAtLeast(1L)
        return Song(id = id, title = title.substringAfter(" - "),
            artist = artist.ifBlank { "<unknown>" }, album = location.segments.dropLast(1).lastOrNull().orEmpty(),
            duration = 0, path = path, albumId = 0, fileName = name, fileSize = size, dateModified = modified,
            onlineSource = if (location.scheme == "sftp") "sftp" else "ftp")
    }
}

internal interface TransferSession : Closeable {
    fun list(location: TransferLocation): List<TransferEntry>
    fun size(location: TransferLocation): Long
    fun read(location: TransferLocation, position: Long = 0): InputStream
}
internal fun transferSession(account: TransferAccount): TransferSession =
    if (account.root.scheme == "sftp") SftpSession(account) else FtpSession(account)

/** Read-only binary transfers in passive mode. Each opened data stream owns its control session. */
private class FtpSession(private val account: TransferAccount) : TransferSession {
    private var client: FTPClient? = null
    private fun connected(): FTPClient {
        client?.takeIf { it.isConnected }?.let { return it }
        val ftp = if (account.root.scheme == "ftps") FTPSClient(false).apply {
            isEndpointCheckingEnabled = true
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(null as KeyStore?) }
            trustManager = factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        } else FTPClient()
        try {
            ftp.connectTimeout = 15_000
            ftp.defaultTimeout = 15_000
            ftp.setDataTimeout(Duration.ofSeconds(20))
            ftp.controlEncoding = "UTF-8"
            ftp.setAutodetectUTF8(true)
            ftp.connect(account.root.host, account.root.port)
            if (!FTPReply.isPositiveCompletion(ftp.replyCode)) throw IOException("FTP connection rejected (${ftp.replyCode})")
            val username = account.username.ifEmpty { "anonymous" }
            require((username + account.password).none { it == '\r' || it == '\n' || it == '\u0000' })
            if (!ftp.login(username, account.password.ifEmpty { if (username == "anonymous") "anonymous@" else "" }))
                throw IOException("FTP authentication rejected (${ftp.replyCode})")
            if (ftp is FTPSClient) { ftp.execPBSZ(0); ftp.execPROT("P") }
            ftp.enterLocalPassiveMode()
            check(ftp.setFileType(FTP.BINARY_FILE_TYPE)) { "FTP binary transfer rejected" }
            return ftp.also { client = it }
        } catch (error: Exception) {
            runCatching { ftp.disconnect() }
            throw IOException("FTP connection failed: ${error.message.orEmpty()}", error)
        }
    }
    override fun list(location: TransferLocation): List<TransferEntry> {
        require(location.within(account.root))
        val ftp = connected()
        val files = ftp.mlistDir(location.path) ?: ftp.listFiles(location.path)
        if (!FTPReply.isPositiveCompletion(ftp.replyCode)) throw IOException("FTP directory unavailable (${ftp.replyCode})")
        return files.filter { (it.isDirectory || it.isFile) && it.name !in listOf(".", "..") }.map {
            TransferEntry(location.child(it.name), it.isDirectory, it.size, (it.timestamp?.timeInMillis ?: 0) / 1000)
        }
    }
    override fun size(location: TransferLocation): Long {
        require(location.within(account.root))
        val ftp = connected()
        val file = ftp.mlistFile(location.path) ?: ftp.listFiles(location.path).singleOrNull()
        if (file == null || !file.isFile) throw IOException("FTP audio file unavailable")
        return file.size
    }
    override fun read(location: TransferLocation, position: Long): InputStream {
        require(location.within(account.root) && position >= 0)
        val ftp = connected()
        ftp.restartOffset = position
        val input = ftp.retrieveFileStream(location.path) ?: throw IOException("FTP transfer rejected (${ftp.replyCode})")
        return object : FilterInputStream(input) {
            override fun close() {
                try { super.close() } finally { this@FtpSession.close() }
            }
        }
    }
    override fun close() { runCatching { client?.disconnect() }; client = null }
}

internal fun sftpHostFingerprint(key: ByteArray): String = "SHA256:" +
    Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(key))
internal class SftpHostKeyException(val host: String, val fingerprint: String, val changed: Boolean) :
    IOException("SFTP ${if (changed) "host key changed" else "host key requires confirmation"}: $host $fingerprint")

private class SftpSession(private val account: TransferAccount) : TransferSession {
    private var session: com.jcraft.jsch.Session? = null
    private var channel: ChannelSftp? = null
    private fun connected(): ChannelSftp {
        channel?.takeIf { it.isConnected }?.let { return it }
        val jsch = JSch()
        var observed = ""
        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String, key: ByteArray): Int {
                observed = sftpHostFingerprint(key)
                return if (observed == account.hostKey) HostKeyRepository.OK else if (account.hostKey.isBlank())
                    HostKeyRepository.NOT_INCLUDED else HostKeyRepository.CHANGED
            }
            override fun add(key: HostKey, ui: UserInfo?) = Unit
            override fun remove(host: String, type: String) = Unit
            override fun remove(host: String, type: String, key: ByteArray) = Unit
            override fun getKnownHostsRepositoryID(): String = "file-library-fingerprint"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        try {
            require(account.username.isNotBlank()) { "SFTP username is required" }
            val ssh = jsch.getSession(account.username, account.root.host, account.root.port).also { session = it }
            ssh.setPassword(account.password)
            ssh.setConfig("StrictHostKeyChecking", "yes")
            ssh.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            ssh.timeout = 20_000
            ssh.connect(15_000)
            return (ssh.openChannel("sftp") as ChannelSftp).also { channel = it; it.connect(15_000) }
        } catch (error: Exception) {
            close()
            if (observed.isNotBlank() && observed != account.hostKey)
                throw SftpHostKeyException(account.root.host, observed, account.hostKey.isNotBlank())
            throw IOException("SFTP connection failed: ${error.message.orEmpty()}", error)
        }
    }
    override fun list(location: TransferLocation): List<TransferEntry> {
        require(location.within(account.root))
        return connected().ls(location.path).map { it as ChannelSftp.LsEntry }.filter {
            it.filename !in listOf(".", "..") && !it.attrs.isLink && (it.attrs.isDir || it.attrs.isReg)
        }.map { TransferEntry(location.child(it.filename), it.attrs.isDir, it.attrs.size, it.attrs.mTime.toLong() and 0xffffffffL) }
    }
    override fun size(location: TransferLocation): Long {
        require(location.within(account.root))
        val stat = connected().lstat(location.path)
        if (!stat.isReg || stat.isLink) throw IOException("SFTP audio file unavailable")
        return stat.size
    }
    override fun read(location: TransferLocation, position: Long): InputStream {
        require(location.within(account.root) && position >= 0)
        size(location)
        return connected().get(location.path, null, position)
    }
    override fun close() { runCatching { channel?.disconnect() }; channel = null; runCatching { session?.disconnect() }; session = null }
}
