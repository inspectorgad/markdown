package com.example

import com.example.data.StatLine
import com.example.stats.aggregate
import com.example.stats.formatAverage
import com.example.stats.summarize
import org.junit.Assert.assertEquals
import org.junit.Test

class BattingStatsTest {

    private fun line(
        atBats: Int = 0,
        runs: Int = 0,
        hits: Int = 0,
        doubles: Int = 0,
        triples: Int = 0,
        homeRuns: Int = 0,
        rbis: Int = 0,
        walks: Int = 0,
        strikeouts: Int = 0,
        hitByPitch: Int = 0,
        sacFlies: Int = 0,
        stolenBases: Int = 0
    ) = StatLine(
        playerId = 1, gameId = 1,
        atBats = atBats, runs = runs, hits = hits, doubles = doubles, triples = triples,
        homeRuns = homeRuns, rbis = rbis, walks = walks, strikeouts = strikeouts,
        hitByPitch = hitByPitch, sacFlies = sacFlies, stolenBases = stolenBases
    )

    @Test
    fun `empty aggregation is all zeros and safe to divide`() {
        val totals = aggregate(emptyList())
        assertEquals(0, totals.games)
        assertEquals(0.0, totals.battingAverage, 0.0)
        assertEquals(0.0, totals.onBasePercentage, 0.0)
        assertEquals(0.0, totals.sluggingPercentage, 0.0)
        assertEquals(0.0, totals.ops, 0.0)
    }

    @Test
    fun `aggregation sums counting stats across games`() {
        val totals = aggregate(
            listOf(
                line(atBats = 4, hits = 2, doubles = 1, rbis = 2, runs = 1),
                line(atBats = 3, hits = 1, homeRuns = 1, rbis = 3, walks = 1),
                line(atBats = 5, hits = 3, triples = 1, stolenBases = 2)
            )
        )
        assertEquals(3, totals.games)
        assertEquals(12, totals.atBats)
        assertEquals(6, totals.hits)
        assertEquals(1, totals.doubles)
        assertEquals(1, totals.triples)
        assertEquals(1, totals.homeRuns)
        assertEquals(5, totals.rbis)
        assertEquals(1, totals.walks)
        assertEquals(2, totals.stolenBases)
        assertEquals(".500", formatAverage(totals.battingAverage))
    }

    @Test
    fun `singles and total bases derive from hit types`() {
        // 6 hits: 3 singles, 1 double, 1 triple, 1 HR -> 3 + 2 + 3 + 4 = 12 TB
        val totals = aggregate(
            listOf(line(atBats = 10, hits = 6, doubles = 1, triples = 1, homeRuns = 1))
        )
        assertEquals(3, totals.singles)
        assertEquals(12, totals.totalBases)
        assertEquals(1.2, totals.sluggingPercentage, 1e-9)
    }

    @Test
    fun `obp counts walks and hbp but not sac flies as times on base`() {
        // (H + BB + HBP) / (AB + BB + HBP + SF) = (2 + 1 + 1) / (8 + 1 + 1 + 2) = 4/12
        val totals = aggregate(
            listOf(line(atBats = 8, hits = 2, walks = 1, hitByPitch = 1, sacFlies = 2))
        )
        assertEquals(4.0 / 12.0, totals.onBasePercentage, 1e-9)
        assertEquals(12, totals.plateAppearances)
    }

    @Test
    fun `ops is obp plus slg`() {
        val totals = aggregate(listOf(line(atBats = 4, hits = 2, homeRuns = 1, walks = 1)))
        assertEquals(
            totals.onBasePercentage + totals.sluggingPercentage,
            totals.ops,
            1e-9
        )
    }

    @Test
    fun `formatAverage uses baseball notation`() {
        assertEquals(".000", formatAverage(0.0))
        assertEquals(".333", formatAverage(1.0 / 3.0))
        assertEquals(".500", formatAverage(0.5))
        assertEquals("1.000", formatAverage(1.0))
        assertEquals("1.200", formatAverage(1.2))
    }

    @Test
    fun `summarize builds a readable game line`() {
        val summary = summarize(
            line(atBats = 4, hits = 2, homeRuns = 1, rbis = 3, runs = 2, stolenBases = 1)
        )
        assertEquals("2-4, HR, 3 RBI, 2 R, 1 SB", summary)
    }

    @Test
    fun `summarize handles hitless game`() {
        assertEquals("0-3", summarize(line(atBats = 3, strikeouts = 2)))
    }
}
