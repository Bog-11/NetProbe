# Implementation Plan - Unified Details UI

Update the `AdvancedDetailsSection` in `NetworkOverviewScreen.kt` to match the visual style of the `MainSummaryCard` and rename the section header.

## Proposed Changes

### UI Components

#### [MODIFY] [NetworkOverviewScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/NetworkOverviewScreen.kt)

- **Header Text**: Rename "NETWORK DIAGNOSTICS" to "MORE DETAILS".
- **Card Styling**: Update the `Card` in `AdvancedDetailsSection` to match `MainSummaryCard`:
    - Set `containerColor` to `MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)`.
    - Set `border` to `BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))`.
- **Divider Styling**: Update `HorizontalDivider` to use `MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)` to stay consistent with the primary-themed card.

## Verification Plan

### Manual Verification
- Deploy the app to a device or emulator.
- Navigate to the Overview screen.
- Verify that the bottom expandable section is now titled "MORE DETAILS".
- Verify that the card background and border match the "Connected to Wi-Fi/Cellular" window at the top.
