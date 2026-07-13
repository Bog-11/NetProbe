package com.example.netprobe.probe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

class HostProbeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(HostProbeUiState())
    val uiState: StateFlow<HostProbeUiState> = _uiState.asStateFlow()

    private data class ServiceInfo(val name: String, val description: String)

    private val portInfoMap = mapOf(
        21 to ServiceInfo("FTP", "File Transfer Protocol - Used for transferring files between a client and a server."),
        22 to ServiceInfo("SSH", "Secure Shell - Used for secure remote login and other secure network services."),
        23 to ServiceInfo("Telnet", "Telnet - An old, unencrypted protocol for remote login."),
        25 to ServiceInfo("SMTP", "Simple Mail Transfer Protocol - Used for sending email messages."),
        53 to ServiceInfo("DNS", "Domain Name System - Translates domain names to IP addresses."),
        80 to ServiceInfo("HTTP", "Hypertext Transfer Protocol - The foundation of data communication for the World Wide Web."),
        110 to ServiceInfo("POP3", "Post Office Protocol v3 - Used by email clients to retrieve messages from a mail server."),
        143 to ServiceInfo("IMAP", "Internet Message Access Protocol - Used by email clients to retrieve and manage messages on a mail server."),
        443 to ServiceInfo("HTTPS", "HTTP Secure - An extension of HTTP for secure communication over a computer network."),
        445 to ServiceInfo("SMB", "Server Message Block - Used for providing shared access to files, printers, and serial ports."),
        554 to ServiceInfo("RTSP", "Real Time Streaming Protocol - Designed for use in entertainment and communications systems to control streaming media servers."),
        3306 to ServiceInfo("MySQL", "MySQL Database - Default port for the MySQL open-source database system."),
        3389 to ServiceInfo("RDP", "Remote Desktop Protocol - Developed by Microsoft, provides a user with a graphical interface to connect to another computer over a network."),
        5432 to ServiceInfo("PostgreSQL", "PostgreSQL Database - Default port for the PostgreSQL object-relational database system."),
        8008 to ServiceInfo("HTTP Alt", "HTTP Alternate - Often used as an alternative port for web servers or management interfaces."),
        8080 to ServiceInfo("HTTP Proxy", "HTTP Proxy - Frequently used for proxy servers and web application testing."),
        8443 to ServiceInfo("HTTPS Alt", "HTTPS Alternate - Often used as an alternative port for secure web management interfaces.")
    )

    fun updateIpAddress(value: String) {
        _uiState.update { it.copy(ipAddress = value, errorMessage = null) }
    }

    fun toggleWhois() {
        _uiState.update { it.copy(isWhoisExpanded = !it.isWhoisExpanded) }
    }

    fun toggleTraceroute() {
        val nextExpanded = !_uiState.value.isTracerouteExpanded
        _uiState.update { it.copy(isTracerouteExpanded = nextExpanded) }
        
        if (nextExpanded && _uiState.value.tracerouteResult == null && _uiState.value.lastProbedHost != null) {
            performTraceroute(_uiState.value.lastProbedHost!!)
        }
    }

    private fun performTraceroute(ip: String) {
        if (_uiState.value.isTracerouteLoading) return

        _uiState.update { it.copy(isTracerouteLoading = true, tracerouteResult = "Initializing route...") }

        viewModelScope.launch(Dispatchers.IO) {
            val result = StringBuilder()
            try {
                for (ttl in 1..20) {
                    val process = Runtime.getRuntime().exec("ping -c 1 -t $ttl -W 1 $ip")
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var output = ""
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output += line + "\n"
                    }
                    
                    val hopIp = if (output.contains("from ")) {
                        output.substringAfter("from ").substringBefore(" ").substringBefore(":")
                    } else if (output.contains("From ")) {
                        output.substringAfter("From ").substringBefore(" ").substringBefore(":")
                    } else {
                        "*"
                    }

                    result.append("$ttl. $hopIp\n")
                    
                    _uiState.update { 
                        it.copy(tracerouteResult = result.toString())
                    }

                    if (hopIp.contains(ip) || output.contains("1 packets transmitted, 1 received")) {
                        break
                    }
                }
            } catch (e: Exception) {
                result.append("Error: ${e.message}")
            } finally {
                _uiState.update { 
                    it.copy(
                        isTracerouteLoading = false,
                        tracerouteResult = result.toString()
                    )
                }
            }
        }
    }

    fun probeHost() {
        val host = _uiState.value.ipAddress.trim()
        
        // Security: Sanitize input to prevent command injection
        val isSanitized = host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        if (!isSanitized || host.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter a valid IP or hostname") }
            return
        }

        _uiState.update {
            it.copy(
                isProbing = true,
                progress = 0f,
                reachablePorts = emptyList(),
                errorMessage = null,
                lastProbedHost = host,
                pingMs = null,
                whoisData = "Fetching WHOIS info...",
                tracerouteResult = null,
                isTracerouteExpanded = false
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ping = runPing(host)
            _uiState.update { it.copy(pingMs = ping) }

            launch {
                val whois = fetchWhois(host)
                _uiState.update { it.copy(whoisData = whois) }
            }

            val portsToScan = (1..1024).toList() + portInfoMap.keys.filter { it > 1024 }
            val results = mutableListOf<PortProbeResult>()
            val chunkSize = 50

            portsToScan.chunked(chunkSize).forEachIndexed { index, chunk ->
                val chunkResults = chunk.map { port ->
                    async {
                        val status = tryConnect(host, port, 250)
                        val info = portInfoMap[port]
                        PortProbeResult(port, status, info?.name, info?.description)
                    }
                }.awaitAll()

                results.addAll(chunkResults.filter { it.status == PortStatus.OPEN })
                
                _uiState.update {
                    it.copy(
                        reachablePorts = results.toList().sortedBy { r -> r.port },
                        progress = (index + 1).toFloat() / (portsToScan.size / chunkSize + 1)
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isProbing = false,
                    progress = 1f
                )
            }
        }
    }

    private fun tryConnect(host: String, port: Int, timeoutMs: Int): PortStatus {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                PortStatus.OPEN
            }
        } catch (e: SocketTimeoutException) {
            PortStatus.TIMEOUT
        } catch (e: Exception) {
            PortStatus.CLOSED
        }
    }

    private fun runPing(host: String): Long? {
        return try {
            val process = Runtime.getRuntime().exec("ping -c 1 -w 2 $host")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    if (l.contains("time=")) {
                        val timePart = l.substringAfter("time=").substringBefore(" ms")
                        return timePart.toDouble().toLong()
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchWhois(host: String): String {
        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
            return "WHOIS not available for local network addresses."
        }
        return try {
            Socket("whois.iana.org", 43).use { socket ->
                socket.soTimeout = 5000
                PrintWriter(socket.outputStream, true).println(host)
                val reader = BufferedReader(InputStreamReader(socket.inputStream))
                val response = reader.readText()
                
                if (response.contains("refer:")) {
                    val refer = response.substringAfter("refer:").trim().split("\n")[0]
                    fetchWhoisFromSource(refer, host)
                } else {
                    response
                }
            }
        } catch (e: Exception) {
            "Failed to fetch WHOIS: ${e.message}"
        }
    }

    private fun fetchWhoisFromSource(source: String, host: String): String {
        return try {
            Socket(source, 43).use { socket ->
                socket.soTimeout = 5000
                PrintWriter(socket.outputStream, true).println(host)
                socket.inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            "Failed to fetch detailed WHOIS from $source: ${e.message}"
        }
    }
}
