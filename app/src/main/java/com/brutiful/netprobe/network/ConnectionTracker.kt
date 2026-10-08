package com.brutiful.netprobe.network

import android.content.Context
import com.brutiful.netprobe.model.AppTrafficStats
import com.brutiful.netprobe.model.ConnectionHistory
import com.brutiful.netprobe.model.ConnectionStatus
import com.brutiful.netprobe.model.LiveConnection
import com.brutiful.netprobe.util.NetProbeLog
import com.brutiful.netprobe.util.NetworkUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel

object ConnectionTracker {
    private val connectionMap = ConcurrentHashMap<String, LiveConnection>()
    private val structuralUpdates = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    
    private val ticker = flow {
        while (true) {
            delay(1000)
            emit(Unit)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val activeConnections: StateFlow<List<LiveConnection>> = 
        merge(structuralUpdates, ticker)
            .onStart { emit(Unit) }
            .map {
                connectionMap.values.toList().sortedWith(
                    compareByDescending<LiveConnection> { it.status == ConnectionStatus.ACTIVE }
                        .thenByDescending { it.sentBytes + it.receivedBytes }
                        .thenByDescending { it.totalPackets }
                        .thenByDescending { it.lastSeen }
                )
            }
            .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val appTrafficStats: StateFlow<List<AppTrafficStats>> =
        activeConnections.map { connections ->
            connections.groupBy { it.uid }.map { (uid, conns) ->
                val first = conns.first()
                AppTrafficStats(
                    packageName = first.packageName,
                    packageNames = first.packageNames,
                    appLabel = first.appLabel,
                    uid = uid,
                    activeConnections = conns.count { it.status == ConnectionStatus.ACTIVE },
                    totalConnections = conns.size,
                    sentBytes = conns.sumOf { it.sentBytes },
                    receivedBytes = conns.sumOf { it.receivedBytes },
                    lastSeen = conns.maxOf { it.lastSeen },
                    resolutionReason = first.resolutionReason
                )
            }.sortedWith(
                compareByDescending<AppTrafficStats> { it.activeConnections > 0 }
                    .thenByDescending { it.activeConnections }
                    .thenByDescending { it.lastSeen }
            )
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val trackedCount: Int get() = connectionMap.size
    
    private val hostNameCache = ConcurrentHashMap<String, String>()
    private val pendingMetadataResolutions = ConcurrentHashMap<String, Job>()
    private var ownerResolver: ConnectionOwnerResolver? = null
    
    private var database: HistoryDatabase? = null
    private var appContext: Context? = null
    private var inactivityJob: Job? = null

    // DB Batching
    private val persistenceQueue = Channel<PersistenceAction>(10000, BufferOverflow.DROP_OLDEST)

    sealed class PersistenceAction {
        data class UpdateLive(val connection: LiveConnection) : PersistenceAction()
        data class InsertHistory(val history: ConnectionHistory) : PersistenceAction()
        object Flush : PersistenceAction()
    }

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        database = HistoryDatabase.getDatabase(app)
        ownerResolver = ConnectionOwnerResolver(app)
        
        scope.launch {
            val saved = database?.liveConnectionDao()?.getAll() ?: emptyList()
            saved.forEach { conn ->
                connectionMap[conn.id] = conn.copy(status = ConnectionStatus.INACTIVE)
            }
            structuralUpdates.emit(Unit)
        }
        
        startInactivityChecker()
        startPersistenceWorker()
    }

    private fun startPersistenceWorker() {
        scope.launch {
            val liveUpdates = mutableMapOf<String, LiveConnection>()
            val historyInserts = mutableListOf<ConnectionHistory>()

            suspend fun flush() {
                val db = database ?: return
                if (liveUpdates.isNotEmpty()) {
                    val list = liveUpdates.values.toList()
                    db.liveConnectionDao().insertAll(list)
                    liveUpdates.clear()
                }
                if (historyInserts.isNotEmpty()) {
                    val list = historyInserts.toList()
                    db.historyDao().insertAll(list)
                    historyInserts.clear()
                }
            }

            while (isActive) {
                try {
                    val action = withTimeoutOrNull(3000) { persistenceQueue.receive() }
                    if (action == null || action is PersistenceAction.Flush) {
                        flush()
                        continue
                    }

                    when (action) {
                        is PersistenceAction.UpdateLive -> liveUpdates[action.connection.id] = action.connection
                        is PersistenceAction.InsertHistory -> historyInserts.add(action.history)
                        else -> {}
                    }

                    if (liveUpdates.size > 100 || historyInserts.size > 100) {
                        flush()
                    }
                } catch (e: Exception) {
                    NetProbeLog.e("ConnectionTracker", "Persistence worker error")
                }
            }
        }
    }

    private fun startInactivityChecker() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            while (isActive) {
                delay(10000)
                checkInactivity()
            }
        }
    }

