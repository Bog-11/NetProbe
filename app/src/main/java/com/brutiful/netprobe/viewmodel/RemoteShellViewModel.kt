package com.brutiful.netprobe.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection

import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.network.SshSessionService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.milliseconds

class RemoteShellViewModel(application: Application) : AndroidViewModel(application) {

    private val _connectionState = MutableStateFlow<SshConnectionState>(SshConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _terminalLines = MutableStateFlow<List<TerminalLine>>(emptyList())
    val terminalLines = _terminalLines.asStateFlow()

    private val _isExecuting = MutableStateFlow(value = false)
    val isExecuting = _isExecuting.asStateFlow()

    private val _host = MutableStateFlow("")
    val host = _host.asStateFlow()

    private val _port = MutableStateFlow("22")
    val port = _port.asStateFlow()

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()

    private val _targetDevice = MutableStateFlow<DiscoveredDevice?>(null)
    val targetDevice = _targetDevice.asStateFlow()

    private var currentPassword = ""
    private var sshServiceRef: WeakReference<SshSessionService>? = null
    private val sshService: SshSessionService? get() = sshServiceRef?.get()
    private var isBound = false
    private var bindDeferred = CompletableDeferred<SshSessionService>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SshSessionService.SshBinder
            val svc = binder.getService()
            sshServiceRef = WeakReference(svc)
            isBound = true
            bindDeferred.complete(svc)
            
            // Sync state from service
            viewModelScope.launch {
                launch {
                    svc.connectionState.collect { state ->
                        _connectionState.value = state
                        _isExecuting.value = ((state is SshConnectionState.Connecting) || 
                                              (state is SshConnectionState.Authenticating) ||
                                              (state is SshConnectionState.OpeningShell))
                    }
                }
                launch {
                    svc.terminalLines.collect { lines ->
                        _terminalLines.value = lines
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sshServiceRef = null
            isBound = false
            // Reset deferred for next time if needed
            if (bindDeferred.isCompleted) {
                bindDeferred = CompletableDeferred()
            }
        }
    }

    init {
        bindService()
    }

    private fun bindService() {
        val context = getApplication<Application>().applicationContext
        val intent = Intent(context, SshSessionService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun updateHost(newHost: String) { _host.value = newHost }
    fun updatePort(newPort: String) { _port.value = newPort }
    fun updateUsername(newUsername: String) { _username.value = newUsername }
    fun updatePassword(password: String) { currentPassword = password }

    fun prefill(device: DiscoveredDevice) {
        _targetDevice.value = device
        _host.value = device.ipString
        _port.value = "22"
    }

    fun prefill(ip: String, port: Int) {
        _host.value = ip
        _port.value = port.toString()
        _targetDevice.value = null
    }

    fun connect() {
        val config = SshConnectionConfig(
            host = _host.value,
            port = _port.value.toIntOrNull() ?: 22,
            username = _username.value,
            authMethod = SshAuthMethod.Password(currentPassword),
        )

        viewModelScope.launch {
            val context = getApplication<Application>().applicationContext
            // Ensure service is started
            val intent = Intent(context, SshSessionService::class.java)
            context.startForegroundService(intent)
            
            // Await binding properly
            val service = if (isBound && sshService != null) {
                sshService
            } else {
                if (!isBound) bindService()
                withTimeoutOrNull(5000.milliseconds) { bindDeferred.await() }
            }
            
            service?.connect(config)
            currentPassword = ""
        }
    }

    fun onResize(cols: Int, rows: Int) {
        sshService?.resizePTY(cols, rows)
    }

    fun sendCommand(command: String) {
        sshService?.sendCommand(command)
    }

    fun disconnect() {
        sshService?.disconnect()
    }

    fun clearOutput() {
        sshService?.clearOutput()
    }

    override fun onCleared() {
        if (isBound) {
            val context = getApplication<Application>().applicationContext
            context.unbindService(serviceConnection)
            isBound = false
        }
        super.onCleared()
    }
}
