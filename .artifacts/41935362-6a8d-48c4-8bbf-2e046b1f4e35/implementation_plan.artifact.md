# Implementation Plan - Add Confirmation for Clear All in Live Connections

Add a confirmation dialog ("Are you sure?") when the user presses the "Clear All" (trash) button in the Live Connections screen to prevent accidental deletion of captured traffic data.

## Proposed Changes

### [Component Name] UI

#### [MODIFY] [LiveConnectionsScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/LiveConnectionsScreen.kt)

- Add `showClearConfirmation` state using `remember { mutableStateOf(false) }`.
- Update the `IconButton` for `DeleteSweep` to trigger the confirmation dialog instead of clearing directly.
- Implement a `ClearConfirmationDialog` private composable (or use a standard `AlertDialog` directly).

## Verification Plan

### Manual Verification
- Deploy the app.
- Go to the Live Traffic Monitor (Live Connections screen).
- Capture some traffic.
- Press the red trash button.
- Verify that a confirmation dialog appears with "Are you sure?" message.
- Verify that pressing "Cancel" does nothing.
- Verify that pressing "Clear" (or "Confirm") clears the connections.
