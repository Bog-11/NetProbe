package com.brutiful.netprobe.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.SshConnectionState
import com.brutiful.netprobe.model.TerminalLine

@Composable
fun RemoteShellScreen(
    state: SshConnectionState,
    terminalLines: List<TerminalLine>,
    isExecuting: Boolean,
    host: String,
    onHostChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    targetDevice: DiscoveredDevice?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSendCommand: (String) -> Unit,
    onClearOutput: () -> Unit,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var password by remember { mutableStateOf("") }
    var customCommand by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeaderBlock(
                    title = "Remote Shell",
                    subtitle = "Interactive SSH Terminal"
                )
            }

            if (targetDevice != null) {
                item {
                    DeviceInfoPanel(device = targetDevice)
                }
            }

            item {
                SshConnectionPanel(
                    state = state,
                    host = host,
                    onHostChange = onHostChange,
                    port = port,
                    onPortChange = onPortChange,
                    username = username,
                    onUsernameChange = onUsernameChange,
                    password = password,
                    onPasswordChange = { 
                        password = it
                        onPasswordChange(it)
                    },
                    showPassword = showPassword,
                    onTogglePassword = { showPassword = !showPassword },
                    onConnect = onConnect,
                    onDisconnect = onDisconnect,
                    isExecuting = isExecuting
                )
            }

            item {
                TerminalCard(
                    lines = terminalLines,
                    listState = listState,
                    command = customCommand,
                    state = state,
                    onCommandChange = { customCommand = it },
                    onRun = {
                        onSendCommand(customCommand)
                        customCommand = ""
                    },
                    onClear = onClearOutput,
                    onResize = onResize
                )
            }

            item {
                QuickDiagnosticsPanel(
                    onRun = onSendCommand,
                    enabled = state is SshConnectionState.Connected
                )
            }

            item {
                Spacer(modifier = Modifier.height(120.dp))
            }
        }
    }

    // Host Key Dialog
    if (state is SshConnectionState.HostKeyVerificationRequired) {
        AlertDialog(
            onDismissRequest = { state.onReject() },
            title = { Text("Verify Host Key") },
            text = {
                Column {
                    Text("The authenticity of host '${state.hostname}' can't be established.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Type: ${state.keyType}", style = MaterialTheme.typography.bodySmall)
                    Text("Fingerprint: ${state.fingerprint}", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Are you sure you want to continue connecting?")
                }
            },
            confirmButton = {
                Button(onClick = { state.onAccept() }) {
                    Text("Yes, Trust")
                }
            },
            dismissButton = {
                TextButton(onClick = { state.onReject() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DeviceInfoPanel(device: DiscoveredDevice) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Target Device Info", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    InfoItem("Hostname", device.hostname ?: device.computedDisplayName())
                    InfoItem("IP Address", device.ipString)
                }
                Column(modifier = Modifier.weight(1f)) {
                    InfoItem("Vendor", device.vendorName ?: "Unknown")
                    InfoItem("Ports", device.openPorts.joinToString(", ").ifBlank { "None detected" })
                }
            }
        }
    }
}

@Composable
private fun InfoItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SshConnectionPanel(
    state: SshConnectionState,
    host: String, onHostChange: (String) -> Unit,
    port: String, onPortChange: (String) -> Unit,
    username: String, onUsernameChange: (String) -> Unit,
    password: String, onPasswordChange: (String) -> Unit,
    showPassword: Boolean, onTogglePassword: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    isExecuting: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "SSH Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ConnectionStateBadge(state)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = onHostChange,
                    label = { Text("Host") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = state is SshConnectionState.Disconnected || state is SshConnectionState.Error
                )
                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Port") },
                    modifier = Modifier.width(80.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = state is SshConnectionState.Disconnected || state is SshConnectionState.Error
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("User") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = state is SshConnectionState.Disconnected || state is SshConnectionState.Error
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = onTogglePassword) {
                        Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                singleLine = true,
                enabled = state is SshConnectionState.Disconnected || state is SshConnectionState.Error
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (state is SshConnectionState.Connected) {
                Button(
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.LinkOff, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disconnect")
                }
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isExecuting && host.isNotBlank() && username.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Link, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect")
                    }
                }
            }
            
            if (state is SshConnectionState.Error) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnectionStateBadge(state: SshConnectionState) {
    val (text, color) = when (state) {
        is SshConnectionState.Disconnected -> "Disconnected" to Color.Gray
        is SshConnectionState.ResolvingHost -> "Resolving Host..." to Color(0xFF673AB7)
        is SshConnectionState.Connecting -> "Connecting..." to Color(0xFF2196F3)
        is SshConnectionState.Authenticating -> "Authenticating..." to Color(0xFFFF9800)
        is SshConnectionState.OpeningShell -> "Opening Shell..." to Color(0xFF00BCD4)
        is SshConnectionState.Connected -> "Connected" to MaterialTheme.colorScheme.primary
        is SshConnectionState.Error -> "Error" to Color(0xFFF44336)
        is SshConnectionState.HostKeyVerificationRequired -> "Verifying Host..." to Color(0xFFFFEB3B)
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickDiagnosticsPanel(onRun: (String) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Quick Diagnostics")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val presets = listOf("hostname", "uptime", "ip a", "df -h", "free -h")
            presets.forEach { cmd ->
                AssistChip(
                    onClick = { onRun(cmd) },
                    label = { Text(cmd) },
                    enabled = enabled,
                    colors = AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}

@Composable
private fun TerminalCard(
    lines: List<TerminalLine>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    command: String,
    state: SshConnectionState,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit,
    onClear: () -> Unit,
    onResize: (Int, Int) -> Unit
) {
    val textMeasurer = rememberTextMeasurer()
    val terminalTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp
    )
    val density = LocalDensity.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(450.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TERMINAL",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                IconButton(onClick = onClear, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.DeleteSweep, "Clear", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val charSize = remember(textMeasurer, terminalTextStyle, density) {
                    textMeasurer.measure("W", terminalTextStyle).size
                }
                
                LaunchedEffect(constraints.maxWidth, constraints.maxHeight, charSize) {
                    val cols = (constraints.maxWidth / charSize.width).toInt().coerceAtLeast(20)
                    val rows = (constraints.maxHeight / charSize.height).toInt().coerceAtLeast(5)
                    onResize(cols, rows)
                }

                LazyColumn(
                    state = listState, 
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(lines) { line ->
                        TerminalLineItem(line)
                    }

                    item {
                        TerminalInputRow(
                            command = command,
                            state = state,
                            onCommandChange = onCommandChange,
                            onRun = onRun
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLineItem(line: TerminalLine) {
    val (text, color, prefix) = when (line) {
        is TerminalLine.SystemMessage -> Triple(line.text, Color(0xFF00BCD4), "[SYSTEM] ")
        is TerminalLine.UserCommand -> Triple(line.command, MaterialTheme.colorScheme.primary, "$ ")
        is TerminalLine.RemoteOutput -> Triple(line.text, Color.White, "")
        is TerminalLine.ErrorMessage -> Triple(line.text, Color(0xFFEF5350), "[ERROR] ")
    }

    Row(modifier = Modifier.padding(vertical = 1.dp)) {
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                color = color.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TerminalInputRow(
    command: String,
    state: SshConnectionState,
    onCommandChange: (String) -> Unit,
    onRun: () -> Unit
) {
    val isConnected = state is SshConnectionState.Connected
    val placeholder = if (isConnected) "type command..." else "connect to start session"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            text = "$ ",
            color = if (isConnected) MaterialTheme.colorScheme.primary else Color.Gray,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    color = if (isConnected) Color.White else Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Go,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onGo = { if (isConnected && command.isNotBlank()) onRun() }
                ),
                decorationBox = { innerTextField ->
                    if (command.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                    innerTextField()
                },
                enabled = isConnected
            )
        }
    }
}
