package com.example.platform

// What the shared code needs from the phone it runs on, as interfaces, so the
// same code can run on Android and on iPhone. AndroidDevice is Android's
// version (androidMain); IosDevice is the iPhone's (iosMain).
//
// There is no secret storage here, unlike the KU Volleyball app's: this app
// holds no API key or password, so nothing would use it.

/** Storage the app keeps on this phone. */
interface Device {
    /** A named group of small saved values (SharedPreferences on Android). */
    fun settings(name: String): Settings

    /** The text of a file shipped inside the app (assets/ on Android), or null if it can't be read. */
    fun readAsset(name: String): String?
}

/** Small saved values. A key never set reads as null, 0 or false. */
interface Settings {
    fun string(key: String): String?
    fun long(key: String, default: Long = 0): Long
    fun int(key: String, default: Int = 0): Int
    fun boolean(key: String, default: Boolean = false): Boolean

    /** Makes all of [changes] together. */
    fun edit(changes: SettingsEditor.() -> Unit)
}

interface SettingsEditor {
    /** A null [value] removes the key. */
    fun putString(key: String, value: String?)
    fun putLong(key: String, value: Long)
    fun putInt(key: String, value: Int)
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

/** The wall clock, as milliseconds since 1970. Its own function so tests can reason about it. */
expect fun nowMillis(): Long
