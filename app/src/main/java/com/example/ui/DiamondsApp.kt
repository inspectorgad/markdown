package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.DiamondsViewModel

enum class Tab(val label: String) { Roster("Roster"), Games("Games"), Leaders("Leaders") }

@Composable
fun DiamondsApp(viewModel: DiamondsViewModel = viewModel()) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Roster) }
    // Detail overlays: at most one is open at a time; back closes it.
    var openPlayerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openGameId by rememberSaveable { mutableStateOf<Long?>(null) }

    val players by viewModel.players.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val statLines by viewModel.statLines.collectAsStateWithLifecycle()

    val showingDetail = openPlayerId != null || openGameId != null
    BackHandler(enabled = showingDetail) {
        openPlayerId = null
        openGameId = null
    }

    val openPlayer = openPlayerId?.let { id -> players.firstOrNull { it.id == id } }
    val openGame = openGameId?.let { id -> games.firstOrNull { it.id == id } }

    when {
        openPlayer != null -> PlayerDetailScreen(
            player = openPlayer,
            games = games,
            statLines = statLines,
            onSavePlayer = viewModel::savePlayer,
            onDeletePlayer = {
                viewModel.deletePlayer(it)
                openPlayerId = null
            },
            onBack = { openPlayerId = null }
        )

        openGame != null -> GameDetailScreen(
            game = openGame,
            players = players,
            statLines = statLines,
            onSaveGame = { viewModel.saveGame(it) },
            onDeleteGame = {
                viewModel.deleteGame(it)
                openGameId = null
            },
            onSaveStatLine = viewModel::saveStatLine,
            onDeleteStatLine = viewModel::deleteStatLine,
            onBack = { openGameId = null }
        )

        else -> Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = when (tab) {
                                        Tab.Roster -> Icons.Default.Groups
                                        Tab.Games -> Icons.AutoMirrored.Filled.List
                                        Tab.Leaders -> Icons.Default.EmojiEvents
                                    },
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            val contentModifier = Modifier.padding(innerPadding)
            when (currentTab) {
                Tab.Roster -> RosterScreen(
                    players = players,
                    statLines = statLines,
                    onSavePlayer = viewModel::savePlayer,
                    onOpenPlayer = { openPlayerId = it.id },
                    modifier = contentModifier
                )

                Tab.Games -> GamesScreen(
                    games = games,
                    statLines = statLines,
                    onSaveGame = { game -> viewModel.saveGame(game) { openGameId = it } },
                    onOpenGame = { openGameId = it.id },
                    modifier = contentModifier
                )

                Tab.Leaders -> LeadersScreen(
                    players = players,
                    games = games,
                    statLines = statLines,
                    modifier = contentModifier
                )
            }
        }
    }
}
