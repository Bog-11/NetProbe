# Remote Shell "PC Terminal" Redesign Walkthrough

I have redesigned the **Remote Shell** section to provide a more professional, prompt-driven "PC Terminal" experience.

## Key Enhancements

### 1. Read-Only Target Reference
- **Reference Card:** When you navigate to the Shell from a discovery result, the target IP and Port now appear in a subtle card at the top as **Discovered Target**.
- **Non-Intrusive:** The form fields (Host, User) are no longer automatically pre-filled, giving you a clean slate.
- **Convenience:** I added a **"Use Info"** button to the reference card that you can tap to quickly copy the discovered IP and Port into the form if you choose.

### 2. Integrated Terminal Prompt
- **Seamless Input:** The command input field has been moved **inside** the black console window.
- **Prompt Style:** It now features a `$ ` prompt prefix and monospaced typography to mimic a real Linux terminal.
- **Auto-Scrolling:** The console now automatically scrolls to the bottom whenever you type or new output arrives, ensuring your "cursor" is always visible.

### 3. Streamlined Form
- The standalone "Custom Command" row has been removed in favor of the integrated terminal prompt.
- The connection parameters (Host, User, Password) remain at the top for easy profile switching.

## How to Use

1.  **Select a Target:** Probe a device or find one in Discovery.
2.  **Navigate to Shell:** Tap the Terminal icon.
3.  **Setup Connection:** Notice the target IP at the top. Tap **"Use Info"** or manually type your connection details.
4.  **Execute:** Tap inside the black console at the bottom (where it says `type command here...`).
5.  **Run:** Type your command and hit **Enter** on your keyboard.

> [!TIP]
> This new layout is designed to handle multiple commands efficiently. You can enter your credentials once and then interact purely with the console prompt at the bottom.

> [!IMPORTANT]
> Errors like "Authentication Failed" will still appear in red directly inside the console history, making troubleshooting much faster.
