package com.realityexpander.data

import com.realityexpander.data.models.socket.Announcement
import com.realityexpander.gson
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.isActive

const val ONE_PLAYER = 1
const val TWO_PLAYERS = 2

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

    ////// GAME STATE MACHINE ///////

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

    ////////////////////////////////////////////////////////////////////////////////////////////////


    ////// DATABASE OPERATIONS ///////

    suspend fun addPlayer(
        clientId: String,
        playerName: String,
        socketSession: WebSocketSession
    ): Player {
        val newPlayer = Player(playerName, socketSession, clientId)

        // we use `x=x+y` instead of `x+=y`, because we want a copy of the immutable list to work in concurrent environments
        players = players + newPlayer

        if (players.size == ONE_PLAYER) {
            phase = GamePhase.WAITING_FOR_PLAYERS
        } else if (players.size == TWO_PLAYERS && phase == GamePhase.WAITING_FOR_PLAYERS) {
            phase = GamePhase.WAITING_FOR_START
            players = players.shuffled()
        } else if (phase == GamePhase.WAITING_FOR_START && players.size == maxPlayers) {
            phase = GamePhase.NEW_ROUND
            players = players.shuffled()
        }

        val announcement = Announcement( "Player $playerName has joined the game",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        broadcast(gson.toJson(announcement))

        return newPlayer
    }

    //////// MESSAGING /////////

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

    //////// UTILITIES /////////

    fun containsPlayer(playerName: String): Boolean {
        return players.find { it.playerName == playerName } != null
    }
}