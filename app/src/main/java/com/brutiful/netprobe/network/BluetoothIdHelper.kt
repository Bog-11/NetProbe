package com.brutiful.netprobe.network

import android.bluetooth.BluetoothClass
import com.brutiful.netprobe.network.OuiRepository
import java.util.Locale

object BluetoothIdHelper {

    fun isRandomizedAddress(address: String): Boolean {
        // A MAC address is randomized (Locally Administered) if the 
        // second-to-least significant bit of the first byte is 1.
        // Valid for: x2, x6, xA, xE in the first byte.
        if (address.length < 2) return false
        return try {
            val firstByte = address.substring(0, 2).toInt(16)
            (firstByte and 0x02) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun getVendorName(address: String): String? {
        if (isRandomizedAddress(address)) return null
        return OuiRepository.getVendor(address)
    }

    fun getDeviceClassName(majorClass: Int): String {
        return when (majorClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
            BluetoothClass.Device.Major.COMPUTER -> "Computer"
            BluetoothClass.Device.Major.HEALTH -> "Health"
            BluetoothClass.Device.Major.IMAGING -> "Imaging"
            BluetoothClass.Device.Major.MISC -> "Misc"
            BluetoothClass.Device.Major.NETWORKING -> "Networking"
            BluetoothClass.Device.Major.PERIPHERAL -> "Peripheral"
            BluetoothClass.Device.Major.PHONE -> "Phone"
            BluetoothClass.Device.Major.TOY -> "Toy"
            BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"
            BluetoothClass.Device.Major.WEARABLE -> "Wearable"
            else -> "Unknown"
        }
    }
}
