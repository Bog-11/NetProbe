package com.brutiful.netprobe.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.brutiful.netprobe.MainActivity
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.util.NetProbeLog
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.DatagramChannel
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * A local-only VPN service that implements a Layer 4 (TCP/UDP) proxy.
 * It reads packets from the TUN interface, relays them to real network sockets,
 * and writes responses back to the TUN interface.
 */
class ConnectionMonitorService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private var selector: Selector? = null
    
    private val tcpSessions = ConcurrentHashMap<String, TcpSession>()
    private val udpSessions = ConcurrentHashMap<String, UdpSession>()
    
    private var tunOutput: FileOutputStream? = null

    private lateinit var certificateManager: CertificateManager
    private lateinit var mitmProxyServer: MitmProxyServer

    companion object {
        private const val CHANNEL_ID = "vpn_monitor_channel"
        private const val NOTIFICATION_ID = 1001
        private const val MTU = 1500
        private const val MAX_TCP_SESSIONS = 1024
        private const val MAX_UDP_SESSIONS = 1024
        private const val TCP_IDLE_TIMEOUT = 60_000L
        private const val UDP_IDLE_TIMEOUT = 30_000L

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        ConnectionTracker.init(this)
        createNotificationChannel()
        certificateManager = CertificateManager(this)
        mitmProxyServer = MitmProxyServer(certificateManager)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Network Monitor Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun updateForeground(connectionCount: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("SCREEN", "monitoring")
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ConnectionMonitorService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NetProbe Monitor Active")
            .setContentText("Monitoring $connectionCount active flows")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", stopPendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, 0)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopVpn()
            return START_NOT_STICKY
        }
        startVpn()
        return START_STICKY
    }

    private fun startVpn() {
        if (isRunning) return
        isRunning = true
        _isServiceRunning.value = true

        // Wipe previous session data before starting new one
        ConnectionTracker.clearAll()

        try {
            val builder = Builder()
                .setSession("NetProbe Monitor")
                .addAddress("10.0.0.1", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("8.8.8.8")
                .setMtu(MTU)
                .setBlocking(false)
            
            builder.addDisallowedApplication(packageName)
            
            // Explicitly exclude self to prevent loop if addDisallowedApplication fails
            // Also ensures we don't capture local traffic from this app
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: Exception) {}

            vpnInterface = builder.establish()
            vpnInterface?.let {
                tunOutput = FileOutputStream(it.fileDescriptor)
            }
            selector = Selector.open()
            
            NetProbeLog.d("VpnService", "Local L4 Proxy VPN established.")
            updateForeground(0)

            mitmProxyServer.start()

            thread(name = "VpnTunLoop") { runTunLoop() }
            thread(name = "VpnProxyLoop") { runProxyLoop() }
            thread(name = "MonitorCleanup") {
                while (isRunning) {
                    updateForeground(ConnectionTracker.trackedCount)
                    closeInactiveSessions()
                    Thread.sleep(5000)
                }
            }
            
        } catch (_: Exception) {
            NetProbeLog.e("VpnService", "Failed to start VPN")
            stopSelf()
        }
    }

    private fun closeInactiveSessions() {
        val now = System.currentTimeMillis()
        
        tcpSessions.forEach { (key, session) ->
            if ((now - session.lastActivity) > TCP_IDLE_TIMEOUT) {
                NetProbeLog.d("VpnService", "Closing idle TCP session: $key")
                closeTcpSession(session)
            }
        }
        
        udpSessions.forEach { (key, session) ->
            if ((now - session.lastActivity) > UDP_IDLE_TIMEOUT) {
                NetProbeLog.d("VpnService", "Closing idle UDP session: $key")
                closeUdpSession(session)
            }
        }
    }

    private fun closeTcpSession(session: TcpSession) {
        try {
            session.remoteChannel.close()
        } catch (_: Exception) {}
        tcpSessions.remove(session.key)
    }

    private fun closeUdpSession(session: UdpSession) {
        try {
            session.channel.close()
        } catch (_: Exception) {}
        udpSessions.remove(session.key)
    }

    private fun runTunLoop() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd).channel
        val tunBuffer = ByteBuffer.allocate(MTU)

        while (isRunning) {
            try {
                tunBuffer.clear()
                val length = input.read(tunBuffer)
                if (length > 0) {
                    tunBuffer.flip()
                    handlePacketFromTun(tunBuffer)
                } else {
                    Thread.sleep(10)
                }
            } catch (_: Exception) {
                if (isRunning) NetProbeLog.e("VpnService", "TUN Loop Error")
            }
        }
    }

    private fun recordPacket(
        srcIp: String, srcPort: Int,
        dstIp: String, dstPort: Int,
        protocol: String, fullPacket: ByteArray,
        isUpstream: Boolean
    ) {
        val flowKey = "$srcIp:$srcPort:$dstIp:$dstPort:$protocol"
        val packet = CapturedPacket(
            connectionId = flowKey,
            direction = if (isUpstream) PacketDirection.UPSTREAM else PacketDirection.DOWNSTREAM,
            protocol = protocol,
            sourceIp = srcIp,
            sourcePort = srcPort,
            destinationIp = dstIp,
            destinationPort = dstPort,
            length = fullPacket.size,
            rawBytes = fullPacket,
            summary = "${if (isUpstream) "→" else "←"} $protocol ${fullPacket.size} bytes"
        )
        PacketRepository.addPacket(packet)
    }

    private fun handlePacketFromTun(buffer: ByteBuffer) {
        val version = (buffer[0].toInt() shr 4) and 0x0F
        
        if (version == 6) {
            // Explicitly bypass IPv6 for now
            NetProbeLog.i("VpnService", "IPv6 packet detected - bypassing local proxy")
            return
        }
        
        if (version != 4) return
        
        val ipHeaderLen = (buffer[0].toInt() and 0x0F) * 4
        val totalIpLen = buffer.getShort(2).toInt() and 0xFFFF
        
        // Basic length validation
        if (buffer.remaining() < totalIpLen) return

        val protocol = buffer[9].toInt() and 0xFF
        val srcIp = buffer.getInt(12)
        val dstIp = buffer.getInt(16)
        
        val srcIpStr = ipToString(srcIp)
        val dstIpStr = ipToString(dstIp)

        when (protocol) {
            6 -> handleTcp(buffer, ipHeaderLen, srcIp, dstIp, srcIpStr, dstIpStr)
            17 -> handleUdp(buffer, ipHeaderLen, srcIp, dstIp, srcIpStr, dstIpStr)
            else -> {
                NetProbeLog.d("VpnService", "Bypassing unsupported IP protocol: $protocol")
            }
        }
    }

    private fun handleTcp(buffer: ByteBuffer, ipHeaderLen: Int, srcIp: Int, dstIp: Int, srcIpStr: String, dstIpStr: String) {
        val srcPort = buffer.getShort(ipHeaderLen).toInt() and 0xFFFF
        val dstPort = buffer.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
        val seq = buffer.getInt(ipHeaderLen + 4).toLong() and 0xFFFFFFFFL
        val ack = buffer.getInt(ipHeaderLen + 8).toLong() and 0xFFFFFFFFL
        val flags = buffer[ipHeaderLen + 13].toInt() and 0x3F
        val tcpHeaderLen = ((buffer[ipHeaderLen + 12].toInt() shr 4) and 0x0F) * 4
        
        val totalIpLen = buffer.getShort(2).toInt() and 0xFFFF
        val payloadLen = totalIpLen - ipHeaderLen - tcpHeaderLen
        val sessionKey = "$srcIpStr:$srcPort->$dstIpStr:$dstPort"
        
        var session = tcpSessions[sessionKey]

        if ((flags and 0x02) != 0) { // SYN
            if (session != null) {
                closeTcpSession(session)
            }
            
            if (tcpSessions.size >= MAX_TCP_SESSIONS) {
                NetProbeLog.w("VpnService", "Max TCP sessions reached ($MAX_TCP_SESSIONS). Dropping.")
                return
            }

            try {
                val mitmEnabled = getSharedPreferences("netprobe_prefs", Context.MODE_PRIVATE)
                    .getBoolean("mitm_enabled", false)

                val remoteChannel = SocketChannel.open()
                remoteChannel.configureBlocking(false)
                
                val isProtected = if (mitmEnabled && dstPort == 443) {
                    remoteChannel.socket().bind(null)
                    val localPort = remoteChannel.socket().localPort
                    mitmProxyServer.registerSession(localPort, MitmProxyServer.SessionInfo(
                        originalConnectionId = sessionKey,
                        srcIp = srcIpStr,
                        srcPort = srcPort,
                        dstIp = dstIpStr,
                        dstPort = dstPort
                    ))
                    remoteChannel.connect(InetSocketAddress("127.0.0.1", mitmProxyServer.port))
                    true // Proxy is local, no protect needed for 127.0.0.1 but we trust it
                } else {
                    val protected = protect(remoteChannel.socket())
                    if (protected) {
                        NetProbeLog.d("VpnService", "DIAGNOSTIC: TCP Session Initiated.")
                        NetProbeLog.d("VpnService", "Original Tuple: $srcIpStr:$srcPort -> $dstIpStr:$dstPort")
                        val localProxyPort = remoteChannel.socket().localPort
                        NetProbeLog.d("VpnService", "Protected Proxy: (Local):$localProxyPort -> $dstIpStr:$dstPort")
                        remoteChannel.connect(InetSocketAddress(dstIpStr, dstPort))
                    }
                    protected
                }
                
                if (!isProtected) {
                    NetProbeLog.e("VpnService", "Failed to protect TCP socket for $sessionKey. Loop prevention triggered.")
                    remoteChannel.close()
                    // Send RST back to app
                    val dummySession = TcpSession(sessionKey, srcIp, srcPort, dstIp, dstPort, remoteChannel)
                    dummySession.clientSeq = seq
                    sendTcpPacket(dummySession, 0x04, 0, seq + 1, null) // RST
                    return
                }
                
                session = TcpSession(sessionKey, srcIp, srcPort, dstIp, dstPort, remoteChannel)
                session.clientSeq = seq
                tcpSessions[sessionKey] = session
                
                remoteChannel.register(selector, SelectionKey.OP_CONNECT, session)
                
                // Reply SYN-ACK
                sendTcpPacket(session, 0x12, session.mySeq, seq + 1, null)
                session.mySeq++
                ConnectionTracker.updateConnection(srcIpStr, srcPort, dstIpStr, dstPort, "TCP", 0, true)
            } catch (e: Exception) {
                NetProbeLog.e("VpnService", "TCP Connect failed for $sessionKey: ${e.message}")
            }
            return
        }

        if (session == null) return

        session.clientSeq = seq
        session.clientAck = ack

        if (payloadLen > 0) {
            session.lastActivity = System.currentTimeMillis()
            val payload = ByteBuffer.allocate(payloadLen)
            buffer.position(ipHeaderLen + tcpHeaderLen)
            payload.put(buffer)
            payload.flip()
            
            try {
                if (session.remoteChannel.isConnected) {
                    session.remoteChannel.write(payload)
                    sendTcpPacket(session, 0x10, session.mySeq, seq + payloadLen, null)
                    ConnectionTracker.updateConnection(srcIpStr, srcPort, dstIpStr, dstPort, "TCP", payloadLen, true)
                    
                    // Record full IP packet for PCAP
                    val fullPacket = ByteArray(totalIpLen)
                    val originalPos = buffer.position()
                    buffer.position(0)
                    buffer.get(fullPacket)
                    buffer.position(originalPos)
                    recordPacket(srcIpStr, srcPort, dstIpStr, dstPort, "TCP", fullPacket, true)
                }
            } catch (_: Exception) {
                NetProbeLog.e("VpnService", "TCP Forward failed")
            }
        } else if ((flags and 0x01) != 0) { // FIN
            sendTcpPacket(session, 0x11, session.mySeq, seq + 1, null)
            try { session.remoteChannel.close() } catch (_: Exception) {}
            tcpSessions.remove(sessionKey)
        }
    }

    private fun handleUdp(buffer: ByteBuffer, ipHeaderLen: Int, srcIp: Int, dstIp: Int, srcIpStr: String, dstIpStr: String) {
        val srcPort = buffer.getShort(ipHeaderLen).toInt() and 0xFFFF
        val dstPort = buffer.getShort(ipHeaderLen + 2).toInt() and 0xFFFF
        val udpLen = buffer.getShort(ipHeaderLen + 4).toInt() and 0xFFFF
        val payloadLen = udpLen - 8
        
        if (payloadLen < 0) return

        val sessionKey = "$srcIpStr:$srcPort->$dstIpStr:$dstPort"
        var session = udpSessions[sessionKey]
        
        if (session == null) {
            if (udpSessions.size >= MAX_UDP_SESSIONS) {
                NetProbeLog.w("VpnService", "Max UDP sessions reached ($MAX_UDP_SESSIONS). Dropping.")
                return
            }
            try {
                val channel = DatagramChannel.open()
                channel.configureBlocking(false)
                val isProtected = protect(channel.socket())
                if (!isProtected) {
                    NetProbeLog.e("VpnService", "Failed to protect UDP socket for $sessionKey")
                    channel.close()
                    return
                }

                NetProbeLog.d("VpnService", "DIAGNOSTIC: UDP Flow Initiated.")
                NetProbeLog.d("VpnService", "Original Tuple: $srcIpStr:$srcPort -> $dstIpStr:$dstPort")
                val localProxyPort = channel.socket().localPort
                NetProbeLog.d("VpnService", "Protected Proxy: (Local):$localProxyPort -> $dstIpStr:$dstPort")

                channel.connect(InetSocketAddress(dstIpStr, dstPort))
                
                session = UdpSession(sessionKey, srcIp, srcPort, dstIp, dstPort, channel)
                udpSessions[sessionKey] = session
                channel.register(selector, SelectionKey.OP_READ, session)
            } catch (e: Exception) {
                NetProbeLog.e("VpnService", "UDP Session failed for $sessionKey: ${e.message}")
                return
            }
        }

        if (payloadLen > 0) {
            session.lastActivity = System.currentTimeMillis()
            val payload = ByteBuffer.allocate(payloadLen)
            buffer.position(ipHeaderLen + 8)
            payload.put(buffer)
            payload.flip()
            try {
                session.channel.write(payload)
                ConnectionTracker.updateConnection(srcIpStr, srcPort, dstIpStr, dstPort, "UDP", payloadLen, true)
                
                // Record full IP packet for PCAP
                val fullPacket = ByteArray(ipHeaderLen + 8 + payloadLen)
                val originalPos = buffer.position()
                buffer.position(0)
                buffer.get(fullPacket)
                buffer.position(originalPos)
                recordPacket(srcIpStr, srcPort, dstIpStr, dstPort, "UDP", fullPacket, true)
            } catch (_: Exception) {
                NetProbeLog.e("VpnService", "UDP Forward failed")
            }
        }
    }

    private fun runProxyLoop() {
        val buffer = ByteBuffer.allocate(MTU)
        while (isRunning) {
            try {
                if (selector?.select(50) == 0) continue
                val keys = selector?.selectedKeys()?.iterator() ?: continue
                while (keys.hasNext()) {
                    val key = keys.next()
                    keys.remove()
                    if (!key.isValid) continue

                    if (key.isConnectable) {
                        val session = key.attachment() as TcpSession
                        val channel = key.channel() as SocketChannel
                        try {
                            if (channel.finishConnect()) {
                                key.interestOps(SelectionKey.OP_READ)
                            }
                        } catch (_: Exception) {
                            key.cancel()
                            channel.close()
                            tcpSessions.remove(session.key)
                        }
                    } else if (key.isReadable) {
                        buffer.clear()
                        val channel = key.channel()
                        val read = try {
                            if (channel is SocketChannel) channel.read(buffer)
                            else (channel as DatagramChannel).read(buffer)
                        } catch (_: Exception) { -1 }

                        if (read > 0) {
                            val attachment = key.attachment()
                            if (attachment is TcpSession) attachment.lastActivity = System.currentTimeMillis()
                            else if (attachment is UdpSession) attachment.lastActivity = System.currentTimeMillis()

                            buffer.flip()
                            val data = ByteArray(read)
                            buffer.get(data)
                            
                            if (attachment is TcpSession) {
                                sendTcpPacket(attachment, 0x18, attachment.mySeq, attachment.clientSeq, data)
                                attachment.mySeq += read
                                val srcIpStr = ipToString(attachment.srcIp)
                                val dstIpStr = ipToString(attachment.dstIp)
                                ConnectionTracker.updateConnection(
                                    srcIpStr, attachment.srcPort,
                                    dstIpStr, attachment.dstPort, "TCP", read, false
                                )
                            } else if (attachment is UdpSession) {
                                sendUdpPacket(attachment, data)
                                val srcIpStr = ipToString(attachment.srcIp)
                                val dstIpStr = ipToString(attachment.dstIp)
                                ConnectionTracker.updateConnection(
                                    srcIpStr, attachment.srcPort,
                                    dstIpStr, attachment.dstPort, "UDP", read, false
                                )
                            }
                        } else if (read < 0) {
                            val attachment = key.attachment()
                            if (attachment is TcpSession) {
                                sendTcpPacket(attachment, 0x11, attachment.mySeq, attachment.clientSeq, null)
                                tcpSessions.remove(attachment.key)
                            } else if (attachment is UdpSession) {
                                udpSessions.remove(attachment.key)
                            }
                            key.cancel()
                            channel.close()
                        }
                    }
                }
            } catch (_: Exception) {
                if (isRunning) NetProbeLog.e("VpnService", "Proxy loop error")
            }
        }
    }

    private fun sendTcpPacket(session: TcpSession, flags: Int, seq: Long, ack: Long, payload: ByteArray?) {
        val payloadLen = payload?.size ?: 0
        val totalLen = 40 + payloadLen
        val buffer = ByteBuffer.allocate(totalLen)
        buffer.order(ByteOrder.BIG_ENDIAN)

        // IP Header
        buffer.put(0, 0x45.toByte())
        buffer.putShort(2, totalLen.toShort())
        buffer.put(9, 6.toByte())
        buffer.putInt(12, session.dstIp)
        buffer.putInt(16, session.srcIp)
        fillIpChecksum(buffer)

        // TCP Header
        buffer.putShort(20, session.dstPort.toShort())
        buffer.putShort(22, session.srcPort.toShort())
        buffer.putInt(24, seq.toInt())
        buffer.putInt(28, ack.toInt())
        buffer.put(32, 0x50.toByte())
        buffer.put(33, flags.toByte())
        buffer.putShort(34, 0xFFFF.toShort())

        if (payload != null) {
            buffer.position(40)
            buffer.put(payload)
        }

        val tcpChecksum = calculateTcpChecksum(buffer, session.dstIp, session.srcIp, 20 + payloadLen)
        buffer.putShort(36, tcpChecksum)

        val fullPacket = buffer.array().copyOf(totalLen)
        synchronized(this) {
            try {
                tunOutput?.write(fullPacket)
                // Record full downstream IP packet for PCAP
                recordPacket(ipToString(session.dstIp), session.dstPort, ipToString(session.srcIp), session.srcPort, "TCP", fullPacket, false)
            } catch (_: Exception) {
                NetProbeLog.e("VpnService", "Write to TUN failed")
            }
        }
    }

    private fun sendUdpPacket(session: UdpSession, payload: ByteArray) {
        val totalLen = 28 + payload.size
        val buffer = ByteBuffer.allocate(totalLen)
        buffer.order(ByteOrder.BIG_ENDIAN)
        
        buffer.put(0, 0x45.toByte())
        buffer.putShort(2, totalLen.toShort())
        buffer.put(9, 17.toByte())
        buffer.putInt(12, session.dstIp)
        buffer.putInt(16, session.srcIp)
        fillIpChecksum(buffer)
        
        buffer.putShort(20, session.dstPort.toShort())
        buffer.putShort(22, session.srcPort.toShort())
        buffer.putShort(24, (8 + payload.size).toShort())
        
        buffer.position(28)
        buffer.put(payload)
        
        val fullPacket = buffer.array().copyOf(totalLen)
        synchronized(this) {
            try {
                tunOutput?.write(fullPacket)
                // Record full downstream IP packet for PCAP
                recordPacket(ipToString(session.dstIp), session.dstPort, ipToString(session.srcIp), session.srcPort, "UDP", fullPacket, false)
            } catch (_: Exception) {
                NetProbeLog.e("VpnService", "Write to TUN failed")
            }
        }
    }

    private fun fillIpChecksum(buffer: ByteBuffer) {
        buffer.putShort(10, 0)
        var sum = 0L
        for (i in 0 until 20 step 2) {
            sum += buffer.getShort(i).toLong() and 0xFFFFL
        }
        while (sum shr 16 > 0) sum = (sum and 0xFFFFL) + (sum shr 16)
        buffer.putShort(10, (sum.inv() and 0xFFFFL).toShort())
    }

    private fun calculateTcpChecksum(buffer: ByteBuffer, srcIp: Int, dstIp: Int, tcpLen: Int): Short {
        var sum = 0L
        sum += (srcIp ushr 16) + (srcIp and 0xFFFF)
        sum += (dstIp ushr 16) + (dstIp and 0xFFFF)
        sum += 6L
        sum += tcpLen.toLong()
        
        for (i in 0 until tcpLen step 2) {
            sum += if (i + 1 < tcpLen) {
                buffer.getShort(20 + i).toLong() and 0xFFFFL
            } else {
                (buffer[20 + i].toLong() and 0xFFL) shl 8
            }
        }
        while (sum shr 16 > 0) sum = (sum and 0xFFFFL) + (sum shr 16)
        return (sum.inv() and 0xFFFFL).toShort()
    }

    private fun ipToString(ip: Int): String {
        return "${(ip ushr 24) and 0xFF}.${(ip ushr 16) and 0xFF}.${(ip ushr 8) and 0xFF}.${ip and 0xFF}"
    }

    private fun stopVpn() {
        isRunning = false
        _isServiceRunning.value = false
        mitmProxyServer.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        try {
            tcpSessions.values.forEach { try { it.remoteChannel.close() } catch(_: Exception) {} }
            udpSessions.values.forEach { try { it.channel.close() } catch(_: Exception) {} }
            
            vpnInterface?.close()
            selector?.close()
            tunOutput?.close()
            ConnectionTracker.flushAndShutdown()
        } catch (_: Exception) {}
        vpnInterface = null
        selector = null
        tunOutput = null
        tcpSessions.clear()
        udpSessions.clear()
        stopSelf()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private class TcpSession(val key: String, val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int, val remoteChannel: SocketChannel) {
        var mySeq = 1000L
        var clientSeq = 0L
        var clientAck = 0L
        var lastActivity = System.currentTimeMillis()
    }

    private class UdpSession(val key: String, val srcIp: Int, val srcPort: Int, val dstIp: Int, val dstPort: Int, val channel: DatagramChannel) {
        var lastActivity = System.currentTimeMillis()
    }
}
