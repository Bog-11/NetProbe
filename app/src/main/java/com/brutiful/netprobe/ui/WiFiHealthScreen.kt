package com.brutiful.netprobe.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.viewmodel.WiFiHealthUiState
import java.util.Locale

@Composable
fun WiFiHealthScreen(
    state: WiFiHealthUiState,
    onStartStabilityTest: () -> Unit,
    onCancelStabilityTest: () -> Unit,
    onStartScan: () -> Unit,
    onStartThroughputTest: (String, Int, Boolean, Int) -> Unit,
    onCancelThroughputTest: () -> Unit,
    onStartWifiProximityTracking: (String, String) -> Unit = { _, _ -> },
    onPrepareAiPrompt: () -> Unit = {},
    onConsumePromptCopiedEvent: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showSignalInfo by remember { mutableStateOf(value = false) }

    LaunchedEffect(state.promptCopiedEvent) {
        if (state.promptCopiedEvent && state.generatedAiPrompt != null) {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("NetProbe AI Prompt", state.generatedAiPrompt)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "AI Diagnostic Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
            onConsumePromptCopiedEvent()
        }
    }

    if (showSignalInfo) {
        SignalInfoDialog { showSignalInfo = false }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HeaderBlock(
            title = "Wi-Fi Health",
            subtitle = "Check signal, stability, congestion, and internet"
        )

        SnapshotSection(
            snapshot = state.snapshot,
            onShowSignalInfo = { showSignalInfo = true }
        )

        if (state.snapshot.bssid != null && state.snapshot.bssid != "02:00:00:00:00:00") {
            Button(
                onClick = { onStartWifiProximityTracking(state.snapshot.bssid, state.snapshot.ssid ?: "Connected Access Point") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Live signal tracking (Proximity Range)")
            }
        }

        StabilityTestSection(
            state = state.stabilityResult,
            isTesting = state.isStabilityTesting,
            onStart = onStartStabilityTest,
            onCancel = onCancelStabilityTest
        )

        AiPromptSection(
            state = state,
            onPrepareAndCopyPrompt = onPrepareAiPrompt,
            onCopyPromptDirectly = { promptText ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("NetProbe AI Prompt", promptText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "AI Diagnostic Prompt copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        )

        NearbyScanSection(
            results = state.nearbyScanResults,
            isScanning = state.isScanning,
            onRefresh = onStartScan
        )

        ThroughputTestSection(
            state = state.throughputResult,
            isTesting = state.isThroughputTesting,
            onStart = onStartThroughputTest,
            onCancel = onCancelThroughputTest
        )

        if (state.snapshot.unavailableReasons.isNotEmpty()) {
            UnavailableDataSection(state.snapshot.unavailableReasons)
        }
    }
}

@Composable
fun AiPromptSection(
    state: WiFiHealthUiState,
    onPrepareAndCopyPrompt: () -> Unit,
    onCopyPromptDirectly: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("AI Troubleshooting Assistant Prompt")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Copy Scan Results for AI",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Generate a formatted prompt with all scan data ready to paste into ChatGPT, Claude, or Gemini for automated diagnostic analysis.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (state.isPreparingAiPrompt) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "Preparing AI Prompt...",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Automatically performing required Wi-Fi scans (60s stability test & nearby scan)...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.isStabilityTesting) {
                                LinearProgressIndicator(
                                    progress = { state.stabilityResult.progress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    text = "60s Stability test: ${(state.stabilityResult.progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                val hasProbes = state.stabilityResult.probes.isNotEmpty()
                val hasNearby = state.nearbyScanResults.isNotEmpty()
                val promptText = state.generatedAiPrompt

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (promptText != null && hasProbes && hasNearby) {
                                onCopyPromptDirectly(promptText)
                            } else {
                                onPrepareAndCopyPrompt()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !state.isPreparingAiPrompt
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (state.isPreparingAiPrompt) "Preparing..."
                            else if (!hasProbes || !hasNearby) "Prepare & Copy AI Prompt"
                            else "Copy AI Prompt"
                        )
                    }

                    if (promptText != null) {
                        OutlinedButton(
                            onClick = { isExpanded = !isExpanded }
                        ) {
                            Text(if (isExpanded) "Hide Preview" else "Preview Prompt")
                        }
                    }
                }

                if (!hasProbes || !hasNearby) {
                    Text(
                        text = "Note: If 60s stability check or nearby networks scan haven't been performed yet, clicking above will automatically run them before copying.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                if (isExpanded && promptText != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Prompt Content:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = promptText,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThroughputTestSection(
    state: WiFiThroughputResult,
    isTesting: Boolean,
    onStart: (String, Int, Boolean, Int) -> Unit,
    onCancel: () -> Unit
) {
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("5201") }
    var isUdp by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf("10") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Local Throughput Test")
        Text("Test against a local iperf3 server (not a public server).", style = MaterialTheme.typography.bodySmall)

        if (isTesting) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Throughput test in progress...", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Cancel")
                    }
                }
            }
        } else {
            if ((state.throughputMbps > 0.0) || (state.errorMessage != null)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.errorMessage != null) 
                            MaterialTheme.colorScheme.errorContainer 
                        else 
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (state.errorMessage != null) {
                            Text("Error: ${state.errorMessage}", color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Measured Throughput", style = MaterialTheme.typography.labelLarge)
                            Text(
                                "${String.format(Locale.US, "%.2f", state.throughputMbps)} Mbps", 
                                style = MaterialTheme.typography.headlineMedium, 
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Note: This is a simplified test; results may vary from official iperf3 clients.", 
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = host,
                onValueChange = { host = it },
                label = { Text("Server IP") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                )
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it },
                    label = { Text("Secs") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    )
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = isUdp, onCheckedChange = { isUdp = it })
                Text("UDP Mode (Experimental)", style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = { onStart(host, port.toIntOrNull() ?: 5201, isUdp, duration.toIntOrNull() ?: 10) },
                modifier = Modifier.fillMaxWidth(),
                enabled = host.isNotBlank()
            ) {
                val buttonText = if (state.throughputMbps > 0.0 || state.errorMessage != null) 
                    "Restart Throughput Test" 
                else 
                    "Start Throughput Test"
                Text(buttonText)
            }
        }
    }
}

