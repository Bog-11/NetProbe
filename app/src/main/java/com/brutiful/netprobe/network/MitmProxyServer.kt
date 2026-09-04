package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.CapturedPacket
import com.brutiful.netprobe.model.DecryptionStatus
import com.brutiful.netprobe.model.PacketDirection
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.*
import kotlin.concurrent.thread
import java.util.concurrent.ConcurrentHashMap

class MitmProxyServer(
    private val certificateManager: CertificateManager
) {
    private var serverSocket: ServerSocket? = null
    var port: Int = 0
        private set

    private val sessionInfoMap = ConcurrentHashMap<Int, SessionInfo>()

    data class SessionInfo(
        val originalConnectionId: String,
        val srcIp: String,
        val srcPort: Int,
        val dstIp: String,
        val dstPort: Int
    )

    fun start() {
        thread(name = "MitmProxyServerStarter") {
            try {
                serverSocket = ServerSocket(0)
                port = serverSocket!!.localPort
                while (serverSocket?.isClosed == false) {
                    val clientSocket = serverSocket?.accept() ?: break
                    thread(name = "MitmHandler-${clientSocket.port}") {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                // Server stopped
            }
        }
    }

    fun stop() {
        serverSocket?.close()
        serverSocket = null
    }

    fun registerSession(proxyRemotePort: Int, info: SessionInfo) {
        sessionInfoMap[proxyRemotePort] = info
    }

    private fun handleClient(clientSocket: Socket) {
        val proxyRemotePort = clientSocket.port
        val sessionInfo = sessionInfoMap.remove(proxyRemotePort) ?: return
        
        var remoteSocket: SSLSocket? = null
        try {
            // 1. Generate leaf cert. Use hostname from tracker if available, else IP.
            val hostname = ConnectionTracker.getActiveConnection(sessionInfo.originalConnectionId)?.destinationHost 
                ?: sessionInfo.dstIp
            
            val (cert, key) = certificateManager.generateLeafCertificate(hostname)

            // 2. TLS Handshake with Client (the VPN service acting on behalf of the app)
            val sslContext = createSslContext(cert, key)
            val sslClientSocket = sslContext.socketFactory.createSocket(
                clientSocket,
                clientSocket.inetAddress.hostAddress,
                clientSocket.port,
                true
            ) as SSLSocket
            sslClientSocket.useClientMode = false
            sslClientSocket.startHandshake()

            // 3. TLS Handshake with Real Remote Server
            remoteSocket = SSLSocketFactory.getDefault().createSocket(hostname, sessionInfo.dstPort) as SSLSocket
            remoteSocket.startHandshake()

            // 4. Pipe and Log
            pipe(sslClientSocket, remoteSocket, sessionInfo)

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { clientSocket.close() } catch (e: Exception) {}
            try { remoteSocket?.close() } catch (e: Exception) {}
        }
    }

    private fun createSslContext(cert: X509Certificate, key: PrivateKey): SSLContext {
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("key", key, "password".toCharArray(), arrayOf(cert))

        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, "password".toCharArray())

        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, null)
        return sslContext
    }

    private fun pipe(client: SSLSocket, remote: SSLSocket, info: SessionInfo) {
        val t1 = thread(name = "MitmPipe-Up-${info.originalConnectionId}") {
            try {
                val input = client.inputStream
                val output = remote.outputStream
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        val data = buffer.copyOfRange(0, read)
                        output.write(data)
                        logDecrypted(info, data, PacketDirection.UPSTREAM)
                    }
                }
            } catch (e: Exception) {}
        }

        val t2 = thread(name = "MitmPipe-Down-${info.originalConnectionId}") {
            try {
                val input = remote.inputStream
                val output = client.outputStream
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    if (read > 0) {
                        val data = buffer.copyOfRange(0, read)
                        output.write(data)
                        logDecrypted(info, data, PacketDirection.DOWNSTREAM)
                    }
                }
            } catch (e: Exception) {}
        }
        
        t1.join()
        t2.join()
    }

    private fun logDecrypted(info: SessionInfo, data: ByteArray, direction: PacketDirection) {
        val packet = CapturedPacket(
            connectionId = info.originalConnectionId,
            direction = direction,
            protocol = "HTTPS",
            sourceIp = info.srcIp,
            sourcePort = info.srcPort,
            destinationIp = info.dstIp,
            destinationPort = info.dstPort,
            length = data.size,
            rawBytes = data,
            decryptedPayload = data,
            decryptionStatus = DecryptionStatus.DECRYPTED,
            summary = "${if (direction == PacketDirection.UPSTREAM) "→" else "←"} HTTPS ${data.size} bytes (Decrypted)"
        )
        PacketRepository.addPacket(packet)
    }
}
