package com.brutiful.netprobe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.network.HexDumpFormatter
import com.brutiful.netprobe.network.NetworkUtils
import com.brutiful.netprobe.network.PacketParser
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTrafficDetailScreen(
    connection: LiveConnection,
    packets: List<CapturedPacket>,
    onBack: () -> Unit,
    onProbe: (LiveConnection) -> Unit
) {
    var selectedPacket by remember { mutableStateOf<CapturedPacket?>(null) }
    var filterText by remember { mutableStateOf("") }
    val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredPackets = packets.filter {
        filterText.isEmpty() || it.summary.contains(filterText, ignoreCase = true) ||
                it.protocol.contains(filterText, ignoreCase = true)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(connection.appLabel, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${connection.destinationHost ?: connection.destinationIp}:${connection.destinationPort}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: Show filter dialog */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filter")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Stats Header
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("Sent", NetworkUtils.formatBytes(connection.sentBytes))
                        StatItem("Received", NetworkUtils.formatBytes(connection.receivedBytes))
                        StatItem("Protocol", connection.protocol)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val isProbeEnabled = connection.destinationIp.isNotBlank()
                        Button(
                            onClick = { onProbe(connection) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isProbeEnabled
                        ) {
                            Text("Probe Destination")
                        }
                    }

                    if (connection.destinationIp.isBlank()) {
                        Text(
                            text = "No valid destination IP to probe.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Packet List
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap a packet below to inspect payload and protocol details",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredPackets) { packet ->
                    PacketRow(packet, timeFormat) {
                        selectedPacket = packet
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (filteredPackets.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No packets captured yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (selectedPacket != null) {
            PacketDetailDialog(
                packet = selectedPacket!!,
                onDismiss = { selectedPacket = null },
                snackbarHostState = snackbarHostState
            )
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PacketRow(packet: CapturedPacket, timeFormat: SimpleDateFormat, onClick: () -> Unit) {
    val color = if (packet.direction == PacketDirection.UPSTREAM) 
        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) 
    else 
        MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            timeFormat.format(Date(packet.timestamp)),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(80.dp)
        )
        
        val directionChar = if (packet.direction == PacketDirection.UPSTREAM) "↑" else "↓"
        val directionColor = if (packet.direction == PacketDirection.UPSTREAM) Color(0xFF4CAF50) else Color(0xFF2196F3)
        
        Text(
            directionChar,
            color = directionColor,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(packet.summary, style = MaterialTheme.typography.bodySmall)
            if (packet.decryptionStatus == DecryptionStatus.DECRYPTED) {
                Text("Decrypted", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
            }
        }
        
        Text(
            "${packet.length} B",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 4.dp)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketDetailDialog(
    packet: CapturedPacket,
    onDismiss: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Overview", "Raw", "Parsed", "Decrypted")
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(modifier = Modifier.fillMaxHeight(0.8f)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).padding(16.dp)) {
                when (selectedTab) {
                    0 -> PacketOverview(packet)
                    1 -> PacketRawView(packet.rawBytes) { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                    2 -> PacketParsedView(packet)
                    3 -> PacketDecryptedView(packet)
                }
            }
        }
    }
}

@Composable
fun PacketOverview(packet: CapturedPacket) {
    val metadata = PacketParser.parsePacket(packet)
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        metadata.forEach { (key, value) ->
            DetailItem(key, value)
        }
    }
}

@Composable
fun PacketRawView(bytes: ByteArray, onNotify: (String) -> Unit) {
    val clipboardManager = LocalClipboardManager.current
    val rows = remember(bytes) { HexDumpFormatter.format(bytes) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Copy Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {
                    clipboardManager.setText(AnnotatedString(HexDumpFormatter.getFullHex(bytes)))
                    onNotify("Hex copied to clipboard")
                },
                label = { Text("Copy Hex", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {
                    clipboardManager.setText(AnnotatedString(HexDumpFormatter.getFullAscii(bytes)))
                    onNotify("ASCII copied to clipboard")
                },
                label = { Text("Copy ASCII", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {
                    clipboardManager.setText(AnnotatedString(HexDumpFormatter.getFullDump(rows)))
                    onNotify("Full dump copied to clipboard")
                },
                label = { Text("Copy Dump", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)) }
            )
            AssistChip(
                onClick = {
                    clipboardManager.setText(AnnotatedString(HexDumpFormatter.getBase64(bytes)))
                    onNotify("Base64 copied to clipboard")
                },
                label = { Text("Copy Base64", fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp)) }
            )
        }

        // Hex View
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Text(
                    text = "Scroll horizontally to see full ASCII dump",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E), shape = MaterialTheme.shapes.small)
                    .padding(8.dp)
            ) {
                SelectionContainer {
                    val verticalScrollState = rememberScrollState()
                    val horizontalScrollState = rememberScrollState()
                    
                    Column(
                        modifier = Modifier
                            .verticalScroll(verticalScrollState)
                            .horizontalScroll(horizontalScrollState)
                    ) {
                        // Column Headers
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            Box(modifier = Modifier.width(60.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "OFFSET",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.width(360.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "HEX DATA",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.width(120.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "ASCII",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 9.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color.Transparent else Color.White.copy(alpha = 0.05f))
                                    .padding(vertical = 1.dp)
                            ) {
                                Text(
                                    text = row.offset,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF888888),
                                    modifier = Modifier.width(60.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = row.hex,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF00FF00),
                                    modifier = Modifier.width(360.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = row.ascii,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF4CAF50),
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PacketParsedView(packet: CapturedPacket) {
    val metadata = PacketParser.parsePacket(packet)
    if (metadata.containsKey("App Protocol")) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text("Protocol Analysis", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            metadata.forEach { (key, value) ->
                DetailItem(key, value)
            }
        }
    } else {
        Text("No advanced parsing available for this packet type.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PacketDecryptedView(packet: CapturedPacket) {
    when (packet.decryptionStatus) {
        DecryptionStatus.DECRYPTED -> {
            if (packet.decryptedPayload != null) {
                Text(
                    String(packet.decryptedPayload, StandardCharsets.UTF_8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            } else {
                Text("Decrypted payload is empty.")
            }
        }
        DecryptionStatus.ENCRYPTED_NOT_DECRYPTED -> {
            Text("Encrypted traffic. Decrypted payload not available.\n\nTLS MITM decryption is required to view this content.", color = MaterialTheme.colorScheme.error)
        }
        DecryptionStatus.DECRYPTION_FAILED -> {
            Text("Decryption failed.", color = MaterialTheme.colorScheme.error)
        }
        DecryptionStatus.NOT_ENCRYPTED -> {
            Text("Traffic is not encrypted. Use 'Parsed' or 'Raw' view.")
        }
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
