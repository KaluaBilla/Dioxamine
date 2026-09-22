# App Language and Localization

Dioxamine allows plugins to integrate seamlessly with the host app's active language and locale settings. Whether the user selects a custom language in **Settings → Language** (English, Russian, Simplified Chinese, Hindi, German) or uses the system default, Dioxamine communicates the language configuration to the plugin via HTML attributes, DOM events, and JavaScript bridge APIs.

**Required Permissions**: None (Safe host environment information).

---

## Injected HTML Attributes

When a plugin loads or when the app's language/locale changes, Dioxamine updates the root `<html>` element with standard internationalization attributes:

```html
<html lang="zh-CN" dir="ltr" data-dioxamine-lang="zh-CN" data-dioxamine-theme="dark">
```

| Attribute | Example Values | Description |
| :--- | :--- | :--- |
| `lang` | `"en"`, `"zh-CN"`, `"ru"`, `"hi"`, `"de"` | The BCP-47 language tag of the active locale. |
| `dir` | `"ltr"`, `"rtl"` | Text direction. Automatically set to `"rtl"` for Right-to-Left scripts (Arabic, Hebrew, Urdu, etc.) and `"ltr"` otherwise. |
| `data-dioxamine-lang` | `"en"`, `"zh-CN"`, `"ru"` | Dioxamine custom data attribute for CSS attribute selectors. |

---

## Language Styling in CSS

Because standard `lang` and `dir` attributes are maintained on `<html>`, you can use native CSS pseudo-classes:

```css
/* Language-specific typography adjustments */
:lang(zh) {
    font-family: 'Noto Sans SC', sans-serif;
}

:lang(ru) {
    font-family: 'Roboto', sans-serif;
}

/* RTL layout handling */
[dir="rtl"] {
    text-align: right;
}

[dir="rtl"] .sidebar {
    left: auto;
    right: 0;
}
```

---

## JavaScript Localization APIs

### `dioxamine.getLanguage()` / `dioxamine.getLocale()`

Returns the current language and locale metadata synchronously.

```javascript
dioxamine.getLanguage(): {
    language: string;    // 2-letter ISO 639-1 code (e.g., "en", "zh", "ru", "hi", "de")
    languageTag: string; // Full BCP-47 language tag (e.g., "en", "zh-CN", "ru", "hi", "de")
    isRtl: boolean;      // true if RTL script, false if LTR
    displayName: string; // Canonical display name (e.g., "English", "简体中文", "Русский")
}
```

#### Example
```javascript
const lang = dioxamine.getLanguage();
console.log("Active language:", lang.language);       // "zh"
console.log("Language tag:", lang.languageTag);       // "zh-CN"
console.log("Is RTL?", lang.isRtl);                   // false
console.log("Display name:", lang.displayName);       // "简体中文"
```

> **Note**: `dioxamine.getLocale()` is supported as an alias for `dioxamine.getLanguage()`.

---

### `dioxamine.getLanguageAsync()` / `dioxamine.getLocaleAsync()`

Asynchronous Promise-based alternative:

```javascript
const lang = await dioxamine.getLanguageAsync();
console.log("Async language tag:", lang.languageTag);
```

---

### `dioxamine.onLanguageChange()`

Registers a listener called whenever the user or system updates the application locale while the plugin is running.

```javascript
dioxamine.onLanguageChange(callback: (langInfo: {
    language: string;
    languageTag: string;
    isRtl: boolean;
    displayName: string;
}) => void): void
```

#### Example
```javascript
dioxamine.onLanguageChange((langInfo) => {
    console.log("App language switched to:", langInfo.languageTag);
    applyTranslations(langInfo.language);
});
```

---

### DOM Event: `dioxamine-language-change`

In addition to `dioxamine.onLanguageChange()`, a standard DOM CustomEvent is dispatched on `window`:

```javascript
window.addEventListener('dioxamine-language-change', (event) => {
    const langInfo = event.detail;
    console.log("Received language change event:", langInfo.languageTag);
});
```

---

## Multi-Language Plugin Example

Below is a complete pattern for implementing multi-language plugins with a string dictionary:

```javascript
const translations = {
    en: {
        title: "Device Diagnostics",
        runTest: "Run Benchmark",
        batteryLevel: "Battery Level"
    },
    ru: {
        title: "Диагностика устройства",
        runTest: "Запустить тест",
        batteryLevel: "Уровень батареи"
    },
    zh: {
        title: "设备诊断",
        runTest: "运行基准测试",
        batteryLevel: "电池电量"
    },
    de: {
        title: "Gerätediagnose",
        runTest: "Benchmark starten",
        batteryLevel: "Akkustand"
    },
    hi: {
        title: "डिवाइस डायग्नोस्टिक्स",
        runTest: "बेंचमार्क चलाएं",
        batteryLevel: "बैटरी स्तर"
    }
};

function applyTranslations(langCode) {
    const strings = translations[langCode] || translations.en;
    document.getElementById("app-title").textContent = strings.title;
    document.getElementById("btn-run").textContent = strings.runTest;
    document.getElementById("battery-label").textContent = strings.batteryLevel;
}

function initI18n() {
    // 1. Get initial language
    const currentLang = dioxamine.getLanguage();
    applyTranslations(currentLang.language);

    // 2. React to dynamic language changes
    dioxamine.onLanguageChange((newLang) => {
        applyTranslations(newLang.language);
    });
}

// Wait for bridge readiness
if (window.dioxamine && window.__dioxamine_bridge_ready) {
    initI18n();
} else {
    window.addEventListener('dioxamine-bridge-ready', initI18n, { once: true });
}
```
