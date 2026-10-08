package com.brutiful.netprobe.viewmodel

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.util.isNotEmpty
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.BluetoothDeviceData
import com.brutiful.netprobe.model.DiscoveredDevice
import com.brutiful.netprobe.model.EnvironmentProfile
import com.brutiful.netprobe.model.LocalAddress
import com.brutiful.netprobe.model.ProximityCalibrationData
import com.brutiful.netprobe.model.ProximityTargetType
import com.brutiful.netprobe.model.ProximityTrackingSession
import com.brutiful.netprobe.model.RecognitionConfidence
import com.brutiful.netprobe.model.TxPowerSource
import com.brutiful.netprobe.network.BluetoothIdHelper
import com.brutiful.netprobe.network.HistoryDatabase
import com.brutiful.netprobe.network.ProximitySignalEstimator
import com.brutiful.netprobe.network.discovery.NetworkDeviceScanner
import com.brutiful.netprobe.network.discovery.SubnetCalculator
import com.brutiful.netprobe.util.NetProbeLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

enum class ScanMode {
    NORMAL,
    EXPANDED
}

enum class DiscoveryPhase {
    IDLE,
    HOST_DISCOVERY,
    ENRICHMENT
}

enum class DiscoveryTab {
    NETWORK,
    BLUETOOTH
}

enum class SortOption {
    NAME,
    IP,
    SIGNAL
}

data class DiscoveryStats(
    val mdnsCount: Int = 0,
    val ssdpCount: Int = 0,
    val wsDiscoveryCount: Int = 0,
    val tcpProbeCount: Int = 0
)

enum class DiscoveryFilter {
    ALL,
    ACTIVE,
    KNOWN,
    UNRESPONSIVE
}

data class DiscoveryUiState(
    val devices: List<DiscoveredDevice> = emptyList(),
    val isScanning: Boolean = false,
    val currentMode: ScanMode? = null,
    val currentPhase: DiscoveryPhase = DiscoveryPhase.IDLE,
    val progress: Float = 0f,
    val hostsFound: Int = 0,
    val hostsEnriched: Int = 0,
    val statusMessage: String? = "Ready to scan",
    val bluetoothDevices: List<BluetoothDeviceData> = emptyList(),
    val isBluetoothScanning: Boolean = false,
    val elapsedTimeSeconds: Int = 0,
    val lastScanTimestamp: Long? = null,
    val selectedTab: DiscoveryTab = DiscoveryTab.NETWORK,
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.IP,
    val filter: DiscoveryFilter = DiscoveryFilter.ALL,
    val stats: DiscoveryStats = DiscoveryStats(),
    val isLocalNetworkPermissionGranted: Boolean = true,
    val liveTrackingSession: ProximityTrackingSession? = null,
    val environmentProfile: EnvironmentProfile = EnvironmentProfile.TYPICAL_INDOOR,
    val showApproximateDistance: Boolean = false,
    val savedCalibrations: Map<String, ProximityCalibrationData> = emptyMap(),
    val isCalibrating: Boolean = false,
    val calibrationProgress: Float = 0f,
    val maxHostCap: Int = 254,
    val isPartialCoverage: Boolean = false,
    val isMulticastBlocked: Boolean = false,
    val subnetCoverageText: String? = null,
    val scanWarningNotice: String? = null
)

class DiscoveryViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "DiscoveryVM"
    private val db = HistoryDatabase.getDatabase(application)

    private val bluetoothManager = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val leScanner = bluetoothAdapter?.bluetoothLeScanner
    private val _bluetoothDevices = ConcurrentHashMap<String, BluetoothDeviceData>()

    private var discoveryJob: Job? = null
    private var timerJob: Job? = null

    private val _uiState = MutableStateFlow(DiscoveryUiState())
    val uiState: StateFlow<DiscoveryUiState> = _uiState.asStateFlow()

    fun checkLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 35) return true
        val perm = ContextCompat.checkSelfPermission(
            getApplication(),
            "android.permission.ACCESS_LOCAL_NETWORK"
        )
        return perm == PackageManager.PERMISSION_GRANTED
    }

    fun updatePermissionState() {
        _uiState.update { it.copy(isLocalNetworkPermissionGranted = checkLocalNetworkPermission()) }
    }

    private fun startTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(elapsedTimeSeconds = 0) }
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _uiState.update { it.copy(elapsedTimeSeconds = it.elapsedTimeSeconds + 1) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun setTab(tab: DiscoveryTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSortBy(sort: SortOption) {
        _uiState.update { it.copy(sortBy = sort) }
    }

    fun setFilter(filter: DiscoveryFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun startDiscovery(localAddress: LocalAddress, mode: ScanMode = ScanMode.NORMAL) {
        updatePermissionState()
        if (!checkLocalNetworkPermission()) {
            _uiState.update {
                it.copy(
                    isScanning = false,
                    isLocalNetworkPermissionGranted = false,
                    statusMessage = "Local network permission required"
                )
            }
            return
        }
        val maskedAddress = NetProbeLog.maskIp(localAddress.address)
        NetProbeLog.d(tag, "startDiscovery called with $maskedAddress/${localAddress.prefixLength}, mode=$mode")
        if (_uiState.value.isScanning) {
            NetProbeLog.d(tag, "Discovery already in progress, ignoring request")
            return
        }
        performDiscovery(localAddress, mode)
    }

    fun stopDiscovery() {
        NetProbeLog.d(tag, "stopDiscovery called")
        discoveryJob?.cancel()
        stopTimer()
        _uiState.update {
            it.copy(
                isScanning = false,
                statusMessage = "Scan canceled",
                lastScanTimestamp = System.currentTimeMillis()
            )
        }
    }

    private fun performDiscovery(localAddress: LocalAddress, mode: ScanMode) {
        val maskedAddress = NetProbeLog.maskIp(localAddress.address)
        NetProbeLog.d(tag, "performDiscovery starting for $maskedAddress/${localAddress.prefixLength}, mode=$mode")
        discoveryJob?.cancel()

        _uiState.update {
            it.copy(
                isScanning = true,
                currentMode = mode,
                currentPhase = DiscoveryPhase.HOST_DISCOVERY,
                devices = emptyList(),
                progress = 0f,
                hostsFound = 0,
                hostsEnriched = 0,
                statusMessage = "Starting best-effort LAN device discovery…",
                stats = DiscoveryStats(),
                scanWarningNotice = null
            )
        }
        startTimer()

        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val phoneIp = InetAddress.getByName(localAddress.address)
                val scanner = NetworkDeviceScanner(getApplication())
                val hostCap = if (mode == ScanMode.EXPANDED) 512 else 254

                val result = scanner.runBestEffortScan(
                    phoneIp = phoneIp,
                    prefixLength = localAddress.prefixLength,
                    maxHostCap = hostCap
                ) { progressRatio, phaseName ->
                    _uiState.update {
                        it.copy(
                            progress = progressRatio,
                            statusMessage = phaseName
                        )
                    }
                }

                val sorted = result.devices.sortedWith(compareBy { d ->
                    d.ipAddress?.let { SubnetCalculator.ipToLong(it) } ?: Long.MAX_VALUE
                })

                val mdnsCount = sorted.count { dev -> dev.sources.contains(com.brutiful.netprobe.model.DiscoverySource.MDNS) }
                val ssdpCount = sorted.count { dev -> dev.sources.contains(com.brutiful.netprobe.model.DiscoverySource.SSDP) }
                val wsCount = sorted.count { dev -> dev.sources.contains(com.brutiful.netprobe.model.DiscoverySource.WS_DISCOVERY) }
                val tcpCount = sorted.count { dev -> dev.sources.contains(com.brutiful.netprobe.model.DiscoverySource.TCP_CONNECT) }

                _uiState.update {
                    it.copy(
                        devices = sorted,
                        hostsFound = sorted.size,
                        isPartialCoverage = result.isPartialCoverage,
                        isMulticastBlocked = result.isMulticastBlocked,
                        subnetCoverageText = "${result.scanRange.scannedHostCount} of ${result.scanRange.totalHostsInSubnet} hosts on ${result.scanRange.networkAddressStr}",
                        scanWarningNotice = result.warningMessage,
                        stats = DiscoveryStats(mdnsCount, ssdpCount, wsCount, tcpCount),
                        progress = 1.0f,
                        statusMessage = "Best-effort scan completed"
                    )
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    NetProbeLog.e(tag, "Discovery error: ${e.message}")
                    _uiState.update { it.copy(statusMessage = "Scan error: ${e.message}") }
                } else {
                    _uiState.update { it.copy(statusMessage = "Scan canceled") }
                }
            } finally {
                stopTimer()
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        lastScanTimestamp = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startBluetoothDiscovery() {
        if (bluetoothAdapter == null || _uiState.value.isBluetoothScanning) return

        _bluetoothDevices.clear()
        _uiState.update {
            it.copy(
                isBluetoothScanning = true,
                bluetoothDevices = emptyList(),
                statusMessage = "Scanning nearby Bluetooth (BLE) devices…"
            )
        }
        startTimer()

        leScanner?.startScan(leScanCallback)

        viewModelScope.launch {
            delay(30000.milliseconds)
            if (_uiState.value.isBluetoothScanning) {
                stopBluetoothDiscovery()
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBluetoothDiscovery() {
        leScanner?.stopScan(leScanCallback)
        stopTimer()
        _uiState.update {
            it.copy(
                isBluetoothScanning = false,
                statusMessage = "Scan completed",
                lastScanTimestamp = System.currentTimeMillis()
            )
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            processBluetoothDevice(result.device, result.rssi.toShort(), result.scanRecord)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                processBluetoothDevice(result.device, result.rssi.toShort(), result.scanRecord)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processBluetoothDevice(device: BluetoothDevice, rssi: Short, scanRecord: ScanRecord? = null) {
        val address = device.address
        val isRandomized = BluetoothIdHelper.isLocallyAdministered(address)

        val evidence = mutableListOf<String>()
        val serviceUuids = scanRecord?.serviceUuids?.map { it.toString() } ?: emptyList()
        val advertisedName = scanRecord?.deviceName

        var manufacturerCompanyId: Int? = null
        var manufacturerCompanyName: String? = null

        scanRecord?.manufacturerSpecificData?.let { data ->
            if (data.isNotEmpty()) {
                val companyId = data.keyAt(0)
                manufacturerCompanyId = companyId
                manufacturerCompanyName = BluetoothIdHelper.getCompanyName(companyId)
                evidence.add("Manufacturer Data: Company ID 0x${Integer.toHexString(companyId).uppercase()}")
            }
        }

        val appearance = parseAppearance(scanRecord?.bytes)

        val majorClass = device.bluetoothClass?.majorDeviceClass
        val fullClass = device.bluetoothClass?.deviceClass

        val categoryLabel = if (advertisedName != null) {
            null
        } else {
            BluetoothIdHelper.getCategoryFromAppearance(appearance ?: -1)
                ?: serviceUuids.firstNotNullOfOrNull { BluetoothIdHelper.getCategoryFromService(it) }
                ?: device.bluetoothClass?.let { BluetoothIdHelper.getDetailedClassName(it.majorDeviceClass, it.deviceClass) }
        }

        advertisedName?.let { evidence.add("Advertised Name: $it") }
        if (serviceUuids.isNotEmpty()) evidence.add("Services: ${serviceUuids.size} found")

        val confidence = when {
            advertisedName != null -> RecognitionConfidence.CONFIRMED
            (categoryLabel != null) || (manufacturerCompanyName != null) -> RecognitionConfidence.LIKELY
            !isRandomized -> RecognitionConfidence.LIMITED
            else -> RecognitionConfidence.UNKNOWN
        }

        val data = BluetoothDeviceData(
            name = device.name,
            advertisedName = advertisedName,
            address = address,
            vendor = if (!isRandomized) BluetoothIdHelper.getVendorName(address) else null,
            isRandomized = isRandomized,
            manufacturerCompanyId = manufacturerCompanyId,
            manufacturerCompanyName = manufacturerCompanyName,
            majorDeviceClass = majorClass,
            deviceClassCode = fullClass,
            serviceUuids = serviceUuids,
            appearance = appearance,
            bondState = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "Bonded"
                BluetoothDevice.BOND_BONDING -> "Bonding..."
                else -> "Not Bonded"
            },
            type = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "Low Energy"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual Mode"
                else -> "Unknown"
            },
            rssi = rssi,
            recognitionConfidence = confidence,
            evidenceList = evidence,
            categoryLabel = categoryLabel
        )

        synchronized(_bluetoothDevices) {
            val existing = _bluetoothDevices[address]
            val smoothedRssi = if (existing != null) {
                ((existing.rssi * 0.8) + (rssi * 0.2)).toInt().toShort()
            } else {
                rssi
            }
            _bluetoothDevices[address] = data.copy(rssi = smoothedRssi)
        }
        updateBluetoothUiState()
    }

    private fun parseAppearance(bytes: ByteArray?): Int? {
        if (bytes == null || bytes.size < 4) return null
        var i = 0
        while (i < bytes.size - 2) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= bytes.size) break

            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x19 && len >= 3) {
                val low = bytes[i + 2].toInt() and 0xFF
                val high = bytes[i + 3].toInt() and 0xFF
                return (high shl 8) or low
            }
            i += len + 1
        }
        return null
    }

    private fun updateBluetoothUiState() {
        val devices = synchronized(_bluetoothDevices) {
            _bluetoothDevices.values.toList()
        }
        _uiState.update { it.copy(bluetoothDevices = devices) }
    }

    fun startCombinedScan(localAddress: LocalAddress?) {
        localAddress?.let { startDiscovery(it, ScanMode.NORMAL) }
        startBluetoothDiscovery()
    }

    override fun onCleared() {
        stopBluetoothDiscovery()
        stopDiscovery()
    }
}
