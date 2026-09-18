package io.github.rhythmcache.dioxamine.scrcpy

import android.content.Context

object ScrcpyConfigStore {
    private const val PREFS_NAME = "scrcpy_device_configs"
    private const val KEY_CONFIG_PREFIX = "cfg_"
    private const val KEY_REMEMBER_PREFIX = "remember_"

    fun isRememberEnabled(context: Context, deviceId: String?): Boolean {
        if (deviceId.isNullOrBlank()) return true
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_REMEMBER_PREFIX + deviceId, true)
    }

    fun setRememberEnabled(context: Context, deviceId: String?, enabled: Boolean) {
        if (deviceId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_REMEMBER_PREFIX + deviceId, enabled).apply()
    }

    fun load(context: Context, deviceId: String?): ScrcpyConfig? {
        if (deviceId.isNullOrBlank()) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG_PREFIX + deviceId, null) ?: return null
        return ScrcpyConfig.fromJson(json)
    }

    fun save(context: Context, deviceId: String?, config: ScrcpyConfig) {
        if (deviceId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG_PREFIX + deviceId, config.toJson()).apply()
    }

    fun resetToDefaults(context: Context, deviceId: String?) {
        if (deviceId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CONFIG_PREFIX + deviceId)
            .apply()
    }
}
