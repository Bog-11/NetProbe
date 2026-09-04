# Implementation Plan - True Black OLED Optimization

Update the app's color palette to use pure black (#000000) for backgrounds and surfaces to ensure pixels are completely turned off on OLED panels.

## Proposed Changes

### [Theming]

#### [MODIFY] [Color.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/theme/Color.kt)
- Update `MatrixBlack` from `0xFF05070A` to `0xFF000000`.
- Update `MatrixSurface` from `0xFF0B0F10` to `0xFF000000`.
- Update `MatrixSurfaceAlt` from `0xFF12181A` to a slightly lighter (but still very dark) grey or pure black. I'll stick with `0xFF000000` for `surface` and keep `surfaceVariant` (Alt) as a very dark grey for subtle separation in cards, or make everything black for the "True Black" effect.
- Considering the "pixels off" requirement, `MatrixBlack` and `MatrixSurface` should definitely be `0xFF000000`.
- I will also darken `MatrixRedDark`, `MatrixPurpleDark`, and `MatrixBlueDark` to be even closer to black, or keep them as is if they are only for card backgrounds that should have some tint, but the user said "black colors".
- Actually, I'll make `MatrixBlack`, `MatrixSurface`, and `MatrixSurfaceAlt` all `0xFF000000` or extremely close. The user wants the pixels off.

## Verification Plan

### Automated Tests
- Build verification: `./gradlew :app:compileDebugKotlin`.

### Manual Verification
- Visual check (if possible on device) to ensure backgrounds are pure black.
- Verify that text and other elements remain legible against the pure black background.
