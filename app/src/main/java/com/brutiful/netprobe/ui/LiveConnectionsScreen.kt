package com.brutiful.netprobe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import com.brutiful.netprobe.model.ConnectionStatus
import com.brutiful.netprobe.model.LiveConnection
import com.brutiful.netprobe.network.ConnectionTracker
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.text.font.FontWeight
import com.brutiful.netprobe.network.ExportMode
import com.brutiful.netprobe.util.NetworkUtils
import com.brutiful.netprobe.ui.theme.MatrixPurple
import com.brutiful.netprobe.ui.theme.MatrixRed
import com.brutiful.netprobe.viewmodel.ExportStatus
import com.brutiful.netprobe.viewmodel.LiveConnectionsViewModel
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LiveConnectionsScreen(
    viewModel: LiveConnectionsViewModel,
    isVpnActive: Boolean,
    primaryIp: String?,
    onToggleVpn: () -> Unit,
    onConnectionClick: (LiveConnection) -> Unit,
    onProbeClick: (LiveConnection) -> Unit,
    modifier: Modifier = Modifier,
    filterUid: Int? = null,
    onBack: (() -> Unit)? = null,
    title: String? = null
) {
    val rawConnections by viewModel.activeConnections.collectAsState(initial = emptyList())
    val connections = remember(rawConnections, filterUid) {
        if (filterUid != null) rawConnections.filter { it.uid == filterUid }
        else rawConnections
    }
    
    // Use provided title, or find app info for the title if filtered
    val headerTitle = remember(connections, filterUid, title) {
        title ?: if (filterUid != null && connections.isNotEmpty()) {
            connections.first().appLabel
        } else "Live Traffic Monitor"
    }

    val exportStatus by viewModel.exportStatus.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    var showExportChooser by remember { mutableStateOf(false) }
    var showExportInfo by remember { mutableStateOf<ExportMode?>(null) }
    var showSecurityWarning by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    val pcapLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.tcpdump.pcap")
    ) { uri ->
        val mode = showExportInfo
        if (uri != null && mode != null) {
            viewModel.exportPcap(context, uri, mode, primaryIp)
        }
        showExportInfo = null
    }

    if (showSecurityWarning) {
        SecurityWarningDialog(
            onDismiss = { showSecurityWarning = false },
            onConfirm = {
                showSecurityWarning = false
                onToggleVpn()
            }
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear All Connections?") },
            text = { Text("Are you sure you want to clear all captured traffic data? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        ConnectionTracker.clearAll()
                        showClearConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    LaunchedEffect(exportStatus) {
        when (val status = exportStatus) {
            is ExportStatus.Success -> {
                val msg = when (status.mode) {
                    ExportMode.RAW_VPN -> "Raw VPN PCAP exported"
                    ExportMode.SANITIZED_PCAP -> "Sanitized PCAP (Headers only) exported"
                    ExportMode.RECONSTRUCTED_SESSION -> "Reconstructed session PCAP exported"
                }
                snackbarHostState.showSnackbar(msg)
                viewModel.clearExportStatus()
            }
            is ExportStatus.Error -> {
                snackbarHostState.showSnackbar("Export failed: ${status.message}")
                viewModel.clearExportStatus()
            }
            else -> {}
        }
    }

    if (showExportChooser) {
        ExportChooserDialog(
            onDismiss = { showExportChooser = false },
            onModeSelected = { mode ->
                showExportChooser = false
                showExportInfo = mode
            }
        )
    }

            if (showExportInfo != null) {
        val mode = showExportInfo!!
        ExportInfoDialog(
            mode = mode,
            onDismiss = { showExportInfo = null },
            onConfirm = {
                val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                val prefix = when (mode) {
                    ExportMode.RAW_VPN -> "capture_raw"
                    ExportMode.SANITIZED_PCAP -> "capture_sanitized"
                    ExportMode.RECONSTRUCTED_SESSION -> "capture_reconstructed"
                }
                pcapLauncher.launch("${prefix}_$dateStr.pcap")
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = headerTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                Row {
                    if (exportStatus is ExportStatus.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(4.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(
                            onClick = { showExportChooser = true },
                            enabled = connections.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.FileDownload,
                                contentDescription = "Export PCAP",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(onClick = { showClearConfirmation = true }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (isVpnActive) {
                        onToggleVpn()
                    } else {
                        showSecurityWarning = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVpnActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (isVpnActive) "Stop Monitoring" else "Start Monitoring")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionTitle("Connections (${connections.size})")
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(connections, key = { it.id }) { connection ->
                    ConnectionCard(
                        connection = connection,
                        timeFormat = timeFormat,
                        onDetailsClick = { onConnectionClick(connection) },
                        onProbeClick = { onProbeClick(connection) }
                    )
                }

                if (connections.isEmpty()) {
                    item {
                        Text(
                            text = if (isVpnActive) "Capturing traffic... apps should appear here soon."
                                   else "Start the monitor to capture live device traffic with real app attribution.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ExportChooserDialog(
    onDismiss: () -> Unit,
    onModeSelected: (ExportMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export PCAP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ExportOption(
                    title = "Raw VPN PCAP",
                    description = "Exports the exact packet bytes captured from the Android VPN interface. Best for low-level debugging. May show tunnel-side addresses and imperfect packet boundaries.",
                    onClick = { onModeSelected(ExportMode.RAW_VPN) }
                )
                ExportOption(
                    title = "Sanitized PCAP (Headers Only)",
                    description = "Recommended for sharing. This mode strips all application data (payloads) and only keeps IP/Transport headers for traffic analysis. Best for privacy.",
                    onClick = { onModeSelected(ExportMode.SANITIZED_PCAP) }
                )
                ExportOption(
                    title = "Reconstructed Session PCAP",
                    description = "Builds a synthetic PCAP using the real endpoints and session metadata shown in the app. Best for Wireshark readability. Not a literal raw capture.",
                    onClick = { onModeSelected(ExportMode.RECONSTRUCTED_SESSION) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SecurityWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MatrixRed) },
        title = { Text("Privacy Warning") },
        text = {
            Text(
                "Starting the Traffic Monitor creates a local-only VPN on this device.\n\n" +
                "Everything stays on your device: all monitoring is performed locally, and traffic data is stored privately and temporarily within the app itself. No data ever leaves your device, and nothing is sent to any external servers or third parties.\n\n" +
                "Location permission is required for advanced Wi-Fi diagnostics, such as identifying your network SSID to help resolve device identities. This information is processed strictly on-device."
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MatrixRed)
            ) {
                Text("I Understand & Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ExportOption(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ExportInfoDialog(
    mode: ExportMode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Export Summary") },
        text = {
            Text(
                when (mode) {
                    ExportMode.RAW_VPN -> "Raw VPN PCAP = exact VPN-captured bytes. Note: these may not match app-visible endpoints due to NAT/Tunneling."
                    ExportMode.SANITIZED_PCAP -> "Sanitized PCAP = privacy-focused. All application payloads are removed, keeping only headers for protocol analysis."
                    ExportMode.RECONSTRUCTED_SESSION -> "Reconstructed Session PCAP = easier to analyze. Note: this is a synthetic reconstruction and not a true wire capture."
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) { Text("Save PCAP") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConnectionCard(
    connection: LiveConnection,
    timeFormat: SimpleDateFormat,
    onDetailsClick: () -> Unit,
    onProbeClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val statusColor = if (connection.status == ConnectionStatus.ACTIVE) MaterialTheme.colorScheme.primary else Color(0xFFF44336)
                    Surface(
                        modifier = Modifier.size(8.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = statusColor
                    ) {}
                    
                    val icon = ConnectionTracker.getAppIcon(connection.packageName)
                    if (icon != null) {
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Text(
                        text = when {
                            connection.appLabel != "Resolving..." && connection.appLabel != "Unknown app" -> connection.appLabel
                            connection.packageName != null && connection.packageName != "unknown" -> connection.packageName
                            connection.uid != -1 -> "UID ${connection.uid}"
                            else -> connection.appLabel // fallback to whatever was there
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = if (connection.status == ConnectionStatus.ACTIVE) "ACTIVE" else "INACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (connection.status == ConnectionStatus.ACTIVE) MaterialTheme.colorScheme.primary else Color(0xFFF44336),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (connection.packageName != null) {
                    Text(
                        text = connection.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = connection.protocol,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Dest: ${connection.destinationHost ?: connection.destinationIp}:${connection.destinationPort}",
                style = MaterialTheme.typography.bodyMedium
            )

            if (connection.destinationHost != null && connection.destinationHost != connection.destinationIp) {
                Text(
                    text = "IP: ${connection.destinationIp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "↑ ${NetworkUtils.formatBytes(connection.sentBytes)}  ↓ ${NetworkUtils.formatBytes(connection.receivedBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Packets: ${connection.totalPackets}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Seen: ${timeFormat.format(Date(connection.lastSeen))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Start: ${timeFormat.format(Date(connection.firstSeen))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDetailsClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Details")
                }
                Button(
                    onClick = onProbeClick,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Probe")
                }
            }
        }
    }
}
