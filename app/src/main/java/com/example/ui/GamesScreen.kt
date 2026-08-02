package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.Game
import com.example.data.Player
import com.example.data.StatLine
import com.example.stats.summarize
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun today(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

@Composable
fun GamesScreen(
    games: List<Game>,
    statLines: List<StatLine>,
    onSaveGame: (Game) -> Unit,
    onOpenGame: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (games.isEmpty()) {
            EmptyState(
                title = "No games yet",
                subtitle = "Add past games to capture historical stats, then keep adding games as the season goes on.",
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(games, key = { it.id }) { game ->
                    val lineCount = statLines.count { it.gameId == game.id }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenGame(game) }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "vs ${game.opponent}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${game.date} · ${game.season} · $lineCount player${if (lineCount == 1) "" else "s"} recorded",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                GameDetails(game)
                            }
                            ScoreText(game)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add game")
        }
    }

    if (showAddDialog) {
        GameDialog(
            game = null,
            defaultSeason = games.maxByOrNull { it.date }?.season ?: "",
            onDismiss = { showAddDialog = false },
            onSave = {
                onSaveGame(it)
                showAddDialog = false
            }
        )
    }
}

/**
 * Shows the promo/event name and venue the scraper picked up off the published
 * schedule. Both are optional — older games predate the schedule sync.
 */
