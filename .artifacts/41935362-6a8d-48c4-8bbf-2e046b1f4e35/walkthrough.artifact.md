# Walkthrough - Confirmation for Clear All Connections

I have added a confirmation dialog to the "Clear All" action in the Live Traffic Monitor to prevent accidental data loss.

## Changes

### Live Traffic Monitor UI

#### [LiveConnectionsScreen.kt](file:///C:/Users/bogda/AndroidStudioProjects/NetProbe/app/src/main/java/com/brutiful/netprobe/ui/LiveConnectionsScreen.kt)

- **State Management**: Introduced `showClearConfirmation` state to track whether the confirmation dialog should be displayed.
- **Confirmation Dialog**: Added an `AlertDialog` that prompts the user with "Are you sure?" before clearing the connections.
- **Button Interaction**: Updated the `IconButton` (trash icon) to trigger the dialog instead of performing the clear operation immediately.

```diff
+    var showClearConfirmation by remember { mutableStateOf(false) }

...

+    if (showClearConfirmation) {
+        AlertDialog(
+            onDismissRequest = { showClearConfirmation = false },
+            title = { Text("Clear All Connections?") },
+            text = { Text("Are you sure you want to clear all captured traffic data? This action cannot be undone.") },
+            confirmButton = {
+                Button(
+                    onClick = {
+                        ConnectionTracker.clearAll()
+                        showClearConfirmation = false
+                    },
+                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
+                ) {
+                    Text("Clear")
+                }
+            },
+            dismissButton = {
+                TextButton(onClick = { showClearConfirmation = false }) {
+                    Text("Cancel")
+                }
+            }
+        )
+    }

...

-                    IconButton(onClick = { ConnectionTracker.clearAll() }) {
+                    IconButton(onClick = { showClearConfirmation = true }) {
                         Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = MaterialTheme.colorScheme.error)
                     }
```

## Verification Results

### Automated Tests
- Ran `analyze_file` on `LiveConnectionsScreen.kt` to ensure no syntax errors were introduced.

### Manual Verification (Expected behavior)
1. User clicks the red trash icon in the top right of the Live Traffic Monitor.
2. A dialog appears asking "Clear All Connections? Are you sure...".
3. User clicks "Cancel" -> Dialog closes, data remains.
4. User clicks "Clear" -> Dialog closes, all captured connections are removed.
