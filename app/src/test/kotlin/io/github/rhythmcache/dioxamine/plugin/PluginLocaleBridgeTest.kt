package io.github.rhythmcache.dioxamine.plugin

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PluginLocaleBridgeTest {

    @Test
    fun testPluginLocaleInfoSerialization() {
        val info = PluginLocaleInfo(
            language = "zh",
            languageTag = "zh-CN",
            isRtl = false,
            displayName = "简体中文",
        )
        val json = Json.encodeToString(info)
        assertTrue(json.contains("\"language\":\"zh\""))
        assertTrue(json.contains("\"languageTag\":\"zh-CN\""))
        assertTrue(json.contains("\"isRtl\":false"))
        assertTrue(json.contains("\"displayName\":\"简体中文\""))

        val decoded = Json.decodeFromString<PluginLocaleInfo>(json)
        assertEquals(info, decoded)
    }

    @Test
    fun testBuildLocaleInjectionScriptLtr() {
        val info = PluginLocaleInfo(
            language = "en",
            languageTag = "en-US",
            isRtl = false,
            displayName = "English",
        )
        val script = buildLocaleInjectionScript(info)
        assertTrue(script.contains("document.documentElement.setAttribute('lang', 'en-US')"))
        assertTrue(script.contains("document.documentElement.setAttribute('dir', 'ltr')"))
        assertTrue(script.contains("document.documentElement.setAttribute('data-dioxamine-lang', 'en-US')"))
        assertTrue(script.contains("window.__dioxamine_locale_info = info;"))
        assertTrue(script.contains("window.__dioxamine_language_listener(info)"))
        assertTrue(script.contains("dioxamine-language-change"))
    }

    @Test
    fun testBuildLocaleInjectionScriptRtl() {
        val info = PluginLocaleInfo(
            language = "ar",
            languageTag = "ar",
            isRtl = true,
            displayName = "العربية",
        )
        val script = buildLocaleInjectionScript(info)
        assertTrue(script.contains("document.documentElement.setAttribute('lang', 'ar')"))
        assertTrue(script.contains("document.documentElement.setAttribute('dir', 'rtl')"))
        assertTrue(script.contains("document.documentElement.setAttribute('data-dioxamine-lang', 'ar')"))
    }
}
