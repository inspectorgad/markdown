package com.example.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.DiamondsViewModel
import com.example.data.opponentRecords

enum class Tab(val label: String) {
    Roster("Roster"), Games("Games"), Leaders("Leaders"), Standings("Standings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiamondsApp(viewModel: DiamondsViewModel) {
    var currentTab by rememberSaveable { mutableStateOf(Tab.Roster) }
    // Detail overlays: at most one is open at a time; back closes it.
    var openPlayerId by rememberSaveable { mutableStateOf<Long?>(null) }
    var openGameId by rememberSaveable { mutableStateOf<Long?>(null) }

    val players by viewModel.players.collectAsStateWithLifecycle()
    val games by viewModel.games.collectAsStateWithLifecycle()
    val statLines by viewModel.statLines.collectAsStateWithLifecycle()
    val standings by viewModel.standings.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val dataUpdatedAt by viewModel.dataUpdatedAt.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.syncMessages.collect { snackbarHostState.showSnackbar(it) }
    }

    val showingDetail = openPlayerId != null || openGameId != null
    SystemBackHandler(enabled = showingDetail) {
        openPlayerId = null
        openGameId = null
    }

    val openPlayer = openPlayerId?.let { id -> players.firstOrNull { it.id == id } }
    val openGame = openGameId?.let { id -> games.firstOrNull { it.id == id } }

    val playerDetail: @Composable () -> Unit = {
        if (openPlayer != null) {
            PlayerDetailScreen(
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
        }
    }
    val gameDetail: @Composable () -> Unit = {
        if (openGame != null) {
            GameDetailScreen(
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
        }
    }

    // From 600dp wide (an unfolded iPhone Duo or Android foldable, a tablet, an
    // Android phone on its side) the tabs move to a rail and a page opens beside
    // its list: see WideLayout.kt. Everything above is shared by both layouts,
    // so folding or unfolding keeps the tab and whatever page was open. Narrower
    // windows - every phone held upright - skip this and run the phone layout
    // below exactly as before.
    val windowWidth = windowWidthDp()
    if (isWideWindow(windowWidth)) {
        WideApp(
            currentTab = currentTab,
            onSelectTab = { tab ->
                if (tab != currentTab) {
                    // Each page belongs to one tab, so choosing another closes
                    // it: folding afterwards never shows a page from a
                    // different tab.
                    openPlayerId = null
                    openGameId = null
                    currentTab = tab
                }
            },
            windowWidth = windowWidth,
            players = players,
            games = games,
            statLines = statLines,
            standings = standings,
            dataUpdatedAt = dataUpdatedAt,
            isSyncing = isSyncing,
            onRefresh = viewModel::refresh,
            snackbarHostState = snackbarHostState,
            openPlayerId = openPlayerId,
            openGameId = openGameId,
            showsDetail = openPlayer != null || openGame != null,
            onOpenPlayer = { openGameId = null; openPlayerId = it },
            onOpenGame = { openPlayerId = null; openGameId = it },
            onSavePlayer = viewModel::savePlayer,
            onSaveGame = { game -> viewModel.saveGame(game) { openGameId = it } },
            detail = { if (openPlayer != null) playerDetail() else gameDetail() }
        )
        return
    }

    when {
        openPlayer != null -> playerDetail()

        openGame != null -> gameDetail()

        else -> Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentTab == tab,
                            onClick = { currentTab = tab },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    // The selected tab's pill is sized from the
                                    // icon: 4dp a side brings Material 3 1.4's
                                    // 56dp pill back to the 64dp it always was.
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            },
                            // Kept to one line: see TabLabel. "Standings" broke
                            // mid-word on a narrow screen.
                            label = { TabLabel(tab.label) },
                            // Material 3 1.4 turned the selected label the
                            // secondary colour; keep it dark, as it was.
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        ) { innerPadding ->
            PullToRefreshBox(
                isRefreshing = isSyncing,
                onRefresh = viewModel::refresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabContent(
                    tab = currentTab,
                    players = players,
                    games = games,
                    statLines = statLines,
                    standings = standings,
                    dataUpdatedAt = dataUpdatedAt,
                    onSavePlayer = viewModel::savePlayer,
                    onOpenPlayer = { openPlayerId = it },
                    onSaveGame = { game -> viewModel.saveGame(game) { openGameId = it } },
                    onOpenGame = { openGameId = it }
                )
            }
        }
    }
}

