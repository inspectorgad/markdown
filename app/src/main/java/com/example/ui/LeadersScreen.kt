package com.example.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.BattingTotals
import com.example.stats.aggregate
import com.example.stats.formatAverage

private const val ALL_SEASONS = "All"

/** Rate-stat leaders must average at least this many at-bats per team game. */
private const val MIN_AB_PER_TEAM_GAME = 1

@Composable
fun LeadersScreen(
    players: List<Player>,
    games: List<Game>,
    statLines: List<StatLine>,
    modifier: Modifier = Modifier,
    dataUpdatedAt: String? = null
) {
    // Seasons ordered most recent first; default selection is the current (latest) season.
    val seasons = games.sortedByDescending { it.date }.map { it.season }.distinct()
    var selectedSeason by rememberSaveable { mutableStateOf<String?>(null) }
    val season = selectedSeason ?: seasons.firstOrNull() ?: ALL_SEASONS

    val seasonGames =
        if (season == ALL_SEASONS) games else games.filter { it.season == season }
    val seasonGameIds = seasonGames.map { it.id }.toSet()
    val seasonLines = statLines.filter { it.gameId in seasonGameIds }
    val playersById = players.associateBy { it.id }
    val totalsByPlayer: Map<Long, BattingTotals> = seasonLines
        .groupBy { it.playerId }
        .mapValues { (_, lines) -> aggregate(lines) }

    val wins = seasonGames.count {
        it.teamScore != null && it.opponentScore != null && it.teamScore > it.opponentScore
    }
    val losses = seasonGames.count {
        it.teamScore != null && it.opponentScore != null && it.teamScore < it.opponentScore
    }
    val ties = seasonGames.count {
        it.teamScore != null && it.opponentScore != null && it.teamScore == it.opponentScore
    }
    val runsFor = seasonGames.sumOf { it.teamScore ?: 0 }
    val runsAgainst = seasonGames.sumOf { it.opponentScore ?: 0 }
    val teamTotals = aggregate(seasonLines)
    val minAtBats = seasonGames.size * MIN_AB_PER_TEAM_GAME

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            (seasons + ALL_SEASONS).forEach { s ->
                FilterChip(
                    selected = season == s,
                    onClick = { selectedSeason = s },
                    label = { Text(s) }
                )
            }
        }

        if (seasonGames.isEmpty()) {
            EmptyState(
                title = "No games recorded",
                subtitle = "Add games and stat lines to see team totals and leaderboards here."
            )
            return@Column
        }

        LazyColumn(
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Team — $season",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Record ${wins}-${losses}" + (if (ties > 0) "-$ties" else "") +
                                " · Runs $runsFor for / $runsAgainst against",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Team ${formatAverage(teamTotals.battingAverage)} AVG · " +
                                "${formatAverage(teamTotals.onBasePercentage)} OBP · " +
                                "${formatAverage(teamTotals.sluggingPercentage)} SLG",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        dataUpdatedAt?.let {
                            Text(
                                "Data updated ${it.take(16).replace('T', ' ')} UTC · pull down to refresh",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item {
                LeaderCard(
                    title = "Batting Average" + if (minAtBats > 0) " (min $minAtBats AB)" else "",
                    entries = totalsByPlayer
                        .filterValues { it.atBats >= minAtBats && it.atBats > 0 }
                        .entries
                        .sortedByDescending { it.value.battingAverage }
                        .take(3)
                        .mapNotNull { (playerId, totals) ->
                            playersById[playerId]?.let {
                                it.name to formatAverage(totals.battingAverage)
                            }
                        }
                )
            }

            val countingCategories = listOf<Pair<String, (BattingTotals) -> Int>>(
                "Hits" to { it.hits },
                "Home Runs" to { it.homeRuns },
                "RBIs" to { it.rbis },
                "Runs" to { it.runs },
                "Stolen Bases" to { it.stolenBases }
            )
            countingCategories.forEach { (title, selector) ->
                item {
                    LeaderCard(
                        title = title,
                        entries = totalsByPlayer.entries
                            .filter { selector(it.value) > 0 }
                            .sortedByDescending { selector(it.value) }
                            .take(3)
                            .mapNotNull { (playerId, totals) ->
                                playersById[playerId]?.let {
                                    it.name to selector(totals).toString()
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun LeaderCard(title: String, entries: List<Pair<String, String>>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (entries.isEmpty()) {
                Text(
                    "No qualifying players yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                entries.forEachIndexed { index, (name, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
