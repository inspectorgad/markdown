@file:OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)

package com.example.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.convert
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.timeIntervalSince1970

/**
 * [Device] on iPhone. Settings go to UserDefaults (each group's keys prefixed
 * with its name), and the bundled season data comes from the app bundle,
 * where Xcode puts the same seed.json the Android app ships in assets/.
 */
class IosDevice : Device {
    override fun settings(name: String): Settings = DefaultsSettings(name)

    override fun readAsset(name: String): String? {
        val path = NSBundle.mainBundle.pathForResource(
            name.substringBeforeLast('.'),
            ofType = name.substringAfterLast('.', "")
        ) ?: return null
        return NSData.dataWithContentsOfFile(path)?.toByteArray()?.decodeToString()
    }
}

private class DefaultsSettings(private val group: String) : Settings {
    private val defaults = NSUserDefaults.standardUserDefaults
    private fun k(key: String) = "$group.$key"
    private fun has(key: String) = defaults.objectForKey(k(key)) != null

    override fun string(key: String): String? = defaults.stringForKey(k(key))
    override fun long(key: String, default: Long): Long = if (has(key)) defaults.integerForKey(k(key)).toLong() else default
    override fun int(key: String, default: Int): Int = if (has(key)) defaults.integerForKey(k(key)).toInt() else default
    override fun boolean(key: String, default: Boolean): Boolean = if (has(key)) defaults.boolForKey(k(key)) else default

    override fun edit(changes: SettingsEditor.() -> Unit) {
        object : SettingsEditor {
            override fun putString(key: String, value: String?) {
                if (value == null) defaults.removeObjectForKey(k(key)) else defaults.setObject(value, forKey = k(key))
            }
            override fun putLong(key: String, value: Long) = defaults.setInteger(value.convert(), forKey = k(key))
            override fun putInt(key: String, value: Int) = defaults.setInteger(value.convert(), forKey = k(key))
            override fun putBoolean(key: String, value: Boolean) = defaults.setBool(value, forKey = k(key))
            override fun remove(key: String) = defaults.removeObjectForKey(k(key))
        }.changes()
    }
}

actual fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
