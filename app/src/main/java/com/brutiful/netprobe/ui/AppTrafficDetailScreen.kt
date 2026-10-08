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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.network.HexDumpFormatter
import com.brutiful.netprobe.network.PacketParser
import com.brutiful.netprobe.util.NetworkUtils
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
    onProbe: (LiveConnection) -> Unit,
) {
    var selectedPacket by remember { mutableStateOf<CapturedPacket?>(null) }
    var filterText by remember { mutableStateOf("") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
        val directionColor = if (packet.direction == PacketDirection.UPSTREAM) MaterialTheme.colorScheme.primary else Color(0xFF2196F3)
        
        Text(
            directionChar,
            color = directionColor,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(24.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(packet.summary, style = MaterialTheme.typography.bodySmall)
            if (packet.decryptionStatus == DecryptionStatus.DECRYPTED) {
                Text("Decrypted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
    var selectedTab by remember { mutableIntStateOf(0) }
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
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        // Direction and Protocol Header
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val color = if (packet.direction == PacketDirection.UPSTREAM) MaterialTheme.colorScheme.primary else Color(0xFF2196F3)
                    Icon(
                        if (packet.direction == PacketDirection.UPSTREAM) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = color
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = packet.protocol,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = if (packet.direction == PacketDirection.UPSTREAM) "Outgoing Traffic" else "Incoming Traffic",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Path Visualization
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AddressBlock(label = "Source", ip = packet.sourceIp, port = packet.sourcePort)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            AddressBlock(label = "Destination", ip = packet.destinationIp, port = packet.destinationPort)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Basic Info Table
        Text("Packet Details", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp)
        
        val timeFormat = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
        DetailItem("Time Captured", timeFormat.format(Date(packet.timestamp)))
        DetailItem("Total Size", "${packet.length} bytes")
        DetailItem("Brief Summary", packet.summary)
    }
}

@Composable
fun AddressBlock(label: String, ip: String, port: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(ip, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text("Port $port", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }

                        rows.forEachIndexed { index, row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if ((index % 2) == 0) Color.Transparent else Color.White.copy(alpha = 0.05f))
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
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(360.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = row.ascii,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
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
    // Filter out keys already shown in Overview
    val appData = metadata.filterKeys { it !in listOf("Timestamp", "Protocol", "Source", "Destination", "Length") }
    
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        if (appData.isNotEmpty()) {
            Text("Protocol Analysis", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            appData.forEach { (key, value) ->
                DetailItem(key, value)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No application-level data detected.\nPayload may be encrypted or an unrecognized format.",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PacketDecryptedView(packet: CapturedPacket) {
    val metadata = PacketParser.parsePacket(packet)
    val status = metadata["_decryption_status"] ?: packet.decryptionStatus.name
    val bytes = packet.rawBytes
    val clipboardManager = LocalClipboardManager.current
    
    // Calculate payload offset
    val ihl = (bytes[0].toInt() and 0x0F) * 4
    var payloadOffset = ihl
    if (packet.protocol == "TCP" && (bytes.size >= ihl + 20)) {
        payloadOffset += ((bytes[ihl + 12].toInt() shr 4) and 0x0F) * 4
    } else if (packet.protocol == "UDP") {
        payloadOffset += 8
    }

    val payload = if (payloadOffset < bytes.size) bytes.copyOfRange(payloadOffset, bytes.size) else null

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        when {
            packet.decryptionStatus == DecryptionStatus.DECRYPTED -> {
                SelectionContainer {
                    Text(
                        String(packet.decryptedPayload ?: byteArrayOf(), StandardCharsets.UTF_8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
            status == "ENCRYPTED_NOT_DECRYPTED" -> {
                EncryptionWarning(packet)
            }
            else -> {
                // Try smart decoding for plaintext
                val decoded = payload?.let { com.brutiful.netprobe.network.ContentDecoder.decodeSmart(it) }
                if (decoded != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Smart-Decoded Content", style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(
                                onClick = { clipboardManager.setText(AnnotatedString(decoded)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                    SelectionContainer {
                        Text(
                            text = decoded,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Traffic is not encrypted.\nNo special encoding (JSON/Base64/Gzip) detected.\nCheck 'Parsed' tab for details.",
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EncryptionWarning(packet: CapturedPacket) {
    Column {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Traffic is Encrypted", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                    Text(
                        "This connection uses ${if (packet.destinationPort == 443) "HTTPS/TLS" else "an encrypted protocol"}. The contents are protected and cannot be read without session-specific decryption keys.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text("Deep Analysis", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            "To view the contents of encrypted traffic, you would need to intercept the session using a Proxy and a Trusted Root Certificate. This app currently supports Layer 4 monitoring only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
