package com.example.netprobe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.netprobe.model.NetworkOverview
import com.example.netprobe.network.NetworkInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NetworkInfoRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(NetworkOverview())
    val uiState: StateFlow<NetworkOverview> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            repository.observeNetworkOverview().collect { overview ->
                _uiState.value = overview
            }
        }
    }
}