@Composable
private fun GameDetails(game: Game) {
    if (game.event.isNotBlank()) {
        Text(
            game.event,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (game.location.isNotBlank()) {
        Text(
            game.location,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreText(game: Game) {
    val us = game.teamScore
    val them = game.opponentScore
    if (us == null || them == null) {
        // ISO dates compare lexicographically, so a plain string compare tells us
        // whether this game has been played yet.
        val upcoming = game.date > today()
        Text(
            if (upcoming) "Upcoming" else "No score",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        val result = when {
            us > them -> "W"
            us < them -> "L"
            else -> "T"
        }
        Text(
            "$result $us–$them",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when (result) {
                "W" -> MaterialTheme.colorScheme.primary
                "L" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun GameDialog(
    game: Game?,
    defaultSeason: String,
    onDismiss: () -> Unit,
    onSave: (Game) -> Unit
) {
    var date by remember { mutableStateOf(game?.date ?: "") }
    var opponent by remember { mutableStateOf(game?.opponent ?: "") }
    var season by remember { mutableStateOf(game?.season ?: defaultSeason) }
    var teamScore by remember { mutableStateOf(game?.teamScore?.toString() ?: "") }
    var oppScore by remember { mutableStateOf(game?.opponentScore?.toString() ?: "") }

    val dateValid = Regex("""\d{4}-\d{2}-\d{2}""").matches(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (game == null) "Add Game" else "Edit Game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it.take(10) },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    isError = date.isNotEmpty() && !dateValid,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = opponent,
                    onValueChange = { opponent = it },
                    label = { Text("Opponent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = season,
                    onValueChange = { season = it },
                    label = { Text("Season (e.g. 2026 Summer)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        label = "Our runs",
                        value = teamScore,
                        onValueChange = { teamScore = it },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "Their runs",
                        value = oppScore,
                        onValueChange = { oppScore = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = dateValid && opponent.isNotBlank() && season.isNotBlank(),
                onClick = {
                    onSave(
                        Game(
                            id = game?.id ?: 0,
                            date = date,
                            opponent = opponent.trim(),
                            season = season.trim(),
                            teamScore = teamScore.toIntOrNull(),
                            opponentScore = oppScore.toIntOrNull(),
                            // Scraped schedule details aren't editable here; carry
                            // them through so an edit doesn't blank them out.
                            event = game?.event ?: "",
                            location = game?.location ?: ""
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: Game,
    players: List<Player>,
    statLines: List<StatLine>,
    onSaveGame: (Game) -> Unit,
    onDeleteGame: (Game) -> Unit,
    onSaveStatLine: (StatLine) -> Unit,
    onDeleteStatLine: (StatLine) -> Unit,
    onBack: () -> Unit
) {
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var editingLineFor by remember { mutableStateOf<Player?>(null) }

    val gameLines = statLines.filter { it.gameId == game.id }
    val linesByPlayer = gameLines.associateBy { it.playerId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("vs ${game.opponent}") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit game")
                    }
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete game")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = ListContentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${game.date} · ${game.season}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            GameDetails(game)
                            Text(
                                "Tap a player below to enter their batting line.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        ScoreText(game)
                    }
                }
            }

            if (players.isEmpty()) {
                item {
                    EmptyState(
                        title = "No players on the roster",
                        subtitle = "Add players on the Roster tab first, then record their stats here."
                    )
                }
            } else {
                items(players, key = { it.id }) { player ->
                    val line = linesByPlayer[player.id]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingLineFor = player }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            JerseyBadge(player.jerseyNumber)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp)
                            ) {
                                Text(
                                    player.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    line?.let { summarize(it) } ?: "Did not play — tap to add",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (line != null) {
                                IconButton(onClick = { onDeleteStatLine(line) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Remove stat line",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    editingLineFor?.let { player ->
        StatLineDialog(
            player = player,
            existing = linesByPlayer[player.id],
            gameId = game.id,
            onDismiss = { editingLineFor = null },
            onSave = {
                onSaveStatLine(it)
                editingLineFor = null
            }
        )
    }

    if (showEditDialog) {
        GameDialog(
            game = game,
            defaultSeason = game.season,
            onDismiss = { showEditDialog = false },
            onSave = {
                onSaveGame(it)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete this game?") },
            text = { Text("This removes the game and every stat line recorded for it. This cannot be undone.") },
            confirmButton = {
                Button(onClick = { onDeleteGame(game) }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun StatLineDialog(
    player: Player,
    existing: StatLine?,
    gameId: Long,
    onDismiss: () -> Unit,
    onSave: (StatLine) -> Unit
) {
    fun init(value: Int?) = value?.takeIf { it != 0 }?.toString() ?: ""

    var ab by remember { mutableStateOf(init(existing?.atBats)) }
    var runs by remember { mutableStateOf(init(existing?.runs)) }
    var hits by remember { mutableStateOf(init(existing?.hits)) }
    var doubles by remember { mutableStateOf(init(existing?.doubles)) }
    var triples by remember { mutableStateOf(init(existing?.triples)) }
    var hr by remember { mutableStateOf(init(existing?.homeRuns)) }
    var rbi by remember { mutableStateOf(init(existing?.rbis)) }
    var bb by remember { mutableStateOf(init(existing?.walks)) }
    var so by remember { mutableStateOf(init(existing?.strikeouts)) }
    var hbp by remember { mutableStateOf(init(existing?.hitByPitch)) }
    var sf by remember { mutableStateOf(init(existing?.sacFlies)) }
    var sb by remember { mutableStateOf(init(existing?.stolenBases)) }

    fun num(s: String) = s.toIntOrNull() ?: 0

    val hitsExceedAtBats = num(hits) > num(ab)
    val extraBasesExceedHits = num(doubles) + num(triples) + num(hr) > num(hits)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${player.name} — Batting Line") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatFieldRow(
                    "AB" to ab to { v: String -> ab = v },
                    "R" to runs to { v: String -> runs = v },
                    "H" to hits to { v: String -> hits = v }
                )
                StatFieldRow(
                    "2B" to doubles to { v: String -> doubles = v },
                    "3B" to triples to { v: String -> triples = v },
                    "HR" to hr to { v: String -> hr = v }
                )
                StatFieldRow(
                    "RBI" to rbi to { v: String -> rbi = v },
                    "BB" to bb to { v: String -> bb = v },
                    "SO" to so to { v: String -> so = v }
                )
                StatFieldRow(
                    "HBP" to hbp to { v: String -> hbp = v },
                    "SF" to sf to { v: String -> sf = v },
                    "SB" to sb to { v: String -> sb = v }
                )
                if (hitsExceedAtBats) {
                    Text(
                        "Hits can't exceed at-bats.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (extraBasesExceedHits) {
                    Text(
                        "2B + 3B + HR can't exceed total hits.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !hitsExceedAtBats && !extraBasesExceedHits,
                onClick = {
                    onSave(
                        StatLine(
                            id = existing?.id ?: 0,
                            playerId = player.id,
                            gameId = gameId,
                            atBats = num(ab),
                            runs = num(runs),
                            hits = num(hits),
                            doubles = num(doubles),
                            triples = num(triples),
                            homeRuns = num(hr),
                            rbis = num(rbi),
                            walks = num(bb),
                            strikeouts = num(so),
                            hitByPitch = num(hbp),
                            sacFlies = num(sf),
                            stolenBases = num(sb)
                        )
                    )
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun StatFieldRow(
    vararg fields: Pair<Pair<String, String>, (String) -> Unit>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        fields.forEach { (labelAndValue, onChange) ->
            val (label, value) = labelAndValue
            NumberField(
                label = label,
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
