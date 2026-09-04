# Walkthrough - True Black OLED Optimization

I have updated the app's color palette to use pure black (#000000) for primary backgrounds and surfaces. This ensures that on OLED panels, the pixels will be completely turned off, saving battery and providing perfect contrast.

## Changes

### [Color Palette Update](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/theme/Color.kt)

- **Pure Black Backgrounds**: `MatrixBlack` and `MatrixSurface` have been changed to `0xFF000000`. This affects the main app background and primary surfaces.
- **Darker Variants**:
    - `MatrixSurfaceAlt` (used for card variants) was darkened to `0xFF080808` to maintain subtle separation while remaining extremely close to black.
    - The themed dark backgrounds (`MatrixRedDark`, `MatrixPurpleDark`, `MatrixBlueDark`) were significantly darkened to ensure they don't glow on OLED screens.

## Verification Results

### Automated Tests
- Build successfully completed with `./gradlew :app:compileDebugKotlin`.

### Visual Impact
> [!TIP]
> This change is most effective on devices with AMOLED or OLED screens. Users will notice deeper blacks and improved battery life when using the app.
