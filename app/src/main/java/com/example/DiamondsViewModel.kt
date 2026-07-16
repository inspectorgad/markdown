package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DiamondsDatabase
import com.example.data.Game
import com.example.data.Player
import com.example.data.Seeder
import com.example.data.StatLine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DiamondsViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = DiamondsDatabase.get(app).dao()

    init {
        // First launch only: load the bundled KC Diamonds roster/game data.
        viewModelScope.launch { Seeder.seedIfEmpty(app, dao) }
    }

    val players: StateFlow<List<Player>> = dao.observePlayers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val games: StateFlow<List<Game>> = dao.observeGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val statLines: StateFlow<List<StatLine>> = dao.observeStatLines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun savePlayer(player: Player) = viewModelScope.launch {
        if (player.id == 0L) dao.insertPlayer(player) else dao.updatePlayer(player)
    }

    fun deletePlayer(player: Player) = viewModelScope.launch { dao.deletePlayer(player) }

    fun saveGame(game: Game, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = if (game.id == 0L) dao.insertGame(game) else {
            dao.updateGame(game)
            game.id
        }
        onSaved(id)
    }

    fun deleteGame(game: Game) = viewModelScope.launch { dao.deleteGame(game) }

    fun saveStatLine(line: StatLine) = viewModelScope.launch { dao.upsertStatLine(line) }

    fun deleteStatLine(line: StatLine) = viewModelScope.launch { dao.deleteStatLine(line) }
}