    private fun checkInactivity() {
        val now = System.currentTimeMillis()
        val timeout = 30000 
        
        var changed = false
        connectionMap.forEach { (key, conn) ->
            if (conn.status == ConnectionStatus.ACTIVE && now - conn.lastSeen > timeout) {
                val inactive = conn.copy(status = ConnectionStatus.INACTIVE)
                connectionMap[key] = inactive
                persistenceQueue.trySend(PersistenceAction.UpdateLive(inactive))
                changed = true
            }
        }
        if (changed) structuralUpdates.tryEmit(Unit)
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

        var isNew = false
        val live = connectionMap.compute(flowKey) { _, existing ->
            if (existing == null) {
                isNew = true
                
                LiveConnection(
                    id = flowKey,
                    destinationIp = destIp,
                    destinationPort = destPort,
                    protocol = protocol,
                    uid = -1,
                    packageName = null,
                    appLabel = "Resolving...",
                    destinationHost = hostNameCache[destIp],
                    firstSeen = now,
                    lastSeen = now,
                    sentBytes = if (isSent) bytes.toLong() else 0,
                    receivedBytes = if (!isSent) bytes.toLong() else 0,
                    totalPackets = 1,
                    status = ConnectionStatus.ACTIVE
                ).also {
                    // Start async resolution.
                    resolveMetadataAsync(flowKey, protocol, srcIp, srcPort, destIp, destPort)
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
        }!!

        if (isNew) {
            structuralUpdates.tryEmit(Unit)
        }

        // Add to history and update live connection
        val history = ConnectionHistory(
            timestamp = now,
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
        
        persistenceQueue.trySend(PersistenceAction.UpdateLive(live))
        persistenceQueue.trySend(PersistenceAction.InsertHistory(history))

        if (!hostNameCache.containsKey(destIp)) {
            resolveHostName(destIp)
        }
    }

    private fun resolveMetadataAsync(
        flowKey: String,
        protocol: String,
        srcIp: String?,
        srcPort: Int,
        destIp: String,
        destPort: Int
    ) {
        if (pendingMetadataResolutions.containsKey(flowKey)) return

        val job = scope.launch {
            try {
                if (srcIp == null) return@launch
                val protoNum = if (protocol == "TCP") 6 else 17
                val srcAddr = InetAddress.getByName(srcIp)
                val destAddr = InetAddress.getByName(destIp)
                
                val uid = ownerResolver?.getOwnerUidWithRetry(protoNum, srcAddr, srcPort, destAddr, destPort) ?: -1

                if (uid != -1) {
                    val metadata = withTimeoutOrNull(5000) {
                        ownerResolver?.resolveMetadata(uid)
                    }

                    if (metadata != null) {
                        connectionMap.computeIfPresent(flowKey) { _, existing ->
                            existing.copy(
                                uid = uid,
                                packageName = metadata.packageNames.firstOrNull(),
                                packageNames = metadata.packageNames,
                                appLabel = metadata.label,
                                resolutionReason = metadata.resolutionReason
                            ).also { updated ->
                                persistenceQueue.trySend(PersistenceAction.UpdateLive(updated))
                            }
                        }
                        structuralUpdates.emit(Unit)
                    }
                } else {
                    connectionMap.computeIfPresent(flowKey) { _, existing ->
                        existing.copy(
                            appLabel = "Unknown app",
                            resolutionReason = "OWNER_UID_UNAVAILABLE"
                        ).also { updated ->
                            persistenceQueue.trySend(PersistenceAction.UpdateLive(updated))
                        }
                    }
                    structuralUpdates.emit(Unit)
                }
            } catch (e: Exception) {
                NetProbeLog.e("ConnectionTracker", "Async metadata resolution failed: ${e.message}")
            } finally {
                pendingMetadataResolutions.remove(flowKey)
            }
        }
        pendingMetadataResolutions[flowKey] = job
    }

    private fun resolveHostName(ip: String) {
        scope.launch {
            try {
                val rawHost = InetAddress.getByName(ip).hostName
                val host = if (rawHost != ip) NetworkUtils.sanitizeHostName(rawHost) ?: rawHost else null
                if (host != null && host != ip) {
                    hostNameCache[ip] = host
                    var changed = false
                    connectionMap.forEach { (key, conn) ->
                        if (conn.destinationIp == ip) {
                            val updated = conn.copy(destinationHost = host)
                            connectionMap[key] = updated
                            persistenceQueue.trySend(PersistenceAction.UpdateLive(updated))
                            changed = true
                        }
                    }
                    if (changed) structuralUpdates.emit(Unit)
                }
            } catch (_: Exception) {
                hostNameCache[ip] = "Unknown Host"
            }
        }
    }

    fun clearAll() {
        scope.launch {
            persistenceQueue.send(PersistenceAction.Flush)
            database?.liveConnectionDao()?.deleteAllConnections()
            database?.historyDao()?.deleteAllHistory()
            PacketRepository.deleteAllPackets()
            connectionMap.clear()
            ownerResolver?.clearCache()
            hostNameCache.clear()
            structuralUpdates.emit(Unit)
        }
    }

    fun flushAndShutdown() {
        persistenceQueue.trySend(PersistenceAction.Flush)
        // Give it a moment
    }

    fun getAppIcon(packageName: String?): android.graphics.drawable.Drawable? {
        return ownerResolver?.getIcon(packageName)
    }

    fun getActiveConnection(key: String): LiveConnection? = connectionMap[key]
}
