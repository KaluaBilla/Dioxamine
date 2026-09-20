# File Operations and Storage API

Dioxamine provides methods to push and pull files between the host and connected ADB device using Android's Storage Access Framework (SAF). This allows streaming large files (such as backups, ROMs, or APKs) directly without loading entire payloads into JavaScript memory.

## Storage Access Framework (SAF) File Pickers

Because plugins run in a sandboxed WebView, they cannot directly access arbitrary paths on the host filesystem. Instead, Dioxamine bridges Android's native system file picker dialogs.

**No special manifest permission is required for SAF pickers.**

### `dioxamine.requestFilePicker()`

Prompts the user to pick a file to open or select a destination to save/create.

```javascript
dioxamine.requestFilePicker(mode: "open" | "create"): Promise<{ requestId: string }>
```

**Modes:**
- `"open"`: Opens the Android document picker for the user to choose an existing file on the host. Returns a `requestId` to use with `dioxamine.adb.push()`.
- `"create"`: Opens the Android document creator for the user to choose where to save a file. Returns a `requestId` to use with `dioxamine.adb.pull()`.

---

## File Pull (`adb pull`)

Downloads a file from the connected ADB device and writes it directly into the selected SAF destination.

**Required Permission**: `"pull"`

### `dioxamine.adb.pull()`

```javascript
dioxamine.adb.pull(remotePath: string, safRequestId: string): Promise<{ bytesTransferred: number }>
```

### Parameters:
- `remotePath` (`string`): Source file path on the connected device (e.g. `"/sdcard/Download/screencap.png"`).
- `safRequestId` (`string`): The request ID obtained from `dioxamine.requestFilePicker("create")`.

### Example:
```javascript
async function downloadFileFromDevice(remotePath) {
    // 1. Prompt user to select save location on Android host
    const picker = await dioxamine.requestFilePicker("create");
    
    // 2. Stream directly from device to chosen SAF destination
    const result = await dioxamine.adb.pull(remotePath, picker.requestId);
    console.log(`Transferred ${result.bytesTransferred} bytes`);
}
```

---

## File Push (`adb push`)

Uploads a file chosen via SAF from the host directly to the target path on the connected ADB device.

**Required Permission**: `"push"`

### `dioxamine.adb.push()`

```javascript
dioxamine.adb.push(localSafRequestId: string, remotePath: string): Promise<{ bytesTransferred: number }>
```

### Parameters:
- `localSafRequestId` (`string`): The request ID obtained from `dioxamine.requestFilePicker("open")`.
- `remotePath` (`string`): Destination path on the target device (e.g. `"/sdcard/Download/archive.zip"`).

### Example:
```javascript
async function uploadFileToDevice(remotePath) {
    // 1. Prompt user to pick file from Android host
    const picker = await dioxamine.requestFilePicker("open");
    
    // 2. Stream directly from SAF file to target device
    const result = await dioxamine.adb.push(picker.requestId, remotePath);
    console.log(`Uploaded ${result.bytesTransferred} bytes`);
}
```
