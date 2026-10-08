package com.brutiful.netprobe.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.brutiful.netprobe.model.BluetoothCategory
import com.brutiful.netprobe.model.BluetoothDeviceData
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.LocalAddress
import com.brutiful.netprobe.model.PresenceState
import com.brutiful.netprobe.model.ProximityTargetType
import com.brutiful.netprobe.network.discovery.SubnetCalculator
import com.brutiful.netprobe.viewmodel.DiscoveryFilter
import com.brutiful.netprobe.viewmodel.DiscoveryTab
import com.brutiful.netprobe.viewmodel.DiscoveryUiState
import com.brutiful.netprobe.viewmodel.ScanMode
import com.brutiful.netprobe.viewmodel.SortOption

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DiscoveryScreen(
    state: DiscoveryUiState,
    localAddresses: List<LocalAddress>,
    isVpnActive: Boolean,
    isExternalVpnActive: Boolean = false,
    onStartDiscovery: (LocalAddress, ScanMode) -> Unit,
    onStopDiscovery: () -> Unit,
    onEnrichDevices: () -> Unit,
    onStartBluetoothDiscovery: () -> Unit,
    onStopBluetoothDiscovery: () -> Unit,
    onStartCombinedScan: (LocalAddress?) -> Unit,
    onSetTab: (DiscoveryTab) -> Unit,
    onSetSearchQuery: (String) -> Unit,
    onSetSortBy: (SortOption) -> Unit,
    onSetFilter: (DiscoveryFilter) -> Unit,
    onProbeDevice: (DiscoveredDevice) -> Unit,
    onSshDevice: (DiscoveredDevice) -> Unit,
    onStartLiveTracking: (String, ProximityTargetType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedDeviceForDetail by remember { mutableStateOf<Any?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showRationaleDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            localAddresses.firstOrNull()?.let { onStartDiscovery(it, ScanMode.NORMAL) }
        }
    }

    fun handleStartScan(mode: ScanMode) {
        if (state.selectedTab == DiscoveryTab.NETWORK) {
            if (!state.isLocalNetworkPermissionGranted) {
                showRationaleDialog = true
            } else {
                localAddresses.firstOrNull()?.let { onStartDiscovery(it, mode) }
            }
        } else if (state.selectedTab == DiscoveryTab.BLUETOOTH) {
            onStartBluetoothDiscovery()
        }
    }

    val filteredNetworkDevices = remember(state.devices, state.searchQuery, state.sortBy, state.filter) {
        state.devices.filter {
            (it.computedDisplayName().contains(state.searchQuery, ignoreCase = true) ||
            it.ipString.contains(state.searchQuery)) &&
            when (state.filter) {
                DiscoveryFilter.ALL -> true
                DiscoveryFilter.ACTIVE -> it.presenceState == PresenceState.CONFIRMED_ACTIVE || it.presenceState == PresenceState.DISCOVERED_BY_MULTICAST
                DiscoveryFilter.KNOWN -> it.presenceState != PresenceState.UNRESPONSIVE_THIS_SCAN
                DiscoveryFilter.UNRESPONSIVE -> it.presenceState == PresenceState.PREVIOUSLY_SEEN || it.presenceState == PresenceState.UNRESPONSIVE_THIS_SCAN
            }
        }.let { list ->
            when (state.sortBy) {
                SortOption.NAME -> list.sortedBy { it.computedDisplayName() }
                SortOption.IP -> list.sortedBy { d -> d.ipAddress?.let { SubnetCalculator.ipToLong(it) } ?: Long.MAX_VALUE }
                else -> list
            }
        }
    }

    var selectedBluetoothCategory by remember { mutableStateOf<BluetoothCategory?>(null) }

    val filteredBluetoothDevices = remember(state.bluetoothDevices, state.searchQuery, state.sortBy, selectedBluetoothCategory) {
        state.bluetoothDevices.filter { device ->
            (selectedBluetoothCategory == null || device.category == selectedBluetoothCategory) &&
            (device.displayName().contains(state.searchQuery, ignoreCase = true) ||
             device.address.contains(state.searchQuery, ignoreCase = true) ||
             device.category.displayName.contains(state.searchQuery, ignoreCase = true))
        }.let { list ->
            when (state.sortBy) {
                SortOption.NAME -> list.sortedBy { it.displayName() }
                SortOption.SIGNAL, SortOption.IP -> list.sortedByDescending { it.rssi }
            }
        }
    }

    Scaffold(
        modifier = modifier,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Header
            item {
                HeaderBlock(
                    title = "Best-effort device discovery",
                    subtitle = "Find devices connected to your Wi‑Fi and nearby Bluetooth hardware."
                )
            }

            // 1b. VPN Warning Banner
            if ((isVpnActive || isExternalVpnActive) && state.selectedTab == DiscoveryTab.NETWORK) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isExternalVpnActive) Icons.Default.PublicOff else Icons.Default.VpnLock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isExternalVpnActive) {
                                    "External VPN active. Some devices might not be discoverable."
                                } else {
                                    "Live Monitoring active."
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 2. Scan Control Card
            item {
                ScanControlCard(
                    state = state,
                    onStartScan = ::handleStartScan,
                    onStopScan = {
                        if (state.isScanning) onStopDiscovery()
                        if (state.isBluetoothScanning) onStopBluetoothDiscovery()
                    },
                    onEnrich = {
                        if (!state.isLocalNetworkPermissionGranted) {
                            showRationaleDialog = true
                        } else {
                            onEnrichDevices()
                        }
                    },
                    onScanAll = {
                        if (!state.isLocalNetworkPermissionGranted) {
                            showRationaleDialog = true
                        } else {
                            onStartCombinedScan(localAddresses.firstOrNull())
                        }
                    }
                )
            }

            // 3. Summary Bar
            item {
                DiscoverySummaryBar(state = state)
            }

            // 3b. Subnet Coverage & Multicast Notice
            if (state.selectedTab == DiscoveryTab.NETWORK && (state.subnetCoverageText != null || state.scanWarningNotice != null)) {
                item {
                    SubnetCoverageNoticeCard(
                        coverageText = state.subnetCoverageText,
                        warningNotice = state.scanWarningNotice
                    )
                }
            }

            // 3c. Discovery Diagnostics
            if (state.isScanning || state.devices.isNotEmpty()) {
                item {
                    DiscoveryDiagnosticsCard(state.stats)
                }
            }

            // 4. Segmented Control
            stickyHeader {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        SegmentedButton(
                            selected = state.selectedTab == DiscoveryTab.NETWORK,
                            onClick = { onSetTab(DiscoveryTab.NETWORK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Default.Lan, contentDescription = null) }
                        ) {
                            Text("Network")
                        }
                        SegmentedButton(
                            selected = state.selectedTab == DiscoveryTab.BLUETOOTH,
                            onClick = { onSetTab(DiscoveryTab.BLUETOOTH) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Default.Bluetooth, contentDescription = null) }
                        ) {
                            Text("Bluetooth")
                        }
                    }
                }
            }

            // 4b. Filter Tabs (Network only)
            if (state.selectedTab == DiscoveryTab.NETWORK && state.devices.isNotEmpty()) {
                item {
                    ScrollableTabRow(
                        selectedTabIndex = state.filter.ordinal,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[state.filter.ordinal]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    ) {
                        DiscoveryFilter.entries.forEach { filter ->
                            Tab(
                                selected = state.filter == filter,
                                onClick = { onSetFilter(filter) },
                                text = {
                                    Text(
                                        text = when(filter) {
                                            DiscoveryFilter.ALL -> "All"
                                            DiscoveryFilter.ACTIVE -> "Active"
                                            DiscoveryFilter.KNOWN -> "Known"
                                            DiscoveryFilter.UNRESPONSIVE -> "Unresponsive"
                                        },
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 4c. Bluetooth Device Category Summary (Bluetooth only)
            if (state.selectedTab == DiscoveryTab.BLUETOOTH && (state.bluetoothDevices.isNotEmpty() || state.isBluetoothScanning)) {
                item {
                    BluetoothCategorySummaryCard(
                        bluetoothDevices = state.bluetoothDevices,
                        selectedCategory = selectedBluetoothCategory,
                        onSelectCategory = { selectedBluetoothCategory = it }
                    )
                }
            }

            // 5. Result Controls (Search & Sort)
            val currentListEmpty = if (state.selectedTab == DiscoveryTab.NETWORK) state.devices.isEmpty() else state.bluetoothDevices.isEmpty()
            if (!currentListEmpty) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = onSetSearchQuery,
                            placeholder = { Text("Search devices...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            modifier = Modifier.weight(1f),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = MaterialTheme.colorScheme.primary)
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Name") },
                                    onClick = { onSetSortBy(SortOption.NAME); showSortMenu = false },
                                    leadingIcon = { RadioButton(selected = state.sortBy == SortOption.NAME, onClick = null) }
                                )
                                if (state.selectedTab == DiscoveryTab.NETWORK) {
                                    DropdownMenuItem(
                                        text = { Text("IP Address") },
                                        onClick = { onSetSortBy(SortOption.IP); showSortMenu = false },
                                        leadingIcon = { RadioButton(selected = state.sortBy == SortOption.IP, onClick = null) }
                                    )
                                } else {
                                    DropdownMenuItem(
                                        text = { Text("Signal Strength (Closest First)") },
                                        onClick = { onSetSortBy(SortOption.SIGNAL); showSortMenu = false },
                                        leadingIcon = { RadioButton(selected = state.sortBy == SortOption.SIGNAL, onClick = null) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 6. Device Results
            if (state.selectedTab == DiscoveryTab.NETWORK) {
                if (!state.isLocalNetworkPermissionGranted) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lan,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = "Local network access is required",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "NetProbe needs access to your Wi‑Fi network to find local devices, cameras, printers, and smart-home equipment.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Button(
                                    onClick = { showRationaleDialog = true }
                                ) {
                                    Text("Allow local network access")
                                }
                            }
                        }
                    }
                } else if (filteredNetworkDevices.isEmpty() && !state.isScanning) {
                    item {
                        DiscoveryEmptyState(
                            title = if (state.devices.isEmpty()) "No Network Scan Yet" else "No matching devices",
                            description = if (state.devices.isEmpty()) "Perform a best-effort LAN scan to find active hosts, servers, and IoT devices." else "Try a different search term.",
                            actionLabel = "Start Network Scan",
                            onAction = { handleStartScan(ScanMode.NORMAL) },
                            icon = Icons.Default.Lan
                        )
                    }
                } else {
                    items(filteredNetworkDevices, key = { it.ipString }) { device ->
                        DeviceResultRow(
                            device = device,
                            onClick = { selectedDeviceForDetail = device }
                        )
                    }
                }
            } else {
                if (filteredBluetoothDevices.isEmpty() && !state.isBluetoothScanning) {
                    item {
                        DiscoveryEmptyState(
                            title = if (state.bluetoothDevices.isEmpty()) "No Bluetooth Scan Yet" else "No matching hardware",
                            description = if (state.bluetoothDevices.isEmpty()) "Press the button above to scan for nearby Bluetooth devices." else "Try a different search term.",
                            actionLabel = "Scan Bluetooth",
                            onAction = { handleStartScan(ScanMode.NORMAL) },
                            icon = Icons.Default.Bluetooth
                        )
                    }
                } else {
                    items(filteredBluetoothDevices, key = { it.address }) { device ->
                        BluetoothDeviceResultRow(
                            device = device,
                            onClick = { selectedDeviceForDetail = device }
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Best-effort network discovery limitations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Android client discovery is best-effort. Devices with firewalls, AP client isolation, sleeping states, or blocking multicast/ICMP may not appear without router or controller access.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    DeviceDetailBottomSheet(
        device = selectedDeviceForDetail,
        onDismiss = { selectedDeviceForDetail = null },
        onProbe = onProbeDevice,
        onSsh = onSshDevice,
        onStartTrackingLive = onStartLiveTracking
    )

    if (showRationaleDialog) {
        AlertDialog(
            onDismissRequest = { showRationaleDialog = false },
            title = { Text("Local Network Access Required") },
            text = { Text("NetProbe uses local network access for best-effort device discovery (mDNS/Bonjour, SSDP, WS-Discovery camera discovery, and TCP probes). It does not send your network data to external servers.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRationaleDialog = false
                        permissionLauncher.launch("android.permission.ACCESS_LOCAL_NETWORK")
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationaleDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
