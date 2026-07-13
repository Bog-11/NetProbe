package com.example.netprobe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.netprobe.model.NetworkOverview
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape

@Composable
fun NetworkOverviewScreen(
    state: NetworkOverview,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { HeaderBlock() }
            item { StatusCard(state) }

            item { SectionTitle("Connection details") }
            item { InfoCard("Local IP", state.primaryIp ?: "Unknown", technical = true) }
            item { InfoCard("Transport", state.transport) }
            item { InfoCard("Wi‑Fi name", state.wifiName ?: "Not available yet") }
            item {
                InfoCard(
                    "Wi‑Fi permission",
                    if (state.wifiPermissionGranted) "Granted" else "Not granted"
                )
            }
            item {
                InfoCard(
                    "Internet access",
                    if (state.isValidated) "Validated" else "Not validated"
                )
            }
            item {
                InfoCard(
                    "Network cost",
                    if (state.isMetered) "Metered" else "Unmetered"
                )
            }
            item { InfoCard("Interface", state.interfaceName ?: "Unknown", technical = true) }

            item { SectionTitle("DNS servers") }
            if (state.dnsServers.isEmpty()) {
                item { InfoCard("DNS", "No DNS servers available", technical = true) }
            } else {
                items(state.dnsServers) { dns ->
                    InfoCard("DNS", dns, technical = true)
                }
            }

            item { SectionTitle("Local network ranges") }
            if (state.localAddresses.isEmpty()) {
                item { InfoCard("Range", "No local network detected", technical = true) }
            } else {
                items(state.localAddresses) { local ->
                    InfoCard("Range", local.getNetworkRange(), technical = true)
                }
            }
        }
    }
}

@Composable
private fun HeaderBlock() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "NetProbe",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Live network status from the phone",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        )
    }
}

@Composable
private fun StatusCard(state: NetworkOverview) {
    val online = state.isConnected
    val statusText = if (online) "CONNECTED TO THE GRID" else "NO LIVE LINK"
    val detailText = if (online) "The phone has an active network connection" else "No active network connection detected"
    val dotColor = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "STATUS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.tertiary
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(12.dp)
                        .background(
                            color = dotColor,
                            shape = CircleShape
                        )
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
fun InfoCard(
    label: String,
    value: String,
    technical: Boolean = false,
    action: (@Composable () -> Unit)? = null
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = if (technical) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (action != null) {
                androidx.compose.foundation.layout.Box(modifier = Modifier.padding(start = 8.dp)) {
                    action()
                }
            }
        }
    }
}
