package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.SpeedTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.abs

object SpeedTestRunner {

    suspend fun runSpeedTest(): SpeedTestResult = withContext(Dispatchers.IO) {
        val (latency, jitter) = measureLatencyAndJitter()
        val downloadSpeed = measureDownloadSpeed()
        val uploadSpeed = measureUploadSpeed()

        SpeedTestResult(
            downloadSpeedMbps = downloadSpeed,
            uploadSpeedMbps = uploadSpeed,
            latencyMs = latency,
            jitterMs = jitter
        )
    }

    private fun measureLatencyAndJitter(): Pair<Long, Long> {
        val samples = mutableListOf<Long>()
        val target = "8.8.8.8"

        try {
            val process = Runtime.getRuntime().exec("ping -c 5 -i 0.2 -W 1 $target")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    if (l.contains("time=")) {
                        val timePart = l.substringAfter("time=").substringBefore(" ms")
                        timePart.toDoubleOrNull()?.let { samples.add(it.toLong()) }
                    }
                }
            }
        } catch (_: Exception) {}

        if (samples.isEmpty()) {
            repeat(4) {
                val start = System.currentTimeMillis()
                try {
                    val socket = Socket()
                    socket.connect(InetSocketAddress("8.8.8.8", 53), 1500)
                    socket.close()
                    val elapsed = System.currentTimeMillis() - start
                    samples.add(elapsed)
                } catch (_: Exception) {}
            }
        }

        if (samples.isEmpty()) return 0L to 0L

        val bestLatency = samples.minOrNull() ?: 0L

        var totalVariation = 0L
        for (i in 0 until samples.size - 1) {
            totalVariation += abs(samples[i] - samples[i + 1])
        }
        val jitter = if (samples.size > 1) totalVariation / (samples.size - 1) else 0L

        return bestLatency to jitter
    }

    private fun measureDownloadSpeed(): Double {
        val urls = listOf(
            "https://speed.cloudflare.com/__down?bytes=25000000",
            "https://cachefly.cachefly.net/10mb.test",
            "https://httpbin.org/bytes/10000000"
        )

        for (testUrl in urls) {
            var totalBytesRead = 0
            val startTime = System.currentTimeMillis()
            try {
                var currentUrl = testUrl
                var redirects = 0
                var connection: HttpURLConnection? = null

                while (redirects < 3) {
                    val url = URL(currentUrl)
                    connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        setRequestProperty("Accept", "*/*")
                        instanceFollowRedirects = true
                    }

                    val code = connection.responseCode
                    if (code in 300..399) {
                        currentUrl = connection.getHeaderField("Location") ?: break
                        connection.disconnect()
                        redirects++
                    } else if (code in 200..299) {
                        break
                    } else {
                        connection.disconnect()
                        break
                    }
                }

                if (connection == null || connection.responseCode !in 200..299) {
                    continue
                }

                val inputStream = connection.inputStream
                val buffer = ByteArray(32768)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    totalBytesRead += bytesRead
                    if (System.currentTimeMillis() - startTime > 7000) break
                }
                inputStream.close()
                connection.disconnect()

                val timeMs = System.currentTimeMillis() - startTime
                if (timeMs > 200 && totalBytesRead > 0) {
                    val bits = totalBytesRead.toDouble() * 8.0
                    val seconds = timeMs.toDouble() / 1000.0
                    return (bits / seconds) / 1_000_000.0
                }
            } catch (_: Exception) {
                // Try next URL fallback
            }
        }
        return 0.0
    }

    private fun measureUploadSpeed(): Double {
        val testUrl = "https://speed.cloudflare.com/__up"
        val data = ByteArray(1024 * 1024 * 5)
        var totalBytesSent = 0

        val startTime = System.currentTimeMillis()
        try {
            val url = URL(testUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                doOutput = true
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Content-Type", "application/octet-stream")
                setFixedLengthStreamingMode(data.size)
            }

            val outputStream = connection.outputStream
            val chunkSize = 32768
            var offset = 0

            while (offset < data.size) {
                val toWrite = minOf(chunkSize, data.size - offset)
                outputStream.write(data, offset, toWrite)
                offset += toWrite
                totalBytesSent += toWrite
                if (System.currentTimeMillis() - startTime > 6000) break
            }
            outputStream.flush()
            outputStream.close()

            if (connection.responseCode in 200..299) {
                connection.inputStream.use { it.readBytes() }
            }
            connection.disconnect()
        } catch (_: Exception) {
            if (totalBytesSent == 0) return 0.0
        }

        val timeMs = System.currentTimeMillis() - startTime
        if (timeMs == 0L || totalBytesSent == 0) return 0.0
        val bits = totalBytesSent.toDouble() * 8.0
        val seconds = timeMs.toDouble() / 1000.0
        return (bits / seconds) / 1_000_000.0
    }
}
