# App Identification Implementation Plan

This plan implements automatic application identification for network connections using the `ConnectivityManager.getConnectionOwnerUid` API (Android 10+). This will replace "Unknown app" labels with real application names and icons.

## User Review Required

> [!NOTE]
> **Dynamic Icon Loading**: Application icons will not be stored in the database to keep it lightweight. Instead, they will be loaded dynamically in the UI using the `packageName`.

## Proposed Changes

### 1. Logic Layer: Connection Ownership Resolution
#### [NEW] [ConnectionOwnerResolver.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/network/ConnectionOwnerResolver.kt)
- Create a dedicated class for resolving connection owners.
- Implement `getAppInfo` using `ConnectivityManager.getConnectionOwnerUid`.
- Include `AppMetadata` data class (Label, Package Name, Icon).
- Implement a `ConcurrentHashMap` for memoization of UID to metadata.

#### [MODIFY] [ConnectionTracker.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/network/ConnectionTracker.kt)
- Integrate `ConnectionOwnerResolver`.
- Remove legacy `resolveUid` and `resolveAppInfo` methods.
- Update `updateConnection` to use the new resolver.

### 2. UI Layer: Displaying App Metadata
#### [MODIFY] [LiveConnectionsScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/LiveConnectionsScreen.kt)
- Create an `AppIcon` composable that loads the icon from `PackageManager` using the `packageName`.
- Update `ConnectionCard` to include the application icon next to the app label.

## Verification Plan

### Automated Tests
- Build the project to ensure no compilation errors.

### Manual Verification
- Start the VPN Monitor.
- Open several apps (e.g., Browser, YouTube, Gmail).
- Verify that the "Live Traffic Monitor" correctly identifies these apps and displays their official names and icons.
- Verify that the identification is stable and cached (no UI flickering when new packets arrive for the same session).
