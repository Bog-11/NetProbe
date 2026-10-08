package com.brutiful.netprobe.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brutiful.netprobe.model.*
import com.brutiful.netprobe.viewmodel.DiscoveryUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProximityTrackingScreen(
    state: DiscoveryUiState,
    onStopTracking: () -> Unit,
    onSetProfile: (EnvironmentProfile) -> Unit,
    onToggleDistance: (Boolean) -> Unit,
    onStartCalibration: () -> Unit,
    onDeleteCalibration: (String) -> Unit,
    onHandleRoaming: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session = state.liveTrackingSession ?: return
    var showInfoSheet by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Lifecycle ON_STOP trigger handle to avoid background scans
    DisposableEffect(lifecycleOwner, session.targetId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                onStopTracking()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Capture visual signal points bounds for 30-60s lightweight history graph
    val signalHistory = remember { mutableStateListOf<Float>() }
    LaunchedEffect(session.smoothedRssi, session.isStale) {
        if (session.isStale) {
            signalHistory.clear()
        } else {
            session.smoothedRssi?.let {
                signalHistory.add(it.toFloat())
                if (signalHistory.size > 50) {
                    signalHistory.removeAt(0)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live signal tracking") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Active State Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${session.targetType.name.replace("_", " ")} · ${session.targetId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (session.state == LiveTrackingState.TRACKING) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text("Live tracking active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    } else {
                        Button(
                            onClick = onStopTracking,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Stop")
                        }
                    }
                }
            }

            // Roaming Handle State
            if (session.state == LiveTrackingState.ACCESS_POINT_CHANGED) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Connected access point changed.", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text("The phone has roamed to a different BSSID. Tracking metrics for the old AP are stopped.", style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onHandleRoaming(true) }) {
                                Text("Track New Access Point")
                            }
                            OutlinedButton(onClick = { onHandleRoaming(false) }) {
                                Text("Stop")
                            }
                        }
                    }
                }
            }

            // Stale Notification View
            if (session.state == LiveTrackingState.SIGNAL_STALE || session.isStale) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.WifiOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = "Live tracking paused until the device advertises again. Last known signal: ${session.rawRssi ?: "N/A"} dBm.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Core Measurement Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Signal estimate", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (session.rawRssi != null) "${session.rawRssi} dBm" else "--",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Trend: ${session.trend.name.replace("_", " ").lowercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Proximity", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Default.Info, contentDescription = "Info", modifier = Modifier.size(14.dp).clickable { showInfoSheet = true }, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = session.proximityBand.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = session.proximityBand.typicalRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Approximate Distance Card Option
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show approximate distance range", fontWeight = FontWeight.SemiBold)
                        Switch(checked = state.showApproximateDistance, onCheckedChange = onToggleDistance)
                    }

                    if (state.showApproximateDistance) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Approximate distance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = session.distanceEstimate?.displayLabel ?: "Distance estimate unavailable",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Confidence", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(12.dp).clickable { showInfoSheet = true }, tint = MaterialTheme.colorScheme.primary)
                                }
                                Text(
                                    text = session.confidence.name,
                                    fontWeight = FontWeight.Bold,
                                    color = when (session.confidence) {
                                        ProximityConfidence.MEDIUM -> Color(0xFF4CAF50)
                                        ProximityConfidence.LOW -> Color(0xFFFF9800)
                                        else -> Color(0xFFF44336)
                                    }
                                )
                            }
                        }
                        Text(
                            text = session.confidenceDetails,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Real-time Linear History Visual Graph Block
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Signal history (Recent 30–60s)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (signalHistory.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("Awaiting live signals...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Canvas(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                            val width = size.width
                            val height = size.height
                            val maxVal = -30f
                            val minVal = -95f
                            val range = maxVal - minVal

                            val points = signalHistory.toList()
                            val path = Path()
                            val stepX = width / (points.size.coerceAtLeast(2) - 1)

                            points.forEachIndexed { index, rssi ->
                                val x = index * stepX
                                val normalizedRssi = ((rssi - minVal) / range).coerceIn(0f, 1f)
                                val y = height - (normalizedRssi * height)
                                if (index == 0) {
                                    path.moveTo(x, y)
                                } else {
                                    path.lineTo(x, y)
                                }
                            }
                            drawPath(path, color = Color(0xFF2196F3), style = Stroke(width = 4f))
                        }
                    }
                }
            }

            // Calibration & Environment Profiles Section Wizards
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Environment Profile & Calibration", fontWeight = FontWeight.Bold)
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EnvironmentProfile.values().forEach { profile ->
                            FilterChip(
                                selected = state.environmentProfile == profile,
                                onClick = { onSetProfile(profile) },
                                label = { Text(profile.name.replace("_", " ").lowercase(), fontSize = 11.sp) }
                            )
                        }
                    }

                    HorizontalDivider()

                    if (state.savedCalibrations.containsKey(session.targetId)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Custom 1-meter calibration saved", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Median RSSI: ${state.savedCalibrations[session.targetId]?.medianRssiAtOneMeter} dBm", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { onDeleteCalibration(session.targetId) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Calibration", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Calibrate at 1 metre", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text("Stand approximately one metre from the target device and keep the phone perfectly still to map local path loss factors.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            
                            if (state.isCalibrating) {
                                LinearProgressIndicator(progress = { state.calibrationProgress }, modifier = Modifier.fillMaxWidth())
                                Text("Collecting baseline signal data samples...", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Button(
                                    onClick = onStartCalibration,
                                    enabled = session.targetId != "02:00:00:00:00:00" && !session.isRandomizedBle
                                ) {
                                    Text("Start 1-meter Calibration")
                                }
                                if (session.isRandomizedBle) {
                                    Text("Calibration unavailable for randomized private Bluetooth addresses.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        AlertDialog(
            onDismissRequest = { showInfoSheet = false },
            title = { Text("About live signal estimates") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "NetProbe estimates proximity from radio signal strength. This can help you see whether a device signal is getting stronger or weaker while you move, but it cannot measure exact distance like a tape measure.\n\n" +
                        "Live tracking shows how the signal changes at this phone. A stronger signal can mean the device is closer, but it can also be caused by fewer obstacles, a different phone angle, or reflected radio waves.\n\n" +
                        "Walls, floors, ceilings, furniture, glass, mirrors, metal, appliances, water pipes, people, and the structure of the building can weaken or reflect Wi‑Fi and Bluetooth signals.\n\n" +
                        "Signals can bounce around rooms and arrive at the phone by multiple paths. This can make a device appear closer or farther away even when it has not moved.\n\n" +
                        "Bluetooth and Wi‑Fi devices also use different transmit power levels. Some devices reduce power to save battery, advertise only occasionally, or rotate their Bluetooth address for privacy.\n\n" +
                        "Use the live trend as a guide:\n" +
                        "· stronger signal usually suggests the phone or device is closer\n" +
                        "· weaker signal usually suggests more distance or more obstruction\n" +
                        "· a sudden change does not prove the device moved\n" +
                        "· use the estimate as guidance, not an exact measurement.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    HorizontalDivider()
                    Text("What improves accuracy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Text("· Calibrate at 1 metre from the target\n· Keep phone and target in the same room where possible\n· Hold the phone consistently and avoid blocking it with your body\n· Observe several seconds of signal history, not one reading\n· Choose the correct environment profile\n· Use \"Find device\" trend mode rather than relying on metres", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showInfoSheet = false }) {
                    Text("Dismiss")
                }
            }
        )
    }
}
