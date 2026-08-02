package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.StandingsRow
import com.example.data.gamesBehind
import com.example.stats.formatAverage

private val TEAM_W = 132.dp
private val CELL_W = 46.dp

@Composable
fun StandingsScreen(
    standings: List<StandingsRow>,
    modifier: Modifier = Modifier,
    dataUpdatedAt: String? = null
) {
    if (standings.isEmpty()) {
        EmptyState(
            title = "Standings not available",
            subtitle = "The league table appears here once the PSL publishes it. " +
                "Pull down to check for an update.",
            modifier = modifier.fillMaxSize()
        )
        return
    }

    val leader = standings.first()
    val scroll = rememberScrollState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = ListContentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                "PSL Standings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            dataUpdatedAt?.let {
                Text(
                    "Updated ${it.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.horizontalScroll(scroll)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCell("Team", TEAM_W, TextAlign.Start)
                        HeaderCell("W", CELL_W)
                        HeaderCell("L", CELL_W)
                        HeaderCell("PCT", CELL_W + 8.dp)
                        HeaderCell("GB", CELL_W)
                        HeaderCell("RF", CELL_W)
                        HeaderCell("RA", CELL_W)
                        HeaderCell("DIFF", CELL_W + 6.dp)
                    }
                    HorizontalDivider()
                    standings.forEachIndexed { index, row ->
                        val gb = gamesBehind(leader, row)
                        Row(
                            modifier = Modifier
                                .then(
                                    if (row.isKC) Modifier.background(
                                        MaterialTheme.colorScheme.primaryContainer
                                    ) else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(modifier = Modifier.width(TEAM_W)) {
                                Text(
                                    "${index + 1}.",
                                    modifier = Modifier.width(22.dp),
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    row.team,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    fontWeight = if (row.isKC) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                            BodyCell(row.wins.toString(), CELL_W, bold = true)
                            BodyCell(row.losses.toString(), CELL_W)
                            BodyCell(formatAverage(row.winPct), CELL_W + 8.dp)
                            BodyCell(if (gb <= 0.0) "—" else trimGb(gb), CELL_W)
                            BodyCell(row.runsFor?.toString() ?: "–", CELL_W)
                            BodyCell(row.runsAgainst?.toString() ?: "–", CELL_W)
                            BodyCell(
                                row.runDiff?.let { if (it > 0) "+$it" else it.toString() } ?: "–",
                                CELL_W + 6.dp
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    "PCT counts a tie as half a win. GB is games behind the leader. " +
                        "Runs for/against appear when the league publishes them.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun trimGb(gb: Double): String =
    if (gb % 1.0 == 0.0) gb.toInt().toString() else String.format("%.1f", gb)

@Composable
private fun HeaderCell(text: String, width: Dp, align: TextAlign = TextAlign.Center) {
    Text(
        text,
        modifier = Modifier.width(width),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = align,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun BodyCell(text: String, width: Dp, bold: Boolean = false) {
    Text(
        text,
        modifier = Modifier.width(width),
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        maxLines = 1,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
    )
}
