package com.brutiful.netprobe.viewmodel

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.brutiful.netprobe.model.NetworkOverview
import com.brutiful.netprobe.network.NetworkInfoRepository
import com.brutiful.netprobe.network.SpeedTestRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkInfoRepository(application.applicationContext)
    private val prefs = application.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(NetworkOverview(
        isBrightnessBoostEnabled = prefs.getBoolean("brightness_boost", true),
        isDarkMode = prefs.getBoolean("dark_mode", true)
    ))
    val uiState: StateFlow<NetworkOverview> = _uiState.asStateFlow()

    private var refreshJob: kotlinx.coroutines.Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            repository.observeNetworkOverview().collect { overview ->
                _uiState.update { current ->
                    overview.copy(
                        speedTestResult = current.speedTestResult,
                        isSpeedTesting = current.isSpeedTesting,
                        isSpeedTestExpanded = current.isSpeedTestExpanded,
                        isBrightnessBoostEnabled = current.isBrightnessBoostEnabled,
                        isDarkMode = current.isDarkMode
                    )
                }
            }
        }
    }

    fun toggleBrightnessBoost(enabled: Boolean) {
        prefs.edit { putBoolean("brightness_boost", enabled) }
        _uiState.update { it.copy(isBrightnessBoostEnabled = enabled) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        prefs.edit { putBoolean("dark_mode", enabled) }
        _uiState.update { it.copy(isDarkMode = enabled) }
    }

    private var autoRefreshJob: kotlinx.coroutines.Job? = null

    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = viewModelScope.launch {
            while (true) {
                refresh() 
                delay(3000.milliseconds)
            }
        }
    }

    fun dismissSpeedTest() {
        _uiState.update { it.copy(isSpeedTestExpanded = false) }
    }

    fun runSpeedTest() {
        if (_uiState.value.isSpeedTesting) return

        _uiState.update { it.copy(isSpeedTesting = true, isSpeedTestExpanded = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val speedTest = SpeedTestRunner.runSpeedTest()
                _uiState.update {
                    it.copy(
                        isSpeedTesting = false,
                        speedTestResult = speedTest
                    )
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSpeedTesting = false) }
            }
        }
    }
}
