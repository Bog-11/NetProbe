# SSH Shortcut in Probe Section Walkthrough

I have added an SSH shortcut (Terminal icon) to the **Host Probe** section. Now, whenever port 22 is identified as open during a host probe, you can jump directly to the Remote Shell with the connection details pre-filled.

## Changes Made

### 1. Remote Shell ViewModel Update
- Added an overloaded `prefill(ip, port)` method to `RemoteShellViewModel`. This allows the app to populate connection details using just the target IP and port, which is what we have in the Probe section.

### 2. Host Probe Screen Enhancements
- **UI Update**: In `HostProbeScreen.kt`, I modified the `PortResultCard` to display the **Terminal icon** next to the port status if the port is `22`.
- **Navigation Hook**: Added an `onSshClick` callback to `HostProbeScreen` and propagated it down to the individual port result cards.

### 3. Integration in MainActivity
- Connected the `onSshClick` event from the `HostProbeScreen` to the `RemoteShellViewModel`.
- When the icon is tapped, the app calls `shellViewModel.prefill(ip, 22)` and navigates to the **Shell** tab (index 4).

## How to Verify

1.  Navigate to the **Probe** tab.
2.  Enter an IP address of a device known to have SSH open (port 22).
3.  Run a **Quick Probe** or **Aggressive Probe**.
4.  Once the scan finds port 22, look for the **Terminal icon** next to the "PORT 22 (ssh)" entry.
5.  Tap the icon. The app will switch to the **Shell** tab with the host and port already filled in.

> [!TIP]
> This completes the integration of the SSH workflow across both the **Discovery** and **Probe** sections of NetProbe, providing multiple entry points for remote management.
