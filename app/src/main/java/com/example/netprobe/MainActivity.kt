package com.example.netprobe

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.netprobe.probe.HostProbeViewModel
import com.example.netprobe.ui.DiscoveryScreen
import com.example.netprobe.ui.HostProbeScreen
import com.example.netprobe.ui.NetworkOverviewScreen
import com.example.netprobe.ui.theme.NetProbeTheme
import com.example.netprobe.viewmodel.DiscoveryViewModel
import com.example.netprobe.viewmodel.NetworkViewModel

class MainActivity : ComponentActivity() {

    private val probeViewModel: HostProbeViewModel by viewModels()
    private val networkViewModel: NetworkViewModel by viewModels()
    private val discoveryViewModel: DiscoveryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NetProbeTheme {
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { results ->
                    if (results.values.any { it }) {
                        networkViewModel.refresh()
                    }
                }

                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }

                var currentScreen by remember { mutableIntStateOf(0) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentScreen == 0,
                                onClick = { currentScreen = 0 },
                                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                                label = { Text("Overview") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 1,
                                onClick = { currentScreen = 1 },
                                icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                label = { Text("Discovery") }
                            )
                            NavigationBarItem(
                                selected = currentScreen == 2,
                                onClick = { currentScreen = 2 },
                                icon = { Icon(Icons.Default.Search, contentDescription = null) },
                                label = { Text("Probe") }
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentScreen) {
                        0 -> {
                            val state by networkViewModel.uiState.collectAsStateWithLifecycle()
                            NetworkOverviewScreen(
                                state = state,
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        1 -> {
                            val discoveryState by discoveryViewModel.uiState.collectAsStateWithLifecycle()
                            val networkState by networkViewModel.uiState.collectAsStateWithLifecycle()
                            DiscoveryScreen(
                                state = discoveryState,
                                localAddresses = networkState.localAddresses,
                                onStartDiscovery = discoveryViewModel::startDiscovery,
                                onProbeDevice = { ip ->
                                    probeViewModel.updateIpAddress(ip)
                                    probeViewModel.probeHost()
                                    currentScreen = 2
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }

                        2 -> {
                            val state by probeViewModel.uiState.collectAsStateWithLifecycle()
                            HostProbeScreen(
                                state = state,
                                onIpChanged = probeViewModel::updateIpAddress,
                                onProbeClick = probeViewModel::probeHost,
                                onToggleWhois = probeViewModel::toggleWhois,
                                onToggleTraceroute = probeViewModel::toggleTraceroute,
                                onOpenBrowser = { url ->
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(intent)
                                },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}
