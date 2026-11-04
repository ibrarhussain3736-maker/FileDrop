package com.filedrop.app

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    private val main = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) {
        main.post(block)
    }

    // ---- settings ----
    var port by mutableStateOf(settings.port); private set
    var folder by mutableStateOf(settings.folder); private set
    var theme by mutableStateOf(settings.theme); private set
    var autoStart by mutableStateOf(settings.autoStart); private set
    var keepAwake by mutableStateOf(settings.keepAwake); private set

    fun setPort(v: Int) {
        if (v == port) return
        port = v
        settings.port = v
        if (receiving) {
            stopReceiver()
            startReceiver()
        }
    }

    fun setFolder(raw: String) {
        val clean = raw.filter { it.isLetterOrDigit() || it in " _-." }
            .trim().trimStart('.').ifEmpty { "FileDrop" }
        folder = clean
        settings.folder = clean
    }

    fun setTheme(v: Int) {
        theme = v
        settings.theme = v
    }

    fun setAutoStart(v: Boolean) {
        autoStart = v
        settings.autoStart = v
    }

    fun setKeepAwake(v: Boolean) {
        keepAwake = v
        settings.keepAwake = v
    }

    // ---- send ----
    var fileUri by mutableStateOf<Uri?>(null); private set
    var fileName by mutableStateOf<String?>(null); private set
    var ip by mutableStateOf(settings.lastIp)
    var sending by mutableStateOf(false); private set
    var sendProgress by mutableStateOf(0f); private set
    var sendStatus by mutableStateOf(""); private set

    val canSend: Boolean get() = fileUri != null && ip.isNotBlank() && !sending

    fun pickFile(uri: Uri) {
        fileUri = uri
        fileName = Transfer.describe(getApplication(), uri).first
        sendProgress = 0f
        sendStatus = ""
    }

    fun send() {
        val uri = fileUri ?: return
        val target = ip.trim()
        val parts = target.split(":")
        val host = parts[0]
        val destPort = parts.getOrNull(1)?.toIntOrNull() ?: port
        settings.lastIp = target
        sending = true
        sendProgress = 0f
        sendStatus = "Connecting…"
        viewModelScope.launch {
            try {
                Transfer.send(getApplication(), uri, host, destPort) { done, total ->
                    ui {
                        if (sending) {
                            sendProgress = if (total > 0) done.toFloat() / total else 1f
                            sendStatus = "Sending…"
                        }
                    }
                }
                sendProgress = 1f
                sendStatus = "Sent successfully"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sendStatus = "Failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                sending = false
            }
        }
    }

    // ---- receive ----
    var receiving by mutableStateOf(false); private set
    var recvName by mutableStateOf(""); private set
    var recvProgress by mutableStateOf(0f); private set
    var ips by mutableStateOf<List<String>>(emptyList()); private set
    val log = mutableStateListOf<String>()
    private var receiver: Receiver? = null

    init {
        refreshIps()
        if (settings.autoStart) startReceiver()
    }

    fun refreshIps() {
        ips = Transfer.localIps()
    }

    private fun addLog(msg: String) {
        log.add(0, msg)
        while (log.size > 50) log.removeAt(log.size - 1)
    }

    fun startReceiver() {
        if (receiving) return
        refreshIps()
        lateinit var r: Receiver
        r = Receiver(
            ctx = getApplication(),
            port = port,
            folder = { folder },
            onProgress = { name, done, total ->
                ui {
                    recvName = name
                    recvProgress = if (total > 0) done.toFloat() / total else 1f
                }
            },
            onEvent = { msg -> ui { addLog(msg) } },
            onStopped = {
                ui {
                    if (receiver === r) {
                        receiver = null
                        receiving = false
                    }
                }
            }
        )
        receiver = r
        receiving = true
        recvName = ""
        recvProgress = 0f
        r.start()
    }

    fun stopReceiver() {
        receiver?.stop()
        receiver = null
        receiving = false
        addLog("Receiver stopped")
    }

    override fun onCleared() {
        receiver?.stop()
    }
}
