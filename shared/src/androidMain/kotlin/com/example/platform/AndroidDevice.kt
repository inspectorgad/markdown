package com.example.platform

import android.content.Context
import android.content.SharedPreferences

/**
 * [Device] on Android. Every value lives where the app kept it before the
 * shared code existed - the same SharedPreferences file ("season_sync") under
 * the same keys, and the same assets/seed.json - so an update finds everything
 * a phone already has and the sync does not start over.
 */
class AndroidDevice(context: Context) : Device {
    private val context = context.applicationContext

    override fun settings(name: String): Settings =
        PreferencesSettings(context.getSharedPreferences(name, Context.MODE_PRIVATE))

    override fun readAsset(name: String): String? =
        runCatching { context.assets.open(name).bufferedReader().use { it.readText() } }.getOrNull()
}

private class PreferencesSettings(private val prefs: SharedPreferences) : Settings {
    override fun string(key: String): String? = prefs.getString(key, null)
    override fun long(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun int(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun boolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)

    override fun edit(changes: SettingsEditor.() -> Unit) {
        val editor = prefs.edit()
        object : SettingsEditor {
            override fun putString(key: String, value: String?) { editor.putString(key, value) }
            override fun putLong(key: String, value: Long) { editor.putLong(key, value) }
            override fun putInt(key: String, value: Int) { editor.putInt(key, value) }
            override fun putBoolean(key: String, value: Boolean) { editor.putBoolean(key, value) }
            override fun remove(key: String) { editor.remove(key) }
        }.changes()
        editor.apply()
    }
}

actual fun nowMillis(): Long = System.currentTimeMillis()
