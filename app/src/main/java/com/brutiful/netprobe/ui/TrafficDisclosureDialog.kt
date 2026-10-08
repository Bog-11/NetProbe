package com.brutiful.netprobe.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.brutiful.netprobe.ui.theme.MatrixPurple

@Composable
fun TrafficDisclosureDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MatrixPurple) },
        title = { 
            Text(
                text = "Network Traffic Monitoring",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Text(
                "NetProbe uses a local VPN to observe network traffic from apps on this device.\n\n" +
                "• All analysis is performed locally on this phone.\n" +
                "• No traffic data is sent to a remote VPN server or external third party.\n" +
                "• This allows NetProbe to show which apps are making connections, their destinations, and bytes transferred.\n" +
                "• You can stop monitoring at any time from the app or the system notification.\n\n" +
                "Do you want to enable the local VPN monitor?"
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MatrixPurple)
            ) {
                Text("Enable Monitor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}