@Composable
fun NearbyScanSection(
    results: List<WiFiScanResult>,
    isScanning: Boolean,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween, 
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionTitle("Nearby Networks")
            Button(
                onClick = onRefresh, 
                enabled = !isScanning, 
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(if (isScanning) "Scanning..." else "Scan Nearby")
            }
        }

        if (results.isEmpty() && !isScanning) {
            Text("No results found. Start a scan to see nearby networks.", style = MaterialTheme.typography.bodySmall)
        }

        val grouped = results.groupBy { it.band }
        grouped.keys.sorted().forEach { band ->
            BandGroup(band, grouped[band] ?: emptyList())
        }
    }
}

@Composable
fun BandGroup(band: String, aps: List<WiFiScanResult>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        Text(band, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
        
        aps.sortedByDescending { it.rssi }.forEach { ap ->
            ScanResultItem(ap)
        }
        
        if (band == "2.4 GHz") {
            val overlapping = aps.filter { it.rssi > -70 && it.channel !in listOf(1, 6, 11) }
            if (overlapping.size > 2) {
                Text(
                    "Possible congestion: ${overlapping.size} networks on overlapping channels (not 1, 6, 11).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
fun ScanResultItem(ap: WiFiScanResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ap.isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        border = if (ap.isCurrent) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (ap.rssi > -60) Icons.Default.Wifi else Icons.Default.WifiOff, 
                contentDescription = null,
                tint = if (ap.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(ap.ssid.ifBlank { "(Hidden SSID)" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    if (ap.isCurrent) {
                        Text("Connected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("${ap.bssid} • CH ${ap.channel} (${ap.frequencyMhz} MHz)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Signal measured from this phone. Last Wi-Fi scan result.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Text("${ap.rssi} dBm", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StabilityTestSection(
    state: WiFiStabilityTestResult,
    isTesting: Boolean,
    onStart: () -> Unit,
    onCancel: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("60-Second Stability Test")
        
        if (!isTesting && state.icmpProbes.isEmpty() && state.samples.isEmpty()) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Run 60-Second Stability Check")
            }
        } else if (isTesting) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stability test in progress...", style = MaterialTheme.typography.titleSmall)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                    )
                    Text("Sampling RSSI, Link Rates & Probing Targets: ${state.samples.size}s / 60s", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onCancel, 
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Test")
                    }
                }
            }
        }

        if (state.icmpProbes.isNotEmpty() || state.samples.isNotEmpty()) {
            ClassificationCard(state)

            // Internet Speed & Overview Metrics Card
            if (state.speedTestResult != null || state.transport != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Internet Speed & Network Overview Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        state.speedTestResult?.let { speed ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Download Speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f Mbps", speed.downloadSpeedMbps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Upload Speed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(String.format(Locale.US, "%.2f Mbps", speed.uploadSpeedMbps), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Internet Latency / Jitter", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${speed.latencyMs} ms / ${speed.jitterMs} ms jitter", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }

                        state.transport?.let { transport ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Protocol Transport", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(transport, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Network Cost", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (state.isMetered) "Metered" else "Unmetered", style = MaterialTheme.typography.bodySmall)
                        }

                        state.interfaceName?.let { iface ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Primary Interface", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(iface, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        state.channelWidthLabel?.let { width ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Channel Width", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(width, style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        if (state.isVpnConnected) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("VPN Tunnel Status", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Active (${state.vpnInterface ?: "VPN"})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // RSSI & Negotiated Link Rate Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Physical Link Metrics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    
                    state.rssiStats?.let { rssi ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("RSSI Category", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("${rssi.category.label} (${rssi.category.rangeDescription})", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text("Avg RSSI: ${String.format(Locale.US, "%.1f", rssi.avgRssi)} dBm (Min: ${rssi.minRssi}, Max: ${rssi.maxRssi}, StdDev: ${String.format(Locale.US, "%.1f", rssi.stdDevRssi)})", style = MaterialTheme.typography.bodySmall)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    state.linkRateStats?.let { rate ->
                        Text("Negotiated Wi-Fi link rate", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(rate.disclaimer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Tx Rate: ${String.format(Locale.US, "%.0f", rate.avgTxMbps)} Mbps avg (Min: ${rate.minTxMbps}, Max: ${rate.maxTxMbps})", style = MaterialTheme.typography.bodySmall)
                            Text("Rx Rate: ${String.format(Locale.US, "%.0f", rate.avgRxMbps)} Mbps avg (Min: ${rate.minRxMbps}, Max: ${rate.maxRxMbps})", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Real DNS & HTTPS Diagnostics Summary Card
            if (state.dnsSummary.totalQueries > 0 || state.httpsProbes.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Application Layer Probes", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        HorizontalDivider()

                        if (state.dnsSummary.totalQueries > 0) {
                            val dns = state.dnsSummary
                            Text("Real DNS Lookups", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Success: ${dns.successfulQueries}/${dns.totalQueries} (${String.format(Locale.US, "%.1f", dns.failureRatePercent)}% failure rate)", style = MaterialTheme.typography.bodySmall)
                            Text("Avg Lookup Duration: ${dns.avgResponseTimeMs.toInt()} ms (Max: ${dns.maxResponseTimeMs} ms)", style = MaterialTheme.typography.bodySmall)
                        }

                        if (state.httpsProbes.isNotEmpty()) {
                            val totalH = state.httpsProbes.size
                            val succH = state.httpsProbes.count { it.isSuccess }
                            val captiveH = state.httpsProbes.count { it.isCaptivePortalDetected }
                            val validTtfb = state.httpsProbes.map { it.ttfbMs }.filter { it > 0 }
                            val avgTtfb = if (validTtfb.isNotEmpty()) validTtfb.average().toInt() else 0

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("HTTPS Connectivity & Captive Portal Check", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Success: $succH/$totalH probes successful | Captive Portals Detected: $captiveH", style = MaterialTheme.typography.bodySmall, color = if (captiveH > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            if (avgTtfb > 0) {
                                Text("Avg Time to First Byte (TTFB): ${avgTtfb} ms", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ICMP Probes Card
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ICMP Reachability & Jitter", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    state.icmpProbes.forEach { probe ->
                        IcmpProbeResultRow(probe)
                    }
                    
                    if (!isTesting) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text("Run Stability Test Again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClassificationCard(state: WiFiStabilityTestResult) {
    val classification = state.primaryClassification
    val color = when (classification) {
        WiFiHealthClassification.HEALTHY -> MaterialTheme.colorScheme.primary
        WiFiHealthClassification.VERY_WEAK_OR_UNSTABLE_WIFI, 
        WiFiHealthClassification.LOCAL_NETWORK_INSTABILITY, 
        WiFiHealthClassification.CAPTIVE_PORTAL_OR_RESTRICTED, 
        WiFiHealthClassification.NO_WIFI_CONNECTION -> MaterialTheme.colorScheme.error
        WiFiHealthClassification.DNS_ISSUE, 
        WiFiHealthClassification.UPSTREAM_CONNECTIVITY_ISSUE -> MaterialTheme.colorScheme.tertiary
        WiFiHealthClassification.INCONCLUSIVE -> MaterialTheme.colorScheme.outline
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(classification.displayName, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = color.copy(alpha = 0.2f)
                ) {
                    Text("Confidence: ${state.confidence}", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
                }
            }

            if (state.primaryExplanation.isNotEmpty()) {
                Text(state.primaryExplanation, style = MaterialTheme.typography.bodyMedium)
            }

            if (state.secondaryObservations.isNotEmpty()) {
                state.secondaryObservations.forEach { obs ->
                    Text("• Note: $obs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (state.possibleCauses.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Possible Causes:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                state.possibleCauses.forEach { cause ->
                    Text("• $cause", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (state.recommendedActions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Recommended Actions:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
                state.recommendedActions.forEach { action ->
                    Text("• $action", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
fun IcmpProbeResultRow(probe: IcmpProbeResult) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(probe.targetName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (probe.isSuspectedRateLimited) {
                    Text("[Rate Limited]", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Text("${String.format(Locale.US, "%.1f", probe.packetLossPercent)}% loss", color = if (probe.packetLossPercent > 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
        Text(probe.targetAddress, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Latency: ${probe.medianLatencyMs.toInt()}ms (med)", style = MaterialTheme.typography.bodySmall)
            Text("Jitter: ${probe.jitterMs.toInt()}ms", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun SnapshotSection(
    snapshot: WiFiHealthSnapshot,
    onShowSignalInfo: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Connection Snapshot")
        
        InfoCard(label = "SSID", value = snapshot.ssid ?: "Unknown")
        InfoCard(label = "BSSID", value = snapshot.bssid ?: "Unknown", technical = true)
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCard(
                    label = "Signal", 
                    value = "${snapshot.rssi ?: "N/A"} dBm",
                    action = {
                        IconButton(onClick = onShowSignalInfo, modifier = Modifier.size(24.dp)) {
                            Icon(
                                Icons.Default.Info, 
                                contentDescription = "Signal Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
                InfoCard(label = "Channel", value = (snapshot.channel ?: "N/A").toString())
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCard(label = "Band", value = snapshot.band ?: "N/A")
                InfoCard(label = "Frequency", value = "${snapshot.frequencyMhz ?: "N/A"} MHz")
            }
        }

        InfoCard(
            label = "Network Info", 
            value = "IP: ${snapshot.localIp ?: "N/A"}\nGateway: ${snapshot.gateway ?: "N/A"}\nDNS: ${snapshot.dnsServers.joinToString(", ").takeIf { it.isNotEmpty() } ?: "N/A"}",
            technical = true
        )

        InfoCard(
            label = "Link Speed", 
            value = "Tx: ${snapshot.txLinkSpeedMbps ?: "N/A"} Mbps / Rx: ${snapshot.rxLinkSpeedMbps ?: "N/A"} Mbps"
        )
    }
}

@Composable
fun SignalInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Signal Strength (dBm)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "dBm (decibel-milliwatts) measures Wi-Fi signal strength. Lower negative values are better.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                SignalLevelRow("Excellent", "-30 to -60 dBm", "Max performance, HD video streaming.")
                SignalLevelRow("Good", "-60 to -70 dBm", "Reliable connection, browsing and VOIP.")
                SignalLevelRow("Fair", "-70 to -80 dBm", "Minimum for reliable packets. Might be slow.")
                SignalLevelRow("Poor", "-80 to -90 dBm", "Unstable, high packet loss, frequent drops.")
                SignalLevelRow("Unusable", "Below -90 dBm", "Connection likely to fail entirely.")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got it")
            }
        }
    )
}

@Composable
fun SignalLevelRow(level: String, range: String, desc: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(level, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
            Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun UnavailableDataSection(reasons: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.error
            )
            Column {
                Text(
                    "Data Limitations", 
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.error
                )
                reasons.forEach { reason ->
                    Text(
                        "• $reason", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
