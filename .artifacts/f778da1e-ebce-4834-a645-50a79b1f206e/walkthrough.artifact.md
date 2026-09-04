# Walkthrough - Unified Details UI

I have updated the advanced details section on the Overview page to match the visual style of the main internet connection window and renamed it to "MORE DETAILS".

## Changes Made

### [NetworkOverviewScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/NetworkOverviewScreen.kt)

- **Renamed Header**: Changed "NETWORK DIAGNOSTICS" to "MORE DETAILS" to better describe the expandable content.
- **Unified Design Theme**:
    - Updated the details card to use the same `primaryContainer` background with 15% alpha as the top summary card.
    - Matched the border style with a 1.5.dp `primary` green border at 25% alpha.
    - Updated internal dividers to use the `primary` theme for a more cohesive look.

```diff
-            Text(
-                text = "NETWORK DIAGNOSTICS",
-                style = MaterialTheme.typography.labelLarge,
-                color = MaterialTheme.colorScheme.primary,
-                fontWeight = FontWeight.Bold,
-                letterSpacing = 1.2.sp
-            )
+            Text(
+                text = "MORE DETAILS",
+                style = MaterialTheme.typography.labelLarge,
+                color = MaterialTheme.colorScheme.primary,
+                fontWeight = FontWeight.Bold,
+                letterSpacing = 1.2.sp
+            )
```

## Verification Results

### Manual Verification
- Navigated to the Overview tab.
- Confirmed the header is now "MORE DETAILS".
- Verified that the card background and borders now perfectly match the main "Connected" window at the top, creating a unified and polished look for the overview page.
