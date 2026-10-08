package com.brutiful.netprobe.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.NetworkOverview
import com.brutiful.netprobe.model.SpeedTestResult
import java.util.Locale

@Composable
fun NetworkOverviewScreen(
    state: NetworkOverview,
    onRunSpeedTest: () -> Unit,
    onDismissSpeedTest: () -> Unit,
    onNavigateToHealthReport: () -> Unit,
    onToggleBrightnessBoost: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var infoToShow by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (infoToShow != null) {
            AlertDialog(
                onDismissRequest = { infoToShow = null },
                confirmButton = {
                    TextButton(onClick = { infoToShow = null }) {
                        Text("Close")
                    }
                },
                title = { Text(text = infoToShow!!.first) },
                text = { Text(text = infoToShow!!.second) },
            )
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Settings") },
                text = {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("High Brightness Boost", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Automatically increases screen brightness for better legibility.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.isBrightnessBoostEnabled,
                                onCheckedChange = onToggleBrightnessBoost
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dark Matrix Theme", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Toggle between the classic Matrix dark theme and the new Light theme.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.isDarkMode,
                                onCheckedChange = onToggleDarkMode
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) {
                        Text("Done")
                    }
                }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    HeaderBlock(
                        title = "NetProbe",
                        subtitle = "Live network status from the phone",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            item {
                MainSummaryCard(
                    state = state,
                    onSpeedTestClick = onRunSpeedTest
                )
            }

            if (state.isSpeedTestExpanded) {
                item {
                    SpeedTestCard(
                        isScanning = state.isSpeedTesting,
                        result = state.speedTestResult,
                        transportType = state.transport,
                        onCollapse = onDismissSpeedTest,
                        onNavigateToHealthReport = onNavigateToHealthReport,
                        onLatencyInfoClick = {
                            infoToShow = "Latency" to "The time it takes for a data packet to travel from your device to a server and back. Measured in milliseconds (ms). Lower values indicate a more responsive connection."
                        },
                    ) {
                        infoToShow = "Jitter" to "The average variation in latency over time. High jitter indicates an unstable connection, which can cause lag or stuttering in real-time applications like gaming or video calls."
                    }
                }
            }

            item { QuickFactsGrid(state) }

            item {
                AdvancedDetailsSection(state = state)
            }
        }
    }
}

@Composable
private fun MainSummaryCard(
    state: NetworkOverview,
    onSpeedTestClick: () -> Unit
) {
    val statusTitle = when {
        !state.isConnected -> "No internet connection"
        state.transport == "Wi‑Fi" -> "Connected to Wi‑Fi"
        state.transport == "Cellular" -> "Connected to mobile data"
        else -> "Connected to ${state.transport}"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val icon = when (state.transport) {
                    "Wi‑Fi" -> Icons.Default.Wifi
                    "Cellular" -> Icons.Default.SignalCellularAlt
                    else -> Icons.Default.SettingsEthernet
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Column {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (state.isValidated) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (state.isValidated) MaterialTheme.colorScheme.primary else Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (state.isValidated) "Internet verified" else "Limited connectivity",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (state.isValidated) MaterialTheme.colorScheme.primary else Color(0xFFF44336)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.transport == "Wi‑Fi" && state.wifiName != null) {
                    StatusChip(label = "Network", value = state.wifiName)
                }
                StatusChip(label = "IP Address", value = state.primaryIp ?: "Searching...")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSpeedTestClick,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Run Speed Test", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun QuickFactsGrid(state: NetworkOverview) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quick Facts")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickFactItem(
                label = "Interface",
                value = state.interfaceName ?: "N/A",
                modifier = Modifier.weight(1f)
            )
            QuickFactItem(
                label = "Network Cost",
                value = if (state.isMetered) "Metered" else "Unmetered",
                modifier = Modifier.weight(1f)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickFactItem(
                label = "DNS Primary",
                value = state.dnsServers.firstOrNull() ?: "None",
                modifier = Modifier.weight(1f)
            )
            QuickFactItem(
                label = "Gateway",
                value = state.localAddresses.firstOrNull()?.getNetworkRange()?.split("/")?.first() ?: "Unknown",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuickFactItem(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AdvancedDetailsSection(
    state: NetworkOverview
) {
    Column(
        modifier = Modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle("More Details")
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            ),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailGroup("Infrastructure") {
                    DetailRow("Primary Interface", state.interfaceName ?: "Unknown")
                    DetailRow("Protocol Transport", state.transport)
                }
                
                HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                
                DetailGroup("Network Radio Diagnostics") {
                    val radioHeader = if (state.transport == "Wi‑Fi") "Wi-Fi" else "Cellular"
                    DetailRow("$radioHeader SSID/Carrier", state.wifiName ?: state.interfaceName ?: "Unknown")
                    state.radioBandLabel?.let { band ->
                        val freqSuffix = state.radioFrequencyMhz?.let { " ($it MHz)" } ?: ""
                        DetailRow("Active Band", "$band$freqSuffix")
                    }
                    if (state.transport == "Wi‑Fi") {
                        state.channelWidthLabel?.let { width ->
                            DetailRow("Channel Width", width)
                        }
                    }
                }

                if (state.dnsServers.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    DetailGroup("DNS Configuration") {
                        DetailRow("Resolvers", state.dnsServers.joinToString("\n"))
                    }
                }

                if (state.localAddresses.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    DetailGroup("IP Routing") {
                        DetailRow("Local Segments", state.localAddresses.joinToString("\n") { it.getNetworkRange() })
                    }
                }

                if (state.isVpnConnected) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    DetailGroup("VPN Tunnel") {
                        DetailRow("Interface", state.vpnInterface ?: "Active")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun SpeedTestCard(
    isScanning: Boolean,
    result: SpeedTestResult?,
    transportType: String,
    onCollapse: () -> Unit,
    onNavigateToHealthReport: () -> Unit,
    onLatencyInfoClick: () -> Unit,
    onJitterInfoClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCollapse() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${transportType.uppercase()} SPEED REPORT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Default.ExpandLess,
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            if (result != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.alpha(if (isScanning) 0.6f else 1.0f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DOWNLOAD",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f Mbps", result.downloadSpeedMbps),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPLOAD",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = String.format(Locale.US, "%.2f Mbps", result.uploadSpeedMbps),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LATENCY",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = onLatencyInfoClick,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Latency Info",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${result.latencyMs} ms",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "JITTER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                IconButton(
                                    onClick = onJitterInfoClick,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Info,
                                        contentDescription = "Jitter Info",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${result.jitterMs} ms",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            } else if (isScanning) {
                Text(
                    text = "Measuring download speed and latency...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Speed test failed. Please check your connection.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (transportType == "Wi‑Fi" && !isScanning && result != null) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onNavigateToHealthReport,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Health report")
                }
            }
        }
    }
}
