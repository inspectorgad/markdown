package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.DiamondsDatabase
import com.example.data.Seeder
import com.example.data.StatLine
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MergeSyncTest {

    private lateinit var db: DiamondsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiamondsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun seedJson(): JSONObject = JSONObject(
        """
        {
          "players": [{"name": "Ada Alpha", "jerseyNumber": "1", "position": "SS"}],
          "games": [
            {"date": "2026-06-01", "opponent": "Foes", "season": "2026",
             "teamScore": 5, "opponentScore": 3,
             "lines": [{"player": "Ada Alpha", "ab": 4, "r": 1, "h": 2, "2b": 1,
                        "3b": 0, "hr": 0, "rbi": 2, "bb": 0, "so": 1,
                        "hbp": 0, "sf": 0, "sb": 0}]}
          ]
        }
        """
    )

    @Test
    fun `merge into empty database inserts everything`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        assertEquals(1, db.dao().playersOnce().size)
        val game = db.dao().gamesOnce().single()
        assertEquals(5, game.teamScore)
        assertEquals(1, db.dao().statLinesOnce().size)
    }

    @Test
    fun `merge is idempotent`() = runTest {
        Seeder.merge(seedJson(), db.dao())
        Seeder.merge(seedJson(), db.dao())
        assertEquals(1, db.dao().playersOnce().size)
        assertEquals(1, db.dao().gamesOnce().size)
        assertEquals(1, db.dao().statLinesOnce().size)
    }

    @Test
    fun `merge fills score of an existing scoreless game but never changes an existing score`() =
        runTest {
            val dao = db.dao()
            dao.insertGame(
                com.example.data.Game(date = "2026-06-01", opponent = "Foes", season = "2026")
            )
            Seeder.merge(seedJson(), dao)
            assertEquals(5, dao.gamesOnce().single().teamScore)

            // A second merge with a different score must NOT overwrite.
            val altered = seedJson().apply {
                getJSONArray("games").getJSONObject(0).put("teamScore", 99)
            }
            Seeder.merge(altered, dao)
            assertEquals(5, dao.gamesOnce().single().teamScore)
        }

    @Test
    fun `merge never adds lines to a game that already has any`() = runTest {
        val dao = db.dao()
        Seeder.merge(seedJson(), dao)
        val game = dao.gamesOnce().single()
        val player = dao.playersOnce().single()
        // User records their own corrected line set: one line only.
        dao.statLinesOnce().forEach { dao.deleteStatLine(it) }
        dao.upsertStatLine(StatLine(playerId = player.id, gameId = game.id, atBats = 3, hits = 3))

        Seeder.merge(seedJson(), dao)
        val lines = dao.statLinesOnce()
        assertEquals(1, lines.size)
        assertEquals(3, lines.single().hits)
    }

    @Test
    fun `unknown player in lines is skipped without error`() = runTest {
        val json = seedJson().apply {
            getJSONArray("games").getJSONObject(0).getJSONArray("lines").getJSONObject(0)
                .put("player", "Nobody Known")
        }
        Seeder.merge(json, db.dao())
        assertEquals(0, db.dao().statLinesOnce().size)
        assertNull(db.dao().gamesOnce().single().teamScore?.takeIf { it != 5 })
    }
}
