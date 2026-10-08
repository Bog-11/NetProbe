package com.brutiful.netprobe.network

import android.bluetooth.BluetoothClass
import com.brutiful.netprobe.network.OuiRepository

object BluetoothIdHelper {

    /**
     * Detects if an address is locally administered (randomized).
     * The second-to-least significant bit of the first byte is 1 for local addresses.
     */
    fun isLocallyAdministered(address: String): Boolean {
        if (address.length < 2) return false
        return try {
            val firstByte = address.substring(0, 2).toInt(16)
            (firstByte and 0x02) != 0
        } catch (e: Exception) {
            false
        }
    }

    // Alias for backward compatibility if needed, though plan says to use honest labels
    fun isRandomizedAddress(address: String): Boolean = isLocallyAdministered(address)

    fun getVendorName(address: String): String? {
        if (isLocallyAdministered(address)) return null
        return OuiRepository.getVendor(address)
    }

    private val companyIds = mapOf(
        0x0006 to "Microsoft",
        0x000A to "Qualcomm",
        0x000D to "Texas Instruments",
        0x001D to "Qualcomm",
        0x0030 to "STMicroelectronics",
        0x004C to "Apple",
        0x0059 to "Nordic Semiconductor",
        0x0075 to "Samsung",
        0x0087 to "Garmin",
        0x00E0 to "Google",
        0x012D to "Sony",
        0x0131 to "Bose",
        0x0157 to "Anhui Huami (Amazfit)",
        0x022B to "Tesla",
        0x027D to "Huawei",
        0x038F to "Xiaomi",
        0x0399 to "Sennheiser",
        0x045E to "Roku",
        0x05AC to "Apple", // Often used in some contexts though 0x004C is primary
        0x0826 to "Hyundai"
    )

    fun getCompanyName(companyId: Int): String? = companyIds[companyId]

    fun getCategoryFromService(uuid: String): String? {
        val shortUuid = if (uuid.length >= 8) uuid.substring(4, 8).uppercase() else uuid.uppercase()
        return when (shortUuid) {
            "180D" -> "Heart Rate Sensor"
            "1812" -> "HID Device"
            "1803", "1802" -> "Proximity Tag"
            "1819" -> "Location/Nav Device"
            "181B" -> "Body Composition Scale"
            "181D" -> "Glucose Meter"
            "1810" -> "Blood Pressure Monitor"
            "180F" -> "Battery-powered Device"
            "181A" -> "Environmental Sensor"
            "1108", "110B", "110E" -> "Audio Device"
            else -> null
        }
    }

    fun getCategoryFromAppearance(appearance: Int): String? {
        val category = appearance shr 6
        return when (category) {
            0x01 -> "Smartphone"
            0x02 -> "Computer"
            0x03 -> "Likely wearable (Watch)"
            0x04 -> "Likely wearable (Clock)"
            0x05 -> "Display"
            0x06 -> "Remote Control"
            0x08 -> "Tag"
            0x09 -> "Keyring"
            0x0A -> "Media Player"
            0x0B -> "Barcode Scanner"
            0x0C -> "Thermometer"
            0x0D -> "Heart Rate Sensor"
            0x0E -> "Blood Pressure Monitor"
            0x0F -> "HID Device"
            0x11 -> "Glucose Meter"
            0x12 -> "Running/Walking Sensor"
            0x13 -> "Cycling Sensor"
            else -> null
        }
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

    fun getDetailedClassName(major: Int, full: Int): String? {
        val minor = full and 0xFC
        return when (major) {
            BluetoothClass.Device.Major.PHONE -> when (minor) {
                BluetoothClass.Device.PHONE_SMART -> "Smartphone"
                BluetoothClass.Device.PHONE_CELLULAR -> "Cellular Phone"
                BluetoothClass.Device.PHONE_CORDLESS -> "Cordless Phone"
                else -> "Phone"
            }
            BluetoothClass.Device.Major.AUDIO_VIDEO -> when (minor) {
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "Headset"
                BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "Hands-free"
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "Headphones"
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "Loudspeaker"
                BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX -> "Set-top Box"
                BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "Hi-Fi Audio"
                else -> "Audio/Video Device"
            }
            BluetoothClass.Device.Major.WEARABLE -> when (minor) {
                BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "Smart Watch"
                BluetoothClass.Device.WEARABLE_PAGER -> "Pager"
                BluetoothClass.Device.WEARABLE_JACKET -> "Smart Jacket"
                BluetoothClass.Device.WEARABLE_HELMET -> "Smart Helmet"
                BluetoothClass.Device.WEARABLE_GLASSES -> "Smart Glasses"
                else -> "Wearable"
            }
            BluetoothClass.Device.Major.COMPUTER -> when (minor) {
                BluetoothClass.Device.COMPUTER_LAPTOP -> "Laptop"
                BluetoothClass.Device.COMPUTER_DESKTOP -> "Desktop Computer"
                BluetoothClass.Device.COMPUTER_SERVER -> "Server"
                BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA -> "PDA"
                else -> "Computer"
            }
            else -> null
        }
    }
}
