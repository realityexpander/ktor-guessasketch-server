package com.realityexpander.data

import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

class Room(
    val name: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf(),
) {

    enum class GamePhase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        ROUND_IN_PROGRESS,
        ROUND_ENDED,
    }

    init {
        setGamePhaseChangeListener { newGamePhase ->
            when(newGamePhase) {
                GamePhase.WAITING_FOR_PLAYERS -> waitingFOrPlayers()
                GamePhase.WAITING_FOR_START -> waitingForStart()
                GamePhase.NEW_ROUND -> newRound()
                GamePhase.ROUND_IN_PROGRESS -> roundInProgress()
                GamePhase.ROUND_ENDED -> roundEnded()
            }
        }
    }

    private var gamePhaseChangeListener: ((GamePhase) -> Unit)? = null
    var phase = GamePhase.WAITING_FOR_PLAYERS
        private set(value) {
            synchronized(field) {  // to allow for concurrent access
                field = value
                gamePhaseChangeListener?.let { gamePhaseChange ->
                    gamePhaseChange(value)  // set the next phase of the game
                }
            }
        }

    private fun setGamePhaseChangeListener(listener: (GamePhase) -> Unit) {
        gamePhaseChangeListener = listener
    }

    private fun waitingFOrPlayers(){

    }

    private fun waitingForStart(){

    }

    private fun newRound(){

    }

    private fun roundInProgress(){

    }

    private fun roundEnded(){

    }

    suspend fun broadcast(message: String) {
        players.forEach {player ->
          if(player.socket.isActive) {
            player.socket.send(Frame.Text(message))
          }
        }
    }

    suspend fun broadcastToAllExcept(message: String, clientIdToExclude: String) {
        players.forEach {player ->
            if(player.clientId != clientIdToExclude && player.socket.isActive) {
                player.socket.send(Frame.Text(message))
            }
        }
    }

    fun containsPlayer(playerName: String): Boolean {
        return players.find { it.username == playerName } != null
    }
}