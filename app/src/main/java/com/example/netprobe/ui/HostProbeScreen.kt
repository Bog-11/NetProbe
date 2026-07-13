package com.example.netprobe.ui

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.example.netprobe.probe.HostProbeUiState
import com.example.netprobe.probe.PortProbeResult
import com.example.netprobe.probe.PortStatus
import com.example.netprobe.ui.theme.MatrixPurple
import com.example.netprobe.ui.theme.MatrixPurpleDark
import com.example.netprobe.ui.theme.MatrixRed
import com.example.netprobe.ui.theme.MatrixRedDark

@Composable
fun HostProbeScreen(
    state: HostProbeUiState,
    onIpChanged: (String) -> Unit,
    onProbeClick: () -> Unit,
    onToggleWhois: () -> Unit,
    onToggleTraceroute: () -> Unit,
    onOpenBrowser: (String) -> Unit,
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
                }
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Aggressive Host Probe",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Text(
                    text = "Deep scan ports 1-1024 + common services",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                OutlinedTextField(
                    value = state.ipAddress,
                    onValueChange = onIpChanged,
                    label = { Text("Target IP or Domain") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onProbeClick()
                    },
                    enabled = !state.isProbing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (state.isProbing) "Scanning..." else "Start Deep Scan")
                }
            }

            if (state.isProbing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Scanning ports... ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            state.errorMessage?.let { message ->
                item {
                    InfoCard("Error", message)
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
                        isExpanded = state.isTracerouteExpanded,
                        isLoading = state.isTracerouteLoading,
                        onToggle = onToggleTraceroute
                    )
                }
            }

            state.whoisData?.let { data ->
                item {
                    WhoisCard(
                        data = data,
                        isExpanded = state.isWhoisExpanded,
                        onToggle = onToggleWhois
                    )
                }
            }

            item {
                SectionTitle("Open Ports Found (${state.reachablePorts.size})")
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
    isExpanded: Boolean,
    isLoading: Boolean,
    onToggle: () -> Unit
) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MatrixPurple,
                        trackColor = MatrixPurple.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = data,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MatrixPurple
                    ),
                    color = MatrixPurple
                )
            }
        }
    }
}

@Composable
private fun WhoisCard(
    data: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "WHOIS REPORT",
                    style = MaterialTheme.typography.labelLarge,
                    color = MatrixRed
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MatrixRed
                )
            }
            if (isExpanded) {
                Text(
                    text = data,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = MatrixRed
                    ),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PortResultCard(
    result: PortProbeResult,
    onOpenBrowser: () -> Unit,
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
