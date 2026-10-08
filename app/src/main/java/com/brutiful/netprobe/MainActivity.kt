package com.brutiful.netprobe

import android.Manifest
import android.content.Intent
import android.database.ContentObserver
import androidx.core.net.toUri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.brutiful.netprobe.model.AppTrafficStats
import com.brutiful.netprobe.viewmodel.ScanMode
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.LiveConnection
import com.brutiful.netprobe.model.ProximityTargetType
import com.brutiful.netprobe.model.TerminalLine
import com.brutiful.netprobe.network.ConnectionMonitorService
import com.brutiful.netprobe.ui.AppTrafficDetailScreen
import com.brutiful.netprobe.viewmodel.AppTrafficDetailViewModel
import com.brutiful.netprobe.viewmodel.HostProbeViewModel
import com.brutiful.netprobe.ui.DiscoveryScreen
import com.brutiful.netprobe.ui.HostProbeScreen
import com.brutiful.netprobe.ui.LiveConnectionsScreen
import com.brutiful.netprobe.ui.LiveTrafficScreen
import com.brutiful.netprobe.ui.NetworkOverviewScreen
import com.brutiful.netprobe.ui.ProximityTrackingScreen
import com.brutiful.netprobe.ui.RemoteShellScreen
import com.brutiful.netprobe.ui.TrafficDisclosureDialog
import com.brutiful.netprobe.ui.WiFiHealthScreen
import com.brutiful.netprobe.ui.theme.NetProbeTheme
import com.brutiful.netprobe.viewmodel.DiscoveryViewModel
import com.brutiful.netprobe.viewmodel.LiveConnectionsViewModel
import com.brutiful.netprobe.viewmodel.NetworkViewModel
import com.brutiful.netprobe.viewmodel.RemoteShellViewModel
import com.brutiful.netprobe.viewmodel.WiFiHealthViewModel

class MainActivity : ComponentActivity() {

    private val probeViewModel: HostProbeViewModel by viewModels()
    private val networkViewModel: NetworkViewModel by viewModels()
    private val discoveryViewModel: DiscoveryViewModel by viewModels()
    private val liveConnectionsViewModel: LiveConnectionsViewModel by viewModels()
    private val shellViewModel: RemoteShellViewModel by viewModels()
    private val wifiHealthViewModel: WiFiHealthViewModel by viewModels()

