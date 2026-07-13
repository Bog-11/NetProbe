package com.example.netprobe.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netprobe.model.DeviceType
import com.example.netprobe.model.LocalAddress
import com.example.netprobe.viewmodel.DiscoveryUiState

@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    localAddresses: List<LocalAddress>,
    onStartDiscovery: (LocalAddress) -> Unit,
    onProbeDevice: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = "Network Discovery",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (state.isScanning) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "Deep scanning ${state.currentSubnet}... ${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            if (localAddresses.isEmpty()) {
                Text("No local network detected.")
            } else {
                localAddresses.forEach { local ->
                    Button(
                        onClick = { onStartDiscovery(local) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Deep Scan ${local.getNetworkRange()}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        SectionTitle("Discovered Devices (${state.devices.size})")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.devices) { device ->
                InfoCard(
                    label = device.hostName ?: "Unknown Device",
                    value = "${device.ipAddress}${if (device.modelName != null) " • ${device.modelName}" else ""}",
                    technical = true,
                    action = {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            DeviceIcon(device.deviceType)
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onProbeDevice(device.ipAddress) }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Probe",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                )
            }

            if (state.devices.isEmpty() && !state.isScanning) {
                item {
                    Text(
                        text = "No devices found yet. Start a deep scan to identify devices on your network.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceIcon(type: DeviceType) {
    val (icon, tint) = when (type) {
        DeviceType.IPHONE -> Icons.Default.Call to MaterialTheme.colorScheme.primary
        DeviceType.MAC -> Icons.Default.Home to MaterialTheme.colorScheme.secondary
        DeviceType.WINDOWS -> Icons.Default.Build to MaterialTheme.colorScheme.tertiary
        DeviceType.ANDROID -> Icons.Default.Person to MaterialTheme.colorScheme.primary
        DeviceType.LINUX -> Icons.AutoMirrored.Filled.List to MaterialTheme.colorScheme.onSurfaceVariant
        DeviceType.PRINTER -> Icons.Default.Star to MaterialTheme.colorScheme.secondary
        DeviceType.IOT -> Icons.Default.Home to MaterialTheme.colorScheme.primary
        DeviceType.UNKNOWN -> Icons.Default.Person to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Icon(
        imageVector = icon,
        contentDescription = type.name,
        tint = tint,
        modifier = Modifier.size(24.dp)
    )
}
