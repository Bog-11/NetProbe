# Implementation Plan - App Brightness Control

This plan introduces a feature to boost screen brightness while using NetProbe to improve UI legibility.

## Proposed Changes

### 1. App Settings State
We need a way to track the user's preference for brightness.

#### [MODIFY] [NetworkOverview.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/model/NetworkOverview.kt)
*   Add `isHighBrightnessEnabled: Boolean` to the `NetworkOverview` state.

### 2. Visibility Booster Logic
Implement the window override in the Activity.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/MainActivity.kt)
*   Observe the `isHighBrightnessEnabled` state from the ViewModel.
*   Update `window.attributes.screenBrightness`:
    *   Set to `1.0f` when enabled.
    *   Set to `-1.0f` (default) when disabled.

### 3. User Interface
Add a control to let the user toggle the mode.

#### [MODIFY] [NetworkViewModel.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/viewmodel/NetworkViewModel.kt)
*   Add `toggleHighBrightness()` method.

#### [MODIFY] [NetworkOverviewScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/NetworkOverviewScreen.kt)
*   Add a new "Visual Preferences" section or a simple toggle switch in the **MORE DETAILS** section or a top-level chip.
*   Label: "Boost Brightness".

## Verification Plan

### Manual Verification
1.  Open the app.
2.  Enable "Boost Brightness".
3.  Observe the screen becoming significantly brighter.
4.  Switch to another app (e.g., Home screen) and verify brightness returns to normal.
5.  Switch back to NetProbe and verify it is still bright.
6.  Disable the toggle and verify it returns to system levels.
