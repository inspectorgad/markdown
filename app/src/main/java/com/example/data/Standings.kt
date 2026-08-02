package com.example.data

/**
 * One league table row. Ships inside season-data.json (the same feed the app
 * already syncs), so standings refresh with the rest of the data — no APK
 * update needed when the league table moves.
 */
data class StandingsRow(
    val team: String,
    val wins: Int,
    val losses: Int,
    val ties: Int = 0,
    val runsFor: Int? = null,
    val runsAgainst: Int? = null,
    val isKC: Boolean = false
) {
    val played: Int get() = wins + losses + ties
    val winPct: Double
        get() = if (played == 0) 0.0 else (wins + ties * 0.5) / played
    val runDiff: Int? get() =
        if (runsFor != null && runsAgainst != null) runsFor - runsAgainst else null
}

/** Games behind the leader, standard baseball convention. */
fun gamesBehind(leader: StandingsRow, row: StandingsRow): Double =
    ((leader.wins - row.wins) + (row.losses - leader.losses)) / 2.0

/**
 * Standings ride along in the synced season feed and are cached as raw JSON so
 * the table is available offline and on first launch from the bundled asset.
 */
object StandingsStore {
    private const val PREFS = "season_sync"
    private const val KEY = "standings_json"

    fun save(context: android.content.Context, root: org.json.JSONObject) {
        val arr = root.optJSONArray("standings") ?: return
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun load(context: android.content.Context): List<StandingsRow> {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return parse(raw)
    }

    fun parse(raw: String): List<StandingsRow> = runCatching {
        val arr = org.json.JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            StandingsRow(
                team = o.getString("team"),
                wins = o.optInt("w"),
                losses = o.optInt("l"),
                ties = o.optInt("t"),
                runsFor = if (o.has("rf")) o.getInt("rf") else null,
                runsAgainst = if (o.has("ra")) o.getInt("ra") else null,
                isKC = o.optBoolean("kc")
            )
        }.sortedWith(compareByDescending<StandingsRow> { it.winPct }.thenByDescending { it.wins })
    }.getOrDefault(emptyList())
}
