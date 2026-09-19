# Network HTTP Client (http.fetch)

The Network API provides native, CORS-free asynchronous HTTP and HTTPS client requests from within the plugin sandbox.

**Required Permission**: `"network"` (or `"internet"`).

---

## Overview and Security Sandbox

Dioxamine's plugin runner operates under a hermetic security sandbox:
- **Direct WebView Network Calls are Blocked**: All external resource loading via standard DOM elements (`<script src="...">`, `<img src="...">`, etc.) as well as browser-native `window.fetch()` or `XMLHttpRequest` are intercepted and rejected with `403 Forbidden`. The WebView engine itself is restricted to loading only local bundled plugin assets (`https://appassets.androidplatform.net/plugin/...`).
- **Bridge-Mediated Network Access**: To communicate with external servers, APIs, or local network daemons, plugins must use the native `window.dioxamine.http.fetch()` API.
- **Permission Gate**: The first time a plugin invokes `dioxamine.http.fetch()`, Dioxamine halts execution and presents an interactive consent dialog to the user. Once approved, the request is executed natively by Android's network stack without CORS restrictions.

---

## `dioxamine.http.fetch()`

Performs an asynchronous HTTP/HTTPS request using the native Android network client.

```javascript
dioxamine.http.fetch(url: string, options?: FetchOptions): Promise<HttpResponse>
```

### Parameters

- `url` (`string`): Target URL. Must begin with `http://` or `https://`. Schemes such as `file://`, `content://`, or `jar://` are strictly rejected.
- `options` (`object`, optional): Request configuration:
  - `method` (`string`, optional): HTTP request method (`'GET'`, `'POST'`, `'PUT'`, `'PATCH'`, `'DELETE'`, `'HEAD'`). Default: `'GET'`.
  - `headers` (`Record<string, string>`, optional): Key-value map of request headers (e.g. `{ "Content-Type": "application/json", "Authorization": "Bearer ..." }`). Default: `{}`.
  - `body` (`string`, optional): Stringified payload to transmit in the request body for `POST`, `PUT`, or `PATCH`. Default: `null`.
  - `timeoutMs` (`number`, optional): Connection and read timeout in milliseconds (clamped between `1000` and `60000`). Default: `15000` (15 seconds).

### Returns

A `Promise` resolving to an `HttpResponse` object:

- `status` (`number`): The HTTP response status code (e.g. `200`, `201`, `404`, `500`).
- `statusText` (`string`): The HTTP status message (e.g. `'OK'`, `'Created'`, `'Not Found'`).
- `headers` (`Record<string, string>`): Map of response headers returned by the server.
- `data` (`string`): The raw text content of the response body.
- `text()` (`() => Promise<string>`): Helper method returning a Promise that resolves with the response body text.
- `json()` (`() => Promise<any>`): Helper method returning a Promise that parses and resolves the response body as JSON.

---

## Examples

### 1. Basic GET Request (Fetching JSON)

```javascript
async function fetchLatestRelease() {
    try {
        const response = await dioxamine.http.fetch("https://api.github.com/repos/rhythmcache/dioxamine/releases/latest", {
            headers: {
                "User-Agent": "Dioxamine-Plugin"
            }
        });

        if (response.status === 200) {
            const data = await response.json();
            console.log("Latest release tag:", data.tag_name);
            dioxamine.showToast(`Latest version: ${data.tag_name}`, "short");
        } else {
            console.error(`Request failed with status ${response.status}: ${response.statusText}`);
        }
    } catch (err) {
        console.error("Network error:", err.message);
        dioxamine.showToast(`Fetch error: ${err.message}`, "long");
    }
}
```

### 2. POST Request with JSON Body

```javascript
async function sendTelemetry(eventData) {
    try {
        const response = await dioxamine.http.fetch("https://example.com/api/telemetry", {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
                "Accept": "application/json"
            },
            body: JSON.stringify(eventData),
            timeoutMs: 10000
        });

        const result = await response.json();
        console.log("Telemetry response:", result);
    } catch (err) {
        console.error("Failed to post telemetry:", err);
    }
}
```

### 3. Communicating with Local Services & ADB Forward Daemons

Because Dioxamine plugins frequently interact with local tools, device daemons, or router administration panels, `dioxamine.http.fetch()` permits connections to local network IP addresses (`192.168.x.x`, `10.x.x.x`) and loopback (`127.0.0.1`):

```javascript
// Forward local port 8080 to an on-device web service running on port 8080
await dioxamine.forwardPort("tcp:8080", "tcp:8080");

// Query the on-device web service via loopback
const res = await dioxamine.http.fetch("http://127.0.0.1:8080/status", {
    timeoutMs: 5000
});

console.log("Device service status:", res.data);
```

---

## Security Safeguards

1. **Permission Gating**: Calling `dioxamine.http.fetch()` without `"network"` declared in `plugin.json` fails immediately. If declared, the user is presented with a native consent dialog on first use.
2. **Protocol Whitelist**: Only `http://` and `https://` URLs are permitted. Internal Android `file://`, `content://`, and `jar://` schemes are strictly prohibited to prevent unauthorized access to local device files.
3. **Cloud Metadata Protection**: Requests directed to link-local cloud metadata endpoints (`169.254.169.254` and `metadata.google.internal`) are blocked to safeguard cloud-hosted testing environments.
4. **Streaming Transmission**: Request payloads are streamed using fixed-length encoding to prevent memory bloat during large transfers.
