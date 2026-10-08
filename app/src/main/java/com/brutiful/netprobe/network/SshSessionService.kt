package com.brutiful.netprobe.network

import android.app.*

import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.brutiful.netprobe.MainActivity
import com.brutiful.netprobe.model.SshConnectionConfig
import com.brutiful.netprobe.model.SshConnectionState
import com.brutiful.netprobe.model.TerminalLine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SshSessionService : Service() {

    private val repository by lazy { SshRepository(applicationContext) }
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var sessionJob: Job? = null

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines = _terminalLines.asStateFlow()

    private val binder = SshBinder()

    inner class SshBinder : Binder() {
        fun getService(): SshSessionService = this@SshSessionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    companion object {
        private const val CHANNEL_ID = "ssh_session_channel"
        private const val NOTIFICATION_ID = 2001
        const val ACTION_DISCONNECT = "com.brutiful.netprobe.ACTION_DISCONNECT"

        private const val MAX_TERMINAL_HISTORY = 500
        private const val FLUSH_TIMEOUT_MS = 100L
        private const val BUFFER_SIZE_THRESHOLD = 1024
    }

    private val outputBuffer = StringBuilder()
    private var flushJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnect()
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Session Service",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun showNotification(title: String, content: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SCREEN", "shell")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, SshSessionService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this, 1, disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(0, "Disconnect", disconnectPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun connect(config: SshConnectionConfig) {
        sessionJob?.cancel()
        _terminalLines.value = emptyList()
        
        showNotification("Connecting SSH", "Target: ${config.username}@${config.host}")
        
        sessionJob = serviceScope.launch {
            repository.startShellSession(
                config = config,
                onOutput = { text -> addRemoteOutput(text) },
                onError = { text -> addErrorMessage(text) },
                onStateChange = { state ->
                    _connectionState.value = state
                    handleStateChange(state)
                },
                onHostKeyVerification = { hostname, fingerprint, keyType, callback ->
                    // For now, auto-accept in background? 
                    // Actually, ViewModel should handle this via the flow.
                    // But Repository is blocking until callback.
                    // This is tricky. Let's see if we can pass it through.
                    _connectionState.value = SshConnectionState.HostKeyVerificationRequired(
                        hostname, fingerprint, keyType,
                        onAccept = { callback(true) },
                        onReject = { callback(false) }
                    )
                }
            )
        }
    }

    private fun handleStateChange(state: SshConnectionState) {
        when (state) {
            is SshConnectionState.Connected -> {
                showNotification("SSH Connected", "Active session: ${state.target}")
            }
            is SshConnectionState.Disconnected, is SshConnectionState.Error -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (state is SshConnectionState.Error) {
                    addErrorMessage("Session Error: ${state.message}")
                } else {
                    addSystemMessage("Disconnected.")
                }
            }
            else -> {}
        }
    }

    fun sendCommand(command: String) {
        serviceScope.launch {
            if (_connectionState.value is SshConnectionState.Connected) {
                addUserCommand(command)
                repository.writeToShell(command)
            }
        }
    }

    fun disconnect() {
        serviceScope.launch {
            repository.disconnect()
            sessionJob?.cancel()
            flushJob?.cancel()
            _connectionState.value = SshConnectionState.Disconnected
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    fun resizePTY(cols: Int, rows: Int) {
        serviceScope.launch {
            repository.updateWindowDimensions(cols, rows)
        }
    }

    private fun addSystemMessage(text: String) {
        updateTerminalLines(TerminalLine.SystemMessage(text))
    }

    private fun addUserCommand(text: String) {
        updateTerminalLines(TerminalLine.UserCommand(text))
    }

    private fun addRemoteOutput(text: String) {
        synchronized(outputBuffer) {
            outputBuffer.append(text)
            val shouldFlush = text.contains("\n") || outputBuffer.length > BUFFER_SIZE_THRESHOLD
            if (shouldFlush) {
                flushBuffer()
            } else {
                scheduleFlush()
            }
        }
    }

    private fun scheduleFlush() {
        flushJob?.cancel()
        flushJob = serviceScope.launch {
            delay(FLUSH_TIMEOUT_MS)
            synchronized(outputBuffer) {
                if (outputBuffer.isNotEmpty()) {
                    flushBuffer()
                }
            }
        }
    }

    private fun flushBuffer() {
        val textToEmit = synchronized(outputBuffer) {
            val content = outputBuffer.toString()
            outputBuffer.setLength(0)
            content
        }
        
        if (textToEmit.isEmpty()) return

        val lines = textToEmit.split("\n")
        val newTerminalLines = mutableListOf<TerminalLine>()
        
        lines.forEachIndexed { index, line ->
            // Add if line has content, OR if it's not the last element in the split (meaning it was followed by a \n)
            if (line.isNotEmpty() || index < lines.size - 1) {
                newTerminalLines.add(TerminalLine.RemoteOutput(line))
            }
        }

        updateTerminalLines(newTerminalLines)
    }

    private fun addErrorMessage(text: String) {
        updateTerminalLines(TerminalLine.ErrorMessage(text))
    }

    private fun updateTerminalLines(newLine: TerminalLine) {
        updateTerminalLines(listOf(newLine))
    }

    private fun updateTerminalLines(newLines: List<TerminalLine>) {
        val currentLines = _terminalLines.value
        val combined = currentLines + newLines
        _terminalLines.value = if (combined.size > MAX_TERMINAL_HISTORY) {
            combined.takeLast(MAX_TERMINAL_HISTORY)
        } else {
            combined
        }
    }

    fun clearOutput() {
        _terminalLines.value = emptyList()
    }

    override fun onDestroy() {
        disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }
}
