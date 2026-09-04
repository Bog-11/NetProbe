package com.brutiful.netprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.BluetoothDeviceData
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.viewmodel.DiscoveryStats
import com.brutiful.netprobe.viewmodel.DiscoveryTab
import com.brutiful.netprobe.viewmodel.DiscoveryUiState
import com.brutiful.netprobe.viewmodel.ScanMode
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ScanControlCard(
    state: DiscoveryUiState,
    onStartScan: (ScanMode) -> Unit,
    onStopScan: () -> Unit,
    onEnrich: () -> Unit,
    onScanAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isAnyScanning = state.isScanning || state.isBluetoothScanning
                val primaryLabel = if (isAnyScanning) {
                    "Stop Discovery"
                } else if (state.selectedTab == DiscoveryTab.NETWORK) {
                    "Scan Network"
                } else {
                    "Scan Bluetooth"
                }
                
                Button(
                    onClick = { if (!isAnyScanning) onStartScan(ScanMode.NORMAL) else onStopScan() },
                    modifier = Modifier.weight(1f),
                    colors = if (isAnyScanning) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Icon(
                        imageVector = if (isAnyScanning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(primaryLabel)
                }

                if (!isAnyScanning) {
                    var showMenu by remember { mutableStateOf(value = false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Normal Scan") },
                                onClick = {
                                    showMenu = false
                                    onStartScan(ScanMode.NORMAL)
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Deep Scan") },
                                onClick = {
                                    showMenu = false
                                    onStartScan(ScanMode.DEEP)
                                },
                                leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Scan All") },
                                onClick = {
                                    showMenu = false
                                    onScanAll()
                                },
                                leadingIcon = { Icon(Icons.Default.AllInclusive, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Enrich Existing") },
                                onClick = {
                                    showMenu = false
                                    onEnrich()
                                },
                                leadingIcon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) }
                            )
                        }
                    }
                }
            }

            if (state.isScanning || state.isBluetoothScanning) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { if (state.isScanning) state.progress else 0.5f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = state.statusMessage ?: "Scanning...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatDuration(state.elapsedTimeSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (state.devices.isEmpty() && state.bluetoothDevices.isEmpty()) {
                Text(
                    text = "Ready to scan",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun DiscoverySummaryBar(state: DiscoveryUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DiscoverySummaryItem(
            label = "Network",
            value = state.devices.size.toString(),
            icon = Icons.Default.Lan
        )
        DiscoverySummaryItem(
            label = "Bluetooth",
            value = state.bluetoothDevices.size.toString(),
            icon = Icons.Default.Bluetooth
        )
        state.lastScanTimestamp?.let {
            DiscoverySummaryItem(
                label = "Last Scan",
                value = formatTimestamp(it),
                icon = Icons.Default.History
            )
        }
    }
}

@Composable
private fun DiscoverySummaryItem(label: String, value: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun DeviceResultRow(
    name: String,
    metadata: String,
    isReachable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (isReachable) MaterialTheme.colorScheme.primary else Color(0xFFF44336))
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun DiscoveryEmptyState(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    icon: ImageVector
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = CircleShape,
            modifier = Modifier.size(80.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        
        Button(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailBottomSheet(
    device: Any?,
    onDismiss: () -> Unit,
    onProbe: (DiscoveredDevice) -> Unit = {},
    onSsh: (DiscoveredDevice) -> Unit = {}
) {
    if (device == null) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            when (device) {
                is DiscoveredDevice -> {
                    DiscoveryDeviceHeader(
                        title = device.displayName(),
                        subtitle = device.ipAddress,
                        icon = Icons.Default.Lan
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiscoveryDetailItem("MAC Address", device.macAddress ?: "Unknown")
                        DiscoveryDetailItem("Manufacturer", device.manufacturer ?: "Unknown")
                        DiscoveryDetailItem("Host Name", device.hostName ?: "N/A")
                        if (device.openPorts.isNotEmpty()) {
                            DiscoveryDetailItem("Open Ports", device.openPorts.joinToString(", "))
                        }
                        if (device.evidence.isNotEmpty()) {
                            DiscoveryDetailItem("Fingerprint", device.getEvidenceSummary())
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (device.openPorts.contains(22)) {
                            Button(
                                onClick = { onSsh(device); onDismiss() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Terminal, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SSH")
                            }
                        }
                        Button(
                            onClick = { onProbe(device); onDismiss() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Probe")
                        }
                    }
                }
                is BluetoothDeviceData -> {
                    DiscoveryDeviceHeader(
                        title = device.displayName(),
                        subtitle = device.address,
                        icon = Icons.Default.Bluetooth
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiscoveryDetailItem("Type", device.type)
                        DiscoveryDetailItem("Vendor", device.vendor ?: "Unknown")
                        DiscoveryDetailItem("Class", device.deviceClass ?: "N/A")
                        DiscoveryDetailItem("Status", device.bondState)
                        DiscoveryDetailItem("Signal", "${device.rssi} dBm")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryDeviceHeader(title: String, subtitle: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun DiscoveryDetailItem(label: String, value: String) {
    Column {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format(Locale.US, "%02d:%02d", m, s)
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun DiscoveryDiagnosticsCard(stats: DiscoveryStats) {
    var infoToShow by remember { mutableStateOf<Pair<String, String>?>(null) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Discovery Sources",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DiagnosticItem("ICMP", stats.icmpCount) {
                    infoToShow = "ICMP" to "Internet Control Message Protocol. Used for 'ping' to check if a device is online and responding."
                }
                DiagnosticItem("TCP", stats.tcpProbeCount) {
                    infoToShow = "TCP" to "Transmission Control Protocol. Probes common web and service ports to find devices that might block ping."
                }
                DiagnosticItem("ARP", stats.arpCount) {
                    infoToShow = "ARP" to "Address Resolution Protocol. Maps IP addresses to MAC addresses by reading the local device's network cache."
                }
                DiagnosticItem("mDNS", stats.mdnsCount) {
                    infoToShow = "mDNS" to "Multicast DNS (Bonjour). Discovers services like printers, Chromecasts, and Apple devices using broadcast names."
                }
                DiagnosticItem("SSDP", stats.ssdpCount) {
                    infoToShow = "SSDP" to "Simple Service Discovery Protocol. Used by UPnP to find smart TVs, media servers, and network routers."
                }
                DiagnosticItem("ONVIF", stats.onvifCount) {
                    infoToShow = "ONVIF" to "Open Network Video Interface Forum. Specialized protocol used to discover and identify IP security cameras."
                }
            }
        }
    }

    if (infoToShow != null) {
        AlertDialog(
            onDismissRequest = { infoToShow = null },
            title = { Text(infoToShow!!.first) },
            text = { Text(infoToShow!!.second) },
            confirmButton = {
                TextButton(onClick = { infoToShow = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun DiagnosticItem(label: String, count: Int, onInfoClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = if (count > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onInfoClick() }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                modifier = Modifier.size(10.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}
