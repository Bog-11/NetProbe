package com.brutiful.netprobe.model

import android.bluetooth.BluetoothClass
import java.util.Locale

enum class RecognitionConfidence {
    CONFIRMED, LIKELY, LIMITED, UNKNOWN
}

enum class BluetoothCategory(
    val displayName: String,
    val shortName: String
) {
    IPHONE("iPhones / Apple", "iPhone"),
    ANDROID("Android", "Android"),
    WINDOWS("Windows", "Windows"),
    LINUX("Linux", "Linux"),
    AUDIO("Audio", "Audio"),
    WEARABLE("Wearables", "Wearable"),
    OTHER("Other BLE", "Other")
}

data class BluetoothDeviceData(
    val name: String?,
    val advertisedName: String?,
    val address: String,
    val vendor: String?,
    val isRandomized: Boolean,
    val manufacturerCompanyId: Int?,
    val manufacturerCompanyName: String?,
    val majorDeviceClass: Int?,
    val deviceClassCode: Int?,
    val serviceUuids: List<String>,
    val appearance: Int?,
    val bondState: String,
    val type: String,
    val rssi: Short,
    val recognitionConfidence: RecognitionConfidence = RecognitionConfidence.UNKNOWN,
    val evidenceList: List<String> = emptyList(),
    val categoryLabel: String? = null
) {
    val category: BluetoothCategory
        get() = determineCategory()

    private fun determineCategory(): BluetoothCategory {
        val fullName = ((name ?: "") + " " + (advertisedName ?: "")).lowercase(Locale.ROOT)
        val vendorStr = (vendor ?: "").lowercase(Locale.ROOT)
        val companyStr = (manufacturerCompanyName ?: "").lowercase(Locale.ROOT)
        val categoryStr = (categoryLabel ?: "").lowercase(Locale.ROOT)

        // 1. iPhone / Apple Devices
        if (fullName.contains("iphone") || fullName.contains("ipad") || fullName.contains("ipod") || 
            fullName.contains("macbook") || fullName.contains("imac") || fullName.contains("mac mini") || 
            fullName.contains("mac studio") || fullName.contains("ios")) {
            return BluetoothCategory.IPHONE
        }
        if (manufacturerCompanyId == 0x004C || manufacturerCompanyId == 0x05AC || 
            companyStr.contains("apple") || vendorStr.contains("apple")) {
            if (fullName.contains("watch") || appearance == 0x03 || categoryStr.contains("watch")) {
                return BluetoothCategory.WEARABLE
            }
            if (fullName.contains("airpods") || fullName.contains("beats") || fullName.contains("buds") || 
                majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || categoryStr.contains("audio")) {
                return BluetoothCategory.AUDIO
            }
            return BluetoothCategory.IPHONE
        }

        // 2. Wearables & Audio (explicit checks before general Android OEM company match)
        if (fullName.contains("headphone") || fullName.contains("headset") || fullName.contains("earbud") || 
            fullName.contains("buds") || fullName.contains("speaker") || fullName.contains("soundbar") || 
            fullName.contains("bose") || fullName.contains("jbl") || fullName.contains("sennheiser") || 
            manufacturerCompanyId == 0x0131 || manufacturerCompanyId == 0x0399 || 
            majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO || appearance == 0x0A) {
            return BluetoothCategory.AUDIO
        }

        if (fullName.contains("watch") || fullName.contains("band") || fullName.contains("fitbit") || 
            fullName.contains("garmin") || fullName.contains("amazfit") || 
            manufacturerCompanyId == 0x0087 || manufacturerCompanyId == 0x0157 || 
            majorDeviceClass == BluetoothClass.Device.Major.WEARABLE || appearance == 0x03 || appearance == 0x04) {
            return BluetoothCategory.WEARABLE
        }

        // 3. Android Devices
        val isFastPairOrGoogleService = serviceUuids.any { uuid ->
            val u = uuid.uppercase(Locale.ROOT)
            u.contains("FE2C") || u.contains("FEF3") || u.contains("FC12") || u.contains("2C18") || u.contains("FD6F")
        }

        if (fullName.contains("android") || fullName.contains("galaxy") || fullName.contains("pixel") || 
            fullName.contains("oneplus") || fullName.contains("redmi") || fullName.contains("xiaomi") || 
            fullName.contains("xperia") || fullName.contains("realme") || fullName.contains("poco") || 
            fullName.contains("oppo") || fullName.contains("vivo") || fullName.contains("motorola") || 
            fullName.contains("nokia") || fullName.contains("honor")) {
            return BluetoothCategory.ANDROID
        }

        if (manufacturerCompanyId == 0x00E0 || // Google
            manufacturerCompanyId == 0x0075 || // Samsung
            manufacturerCompanyId == 0x027D || // Huawei
            manufacturerCompanyId == 0x038F || // Xiaomi
            manufacturerCompanyId == 0x022A || // OPPO
            isFastPairOrGoogleService ||
            vendorStr.contains("google") || vendorStr.contains("samsung electronics") || vendorStr.contains("samsung") || 
            vendorStr.contains("oneplus") || vendorStr.contains("xiaomi") || vendorStr.contains("huawei") || 
            vendorStr.contains("oppo") || vendorStr.contains("vivo") || vendorStr.contains("motorola")) {
            return BluetoothCategory.ANDROID
        }

        if (majorDeviceClass == BluetoothClass.Device.Major.PHONE || categoryStr.contains("smartphone")) {
            return BluetoothCategory.ANDROID
        }

        // 4. Windows
        if (fullName.contains("windows") || fullName.contains("desktop-") || fullName.contains("laptop-") || 
            fullName.contains("surface") || fullName.contains("win10") || fullName.contains("win11")) {
            return BluetoothCategory.WINDOWS
        }
        if (manufacturerCompanyId == 0x0006 || manufacturerCompanyId == 0x045E || 
            companyStr.contains("microsoft") || vendorStr.contains("microsoft")) {
            return BluetoothCategory.WINDOWS
        }

        // 5. Linux
        if (fullName.contains("linux") || fullName.contains("ubuntu") || fullName.contains("debian") || 
            fullName.contains("fedora") || fullName.contains("raspberry") || fullName.contains("arch") || 
            fullName.contains("steamdeck") || fullName.contains("steam deck")) {
            return BluetoothCategory.LINUX
        }
        if (vendorStr.contains("raspberry pi") || vendorStr.contains("linux foundation") || companyStr.contains("raspberry")) {
            return BluetoothCategory.LINUX
        }

        return BluetoothCategory.OTHER
    }

    fun displayName(): String {
        // Priority per Implementation Plan:
        // 1. Advertised local name (from ScanRecord or BluetoothDevice)
        // 2. Category from Appearance/Services (e.g., "Likely wearable")
        // 3. Company identifier (e.g., "Apple BLE device")
        // 4. Classic Bluetooth Minor Class (e.g., "Headset")
        // 5. MAC OUI (e.g., "Samsung Electronics") - Public only
        // 6. Fallback
        
        return name ?: advertisedName ?: categoryLabel ?: 
            manufacturerCompanyName?.let { "$it BLE device" } ?: 
            vendor ?: 
            if (isRandomized) "Private BLE device" else "Unknown BLE device"
    }
}
