package com.brutiful.netprobe.network

import com.brutiful.netprobe.model.BluetoothDeviceData
import com.brutiful.netprobe.model.RecognitionConfidence
import org.junit.Assert.*
import org.junit.Test

class BluetoothRecognitionTest {

    @Test
    fun testLocallyAdministeredAddress() {
        // Public addresses (2nd bit of 1st byte is 0)
        assertFalse(BluetoothIdHelper.isLocallyAdministered("00:11:22:33:44:55"))
        assertFalse(BluetoothIdHelper.isLocallyAdministered("01:11:22:33:44:55"))
        assertFalse(BluetoothIdHelper.isLocallyAdministered("FC:11:22:33:44:55"))
        
        // Locally administered (2nd bit of 1st byte is 1)
        // x2, x6, xA, xE in first byte
        assertTrue(BluetoothIdHelper.isLocallyAdministered("02:11:22:33:44:55"))
        assertTrue(BluetoothIdHelper.isLocallyAdministered("46:11:22:33:44:55"))
        assertTrue(BluetoothIdHelper.isLocallyAdministered("AE:11:22:33:44:55"))
    }

    @Test
    fun testCompanyNameMapping() {
        assertEquals("Apple", BluetoothIdHelper.getCompanyName(0x004C))
        assertEquals("Google", BluetoothIdHelper.getCompanyName(0x00E0))
        assertEquals("Samsung", BluetoothIdHelper.getCompanyName(0x0075))
        assertNull(BluetoothIdHelper.getCompanyName(0xFFFF))
    }

    @Test
    fun testCategoryFromService() {
        // Standard Heart Rate Service
        assertEquals("Heart Rate Sensor", BluetoothIdHelper.getCategoryFromService("0000180D-0000-1000-8000-00805f9b34fb"))
        // Short UUID
        assertEquals("HID Device", BluetoothIdHelper.getCategoryFromService("1812"))
        assertNull(BluetoothIdHelper.getCategoryFromService("FFFF"))
    }

    @Test
    fun testCategoryFromAppearance() {
        // 0x00C0 (Watch) -> category bits (appearance >> 6) = 0x03
        assertEquals("Likely wearable (Watch)", BluetoothIdHelper.getCategoryFromAppearance(0x00C0))
        // 0x0040 (Phone) -> category bits = 0x01
        assertEquals("Smartphone", BluetoothIdHelper.getCategoryFromAppearance(0x0040))
    }

    @Test
    fun testDisplayNamePriority() {
        val base = BluetoothDeviceData(
            name = null,
            advertisedName = null,
            address = "02:00:00:00:00:00",
            vendor = null,
            isRandomized = true,
            manufacturerCompanyId = null,
            manufacturerCompanyName = null,
            majorDeviceClass = null,
            deviceClassCode = null,
            serviceUuids = emptyList(),
            appearance = null,
            bondState = "Not Bonded",
            type = "Low Energy",
            rssi = -60,
            recognitionConfidence = RecognitionConfidence.UNKNOWN,
            evidenceList = emptyList(),
            categoryLabel = null
        )

        // 1. Advertised Name
        val withAdvName = base.copy(advertisedName = "My Device")
        assertEquals("My Device", withAdvName.displayName())

        // 2. Category Label
        val withCategory = base.copy(categoryLabel = "Likely wearable")
        assertEquals("Likely wearable", withCategory.displayName())

        // 3. Company Name
        val withCompany = base.copy(manufacturerCompanyName = "Apple")
        assertEquals("Apple BLE device", withCompany.displayName())

        // 4. Vendor (Public only)
        val publicWithVendor = base.copy(isRandomized = false, vendor = "Samsung Electronics")
        assertEquals("Samsung Electronics", publicWithVendor.displayName())

        // 5. Randomized Fallback
        assertEquals("Private BLE device", base.displayName())
        
        // 6. Public Fallback
        assertEquals("Unknown BLE device", base.copy(isRandomized = false).displayName())
    }
}
