package com.brutiful.netprobe.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.brutiful.netprobe.model.AppTrafficStats
import com.brutiful.netprobe.network.ConnectionTracker
import com.brutiful.netprobe.util.NetworkUtils
import com.brutiful.netprobe.viewmodel.LiveConnectionsViewModel

@Composable
fun LiveTrafficScreen(
    viewModel: LiveConnectionsViewModel,
    isVpnActive: Boolean,
    onToggleVpn: () -> Unit,
    onAppClick: (AppTrafficStats) -> Unit,
    onShowConnections: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appStats by viewModel.appTrafficStats.collectAsState()
    val totalUp = appStats.sumOf { it.sentBytes }
    val totalDown = appStats.sumOf { it.receivedBytes }
    val activeApps = appStats.count { it.activeConnections > 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App Traffic Monitor",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(onClick = onShowConnections) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show All Connections")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // VPN Control Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isVpnActive) MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isVpnActive) "Monitoring Active" else "Monitor Ready",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isVpnActive) "Traffic is being analyzed locally" 
                                   else "Start monitor to see app traffic",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(
                        checked = isVpnActive,
                        onCheckedChange = { onToggleVpn() }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Stats
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StatCard(
                label = "Active Apps",
                value = activeApps.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Total Up",
                value = NetworkUtils.formatBytes(totalUp),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Total Down",
                value = NetworkUtils.formatBytes(totalDown),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Applications",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            items(appStats, key = { it.uid }) { stats ->
                AppTrafficRow(
                    stats = stats,
                    onClick = { onAppClick(stats) }
                )
            }

            if (appStats.isEmpty() && isVpnActive) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for app traffic...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun AppTrafficRow(
    stats: AppTrafficStats,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = ConnectionTracker.getAppIcon(stats.packageName)
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (stats.appLabel == "Resolving...") "Resolving..." 
                           else if (stats.appLabel == "Unknown app" && stats.resolutionReason != null) "Unknown (${stats.resolutionReason})"
                           else stats.appLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                val subText = when {
                    stats.packageNames.size > 1 -> "Shared (${stats.packageNames.size}): ${stats.packageNames.joinToString(", ")}"
                    stats.packageName != null && stats.packageName != "unknown" -> stats.packageName
                    stats.uid != -1 -> "UID ${stats.uid}"
                    else -> "Unknown source"
                }

                Text(
                    text = subText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "↑ ${NetworkUtils.formatBytes(stats.sentBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "↓ ${NetworkUtils.formatBytes(stats.receivedBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "${stats.activeConnections} active flows",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}