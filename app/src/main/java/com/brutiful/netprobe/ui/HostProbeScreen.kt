package com.brutiful.netprobe.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import com.brutiful.netprobe.model.TargetType
import com.brutiful.netprobe.model.WhoisReport
import com.brutiful.netprobe.model.PortProbeResult
import com.brutiful.netprobe.model.PortStatus
import com.brutiful.netprobe.viewmodel.HostProbeUiState
import com.brutiful.netprobe.ui.theme.MatrixBlue
import com.brutiful.netprobe.ui.theme.MatrixPurple
import com.brutiful.netprobe.ui.theme.MatrixPurpleDark
import com.brutiful.netprobe.ui.theme.MatrixRed
import com.brutiful.netprobe.ui.theme.MatrixRedDark

@Composable
fun HostProbeScreen(
    state: HostProbeUiState,
    onIpChanged: (String) -> Unit,
    onStartAggressiveProbe: () -> Unit,
    onStartQuickProbe: () -> Unit,
    onCancelProbe: () -> Unit,
    onToggleWhois: () -> Unit,
    onToggleTraceroute: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    onSshClick: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var portInfoToShow by remember { mutableStateOf<PortProbeResult?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        if (portInfoToShow != null) {
            AlertDialog(
                onDismissRequest = { portInfoToShow = null },
                confirmButton = {
                    TextButton(onClick = { portInfoToShow = null }) {
                        Text("Close")
                    }
                },
                title = {
                    Text(text = "Port ${portInfoToShow?.port}: ${portInfoToShow?.serviceName ?: "Unknown"}")
                },
                text = {
                    Text(text = portInfoToShow?.description ?: "No detailed information available for this port.")
                },
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Exhaustive Host Probe",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "Complete TCP scan (1-65535) and deep fingerprinting. This will be slow.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.deviceLabel?.let { label ->
                item {
                    InfoCard(
                        label = "Discovered Device",
                        value = label,
                        technical = false
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = state.ipAddress,
                        onValueChange = onIpChanged,
                        label = { Text("Target IP or Domain") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    
                    state.targetPort?.let { port ->
                        Text(
                            text = "Detected active port: $port",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.isProbing) {
                        Button(
                            onClick = { },
                            enabled = false,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                disabledContainerColor = if (state.isAggressive) MatrixRed else MatrixBlue,
                                disabledContentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Probing...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onCancelProbe,
                            modifier = Modifier.width(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Cancel")
                        }
                    } else {
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onStartQuickProbe()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MatrixBlue)
                        ) {
                            Text("Quick Probe")
                        }

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                onStartAggressiveProbe()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MatrixRed)
                        ) {
                            Text("Aggressive Probe")
                        }
                    }
                }
            }

            if (state.isProbing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MatrixRed
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Ports: ${state.scannedPorts} / 65535",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                text = "${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        
                        state.currentPhase?.let { phase ->
                            Text(
                                text = "Phase: $phase",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        state.currentService?.let { service ->
                            Text(
                                text = "Current: $service",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            state.errorMessage?.let { message ->
                item {
                    if (message.startsWith("Scan Type:")) {
                        InfoCard("Identity Summary (EXperemental)", message, technical = true)
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Error: $message",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            state.pingMs?.let { ms ->
                item {
                    InfoCard("Ping Response", "$ms ms", technical = true)
                }
            }

            if (state.lastProbedHost != null) {
                item {
                    TracerouteCard(
                        data = state.tracerouteResult ?: "Tap to start traceroute analysis",
                        hops = state.tracerouteHops,
                        isExpanded = state.isTracerouteExpanded,
                        isLoading = state.isTracerouteLoading,
                        onToggle = onToggleTraceroute
                    )
                }
            }

            state.whoisReport?.let { report ->
                item {
                    WhoisReportCard(
                        report = report,
                        isExpanded = state.isWhoisExpanded,
                        onToggle = onToggleWhois
                    )
                }
            }

            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MeetingRoom,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp).size(24.dp)
                    )
                    SectionTitle("Open Ports Found (${state.reachablePorts.size})")
                }
            }

            if (!state.isProbing && state.reachablePorts.isEmpty() && (state.lastProbedHost != null)) {
                item {
                    InfoCard("Result", "No open ports found in the scanned range.", technical = true)
                }
            } else {
                items(state.reachablePorts) { result ->
                    PortResultCard(
                        result = result,
                        onOpenBrowser = {
                            val url = when (result.port) {
                                443 -> "https://${state.lastProbedHost}"
                                80 -> "http://${state.lastProbedHost}"
                                else -> "http://${state.lastProbedHost}:${result.port}"
                            }
                            onOpenBrowser(url)
                        },
                        onSshClick = {
                            onSshClick(state.lastProbedHost!!, result.port)
                        }
                    ) {
                        portInfoToShow = result
                    }
                }
            }
        }
    }
}

@Composable
private fun TracerouteCard(
    data: String,
    hops: List<com.brutiful.netprobe.model.TracerouteHop>,
    isExpanded: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
    var showHelp by remember { mutableStateOf(value = false) }

    if (showHelp) {
        TracerouteHelpDialog { showHelp = false }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MatrixPurpleDark
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggle() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "TRACEROUTE REPORT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MatrixPurple
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MatrixPurple
                    )
                }
                
                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Traceroute Help",
                        tint = MatrixPurple.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading) {
                    val transition = rememberInfiniteTransition(label = "dots")
                    val dotCount by transition.animateFloat(
                        initialValue = 0f,
                        targetValue = 4f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "dotCount"
                    )
                    
                    Text(
                        text = "Tracing path" + ".".repeat(dotCount.toInt()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MatrixPurple,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                
                if (hops.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        hops.forEach { hop ->
                            TracerouteHopRow(hop)
                        }
                    }
                } else if (!isLoading) {
                    Text(
                        text = data,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = MatrixPurple
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TracerouteHopRow(hop: com.brutiful.netprobe.model.TracerouteHop) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(java.util.Locale.US, "%2d", hop.ttl),
                style = MaterialTheme.typography.labelMedium,
                color = MatrixPurple.copy(alpha = 0.6f),
                modifier = Modifier.width(20.dp)
            )
            
            Text(
                text = hop.ip ?: "* * *",
                style = MaterialTheme.typography.bodySmall,
                color = if (hop.ip == null) MatrixPurple.copy(alpha = 0.4f) else MatrixPurple,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            
            hop.latencyMs?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MatrixPurple,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        hop.enrichment?.let { enrich ->
            Column(
                modifier = Modifier
                    .padding(start = 32.dp, top = 2.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (enrich.isPrivate) {
                    EnrichmentText(Icons.Default.Router, "Internal / Private Network")
                } else {
                    enrich.hostname?.let { EnrichmentText(Icons.Default.Language, it) }
                    
                    if ((enrich.asn != null) || (enrich.organization != null)) {
                        val orgText = buildString {
                            if (enrich.asn != null) {
                                append("[${enrich.asn}] ")
                            }
                            if (enrich.organization != null) {
                                append(enrich.organization)
                            }
                        }
                        EnrichmentText(Icons.Default.Public, orgText)
                    }
                    
                    enrich.country?.let { EnrichmentText(Icons.Default.Language, it) }
                }
            }
        }
    }
}

@Composable
private fun EnrichmentText(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(10.dp),
            tint = MatrixPurple.copy(alpha = 0.5f)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = MatrixPurple.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TracerouteHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Sticky Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MatrixPurple,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Understanding Traceroute",
                        style = MaterialTheme.typography.titleLarge,
                        color = MatrixPurple,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scrollable Content Area
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .verticalScrollbar(scrollState, MatrixPurple)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HelpSectionModern(
                            icon = Icons.AutoMirrored.Filled.HelpOutline,
                            title = "What is it?",
                            content = "Traceroute is a tool that shows the path (route) a packet takes across the internet from your device to the target. It is used for troubleshooting connection gaps and seeing how many networks (Hops) you pass through."
                        )
                        HelpSectionModern(
                            icon = Icons.Default.Router,
                            title = "What is a 'Hop'?",
                            content = "Each 'hop' represents a router or network device the data passes through. The first hop is almost always your local router or default gateway (the device you connect to via Wi-Fi or cable)."
                        )
                        HelpSectionModern(
                            icon = Icons.Default.Timer,
                            title = "How it works (TTL)",
                            content = "Traceroute sends packets with a 'Time To Live' (TTL). As the packet hits a router, the TTL drops. When it hits zero, the router stops it and sends back a 'Time Exceeded' message. This identifies that router."
                        )
                        HelpSectionModern(
                            icon = Icons.Default.Speed,
                            title = "Reading Latency (ms)",
                            content = "The millisecond (ms) values show how long it took to get a response from that specific hop. Higher values usually mean the hop is physically further away or the network segment is congested."
                        )
                        HelpSectionModern(
                            icon = Icons.Default.Warning,
                            title = "What do * * * mean?",
                            content = "Timeouts (*) happen when a router doesn't send a response back. This doesn't always mean a problem; many routers ignore traceroute requests for security or performance reasons."
                        )
                        HelpSectionModern(
                            icon = Icons.Default.Description,
                            title = "Practical Notes",
                            content = "• The path shown might differ from real app traffic (which can take shortcuts).\n• Traceroute is mainly for path visibility and pinpointing where a connection might be failing."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sticky Footer
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatrixPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun HelpSectionModern(
    icon: ImageVector,
    title: String,
    content: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MatrixPurple,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MatrixPurple,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = Color.LightGray
                )
            )
        }
    }
}

@Composable
private fun WhoisReportCard(
    report: WhoisReport,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    var showHelp by remember { mutableStateOf(false) }

    if (showHelp) {
        WhoisHelpDialog(onDismiss = { showHelp = false })
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MatrixRedDark
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onToggle() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WHOIS / RDAP REPORT",
                        style = MaterialTheme.typography.labelLarge,
                        color = MatrixRed
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MatrixRed
                    )
                }

                IconButton(
                    onClick = { showHelp = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "WHOIS Help",
                        tint = MatrixRed.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                if (report.isPrivate) {
                    Text(
                        "Private/local IP address. Global WHOIS/RDAP registration data is not applicable.",
                        color = MatrixRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (report.errorMessage != null) {
                    Text(
                        "Error: ${report.errorMessage}",
                        color = MatrixRed,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (report.type == TargetType.DOMAIN) {
                    DomainReportContent(report)
                } else {
                    GenericReportContent(report)
                }

                // Raw Section
                if (report.rawData != null) {
                    var showRaw by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { showRaw = !showRaw },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (showRaw) "HIDE RAW DATA" else "VIEW RAW DATA", color = MatrixRed, style = MaterialTheme.typography.labelSmall)
                    }
                    if (showRaw) {
                        Text(
                            text = report.rawData,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            ),
                            color = MatrixRed,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainReportContent(report: WhoisReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Summary
        Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
        report.summary.forEach { (key, value) ->
            if (key !in listOf("Created", "Updated", "Expires")) {
                WhoisDetailItem(key.uppercase(), value)
            }
        }

        // Registrar
        val registrar = report.entities.find { it.roles.contains("registrar") }
        if (registrar != null) {
            Text("REGISTRAR", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            WhoisDetailItem("NAME", registrar.name ?: registrar.organization ?: "Redacted by registry/registrar")
            registrar.handle?.let { WhoisDetailItem("IANA ID / HANDLE", it) }
            registrar.contactUri?.let { WhoisDetailItem("CONTACT", it) }
        }

        // Name Servers
        if (report.nameservers.isNotEmpty()) {
            Text("NAME SERVERS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            report.nameservers.forEach { ns ->
                Text("• $ns", style = MaterialTheme.typography.bodyMedium, color = MatrixRed)
            }
        } else {
            Text("NAME SERVERS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            Text("Not provided in RDAP response", style = MaterialTheme.typography.bodyMedium, color = MatrixRed.copy(alpha = 0.5f))
        }

        // Status
        if (report.status.isNotEmpty()) {
            Text("STATUS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            report.status.forEach { status ->
                Text("• $status", style = MaterialTheme.typography.bodyMedium, color = MatrixRed)
            }
        }

        // Dates
        Text("DATES", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
        report.summary["Created"]?.let { WhoisDetailItem("REGISTRATION", it) }
        report.summary["Updated"]?.let { WhoisDetailItem("LAST CHANGED", it) }
        report.summary["Expires"]?.let { WhoisDetailItem("EXPIRATION", it) }
        
        // Other events not in summary
        report.events.forEach { event ->
            if (event.action.lowercase() !in listOf("registration", "created", "last changed", "updated", "expiration", "expiry")) {
                WhoisDetailItem(event.action.replace("_", " ").uppercase(), event.date)
            }
        }

        // Entities / Contacts
        val nonRegistrarEntities = report.entities.filter { !it.roles.contains("registrar") }
        if (nonRegistrarEntities.isNotEmpty()) {
            Text("ENTITIES / CONTACTS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            nonRegistrarEntities.forEach { entity ->
                val roles = entity.roles.joinToString(", ").uppercase()
                val info = buildString {
                    append(entity.name ?: "Redacted by registry/registrar")
                    entity.organization?.let { append("\nOrg: $it") }
                    entity.email?.let { append("\nEmail: $it") }
                    entity.phone?.let { append("\nTel: $it") }
                    entity.address?.let { append("\nAddr: $it") }
                    entity.contactUri?.let { append("\nLink: $it") }
                }
                WhoisDetailItem(roles.ifBlank { "ENTITY" }, info)
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun GenericReportContent(report: WhoisReport) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (report.summary.isNotEmpty()) {
            Text("SUMMARY", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            report.summary.forEach { (key, value) ->
                WhoisDetailItem(key.uppercase(), value)
            }
        }

        if (report.entities.isNotEmpty()) {
            Text("ENTITIES / CONTACTS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            report.entities.forEach { entity ->
                val roles = entity.roles.joinToString(", ").uppercase()
                val info = buildString {
                    append(entity.name ?: "Redacted by registry/registrar")
                    entity.organization?.let { append("\n$it") }
                    entity.email?.let { append("\n$it") }
                    entity.address?.let { append("\n$it") }
                }
                WhoisDetailItem(roles.ifBlank { "ENTITY" }, info.ifBlank { entity.handle ?: "Unknown" })
            }
        }

        if (report.events.isNotEmpty()) {
            Text("REGISTRATION EVENTS", style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.7f))
            report.events.forEach { event ->
                WhoisDetailItem(event.action.replace("_", " ").uppercase(), event.date)
            }
        }
    }
}

@Composable
private fun WhoisHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Sticky Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MatrixRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "WHOIS & RDAP Guide",
                        style = MaterialTheme.typography.titleLarge,
                        color = MatrixRed,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Scrollable Content Area
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(scrollState)
                            .verticalScrollbar(scrollState, MatrixRed)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        HelpSectionModernRed(
                            icon = Icons.Default.QuestionMark,
                            title = "What is this report?",
                            content = "This report shows registration and ownership data for domains, IP addresses, or ASNs. It tells you who is responsible for a particular part of the internet."
                        )
                        HelpSectionModernRed(
                            icon = Icons.Default.History,
                            title = "WHOIS vs. RDAP",
                            content = "WHOIS is the classic way to look up this info. RDAP is its modern successor; it provides data in a more structured and secure format, making it easier to read and parse."
                        )
                        HelpSectionModernRed(
                            icon = Icons.Default.ContactPage,
                            title = "What can I find here?",
                            content = "• Ownership context: Who owns the network.\n• Registry Info: Which organization allocated the IP.\n• Contacts: Names or emails of technical/admin contacts.\n• Status & Dates: When the record was created or updated."
                        )
                        HelpSectionModernRed(
                            icon = Icons.Default.LocationOn,
                            title = "Location vs. Registration",
                            content = "The 'Country' shown is where the organization is registered. It does NOT guarantee the physical location of the server. For example, a US company might have servers in Europe."
                        )
                        HelpSectionModernRed(
                            icon = Icons.Default.Lock,
                            title = "Private & Local IPs",
                            content = "Private or local IP addresses (like 192.168.x.x) do not have public WHOIS/RDAP records because they are only used within your own private network."
                        )
                        HelpSectionModernRed(
                            icon = Icons.Default.Business,
                            title = "Limits & Redaction",
                            content = "Due to privacy laws like GDPR, some ownership data (like personal names or emails) may be 'redacted' or hidden by the registrar."
                        )

                        Text(
                            text = "Warning: Do not interpret this as a live traffic path tool or as an exact server location.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MatrixRed.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Sticky Footer
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MatrixRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(
                        text = "Got it",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Modifier.verticalScrollbar(
    scrollState: ScrollState,
    color: Color,
    width: Dp = 3.dp
): Modifier = this.drawWithContent {
    drawContent()
    if (scrollState.maxValue > 0) {
        val viewPortHeight = this.size.height
        val maxValue = scrollState.maxValue.toFloat()
        val scrollValue = scrollState.value.toFloat()
        
        // Calculate the height of the thumb relative to the content length
        // but capped to prevent it from occupying most of the screen when content is short.
        val contentHeight = viewPortHeight + maxValue
        val proportionalHeight = (viewPortHeight / contentHeight) * viewPortHeight
        val finalHeight = proportionalHeight.coerceIn(32.dp.toPx(), viewPortHeight * 0.2f)
        
        // Offset calculation: maps scroll progress (0..maxValue) to (0..maxAvailableTrack)
        val maxOffset = viewPortHeight - finalHeight
        val scrollbarOffset = (scrollValue / maxValue) * maxOffset

        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width - width.toPx(), scrollbarOffset),
            size = Size(width.toPx(), finalHeight),
            cornerRadius = CornerRadius(width.toPx() / 2, width.toPx() / 2),
            alpha = 0.7f
        )
    }
}

@Composable
private fun HelpSectionModernRed(
    icon: ImageVector,
    title: String,
    content: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MatrixRed,
            modifier = Modifier
                .size(24.dp)
                .padding(top = 2.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MatrixRed,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 22.sp,
                    color = Color.LightGray
                )
            )
        }
    }
}

@Composable
private fun WhoisDetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MatrixRed.copy(alpha = 0.5f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MatrixRed)
    }
}

@Composable
private fun PortResultCard(
    result: PortProbeResult,
    onOpenBrowser: () -> Unit,
    onSshClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PORT ${result.port}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    result.serviceName?.let { name ->
                        Text(
                            text = "($name)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    IconButton(
                        onClick = onInfoClick,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Port Information",
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Text(
                    text = "Status: ${result.status.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.status == PortStatus.OPEN) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }

            Row {
                if (result.port == 22) {
                    IconButton(onClick = onSshClick) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Open SSH",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if ((result.port == 80) || (result.port == 443) || (result.port == 8080) || (result.port == 8443)) {
                    IconButton(onClick = onOpenBrowser) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Open in browser",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
