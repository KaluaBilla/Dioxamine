# Port Forwarding and Reverse API

The Port Forwarding and Reverse API allows plugins to route TCP network traffic between the host Android device and the target ADB device.

## Port Forwarding (`adb forward`)

Forwarding redirects connections made to a socket on the host to a socket on the connected device.

**Required Permission**: `"forward"`

### `dioxamine.adb.forwardAdd()`
Binds a local host socket and routes incoming connections to a remote target socket.

```javascript
dioxamine.adb.forwardAdd(local: string, remote: string): Promise<void>
```

**Parameters:**
- `local` (`string`): Local host specification (for example, `"tcp:8080"`).
- `remote` (`string`): Remote device specification (for example, `"tcp:8080"` or `"localabstract:scrcpy"`).

### `dioxamine.adb.forwardRemove()`
Removes an active port forward binding.

```javascript
dioxamine.adb.forwardRemove(local: string): Promise<void>
```

---

## Port Reverse (`adb reverse`)

Reversing redirects connections made to a socket on the connected device back to a socket on the host device.

**Required Permission**: `"reverse"`

### `dioxamine.adb.reverseAdd()`
Binds a remote device socket and routes connections back to a local host socket.

```javascript
dioxamine.adb.reverseAdd(remote: string, local: string): Promise<void>
```

**Parameters:**
- `remote` (`string`): Remote device specification (for example, `"tcp:3000"`).
- `local` (`string`): Local host specification (for example, `"tcp:3000"`).

### `dioxamine.adb.reverseRemove()`
Removes an active reverse socket binding.

```javascript
dioxamine.adb.reverseRemove(remote: string): Promise<void>
```

---

## Automatic Session Cleanup

All port forward and reverse mappings created during a plugin session are automatically closed and cleaned up by the native runtime when the plugin is closed or disposed.