    private val _navigationRequest = MutableStateFlow<String?>(null)
    private val navigationRequest = _navigationRequest.asStateFlow()

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _navigationRequest.value = intent.getStringExtra("SCREEN")
    }

    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            applyBrightnessBoost()
        }
    }

    override fun onResume() {
        super.onResume()
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            false,
            brightnessObserver,
        )
        if (networkViewModel.uiState.value.isBrightnessBoostEnabled) {
            applyBrightnessBoost()
        }
        discoveryViewModel.updatePermissionState()
    }

    override fun onPause() {
        super.onPause()
        contentResolver.unregisterContentObserver(brightnessObserver)
    }

    private fun applyBrightnessBoost() {
        if (!networkViewModel.uiState.value.isBrightnessBoostEnabled) {
            // Restore default behavior by setting screenBrightness to -1 (use system default)
            val layoutParams = window.attributes
            layoutParams.screenBrightness = -1f
            window.attributes = layoutParams
            return
        }

        try {
            // Read current system brightness (0-255)
            val systemBrightness = Settings.System.getInt(
                contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
            )
            // Increase by 20% of the total range (255 * 0.2 = 51)
            val boostedBrightness = (systemBrightness + 51).coerceAtMost(255)
            
            val layoutParams = window.attributes
            layoutParams.screenBrightness = boostedBrightness / 255f
            window.attributes = layoutParams
        } catch (_: Exception) {
            // Fallback to a fixed high value if reading system settings fails
            val layoutParams = window.attributes
            if (layoutParams.screenBrightness < 0.8f) {
                layoutParams.screenBrightness = 0.8f
                window.attributes = layoutParams
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        _navigationRequest.value = intent.getStringExtra("SCREEN")

        setContent {
            val networkState by networkViewModel.uiState.collectAsStateWithLifecycle()
            
            NetProbeTheme(darkTheme = networkState.isDarkMode) {
                val isVpnActive by ConnectionMonitorService.isServiceRunning.collectAsStateWithLifecycle()

                val vpnLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == RESULT_OK) {
                        startVpnService()
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    if (results.values.any { it }) {
                        networkViewModel.refresh()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf<String>()
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                    }
                    
                    // Always include Bluetooth scan/connect if supported
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    
                    if (permissions.isNotEmpty()) {
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                }

                val backStack = remember { mutableStateListOf(0) }
                val currentScreen by remember { derivedStateOf { backStack.last() } }
                var selectedConnection by remember { mutableStateOf<LiveConnection?>(null) }
                var selectedApp by remember { mutableStateOf<AppTrafficStats?>(null) }
                var showTrafficDisclosure by remember { mutableStateOf(value = false) }
                var showConnectionsOnly by remember { mutableStateOf(false) }

                fun navigateTo(screen: Int) {
                    if (screen == 0) {
                        backStack.clear()
                        backStack.add(0)
                        selectedConnection = null
                        selectedApp = null
                        showConnectionsOnly = false
                        val index = backStack.indexOf(screen)
                        while (backStack.size > (index + 1)) {
                            backStack.removeAt(backStack.size - 1)
                        }
                    } else {
                        backStack.add(screen)
                    }
                }

                val navRequest by navigationRequest.collectAsStateWithLifecycle()
                LaunchedEffect(navRequest) {
                    navRequest?.let { screen ->
                        when (screen) {
                            "monitoring" -> {
                                selectedConnection = null
                                selectedApp = null
                                showConnectionsOnly = false
                                if (backStack.lastOrNull() != 3) {
                                    navigateTo(3)
                                }
                            }
                            "shell" -> {
                                if (backStack.lastOrNull() != 4) {
                                    navigateTo(4)
                                }
                            }
                        }
                        _navigationRequest.value = null
                    }
                }

                BackHandler(enabled = (backStack.size > 1) || (selectedConnection != null) || (selectedApp != null) || showConnectionsOnly) {
                    if (selectedConnection != null) {
                        selectedConnection = null
                    } else if (selectedApp != null) {
                        selectedApp = null
                    } else if (showConnectionsOnly) {
                        showConnectionsOnly = false
                    } else if (backStack.size > 1) {
                        backStack.removeAt(backStack.size - 1)
                    }
                }

                if (showTrafficDisclosure) {
                    TrafficDisclosureDialog(
                        onDismiss = { showTrafficDisclosure = false }
                    ) {
                        showTrafficDisclosure = false
                        val intent = VpnService.prepare(this@MainActivity)
                        if (intent != null) {
                            vpnLauncher.launch(intent)
                        } else {
                            startVpnService()
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == 0,
                                onClick = { navigateTo(0) },
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                label = { Text("Overview") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 5,
                                onClick = { navigateTo(5) },
                                icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                                label = { Text("Health") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 1,
                                onClick = { navigateTo(1) },
                                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                label = { Text("Discovery") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 2,
                                onClick = { navigateTo(2) },
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("Probe") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 3,
                                onClick = { navigateTo(3) },
                                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                                label = { Text("Live") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 4,
                                onClick = { navigateTo(4) },
                                icon = { Icon(Icons.Filled.Terminal, contentDescription = null) },
                                label = { Text("Shell") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .consumeWindowInsets(innerPadding)
                            .imePadding()
                    ) {
                        when (currentScreen) {
                            0 -> {
                                LaunchedEffect(Unit) {
                                    networkViewModel.startAutoRefresh()
                                }
                                val state by networkViewModel.uiState.collectAsStateWithLifecycle()

                                LaunchedEffect(state.isBrightnessBoostEnabled) {
                                    applyBrightnessBoost()
                                }

                                NetworkOverviewScreen(
                                    state = state,
                                    onRunSpeedTest = networkViewModel::runSpeedTest,
                                    onDismissSpeedTest = networkViewModel::dismissSpeedTest,
                                    onNavigateToHealthReport = { navigateTo(5) },
                                    onToggleBrightnessBoost = networkViewModel::toggleBrightnessBoost,
                                    onToggleDarkMode = networkViewModel::toggleDarkMode
                                )
                            }

                            1 -> {
                                val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
                                val networkState by networkViewModel.uiState.collectAsStateWithLifecycle()
                                DiscoveryScreen(
                                    state = discoveryState,
                                    localAddresses = networkState.localAddresses,
                                    isVpnActive = isVpnActive,
                                    isExternalVpnActive = networkState.isVpnConnected && !isVpnActive,
                                    onStartDiscovery = discoveryViewModel::startDiscovery,
                                    onStopDiscovery = discoveryViewModel::stopDiscovery,
                                    onEnrichDevices = { networkState.localAddresses.firstOrNull()?.let { discoveryViewModel.startDiscovery(it, ScanMode.EXPANDED) } },
                                    onStartBluetoothDiscovery = discoveryViewModel::startBluetoothDiscovery,
                                    onStopBluetoothDiscovery = discoveryViewModel::stopBluetoothDiscovery,
                                    onStartCombinedScan = discoveryViewModel::startCombinedScan,
                                    onSetTab = discoveryViewModel::setTab,
                                    onSetSearchQuery = discoveryViewModel::setSearchQuery,
                                    onSetSortBy = discoveryViewModel::setSortBy,
                                    onSetFilter = discoveryViewModel::setFilter,
                                    onProbeDevice = { device ->
                                        probeViewModel.updateIpAddress(device.ipString)
                                        probeViewModel.updateDeviceLabel(device.computedDisplayName())
                                        probeViewModel.startQuickProbe()
                                        navigateTo(2)
                                    },
                                    onSshDevice = { device ->
                                        shellViewModel.prefill(device)
                                        navigateTo(4)
                                    },
                                    onStartLiveTracking = { _, _, _ ->
                                        navigateTo(6)
                                    }
                                )
                            }

                            2 -> {
                                val state by probeViewModel.uiState.collectAsStateWithLifecycle()
                                HostProbeScreen(
                                    state = state,
                                    onIpChanged = probeViewModel::updateIpAddress,
                                    onStartAggressiveProbe = probeViewModel::startAggressiveProbe,
                                    onStartQuickProbe = probeViewModel::startQuickProbe,
                                    onCancelProbe = probeViewModel::cancelAggressiveProbe,
                                    onToggleWhois = probeViewModel::toggleWhois,
                                    onToggleTraceroute = probeViewModel::toggleTraceroute,
                                    onOpenBrowser = { url ->
                                        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                                        startActivity(intent)
                                    },
                                    onSshClick = { ip, port ->
                                        shellViewModel.prefill(ip, port)
                                        navigateTo(4)
                                    }
                                )
                            }

                            3 -> {
                                if (selectedConnection != null) {
                                    val detailViewModel: AppTrafficDetailViewModel = viewModel()
                                    LaunchedEffect(selectedConnection) {
                                        detailViewModel.setConnection(selectedConnection!!.id)
                                    }
                                    val packets by detailViewModel.packets.collectAsState()
                                    AppTrafficDetailScreen(
                                        connection = selectedConnection!!,
                                        packets = packets,
                                        onBack = { selectedConnection = null }
                                    ) { connection ->
                                        probeViewModel.updateIpAddress(connection.destinationIp)
                                        probeViewModel.updateDeviceLabel(connection.appLabel)
                                        probeViewModel.setTargetPort(connection.destinationPort)
                                        navigateTo(2)
                                    }
                                } else if (selectedApp != null || showConnectionsOnly) {
                                    val networkState by networkViewModel.uiState.collectAsStateWithLifecycle()
                                    LiveConnectionsScreen(
                                        viewModel = liveConnectionsViewModel,
                                        isVpnActive = isVpnActive,
                                        primaryIp = networkState.primaryIp,
                                        onToggleVpn = {
                                            if (isVpnActive) {
                                                stopVpnService()
                                            } else {
                                                showTrafficDisclosure = true
                                            }
                                        },
                                        onConnectionClick = { selectedConnection = it },
                                        onProbeClick = { connection ->
                                            probeViewModel.updateIpAddress(connection.destinationIp)
                                            probeViewModel.updateDeviceLabel(connection.appLabel)
                                            probeViewModel.setTargetPort(connection.destinationPort)
                                            navigateTo(2)
                                        },
                                        filterUid = selectedApp?.uid,
                                        onBack = {
                                            selectedApp = null
                                            showConnectionsOnly = false
                                        },
                                        title = if (showConnectionsOnly) "All Connections" else selectedApp?.appLabel
                                    )
                                } else {
                                    LiveTrafficScreen(
                                        viewModel = liveConnectionsViewModel,
                                        isVpnActive = isVpnActive,
                                        onToggleVpn = {
                                            if (isVpnActive) {
                                                stopVpnService()
                                            } else {
                                                showTrafficDisclosure = true
                                            }
                                        },
                                        onAppClick = { selectedApp = it },
                                        onShowConnections = { showConnectionsOnly = true }
                                    )
                                }
                            }

                            4 -> {
                                val shellState by shellViewModel.connectionState.collectAsStateWithLifecycle()
                                val terminalLines by shellViewModel.terminalLines.collectAsStateWithLifecycle()
                                val targetDevice by shellViewModel.targetDevice.collectAsStateWithLifecycle()
                                val isExecuting by shellViewModel.isExecuting.collectAsStateWithLifecycle()
                                val shellHost by shellViewModel.host.collectAsStateWithLifecycle()
                                val shellPort by shellViewModel.port.collectAsStateWithLifecycle()
                                val shellUsername by shellViewModel.username.collectAsStateWithLifecycle()

                                RemoteShellScreen(
                                    state = shellState,
                                    terminalLines = terminalLines,
                                    isExecuting = isExecuting,
                                    host = shellHost,
                                    onHostChange = shellViewModel::updateHost,
                                    port = shellPort,
                                    onPortChange = shellViewModel::updatePort,
                                    username = shellUsername,
                                    onUsernameChange = shellViewModel::updateUsername,
                                    onPasswordChange = shellViewModel::updatePassword,
                                    targetDevice = targetDevice,
                                    onConnect = shellViewModel::connect,
                                    onDisconnect = shellViewModel::disconnect,
                                    onSendCommand = shellViewModel::sendCommand,
                                    onClearOutput = shellViewModel::clearOutput,
                                    onResize = shellViewModel::onResize
                                )
                            }

                            5 -> {
                                val wifiState by wifiHealthViewModel.uiState.collectAsStateWithLifecycle()
                                WiFiHealthScreen(
                                    state = wifiState,
                                    onStartStabilityTest = wifiHealthViewModel::runStabilityTest,
                                    onCancelStabilityTest = wifiHealthViewModel::cancelStabilityTest,
                                    onStartScan = wifiHealthViewModel::startWiFiScan,
                                    onStartThroughputTest = wifiHealthViewModel::runThroughputTest,
                                    onCancelThroughputTest = wifiHealthViewModel::cancelThroughputTest,
                                    onStartWifiProximityTracking = { _, _ ->
                                        navigateTo(6)
                                    },
                                    onPrepareAiPrompt = wifiHealthViewModel::prepareAiPrompt,
                                    onConsumePromptCopiedEvent = wifiHealthViewModel::consumePromptCopiedEvent
                                )
                            }

                            6 -> {
                                val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
                                ProximityTrackingScreen(
                                    state = discoveryState,
                                    onStopTracking = {},
                                    onSetProfile = {},
                                    onToggleDistance = {},
                                    onStartCalibration = {},
                                    onDeleteCalibration = {},
                                    onHandleRoaming = {},
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startVpnService() {
        startService(Intent(this, ConnectionMonitorService::class.java))
    }

    private fun stopVpnService() {
        startService(Intent(this, ConnectionMonitorService::class.java).apply { action = "STOP" })
    }
}
