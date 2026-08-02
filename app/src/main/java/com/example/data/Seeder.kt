package com.example.data

import android.content.Context
import org.json.JSONObject

/**
 * Syncs the bundled assets/seed.json into the database on every launch,
 * gap-filling only — it never overwrites user-entered data:
 * - players are added if their name isn't already present
 * - games are added if no game exists on that date
 * - an existing game gets seed scores only if it has none
 * - an existing game gets seed stat lines only if it has none
 *
 * This lets an updated APK (with fresh season data baked in) install over the
 * old one and pick up the new games while keeping local edits intact.
 *
 * Seed game shape:
 * {
 *   "date": "2026-06-17", "opponent": "...", "season": "2026",
 *   "teamScore": 5, "opponentScore": 3,
 *   "lines": [{"player": "<player name>", "ab": 4, "r": 1, "h": 2, "2b": 1,
 *              "3b": 0, "hr": 0, "rbi": 2, "bb": 0, "so": 1, "hbp": 0,
 *              "sf": 0, "sb": 0}]
 * }
 */
object Seeder {

    suspend fun sync(context: Context, dao: DiamondsDao) {
        val json = runCatching {
            context.assets.open("seed.json").bufferedReader().use { it.readText() }
        }.getOrNull() ?: return

        runCatching {
            val root = JSONObject(json)
            StandingsStore.save(context, root)
            merge(root, dao)
        }
    }

    /** Also used by [SeasonSync] for network-fetched season data. */
    suspend fun merge(root: JSONObject, dao: DiamondsDao) {
        val playerIdsByName = dao.playersOnce().associate { it.name to it.id }.toMutableMap()

        val players = root.optJSONArray("players")
        if (players != null) {
            for (i in 0 until players.length()) {
                val p = players.getJSONObject(i)
                val name = p.getString("name")
                if (name !in playerIdsByName) {
                    playerIdsByName[name] = dao.insertPlayer(
                        Player(
                            name = name,
                            jerseyNumber = p.optString("jerseyNumber", ""),
                            position = p.optString("position", "")
                        )
                    )
                }
            }
        }

        val gamesByDate = dao.gamesOnce().associateBy { it.date }
        val gamesWithLines = dao.statLinesOnce().map { it.gameId }.toSet()

        val games = root.optJSONArray("games") ?: return
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val date = g.getString("date")
            val seedTeamScore = if (g.has("teamScore")) g.getInt("teamScore") else null
            val seedOppScore = if (g.has("opponentScore")) g.getInt("opponentScore") else null

            val existing = gamesByDate[date]
            val gameId: Long
            if (existing == null) {
                gameId = dao.insertGame(
                    Game(
                        date = date,
                        opponent = g.getString("opponent"),
                        season = g.getString("season"),
                        teamScore = seedTeamScore,
                        opponentScore = seedOppScore,
                        event = g.optString("event", ""),
                        location = g.optString("location", "")
                    )
                )
            } else {
                gameId = existing.id
                val seedEvent = g.optString("event", "")
                val seedLocation = g.optString("location", "")
                val scoreArrived = existing.teamScore == null && existing.opponentScore == null &&
                    (seedTeamScore != null || seedOppScore != null)
                val detailsChanged = (seedEvent.isNotEmpty() && seedEvent != existing.event) ||
                    (seedLocation.isNotEmpty() && seedLocation != existing.location)
                if (scoreArrived || detailsChanged) {
                    dao.updateGame(
                        existing.copy(
                            teamScore = if (scoreArrived) seedTeamScore else existing.teamScore,
                            opponentScore =
                                if (scoreArrived) seedOppScore else existing.opponentScore,
                            event = seedEvent.ifEmpty { existing.event },
                            location = seedLocation.ifEmpty { existing.location }
                        )
                    )
                }
            }

            if (existing != null && gameId in gamesWithLines) continue
            val lines = g.optJSONArray("lines") ?: continue
            for (j in 0 until lines.length()) {
                val l = lines.getJSONObject(j)
                val playerId = playerIdsByName[l.getString("player")] ?: continue
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
