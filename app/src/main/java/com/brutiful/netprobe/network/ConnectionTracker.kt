package com.brutiful.netprobe.network

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.util.Log
import com.brutiful.netprobe.model.ConnectionHistory
import com.brutiful.netprobe.model.ConnectionStatus
import com.brutiful.netprobe.model.LiveConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

object ConnectionTracker {
    private val _activeConnections = MutableStateFlow<Map<String, LiveConnection>>(emptyMap())
    val activeConnections = _activeConnections.asStateFlow().map { currentMap ->
        currentMap.values.toList().sortedWith(
            compareByDescending<LiveConnection> { it.status == ConnectionStatus.ACTIVE }
                .thenByDescending { it.sentBytes + it.receivedBytes }
                .thenByDescending { it.totalPackets }
                .thenByDescending { it.lastSeen }
        )
    }

    val trackedCount: Int get() = _activeConnections.value.size
    
    private val hostNameCache = ConcurrentHashMap<String, String>()
    private val pendingMetadataResolutions = ConcurrentHashMap<String, Job>()
    private var ownerResolver: ConnectionOwnerResolver? = null
    
    private var database: HistoryDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var appContext: Context? = null
    private var inactivityJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        database = HistoryDatabase.getDatabase(context)
        ownerResolver = ConnectionOwnerResolver(context)
        
        scope.launch {
            val saved = database?.liveConnectionDao()?.getAll() ?: emptyList()
            _activeConnections.update { current ->
                val newMap = current.toMutableMap()
                saved.forEach { conn ->
                    // When loading from DB, mark as INACTIVE initially
                    newMap[conn.id] = conn.copy(status = ConnectionStatus.INACTIVE)
                }
                newMap
            }
        }
        
