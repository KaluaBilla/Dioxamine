# JavaScript Bridge API Reference

The global `dioxamine` object provides asynchronous methods for interacting with connected devices, native dialogs, file systems, and the Android host. Device operations are organized into dedicated namespaces:
- **`dioxamine.adb.*`**: ADB device operations (shell, sync, port forwarding).
- **`dioxamine.fastboot.*`**: Fastboot bootloader operations (coming soon).
- **`dioxamine.*`**: Common UI dialogs, file pickers, theming, logging, and HTTP client.

## Bridge Initialization

The native bridge is loaded automatically before any plugin scripts execute. To guarantee readiness across all initialization states, listen for the `dioxamine-bridge-ready` event:

```javascript
function initPlugin() {
    console.log("Dioxamine Bridge is ready:", window.dioxamine);
}

if (window.dioxamine && window.__dioxamine_bridge_ready) {
    initPlugin();
} else {
    window.addEventListener('dioxamine-bridge-ready', initPlugin, { once: true });
}
```

## API Modules

The API is organized into the following specialized modules:

1. **[ADB Device Management](api/device.md)**: Query the active ADB connection status via `dioxamine.adb.getActiveDevice()`.
2. **[ADB Single Command Execution](api/shell.md)**: Run non-interactive commands via `dioxamine.adb.shellExec()`.
3. **[ADB Interactive Shell Sessions](api/interactive-shell.md)**: Open persistent bi-directional PTY streams via `dioxamine.adb.openInteractiveShell()`.
4. **[File Operations](api/files.md)**: Pull/push files via `dioxamine.adb.pull()` / `dioxamine.adb.push()`, and open system file pickers via `dioxamine.requestFilePicker()`.
5. **[ADB Port Forwarding and Reverse](api/port-forwarding.md)**: Manage TCP socket forwarding via `dioxamine.adb.forwardAdd()` / `reverseAdd()`.
6. **[UI Controls, Dialogs and Fullscreen](api/ui.md)**: Show native Material toasts, blocking dialogs, and toggle edge-to-edge full-screen mode.
7. **[Native Logging and Debugging](api/logging.md)**: Forward logs and console output directly to Android Logcat.
8. **[Dynamic Theming and Material 3](api/theming.md)**: Integrate with Dioxamine's dynamic color schemes and listen for theme changes.
9. **[Network HTTP Client (http.fetch)](api/network.md)**: Perform asynchronous HTTP/HTTPS network requests with native CORS-free execution and permission gating.
