package com.filedrop.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/*
 * Wire protocol (same as filedrop.py):
 *   int32 nameLength | name bytes | int64 fileSize | file bytes
 * The receiver answers with the two bytes "OK" once the file is saved.
 */
private const val CHUNK = 64 * 1024

object Transfer {

    fun describe(ctx: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "file"
        var size = -1L
        runCatching {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        }
        return name to size
    }

    suspend fun send(
        ctx: Context,
        uri: Uri,
        host: String,
        port: Int,
        onProgress: (Long, Long) -> Unit
    ) = withContext(Dispatchers.IO) {
        val (name, querySize) = describe(ctx, uri)
        var size = querySize
        if (size < 0) {
            size = runCatching {
                ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
            }.getOrNull() ?: -1L
        }
        if (size < 0) throw IOException("Could not read the file size")

        Socket().use { s ->
            s.connect(InetSocketAddress(host, port), 10_000)
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream(), CHUNK))
            val nameBytes = name.toByteArray()
            out.writeInt(nameBytes.size)
            out.write(nameBytes)
            out.writeLong(size)

            val input = ctx.contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open the file")
            input.use { inp ->
                val buf = ByteArray(CHUNK)
                var sent = 0L
                while (sent < size) {
                    val n = inp.read(buf, 0, minOf(buf.size.toLong(), size - sent).toInt())
                    if (n < 0) throw IOException("File ended early")
                    out.write(buf, 0, n)
                    sent += n
                    onProgress(sent, size)
                }
            }
            out.flush()

            s.soTimeout = 30_000
            val ack = ByteArray(2)
            DataInputStream(s.getInputStream()).readFully(ack)
            if (String(ack) != "OK") throw IOException("Receiver did not confirm")
        }
    }

    fun localIps(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
    }.getOrDefault(emptyList())
}

class Receiver(
    private val ctx: Context,
    private val port: Int,
    private val folder: () -> String,
    private val onProgress: (String, Long, Long) -> Unit,
    private val onEvent: (String) -> Unit,
    private val onStopped: () -> Unit
) {
    @Volatile
    private var server: ServerSocket? = null

    fun start() {
        thread(isDaemon = true, name = "filedrop-receiver") {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress(port))
                server = ss
                onEvent("Listening on port $port")
                while (true) {
                    val s = try {
                        ss.accept()
                    } catch (e: IOException) {
                        break
                    }
                    handle(s)
                }
            } catch (e: Exception) {
                onEvent("Error: ${e.message}")
            } finally {
                onStopped()
            }
        }
    }

    fun stop() {
        runCatching { server?.close() }
    }

    private fun handle(s: Socket) {
        val resolver = ctx.contentResolver
        var target: Uri? = null
        try {
            s.use {
                s.soTimeout = 30_000
                val inp = DataInputStream(BufferedInputStream(s.getInputStream(), CHUNK))
                val nameLen = inp.readInt()
                if (nameLen !in 1..1024) throw IOException("Invalid header")
                val nameBytes = ByteArray(nameLen)
                inp.readFully(nameBytes)
                val name = String(nameBytes)
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .ifBlank { "file" }
                val size = inp.readLong()
                if (size < 0) throw IOException("Invalid header")

                val ext = name.substringAfterLast('.', "").lowercase()
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + folder()
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: throw IOException("Could not create the file")
                target = uri

                onEvent("Receiving $name from ${s.inetAddress?.hostAddress}")
                val out = resolver.openOutputStream(uri) ?: throw IOException("Could not write the file")
                out.use { o ->
                    val buf = ByteArray(CHUNK)
                    var got = 0L
                    while (got < size) {
                        val n = inp.read(buf, 0, minOf(buf.size.toLong(), size - got).toInt())
                        if (n < 0) throw IOException("Connection closed early")
                        o.write(buf, 0, n)
                        got += n
                        onProgress(name, got, size)
                    }
                }
                val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                resolver.update(uri, done, null, null)

                val reply = s.getOutputStream()
                reply.write("OK".toByteArray())
                reply.flush()
                onEvent("Saved $name to Downloads/${folder()}")
            }
        } catch (e: Exception) {
            target?.let { runCatching { resolver.delete(it, null, null) } }
            onEvent("Failed: ${e.message ?: e.javaClass.simpleName}")
        }
    }
}
