# Bluetooth Scanning Possibilities for NetProbe

Adding Bluetooth scanning to NetProbe would expand its capabilities from IP-based network discovery to local proximity discovery. Below are the technical possibilities for scanning device types, classes, and metadata.

## 1. Hybrid Device Discovery
NetProbe can perform a dual-mode scan to capture the full spectrum of nearby devices:
*   **Classic Bluetooth Discovery**: Finds legacy devices like older smartphones, car audio systems, and classic wireless speakers using `BluetoothAdapter.startDiscovery()`.
*   **BLE (Low Energy) Scanning**: Efficiently finds modern IoT devices, fitness trackers, smart tags, and smart home sensors using `BluetoothLeScanner`.

## 2. Rich Device Metadata
For each discovered device, we can extract and display:
*   **Transport Type**: Identify if the device is `Classic`, `LE`, or `Dual Mode`.
*   **Class of Device (CoD)**:
    *   **Major Class**: Identify the broad category (e.g., Computer, Phone, Audio/Video, Peripheral, Wearable, Toy).
    *   **Minor Class**: Get specific details (e.g., distinguishing a Laptop from a Desktop, or a Headset from a standalone Speaker).
*   **Appearance (BLE)**: A standard 16-bit value that tells the UI how to represent the device (e.g., an icon for a Heart Rate Sensor, Keyboard, or Thermometer).

## 3. Manufacturer & Vendor Identification
*   **MAC OUI Lookup**: By analyzing the first 3 bytes of the Bluetooth MAC address, we can cross-reference the IEEE database to identify the hardware manufacturer (e.g., Apple, Samsung, Espressif).
*   **Manufacturer Specific Data**: Parse BLE advertisement packets for Company IDs to identify specific ecosystem devices (e.g., identifying a device as an "Apple AirTag" or a "Microsoft Surface Dial").

## 4. Signal Analysis & Proximity
*   **Real-time RSSI**: Display the signal strength in dBm to help users locate devices physically.
*   **Distance Estimation**: Use the `TxPower` level (available in some BLE packets) alongside `RSSI` to calculate a rough distance in meters.
*   **Signal Stability**: Track how often a device is seen to distinguish between a stationary smart home hub and a passing wearable.

## 5. Deep Probing (Service Discovery)
*   **GATT Service Listing**: Without pairing, NetProbe can list the available Service UUIDs (e.g., identifying a device supports the "Battery Service" or "Human Interface Device" profile).
*   **SDP Probing (Classic)**: Query classic devices for supported profiles like A2DP (Audio) or SPP (Serial Port).

## 6. Security & State Monitoring
*   **Bonding State**: Identify if devices are already paired (`BONDED`) or open for connection.
*   **Discoverability Flags**: Detect if a device is in "Limited Discoverable" or "General Discoverable" mode.

---

### Implementation Requirements
To implement these, the app will need to request the following permissions (depending on the target API level):
*   `android.permission.BLUETOOTH_SCAN` (API 31+)
*   `android.permission.BLUETOOTH_CONNECT` (API 31+)
*   `android.permission.ACCESS_FINE_LOCATION` (Required for physical proximity data on older Android versions)

> [!TIP]
> **Use Case for NetProbe**: Bluetooth scanning is excellent for "Network Inventory" tasks where you want to verify all hardware in a room, including those not connected to the Wi-Fi (like wireless peripherals or isolated IoT sensors).
