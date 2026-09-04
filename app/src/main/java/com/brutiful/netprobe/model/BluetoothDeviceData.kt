package com.brutiful.netprobe.model

data class BluetoothDeviceData(
    val name: String?,
    val address: String,
    val vendor: String?,
    val isRandomized: Boolean,
    val deviceClass: String?,
    val bondState: String,
    val type: String,
    val rssi: Short
) {
    fun displayName(): String = name ?: vendor ?: if (isRandomized) "Private Device" else "Unknown Device"
}
