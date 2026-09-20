# UI Controls, Dialogs and Fullscreen

The UI API allows plugins to trigger native Android toast notifications, display blocking confirmation dialogs, and toggle edge-to-edge full-screen mode.

**Required Permissions**: None (Safe native UI methods).

---

## Native Toast Notifications

### `dioxamine.showToast()`

Displays a native Android toast popup message.

```javascript
dioxamine.showToast(message: string, duration?: 'short' | 'long'): void
```

#### Parameters
- `message` (`string`): The text to display.
- `duration` (`string`, optional): Either `'short'` (2 seconds) or `'long'` (3.5 seconds). Default: `'short'`.

#### Example
```javascript
dioxamine.showToast("Settings saved successfully", "short");
```

---

## Native Material Confirmation Dialogs

### `dioxamine.showDialog()`

Displays a native Material 3 alert dialog and waits for user button selection.

```javascript
dioxamine.showDialog(options: {
    title: string;
    message: string;
    buttons?: string[];
}): Promise<{ buttonIndex: number }>
```

#### Parameters
- `title` (`string`): Dialog heading text.
- `message` (`string`): Descriptive dialog body text.
- `buttons` (`string[]`, optional): Array of button labels in order. Default: `['OK']`.

#### Returns
A `Promise` resolving to `{ buttonIndex: number }`, where `buttonIndex` corresponds to the clicked button index (0-based). If dismissed without clicking a button, returns `-1`.

#### Example
```javascript
async function confirmReboot() {
    const res = await dioxamine.showDialog({
        title: "Reboot Device",
        message: "Are you sure you want to reboot the target device into Recovery mode?",
        buttons: ["Cancel", "Reboot to Recovery"]
    });

    if (res.buttonIndex === 1) {
        await dioxamine.shellExec("reboot recovery");
        dioxamine.showToast("Rebooting...", "short");
    }
}
```

---

## Fullscreen Controls

### `dioxamine.setFullScreen()` / `dioxamine.fullScreen()`

Dynamically toggles whether Dioxamine's top bar is visible or hidden, giving the plugin the entire screen.

```javascript
dioxamine.setFullScreen(enable: boolean): void
```

#### Parameters
- `enable` (`boolean`): Pass `true` to enter full-screen mode (hide top bar) or `false` to restore the windowed top bar.

#### Note
`dioxamine.fullScreen(enable)` is also supported as an alias for `dioxamine.setFullScreen(enable)`.

#### Example
```javascript
// Toggle fullscreen based on user button click
let isFull = false;

document.getElementById('toggle-fullscreen-btn').addEventListener('click', () => {
    isFull = !isFull;
    dioxamine.setFullScreen(isFull);
});
```

---

## Exiting and Closing Plugins

### 1. System Back Gesture / Back Button
Users can exit any plugin at any time by performing the standard Android **Back gesture** (swiping from the left/right screen edge) or pressing the system **Back button**.

### 2. Programmatic Exit (`dioxamine.exitPlugin()`)
Plugins can also provide an in-app "Exit" or "Close" button in their web UI:

```javascript
dioxamine.exitPlugin(): void
```

#### Example
```javascript
document.getElementById('close-btn').addEventListener('click', () => {
    dioxamine.exitPlugin();
});
```

#### Note
`dioxamine.closePlugin()` is also supported as an alias.

---

## Opening External Links

### `dioxamine.openBrowser()` / `dioxamine.openUrl()`

Opens an external web URL directly in the default web browser of the host Android device running Dioxamine (not on the connected ADB device).

To protect user safety and prevent unauthorized redirects or phishing attacks, Dioxamine presents a native confirmation dialog to the user displaying the target URL and origin plugin name before launching the browser.

```javascript
dioxamine.openBrowser(url: string): Promise<{ success: boolean }>
```

#### Parameters
- `url` (`string`): The web address to open. Must begin with `http://` or `https://` and have a valid host (maximum length: 2048 characters).

#### Returns
A `Promise` resolving to `{ success: true }` when the user approves the prompt and the browser is launched. If the user dismisses or clicks **Cancel**, the promise rejects with `"User cancelled opening external link"`. If no browser application is available or the URL format is invalid, it rejects with a descriptive error.

#### Example
```javascript
// Open external documentation or repository in host device browser
document.getElementById('docs-link').addEventListener('click', () => {
    dioxamine.openBrowser("https://example.com/docs")
        .then(() => {
            console.log("Browser opened successfully");
        })
        .catch(err => {
            if (err.message.includes("cancelled")) {
                console.log("User cancelled browser launch");
            } else {
                dioxamine.showToast("Failed to open browser: " + err.message, "short");
            }
        });
});
```

#### Note
`dioxamine.openUrl(url)` is also supported as an alias.
