package com.example.data

import com.example.json.JSONObject
import com.example.platform.Device
import com.example.platform.HttpException
import com.example.platform.newHttp
import com.example.platform.nowMillis
import com.example.text.hex2
import com.example.text.sha256

sealed interface SyncResult {
    data class Updated(val generatedAt: String?) : SyncResult
    data object NoChange : SyncResult
    data class Failed(val reason: String) : SyncResult
}

/**
 * Downloads the latest parsed season data (published by CI next to the APK)
 * and folds it into the database via [Seeder.merge] — the same gap-filling
 * merge used for the bundled seed, so user-entered data is never overwritten.
 *
 * Android and iPhone take the same file from the same address; only the HTTP
 * underneath differs (OkHttp there, NSURLSession here). What each phone
 * remembers about its last sync lives in the same "season_sync" settings under
 * the same keys as before, so an Android update does not re-download or lose
 * the date it last saw.
 */
object SeasonSync {

    private const val DATA_URL =
        "https://github.com/inspectorgad/markdown/releases/latest/download/season-data.json"
    private const val PREFS = "season_sync"
    private const val KEY_HASH = "last_hash"
    private const val KEY_SUCCESS_MS = "last_success_ms"
    private const val KEY_GENERATED_AT = "last_generated_at"
    private const val AUTO_SYNC_INTERVAL_MS = 6L * 60 * 60 * 1000

    private val http by lazy { newHttp(connectSeconds = 10, readSeconds = 20) }

    fun lastGeneratedAt(device: Device): String? =
        device.settings(PREFS).string(KEY_GENERATED_AT)

    fun shouldAutoSync(device: Device): Boolean =
        nowMillis() - device.settings(PREFS).long(KEY_SUCCESS_MS) > AUTO_SYNC_INTERVAL_MS

    suspend fun sync(device: Device, dao: DiamondsDao): SyncResult {
        val body = try {
            val response = http.get(DATA_URL)
            if (!response.isSuccessful) {
                return SyncResult.Failed("server returned ${response.code}")
            }
            response.body
        } catch (e: HttpException) {
            return SyncResult.Failed(e.reason ?: e.message ?: "network error")
        }
        if (body.isEmpty()) return SyncResult.Failed("empty response")

        val root = SeasonDataValidator.parse(body)
            ?: return SyncResult.Failed("downloaded data failed validation")

        val hash = sha256(body).joinToString("") { hex2(it) }
        val settings = device.settings(PREFS)
        if (hash == settings.string(KEY_HASH)) {
            settings.edit { putLong(KEY_SUCCESS_MS, nowMillis()) }
            return SyncResult.NoChange
        }

        try {
            StandingsStore.save(device, root)
            Seeder.merge(root, dao)
        } catch (e: Exception) {
            return SyncResult.Failed("merge failed: ${e.message}")
        }

        val generatedAt = root.optString("generatedAt").takeIf { it.isNotBlank() }
        settings.edit {
            putString(KEY_HASH, hash)
            putLong(KEY_SUCCESS_MS, nowMillis())
            putString(KEY_GENERATED_AT, generatedAt)
        }
        return SyncResult.Updated(generatedAt)
    }
}

/**
 * Sanity gate for downloaded season data: a truncated, garbled, or
 * newer-format payload is rejected before it can reach the database.
 */
object SeasonDataValidator {
    const val SUPPORTED_FORMAT = 1
    const val MIN_PLAYERS = 10
    const val MIN_GAMES = 15

    fun parse(bytes: ByteArray): JSONObject? = parse(bytes.decodeToString())

    fun parse(text: String): JSONObject? = try {
        val root = JSONObject(text)
        val valid = root.optInt("formatVersion", 1) <= SUPPORTED_FORMAT &&
            (root.optJSONArray("players")?.length() ?: 0) >= MIN_PLAYERS &&
            (root.optJSONArray("games")?.length() ?: 0) >= MIN_GAMES
        if (valid) root else null
    } catch (e: Exception) {
        null
    }
}
