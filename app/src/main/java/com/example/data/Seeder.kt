package com.example.data

import android.content.Context
import org.json.JSONObject

/**
 * One-time database seeding from assets/seed.json. Runs only when the database
 * is completely empty, so it never overwrites user-entered data.
 *
 * Expected shape:
 * {
 *   "players": [{"name": "...", "jerseyNumber": "12", "position": "SS"}],
 *   "games": [{
 *     "date": "2026-06-17", "opponent": "...", "season": "2026",
 *     "teamScore": 5, "opponentScore": 3,
 *     "lines": [{"player": "<player name>", "ab": 4, "r": 1, "h": 2, "2b": 1,
 *                "3b": 0, "hr": 0, "rbi": 2, "bb": 0, "so": 1, "hbp": 0,
 *                "sf": 0, "sb": 0}]
 *   }]
 * }
 */
object Seeder {

    suspend fun seedIfEmpty(context: Context, dao: DiamondsDao) {
        if (dao.playerCount() > 0 || dao.gameCount() > 0) return

        val json = runCatching {
            context.assets.open("seed.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        runCatching { seed(JSONObject(json), dao) }
    }

    private suspend fun seed(root: JSONObject, dao: DiamondsDao) {
        val playerIds = mutableMapOf<String, Long>()

        val players = root.optJSONArray("players") ?: return
        for (i in 0 until players.length()) {
            val p = players.getJSONObject(i)
            val name = p.getString("name")
            playerIds[name] = dao.insertPlayer(
                Player(
                    name = name,
                    jerseyNumber = p.optString("jerseyNumber", ""),
                    position = p.optString("position", "")
                )
            )
        }

        val games = root.optJSONArray("games") ?: return
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val gameId = dao.insertGame(
                Game(
                    date = g.getString("date"),
                    opponent = g.getString("opponent"),
                    season = g.getString("season"),
                    teamScore = if (g.has("teamScore")) g.getInt("teamScore") else null,
                    opponentScore = if (g.has("opponentScore")) g.getInt("opponentScore") else null
                )
            )
            val lines = g.optJSONArray("lines") ?: continue
            for (j in 0 until lines.length()) {
                val l = lines.getJSONObject(j)
                val playerId = playerIds[l.getString("player")] ?: continue
                dao.upsertStatLine(
                    StatLine(
                        playerId = playerId,
                        gameId = gameId,
                        atBats = l.optInt("ab"),
                        runs = l.optInt("r"),
                        hits = l.optInt("h"),
                        doubles = l.optInt("2b"),
                        triples = l.optInt("3b"),
                        homeRuns = l.optInt("hr"),
                        rbis = l.optInt("rbi"),
                        walks = l.optInt("bb"),
                        strikeouts = l.optInt("so"),
                        hitByPitch = l.optInt("hbp"),
                        sacFlies = l.optInt("sf"),
                        stolenBases = l.optInt("sb")
                    )
                )
            }
        }
    }
}
