package com.example

import com.example.data.Game
import com.example.data.opponentRecords
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpponentRecordsTest {

    private fun game(
        date: String,
        opponent: String,
        us: Int? = null,
        them: Int? = null
    ) = Game(
        date = date,
        opponent = opponent,
        season = "2026",
        teamScore = us,
        opponentScore = them
    )

    @Test
    fun `rolls up wins losses and runs per opponent`() {
        val records = opponentRecords(
            listOf(
                game("2026-06-12", "Atlanta Smoke", 10, 0),
                game("2026-06-13", "Atlanta Smoke", 2, 1),
                game("2026-06-14", "Atlanta Smoke", 5, 9),
                game("2026-06-17", "Florida Vibe", 5, 3)
            )
        )

        val atlanta = records.single { it.opponent == "Atlanta Smoke" }
        assertEquals(2, atlanta.wins)
        assertEquals(1, atlanta.losses)
        assertEquals(0, atlanta.ties)
        assertEquals(17, atlanta.runsFor)
        assertEquals(10, atlanta.runsAgainst)
        assertEquals(7, atlanta.runDiff)
    }

    @Test
    fun `scheduled games with no score are not counted`() {
        val records = opponentRecords(
            listOf(
                game("2026-08-05", "TBD"),
                game("2026-10-10", "Nebraska & KU"),
                game("2026-06-12", "Atlanta Smoke", 10, 0)
            )
        )

        assertEquals(listOf("Atlanta Smoke"), records.map { it.opponent })
        assertEquals(1, records.single().played)
    }

    @Test
    fun `a tie counts as half a win`() {
        val records = opponentRecords(
            listOf(
                game("2026-06-12", "Atlanta Smoke", 3, 3),
                game("2026-06-13", "Atlanta Smoke", 1, 4)
            )
        )

        val atlanta = records.single()
        assertEquals(0, atlanta.wins)
        assertEquals(1, atlanta.losses)
        assertEquals(1, atlanta.ties)
        assertEquals(0.25, atlanta.winPct, 1e-9)
    }

    @Test
    fun `best win percentage sorts first`() {
        val records = opponentRecords(
            listOf(
                game("2026-06-12", "Beat Us", 0, 1),
                game("2026-06-13", "We Beat Them", 1, 0)
            )
        )

        assertEquals("We Beat Them", records.first().opponent)
    }

    @Test
    fun `no finished games yields no rows rather than a zero row`() {
        assertTrue(opponentRecords(listOf(game("2026-08-05", "TBD"))).isEmpty())
    }
}