        startInactivityChecker()
    }

    private fun startInactivityChecker() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            while (isActive) {
                delay(10000) // Check every 10 seconds
                checkInactivity()
            }
        }
    }

    private fun checkInactivity() {
        val now = System.currentTimeMillis()
        val timeout = 30000 // 30 seconds of no traffic = INACTIVE
        
        _activeConnections.update { current ->
            var changed = false
            val updatedMap = current.mapValues { (_, conn) ->
                if (conn.status == ConnectionStatus.ACTIVE && now - conn.lastSeen > timeout) {
                    changed = true
                    val inactiveConn = conn.copy(status = ConnectionStatus.INACTIVE)
                    persistLiveConnection(inactiveConn)
                    inactiveConn
                } else {
                    conn
                }
            }
            if (changed) updatedMap else current
        }
    }

    fun updateConnection(
        srcIp: String?,
        srcPort: Int,
        destIp: String,
        destPort: Int,
        protocol: String,
        bytes: Int,
        isSent: Boolean
    ) {
        val flowKey = "$srcIp:$srcPort:$destIp:$destPort:$protocol"
        val now = System.currentTimeMillis()

        _activeConnections.update { current ->
            val existing = current[flowKey]
            
            val live = if (existing == null) {
                val uid = getOwnerUid(protocol, srcIp, srcPort, destIp, destPort)
                val cachedMetadata = if (uid != -1) ownerResolver?.getCachedMetadata(uid) else null
                
                // Diagnostic log for first-seen
                Log.d("ConnectionTracker", "First seen: $flowKey, UID: $uid, Cached: ${cachedMetadata?.label ?: "no"}")

                val initialLabel = cachedMetadata?.label ?: if (uid != -1) "Resolving..." else "Unknown app"
                
                LiveConnection(
                    id = flowKey,
                    destinationIp = destIp,
                    destinationPort = destPort,
                    protocol = protocol,
                    uid = uid,
                    packageName = cachedMetadata?.packageName,
                    appLabel = initialLabel,
                    destinationHost = hostNameCache[destIp],
                    firstSeen = now,
                    lastSeen = now,
                    sentBytes = if (isSent) bytes.toLong() else 0,
                    receivedBytes = if (!isSent) bytes.toLong() else 0,
                    totalPackets = 1,
                    status = ConnectionStatus.ACTIVE
                ).also {
                    if (uid != -1 && cachedMetadata == null) {
                        resolveMetadataAsync(flowKey, uid)
                    }
                }
            } else {
                existing.copy(
                    lastSeen = now,
                    sentBytes = existing.sentBytes + (if (isSent) bytes.toLong() else 0),
                    receivedBytes = existing.receivedBytes + (if (!isSent) bytes.toLong() else 0),
                    totalPackets = existing.totalPackets + 1,
                    status = ConnectionStatus.ACTIVE,
                    destinationHost = hostNameCache[destIp] ?: existing.destinationHost
                )
            }

            saveToHistory(srcIp, srcPort, live, bytes, isSent)
            persistLiveConnection(live)
            current + (flowKey to live)
        }

        if (!hostNameCache.containsKey(destIp)) {
            resolveHostName(destIp)
        }
    }

    private fun getOwnerUid(protocol: String, srcIp: String?, srcPort: Int, destIp: String, destPort: Int): Int {
        if (srcIp == null) return -1
        return try {
            val protoNum = if (protocol == "TCP") 6 else 17
            val srcAddr = InetAddress.getByName(srcIp)
            val destAddr = InetAddress.getByName(destIp)
            ownerResolver?.getOwnerUid(protoNum, srcAddr, srcPort, destAddr, destPort) ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    private fun resolveMetadataAsync(flowKey: String, uid: Int) {
        if (pendingMetadataResolutions.containsKey(flowKey)) return

        val job = scope.launch {
            try {
                val metadata = withTimeoutOrNull(5000) {
                    ownerResolver?.resolveMetadata(uid)
                }

                _activeConnections.update { current ->
                    val existing = current[flowKey]
                    if (existing != null && metadata != null) {
                        val updated = existing.copy(
                            packageName = metadata.packageName,
                            appLabel = metadata.label
                        )
                        Log.d("ConnectionTracker", "Updated metadata for $flowKey: ${metadata.label}")
                        persistLiveConnection(updated)
                        current + (flowKey to updated)
                    } else if (existing != null && existing.appLabel == "Resolving...") {
                        // Cleanup Resolving... if failed/timeout
                        val fallback = existing.copy(appLabel = "UID $uid")
                        persistLiveConnection(fallback)
                        current + (flowKey to fallback)
                    } else {
                        current
                    }
                }
            } catch (e: Exception) {
                Log.e("ConnectionTracker", "Async metadata resolution failed", e)
            } finally {
                pendingMetadataResolutions.remove(flowKey)
            }
        }
        pendingMetadataResolutions[flowKey] = job
    }

    private fun saveToHistory(srcIp: String?, srcPort: Int, live: LiveConnection, bytes: Int, isSent: Boolean) {
        val db = database ?: return
        scope.launch {
            val history = ConnectionHistory(
                timestamp = System.currentTimeMillis(),
                sourceIp = srcIp,
                sourcePort = srcPort,
                destinationIp = live.destinationIp,
                destinationPort = live.destinationPort,
                protocol = live.protocol,
                uid = live.uid,
                packageName = live.packageName,
                appLabel = live.appLabel,
                destinationHost = live.destinationHost,
                sentBytes = if (isSent) bytes.toLong() else 0,
                receivedBytes = if (!isSent) bytes.toLong() else 0
            )
            db.historyDao().insert(history)
        }
    }

    private fun resolveHostName(ip: String) {
        scope.launch {
            try {
                val host = InetAddress.getByName(ip).hostName
                if (host != ip) {
                    hostNameCache[ip] = host
                    _activeConnections.update { current ->
                        current.mapValues { (key, conn) ->
                            if (conn.destinationIp == ip) {
                                val updated = conn.copy(destinationHost = host)
                                persistLiveConnection(updated)
                                updated
                            } else conn
                        }
                    }
                }
            } catch (_: Exception) {
                hostNameCache[ip] = "Unknown Host"
            }
        }
    }

    private fun persistLiveConnection(connection: LiveConnection) {
        val db = database ?: return
        scope.launch {
            db.liveConnectionDao().insert(connection)
        }
    }

    fun clearAll() {
        scope.launch {
            database?.liveConnectionDao()?.deleteAllConnections()
            database?.historyDao()?.deleteAllHistory()
            PacketRepository.deleteAllPackets()
            _activeConnections.value = emptyMap()
            ownerResolver?.clearCache()
            hostNameCache.clear()
        }
    }

    fun clearOldConnections() {
        // No-op or keep for API compatibility but disable pruning
        // In the new requirement, we don't clear them, we just mark them INACTIVE.
    }

    fun getAppIcon(packageName: String?): android.graphics.drawable.Drawable? {
        return ownerResolver?.getIcon(packageName)
    }
}
