package com.example.data

import com.example.json.JSONObject
import com.example.platform.Device

/**
 * Syncs the bundled seed.json into the database on every launch (assets/ on
 * Android, the app bundle on iPhone), gap-filling only — it never overwrites
 * user-entered data:
 * - players are added if their name isn't already present
 * - games are added if no game exists on that date
 * - an existing game gets seed scores only if it has none
 * - an existing game gets seed stat lines only if it has none
 *
 * This lets an updated APK (with fresh season data baked in) install over the
 * old one and pick up the new games while keeping local edits intact.
 *
 * The one thing the bundled copy is not allowed to do is go backwards. The
 * standings ride along in the same file and are rewritten wholesale, and a
 * build carries whatever season data was current when it was made. So a phone
 * that has already synced something newer from the feed holds the bundled copy
 * back: applied, it would roll the table back on every launch, and the sync
 * would not put it right while the feed itself is unchanged.
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

    suspend fun sync(device: Device, dao: DiamondsDao) {
        val json = device.readAsset("seed.json") ?: return

        runCatching {
            val root = JSONObject(json)
            if (olderThan(root.optString("generatedAt"), SeasonSync.lastGeneratedAt(device))) return
            StandingsStore.save(device, root)
            merge(root, dao)
        }
    }

    /**
     * Whether the bundled copy stamped [seed] is older than the feed copy
     * stamped [feed] that this phone has already taken.
     *
     * Both stamps are the ISO instants the data pipeline writes
     * ("2026-10-20T07:45:43Z"), which sort correctly as text. Anything that is
     * missing or not in that shape is not compared at all: an unreadable stamp
     * is no evidence that the bundled copy is behind, and holding it back on a
     * guess would strand a phone with no data at all on its first launch.
     */
    internal fun olderThan(seed: String?, feed: String?): Boolean =
        seed != null && feed != null && STAMP.matches(seed) && STAMP.matches(feed) && seed < feed

    private val STAMP = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d+)?Z""")

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

        // Kept in step with the database as we go. A stale snapshot let the
        // second game of a doubleheader re-match the row the first had just
        // claimed, so the two games collapsed into one.
        val stored = dao.gamesOnce().toMutableList()
        // Rows already matched this pass. Without this, the second game of a
        // doubleheader re-matches the row the first just took.
        val claimed = mutableSetOf<Long>()
        val gamesWithLines = dao.statLinesOnce().map { it.gameId }.toSet()

        val games = root.optJSONArray("games") ?: return
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val date = g.getString("date")
            val seedTeamScore = if (g.has("teamScore")) g.getInt("teamScore") else null
            val seedOppScore = if (g.has("opponentScore")) g.getInt("opponentScore") else null

            // Identify the game by its upstream id where we have one. Two games
            // can share a date (an Aug 8 doubleheader), so keying on date alone
            // made the second overwrite the first.
            val seedSrcId = g.optString("srcId", "")
            val onDate = stored.filter { it.date == date && it.id !in claimed }
            val existing = stored.firstOrNull {
                seedSrcId.isNotEmpty() && it.srcId == seedSrcId
            }
                ?: onDate.firstOrNull {
                    it.srcId.isEmpty() &&
                        it.teamScore == seedTeamScore && it.opponentScore == seedOppScore
                }
                ?: onDate.firstOrNull { it.srcId.isEmpty() && it.teamScore == null }
                // With no id to go on we cannot be looking at a second game, so
                // reuse the day's row rather than inserting a duplicate. This
                // is the path an upstream score correction arrives by, and the
                // update below leaves an existing score untouched.
                ?: onDate.firstOrNull { it.srcId.isEmpty() && seedSrcId.isEmpty() }
            val gameId: Long
            if (existing == null) {
                val fresh = Game(
                    date = date,
                    opponent = g.getString("opponent"),
                    season = g.getString("season"),
                    teamScore = seedTeamScore,
                    opponentScore = seedOppScore,
                    event = g.optString("event", ""),
                    location = g.optString("location", ""),
                    srcId = seedSrcId
                )
                gameId = dao.insertGame(fresh)
                stored += fresh.copy(id = gameId)
                claimed += gameId
            } else {
                gameId = existing.id
                claimed += gameId
                val seedEvent = g.optString("event", "")
                val seedLocation = g.optString("location", "")
                val scoreArrived = existing.teamScore == null && existing.opponentScore == null &&
                    (seedTeamScore != null || seedOppScore != null)
                val detailsChanged = (seedEvent.isNotEmpty() && seedEvent != existing.event) ||
                    (seedLocation.isNotEmpty() && seedLocation != existing.location)
                // Stamping the id onto a row we matched by score lets the next
                // sync find it directly rather than re-matching.
                val srcIdArrived = seedSrcId.isNotEmpty() && existing.srcId.isEmpty()
                if (scoreArrived || detailsChanged || srcIdArrived) {
                    val updated = existing.copy(
                        teamScore = if (scoreArrived) seedTeamScore else existing.teamScore,
                        opponentScore =
                            if (scoreArrived) seedOppScore else existing.opponentScore,
                        event = seedEvent.ifEmpty { existing.event },
                        location = seedLocation.ifEmpty { existing.location },
                        srcId = if (srcIdArrived) seedSrcId else existing.srcId
                    )
                    dao.updateGame(updated)
                    stored[stored.indexOfFirst { it.id == existing.id }] = updated
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