/**
 * The wide window: the tabs on a rail, and the chosen tab's list with its page
 * beside it. The pieces are the same screens the phone draws.
 */
@Composable
private fun WideApp(
    currentTab: Tab,
    onSelectTab: (Tab) -> Unit,
    windowWidth: Float,
    players: List<com.example.data.Player>,
    games: List<com.example.data.Game>,
    statLines: List<com.example.data.StatLine>,
    standings: List<com.example.data.StandingsRow>,
    dataUpdatedAt: String?,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    snackbarHostState: SnackbarHostState,
    openPlayerId: Long?,
    openGameId: Long?,
    showsDetail: Boolean,
    onOpenPlayer: (Long) -> Unit,
    onOpenGame: (Long) -> Unit,
    onSavePlayer: (com.example.data.Player) -> Unit,
    onSaveGame: (com.example.data.Game) -> Unit,
    detail: @Composable () -> Unit,
) {
    WideFrame(currentTab = currentTab, onSelectTab = onSelectTab) {
        ListDetailPanes(
            windowWidth = windowWidth.dp,
            showsDetail = showsDetail,
            placeholder = placeholderFor(currentTab),
            isRefreshing = isSyncing,
            onRefresh = onRefresh,
            snackbarHostState = snackbarHostState,
            list = {
                TabContent(
                    tab = currentTab,
                    players = players,
                    games = games,
                    statLines = statLines,
                    standings = standings,
                    dataUpdatedAt = dataUpdatedAt,
                    onSavePlayer = onSavePlayer,
                    onOpenPlayer = onOpenPlayer,
                    onSaveGame = onSaveGame,
                    onOpenGame = onOpenGame,
                    selectedPlayerId = openPlayerId,
                    selectedGameId = openGameId
                )
            },
            detail = detail
        )
    }
}

/**
 * One tab's own screen. Shared by both layouts so a tab looks the same whether
 * it fills a phone or sits in the list pane beside a page.
 */
@Composable
private fun TabContent(
    tab: Tab,
    players: List<com.example.data.Player>,
    games: List<com.example.data.Game>,
    statLines: List<com.example.data.StatLine>,
    standings: List<com.example.data.StandingsRow>,
    dataUpdatedAt: String?,
    onSavePlayer: (com.example.data.Player) -> Unit,
    onOpenPlayer: (Long) -> Unit,
    onSaveGame: (com.example.data.Game) -> Unit,
    onOpenGame: (Long) -> Unit,
    selectedPlayerId: Long? = null,
    selectedGameId: Long? = null,
) {
    when (tab) {
        Tab.Roster -> RosterScreen(
            players = players,
            statLines = statLines,
            onSavePlayer = onSavePlayer,
            onOpenPlayer = { onOpenPlayer(it.id) },
            selectedPlayerId = selectedPlayerId
        )

        Tab.Games -> GamesScreen(
            games = games,
            statLines = statLines,
            onSaveGame = onSaveGame,
            onOpenGame = { onOpenGame(it.id) },
            selectedGameId = selectedGameId
        )

        Tab.Leaders -> LeadersScreen(
            players = players,
            games = games,
            statLines = statLines,
            dataUpdatedAt = dataUpdatedAt
        )

        Tab.Standings -> StandingsScreen(
            standings = standings,
            dataUpdatedAt = dataUpdatedAt,
            opponentRecords = remember(games) { opponentRecords(games) }
        )
    }
}
