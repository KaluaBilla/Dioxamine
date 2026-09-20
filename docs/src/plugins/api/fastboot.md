# Fastboot Protocol API

The Fastboot API provides direct communication with Android devices in bootloader / Fastboot mode over USB.

Plugins can inspect device variables, run raw wire-format commands, flash partitions from SAF-picked images with real-time progress callbacks, live boot images directly into RAM, switch active slots, and reboot.

## Permission Requirement

All methods under `dioxamine.fastboot.*` require the `"fastboot"` permission declared in `plugin.json`:

```json
{
  "permissions": {
    "fastboot": [
      "fastboot"
    ],
    "common": [
      "saf"
    ]
  }
}
```

When invoked, Dioxamine prompts the user once to authorize Fastboot USB access. Once authorized, the plugin has direct, unrestricted bootloader access.

---

## 1. Device Presence

### `dioxamine.fastboot.getActiveDevice()`

Checks whether a device is connected in Fastboot mode over USB.

#### Signature
```typescript
dioxamine.fastboot.getActiveDevice(): Promise<FastbootDeviceStatus | null>
```

#### Return Type
```typescript
interface FastbootDeviceStatus {
    connected: boolean;
    id?: string;         // e.g. "fastboot:XXXXXXXX"
    label?: string;      // Device product name or serial
    deviceName?: string; // USB sysfs path (e.g. "/dev/bus/usb/001/002")
}
```

#### Example
```javascript
const device = await dioxamine.fastboot.getActiveDevice();
if (!device || !device.connected) {
    console.error("No Fastboot device connected. Connect device via USB OTG in bootloader mode.");
    return;
}
console.log(`Connected to ${device.label} (${device.id})`);
```

---

## 2. Bootloader Variables

### `dioxamine.fastboot.getVariable(name)` (alias: `getVar`)

Queries a single bootloader variable (sends `getvar:<name>`).

#### Signature
```typescript
dioxamine.fastboot.getVariable(name: string): Promise<string>
```

#### Example
```javascript
const product = await dioxamine.fastboot.getVariable("product");
const currentSlot = await dioxamine.fastboot.getVariable("current-slot");
const unlocked = await dioxamine.fastboot.getVariable("unlocked");

console.log(`Device: ${product}, Current Slot: ${currentSlot}, Unlocked: ${unlocked}`);
```

### `dioxamine.fastboot.getAllVariables()` (alias: `getAllVars`)

Queries all variables reported by the bootloader (sends `getvar:all`).

#### Signature
```typescript
dioxamine.fastboot.getAllVariables(): Promise<Record<string, string>>
```

#### Example
```javascript
const vars = await dioxamine.fastboot.getAllVariables();
for (const [key, value] of Object.entries(vars)) {
    console.log(`${key}: ${value}`);
}
```

---

## 3. Raw Protocol Commands

### `dioxamine.fastboot.rawCommand(command)`

Executes any raw wire-protocol Fastboot command. Useful for OEM-specific commands (`oem ...`), partition management, or any wire command not wrapped by helper methods.

#### Signature
```typescript
dioxamine.fastboot.rawCommand(command: string): Promise<{
    response: string;
    info: string[];
}>
```

- `response`: Final response string sent by the device (e.g. status or output).
- `info`: Array of intermediate `INFO` lines returned by the device during execution.

#### Example
```javascript
// Run OEM device info command
const result = await dioxamine.fastboot.rawCommand("oem device-info");
console.log("Device info response:", result.response);
result.info.forEach(line => console.log("INFO:", line));
```

---

## 4. Partition Operations

### `dioxamine.fastboot.erase(partition)`

Erases a partition on the device (sends `erase:<partition>`).

#### Signature
```typescript
dioxamine.fastboot.erase(partition: string): Promise<{
    response: string;
    info: string[];
}>
```

#### Example
```javascript
await dioxamine.fastboot.erase("cache");
console.log("Cache partition erased");
```

### `dioxamine.fastboot.setActiveSlot(slot)`

Switches the active boot slot on A/B devices (sends `set_active:<slot>`).

#### Signature
```typescript
dioxamine.fastboot.setActiveSlot(slot: string): Promise<{
    response: string;
    info: string[];
}>
```

#### Example
```javascript
await dioxamine.fastboot.setActiveSlot("b");
console.log("Switched active slot to B");
```

---

## 5. Flashing & Booting Images (SAF Integration)

Both `flash` and `boot` accept a `safRequestId` obtained from `dioxamine.requestFilePicker("open")`. The file stream is passed directly to the device over USB bulk endpoints with zero intermediate buffering.

### `dioxamine.fastboot.flash(partition, safRequestId, onProgress?)`

Flashes an image picked by the user into the specified partition.

#### Signature
```typescript
dioxamine.fastboot.flash(
    partition: string,
    safRequestId: string,
    onProgress?: (progress: { current: number; total: number; percentage: number }) => void
): Promise<{
    success: boolean;
    bytesTransferred: number;
}>
```

#### Example
```javascript
// 1. Prompt user to select image
const file = await dioxamine.requestFilePicker("open");

// 2. Flash with progress tracking
await dioxamine.fastboot.flash("boot", file.requestId, (p) => {
    console.log(`Flashing boot: ${p.percentage}% (${p.current} / ${p.total} bytes)`);
});

console.log("Flashing complete!");
```

### `dioxamine.fastboot.boot(safRequestId, onProgress?)`

Downloads an image picked by the user into device RAM and boots it immediately without writing to flash memory.

#### Signature
```typescript
dioxamine.fastboot.boot(
    safRequestId: string,
    onProgress?: (progress: { current: number; total: number; percentage: number }) => void
): Promise<{
    success: boolean;
    bytesTransferred: number;
}>
```

#### Example
```javascript
const file = await dioxamine.requestFilePicker("open");

await dioxamine.fastboot.boot(file.requestId, (p) => {
    console.log(`Uploading kernel image: ${p.percentage}%`);
});

console.log("Device is booting image...");
```

---

## 6. Power & Lifecycle Controls

### `dioxamine.fastboot.reboot(target?)`

Reboots the device.

#### Signature
```typescript
dioxamine.fastboot.reboot(target?: "system" | "bootloader" | "recovery" | "fastboot" | string): Promise<{ success: boolean }>
```

- Default: `"system"` (normal device boot).
- Targets: `"bootloader"`, `"recovery"`, `"fastboot"`, or custom hardware targets (e.g. `"edl"`).

#### Example
```javascript
// Reboot to recovery
await dioxamine.fastboot.reboot("recovery");

// Normal reboot to system
await dioxamine.fastboot.reboot();
```

### `dioxamine.fastboot.continueBoot()`

Resumes normal boot sequence without resetting (sends `continue`).

```javascript
await dioxamine.fastboot.continueBoot();
```

### `dioxamine.fastboot.shutdown()`

Powers down the connected device (sends `shutdown`).

```javascript
await dioxamine.fastboot.shutdown();
```

### `dioxamine.fastboot.setLockMode(mode)`

Changes bootloader lock state (sends `flashing <mode>`).

#### Signature
```typescript
dioxamine.fastboot.setLockMode(mode: "lock" | "unlock" | "lock_critical" | "unlock_critical"): Promise<{
    response: string;
    info: string[];
}>
```

```javascript
await dioxamine.fastboot.setLockMode("unlock");
```
