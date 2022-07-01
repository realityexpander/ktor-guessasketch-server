package com.realityexpander.data

import com.realityexpander.common.Constants.SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
import com.realityexpander.data.models.socket.Announcement
import com.realityexpander.data.models.socket.WordToGuess
import com.realityexpander.data.models.socket.GamePhaseChange
import com.realityexpander.gson
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*

const val ONE_PLAYER = 1
const val TWO_PLAYERS = 2

class Room(
    val roomName: String,
    val maxPlayers: Int,
    var players: List<Player> = listOf(),
) {

    enum class GamePhase {
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        ROUND_IN_PROGRESS,  // game_running
        ROUND_ENDED,  // show_word
    }

    companion object {
        const val UPDATE_TIME_FREQUENCY_MILLIS = 1000L

        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS = 10000L
        const val DELAY_NEW_ROUND_TO_ROUND_IN_PROGRESS_MILLIS = 20000L
        const val DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS = 60000L
        const val DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS = 10000L
    }

    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<Player>()
    private var wordToGuess: String? = null


    ////// GAME STATE MACHINE ///////

    init {
        setGamePhaseChangeListener { newGamePhase ->
            when(newGamePhase) {
                GamePhase.WAITING_FOR_PLAYERS -> waitingForPlayersPhase()
                GamePhase.WAITING_FOR_START -> waitingForStartPhase()
                GamePhase.NEW_ROUND -> newRoundPhase()
                GamePhase.ROUND_IN_PROGRESS -> roundInProgressPhase()
                GamePhase.ROUND_ENDED -> roundEndedPhase()
            }
        }
    }

    private var gamePhaseChangeListener: ((GamePhase) -> Unit)? = null
    var gamePhase = GamePhase.WAITING_FOR_PLAYERS
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


    /// GAME PHASES ///

    private fun waitingForPlayersPhase(){
        GlobalScope.launch {
            val gamePhaseChange = GamePhaseChange(
                gamePhase = GamePhase.WAITING_FOR_PLAYERS
            )
            broadcast(gson.toJson(gamePhaseChange))
        }
    }

    private fun waitingForStartPhase(){
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotify(
                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            )

            val gamePhaseChange = GamePhaseChange(
                gamePhase = GamePhase.WAITING_FOR_START,
                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            )
            broadcast(gson.toJson(gamePhaseChange))
        }
    }

    private fun newRoundPhase(){
        GlobalScope.launch {

        }
    }

    private fun roundInProgressPhase(){
        // game_running
    }

    private fun roundEndedPhase(){
        // show_word

        GlobalScope.launch {

            // Reduce the drawing player's score by the penalty for not guessing the word
            if(winningPlayers.isEmpty()) {
                drawingPlayer?.let {player ->
                    player.score -= SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
                }
            }

            // Broadcast the wordToGuess to all players
            wordToGuess?.let { wordToGuess ->
                val word = WordToGuess(
                    wordToGuess = wordToGuess,
                    roomName = roomName
                )
                broadcast(gson.toJson(word))
            }

            startGamePhaseCountdownTimerAndNotify(DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS)
            val gamePhaseChange = GamePhaseChange(
                gamePhase = GamePhase.ROUND_ENDED,
                DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS
            )
            broadcast(gson.toJson(gamePhaseChange))

        }
    }

    fun setWordToGuessAndStartRound(wordToGuess: String) {
        this.wordToGuess = wordToGuess
        gamePhase = GamePhase.ROUND_IN_PROGRESS
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

        // Only player in the room?  -> keep waiting for more players
        if (players.size == ONE_PLAYER) {
            gamePhase = GamePhase.WAITING_FOR_PLAYERS
        }

        // Two players in the room?  -> ready to start the game
        if (players.size == TWO_PLAYERS && gamePhase == GamePhase.WAITING_FOR_PLAYERS) {
            gamePhase = GamePhase.WAITING_FOR_START
            players = players.shuffled()
        }

        // Max number of players in the room?  -> start the game
        if (players.size == maxPlayers && gamePhase == GamePhase.WAITING_FOR_START) {
            gamePhase = GamePhase.NEW_ROUND
            players = players.shuffled()
        }

        // Send announcement to all players
        val announcement = Announcement( "Player $playerName has joined the game",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        broadcast(gson.toJson(announcement))

        return newPlayer
    }

    //////// MESSAGING /////////

    private fun startGamePhaseCountdownTimerAndNotify(startPhaseTimerMillis: Long) {
        timerJob?.cancel()
        timerJob = GlobalScope.launch {
            val gamePhaseChange = GamePhaseChange(
                gamePhase,
                startPhaseTimerMillis,
                drawingPlayer?.playerName  // should use clientId? TODO
            )

            // Update the countdown timer
            repeat( (startPhaseTimerMillis/ UPDATE_TIME_FREQUENCY_MILLIS).toInt() ) { count ->
                if(count != 0) {
                    gamePhaseChange.gamePhase = null
                }

                broadcast(gson.toJson(gamePhaseChange))
                gamePhaseChange.countdownTimerMillis -= UPDATE_TIME_FREQUENCY_MILLIS
                delay(UPDATE_TIME_FREQUENCY_MILLIS)
            }

            // Update the game phase to the next one
            gamePhase = when(gamePhase) {
                GamePhase.WAITING_FOR_START -> GamePhase.NEW_ROUND
                GamePhase.ROUND_IN_PROGRESS -> GamePhase.ROUND_ENDED
                GamePhase.ROUND_ENDED -> GamePhase.NEW_ROUND
                GamePhase.NEW_ROUND -> GamePhase.ROUND_IN_PROGRESS
                else -> GamePhase.WAITING_FOR_PLAYERS
            }
        }
    }

    suspend fun broadcast(messageJson: String) {
        players.forEach {player ->
          if(player.socket.isActive) {
            player.socket.send(Frame.Text(messageJson))
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