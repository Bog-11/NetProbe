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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.BluetoothCategory
import com.brutiful.netprobe.model.BluetoothDeviceData
import com.brutiful.netprobe.model.Confidence
import com.brutiful.netprobe.model.DeviceCategory
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.DiscoverySource
import com.brutiful.netprobe.model.PresenceState
import com.brutiful.netprobe.model.ProximityTargetType
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
                    "Best-Effort LAN Scan"
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

                if (!isAnyScanning && state.selectedTab == DiscoveryTab.NETWORK) {
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More scan options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Fast Best-Effort Discovery") },
                                onClick = {
                                    showMenu = false
                                    onStartScan(ScanMode.NORMAL)
                                },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Expanded Subnet Scan") },
                                onClick = {
                                    showMenu = false
                                    onStartScan(ScanMode.EXPANDED)
                                },
                                leadingIcon = { Icon(Icons.Default.TravelExplore, contentDescription = null) }
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
                            text = state.statusMessage ?: "Scanning…",
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
fun SubnetCoverageNoticeCard(
    coverageText: String?,
    warningNotice: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            coverageText?.let {
                Text(
                    text = "Subnet Coverage: $it",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            warningNotice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
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
    device: DiscoveredDevice,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presenceColor = when (device.presenceState) {
        PresenceState.CONFIRMED_ACTIVE,
        PresenceState.DISCOVERED_BY_MULTICAST -> MaterialTheme.colorScheme.primary
        PresenceState.ROUTER_REPORTED -> Color(0xFF4CAF50)
        PresenceState.PREVIOUSLY_SEEN,
        PresenceState.UNRESPONSIVE_THIS_SCAN -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        PresenceState.NOT_REACHABLE_FROM_PHONE -> Color(0xFFF44336)
    }

    val alpha = if (device.presenceState == PresenceState.PREVIOUSLY_SEEN ||
        device.presenceState == PresenceState.UNRESPONSIVE_THIS_SCAN
    ) 0.6f else 1.0f

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
                .background(presenceColor)
        )

        Column(modifier = Modifier.weight(1f).alpha(alpha)) {
            Text(
                text = device.computedDisplayName(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val metadata = buildString {
                append(device.ipString)
                device.manufacturer?.let { append(" • $it") }
                if (device.sources.isNotEmpty()) {
                    append(" • ${device.sources.joinToString { it.name }}")
                }
            }
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (device.sources.isNotEmpty()) {
            Icon(
                imageVector = when {
                    device.sources.contains(DiscoverySource.WS_DISCOVERY) -> Icons.Default.Videocam
                    device.sources.contains(DiscoverySource.MDNS) ||
                            device.sources.contains(DiscoverySource.SSDP) -> Icons.Default.SettingsInputAntenna
                    else -> Icons.Default.Wifi
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
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
fun getCategoryIcon(category: BluetoothCategory): ImageVector {
    return when (category) {
        BluetoothCategory.IPHONE -> Icons.Default.Smartphone
        BluetoothCategory.ANDROID -> Icons.Default.Android
        BluetoothCategory.WINDOWS -> Icons.Default.Computer
        BluetoothCategory.LINUX -> Icons.Default.Terminal
        BluetoothCategory.AUDIO -> Icons.Default.Headphones
        BluetoothCategory.WEARABLE -> Icons.Default.Watch
        BluetoothCategory.OTHER -> Icons.Default.Bluetooth
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothCategorySummaryCard(
    bluetoothDevices: List<BluetoothDeviceData>,
    selectedCategory: BluetoothCategory?,
    onSelectCategory: (BluetoothCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryCounts = remember(bluetoothDevices) {
        bluetoothDevices.groupingBy { it.category }.eachCount()
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discovered Device Types",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (selectedCategory != null) {
                    TextButton(
                        onClick = { onSelectCategory(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Show All (${bluetoothDevices.size})", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        text = "${bluetoothDevices.size} total",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                BluetoothCategory.entries.forEach { cat ->
                    val count = categoryCounts[cat] ?: 0
                    val isSelected = selectedCategory == cat

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            if (isSelected) onSelectCategory(null) else onSelectCategory(cat)
                        },
                        label = {
                            Text(
                                text = "${cat.displayName}: $count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected || count > 0) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = getCategoryIcon(cat),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (count > 0) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                            labelColor = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BluetoothDeviceResultRow(
    device: BluetoothDeviceData,
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
                .background(MaterialTheme.colorScheme.primary)
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = device.displayName(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(device.category),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = device.category.shortName,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            Text(
                text = "${device.address} • ${device.type} • ${device.rssi} dBm",
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
    onSsh: (DiscoveredDevice) -> Unit = {},
    onStartTrackingLive: (String, ProximityTargetType, String) -> Unit = { _, _, _ -> }
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
                        title = device.computedDisplayName(),
                        subtitle = device.ipString,
                        icon = Icons.Default.Lan
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DiscoveryDetailItem("IP Address", device.ipString)
                        device.macAddress?.let { DiscoveryDetailItem("MAC Address", it) }
                        device.hostname?.let { DiscoveryDetailItem("Hostname", it) }
                        if (device.openPorts.isNotEmpty()) {
                            DiscoveryDetailItem("Open Ports", device.openPorts.joinToString(", "))
                        }
                        DiscoveryDetailItem("Evidence Summary", device.evidenceSummary())
                        if (device.notes.isNotEmpty()) {
                            DiscoveryDetailItem("Notes", device.notes.joinToString("; "))
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
                            Icon(Icons.Default.ManageSearch, contentDescription = null)
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
                        DiscoveryDetailItem("Address", device.address)
                        DiscoveryDetailItem("Device Type", device.type)
                        DiscoveryDetailItem("Signal Strength", "${device.rssi} dBm")
                        device.vendor?.let { DiscoveryDetailItem("Vendor", it) }
                        device.categoryLabel?.let { DiscoveryDetailItem("Category", it) }
                        DiscoveryDetailItem("Bond State", device.bondState)
                        if (device.evidenceList.isNotEmpty()) {
                            DiscoveryDetailItem("Evidence", device.evidenceList.joinToString("; "))
                        }
                    }

                    Button(
                        onClick = {
                            onStartTrackingLive(
                                device.address,
                                ProximityTargetType.BLE_DEVICE,
                                device.displayName()
                            )
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Radar, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Live Signal Tracking")
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryDeviceHeader(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoveryDetailItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 220.dp)
        )
    }
}

@Composable
fun DiscoveryDiagnosticsCard(stats: DiscoveryStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            DiagnosticCount("mDNS", stats.mdnsCount)
            DiagnosticCount("SSDP", stats.ssdpCount)
            DiagnosticCount("WS-Disc", stats.wsDiscoveryCount)
            DiagnosticCount("TCP", stats.tcpProbeCount)
        }
    }
}

@Composable
private fun DiagnosticCount(label: String, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
