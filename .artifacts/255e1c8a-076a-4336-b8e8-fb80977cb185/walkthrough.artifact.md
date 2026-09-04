# UI Improvement: Animated Traceroute Loading

I have replaced the linear progress bar in the Traceroute report with a more thematic "moving dots" loading indicator.

## Changes Made

### 🎨 UI Improvements

#### [HostProbeScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/HostProbeScreen.kt)
- **Removed `LinearProgressIndicator`**: The standard progress bar was removed from the `TracerouteCard` to reduce visual clutter.
- **Added Animated "Tracing path..." Indicator**: Implemented a custom animation using `rememberInfiniteTransition` that cycles through "Tracing path.", "Tracing path..", and "Tracing path...".
- **Code Cleanup**: Removed the unused `progress` parameter from the `TracerouteCard` function and its call site.

## Verification Results

### Automated Tests
- ✅ Build successful: `app:assembleDebug` completed without errors.

### Manual Verification Required
- [ ] **Traceroute Animation**: Start a traceroute and verify that the "Tracing path..." text animates correctly with moving dots.
- [ ] **Clean Layout**: Ensure the progress bar is gone and the text fits well within the `TracerouteCard` when expanded.

render_diffs(file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/HostProbeScreen.kt)
