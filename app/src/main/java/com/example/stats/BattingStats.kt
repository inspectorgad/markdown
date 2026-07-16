package com.example.stats

import com.example.data.StatLine
import java.util.Locale

/**
 * Aggregated batting totals for any collection of stat lines
 * (one player's game, a season, a career, or the whole team).
 */
data class BattingTotals(
    val games: Int = 0,
    val atBats: Int = 0,
    val runs: Int = 0,
    val hits: Int = 0,
    val doubles: Int = 0,
    val triples: Int = 0,
    val homeRuns: Int = 0,
    val rbis: Int = 0,
    val walks: Int = 0,
    val strikeouts: Int = 0,
    val hitByPitch: Int = 0,
    val sacFlies: Int = 0,
    val stolenBases: Int = 0
) {
    val singles: Int get() = hits - doubles - triples - homeRuns
    val totalBases: Int get() = singles + 2 * doubles + 3 * triples + 4 * homeRuns
    val plateAppearances: Int get() = atBats + walks + hitByPitch + sacFlies

    val battingAverage: Double get() = ratio(hits, atBats)
    val onBasePercentage: Double
        get() = ratio(hits + walks + hitByPitch, atBats + walks + hitByPitch + sacFlies)
    val sluggingPercentage: Double get() = ratio(totalBases, atBats)
    val ops: Double get() = onBasePercentage + sluggingPercentage

    private fun ratio(numerator: Int, denominator: Int): Double =
        if (denominator == 0) 0.0 else numerator.toDouble() / denominator
}

/** Sums a set of stat lines into one totals row. */
fun aggregate(lines: Collection<StatLine>): BattingTotals = BattingTotals(
    games = lines.size,
    atBats = lines.sumOf { it.atBats },
    runs = lines.sumOf { it.runs },
    hits = lines.sumOf { it.hits },
    doubles = lines.sumOf { it.doubles },
    triples = lines.sumOf { it.triples },
    homeRuns = lines.sumOf { it.homeRuns },
    rbis = lines.sumOf { it.rbis },
    walks = lines.sumOf { it.walks },
    strikeouts = lines.sumOf { it.strikeouts },
    hitByPitch = lines.sumOf { it.hitByPitch },
    sacFlies = lines.sumOf { it.sacFlies },
    stolenBases = lines.sumOf { it.stolenBases }
)

/** Formats a rate stat baseball-style: .333, .500, 1.000 */
fun formatAverage(value: Double): String {
    val formatted = String.format(Locale.US, "%.3f", value)
    return if (formatted.startsWith("0.")) formatted.substring(1) else formatted
}

/** Short human summary of a single game line, e.g. "2-4, HR, 2B, 3 RBI, SB". */
fun summarize(line: StatLine): String {
    val parts = mutableListOf("${line.hits}-${line.atBats}")
    repeat(line.homeRuns) { parts.add("HR") }
    repeat(line.triples) { parts.add("3B") }
    repeat(line.doubles) { parts.add("2B") }
    if (line.rbis > 0) parts.add("${line.rbis} RBI")
    if (line.runs > 0) parts.add("${line.runs} R")
    if (line.walks > 0) parts.add("${line.walks} BB")
    if (line.stolenBases > 0) parts.add("${line.stolenBases} SB")
    return parts.joinToString(", ")
}
