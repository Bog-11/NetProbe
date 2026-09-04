# Implementation Plan - Discovery Screen Redesign

This plan outlines a complete redesign of the `DiscoveryScreen` to provide a unified, performant, and intuitive discovery experience centered around a single scrollable result surface.

## User Review Required

> [!IMPORTANT]
> **Breaking UI Changes**: The current two-list vertical layout will be replaced by a segmented control. Users will toggle between "Network" and "Bluetooth" views.
> **Detailed View**: Tapping a device will now open a Bottom Sheet instead of expanding the row in-place.

## Proposed Changes

### Data & State Layer

#### [MODIFY] [DiscoveryViewModel.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/viewmodel/DiscoveryViewModel.kt)
*   Update `DiscoveryUiState` to include:
    *   `elapsedTimeSeconds: Int` (Timer for active scans)
    *   `lastScanTimestamp: Long?` (To show "Last scan time")
    *   `searchQuery: String`
    *   `selectedTab: DiscoveryTab` (Network vs Bluetooth)
    *   `sortBy: SortOption` (Name, IP, Signal, etc.)
*   Add logic to start/stop a 1-second interval timer during active scans.
*   Implement a derived state or function to return filtered and sorted lists based on UI state.

### UI Layer - Components

#### [NEW] [DiscoveryComponents.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/DiscoveryComponents.kt)
*   `ScanControlCard`: A unified card at the top showing the primary action, progress bar, and elapsed time.
*   `DiscoverySummaryBar`: Compact row showing metrics (Counts, Status, Last Time).
*   `DeviceResultRow`: A compact two-line row with a status dot and chevron.
*   `DeviceDetailBottomSheet`: A comprehensive detail view for all technical metadata.
*   `DiscoveryEmptyState`: Reusable component for "First Scan", "No Results", and "Error" states.

### Discovery Screen Redesign

#### [MODIFY] [DiscoveryScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/DiscoveryScreen.kt)
*   Remove all existing `LazyColumn` and card layouts.
*   Implement a single `Scaffold` with a compact `TopAppBar`.
*   Use one `LazyColumn` as the main content area:
    *   `item { ScanControlCard(...) }`
    *   `item { DiscoverySummaryBar(...) }`
    *   `stickyHeader { SegmentedControl(...) }`
    *   `item { SearchField(...) }` (Conditional)
    *   `items(devices) { DeviceResultRow(...) }` (Grouped with sticky headers for Known/New/Unreachable).

## Verification Plan

### Automated Tests
*   Verify that `DiscoveryViewModel` correctly calculates elapsed time.
*   Verify that sorting logic handles null IPs or names gracefully.

### Manual Verification
1.  **Initial State**: Verify the "First Scan" empty state is clear and has a single primary button.
2.  **Active Scan**: Verify the progress bar and timer update in real-time.
3.  **Navigation**: Toggle between Network and Bluetooth segments; verify the list swaps correctly.
4.  **Grouping**: Verify that devices are grouped (e.g., Unreachable devices appear in a separate group if present).
5.  **Details**: Tap a device and verify the Bottom Sheet appears with full technical details.
6.  **Search/Sort**: Filter a long list and verify performance (stable keys in LazyColumn).
