package com.realityexpander.data

import com.realityexpander.common.*
import com.realityexpander.common.Constants.PLAYER_REMOVE_DELAY_MILLIS
import com.realityexpander.common.Constants.SCORE_FOR_DRAWING_PLAYER_WHEN_OTHER_PLAYER_CORRECT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_DEFAULT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_MULTIPLIER
import com.realityexpander.common.Constants.SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
import com.realityexpander.data.models.socket.*
import com.realityexpander.data.models.socket.Announcement.Companion.TYPE_PLAYER_EXITED_ROOM
import com.realityexpander.gson
import com.realityexpander.serverDB
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

const val ONE_PLAYER = 1
const val TWO_PLAYERS = 2

class Room(
    val roomName: RoomName,
    val maxPlayers: Int,
    var players: List<Player> = listOf(),
) {

    enum class GamePhase {
        INITIAL_STATE,       // no state yet.
        WAITING_FOR_PLAYERS,
        WAITING_FOR_START,
        NEW_ROUND,
        ROUND_IN_PROGRESS,  // game_running  // todo remove at end
        ROUND_ENDED,  // show_word // todo remove at end
    }

    companion object {
        const val UPDATE_TIME_FREQUENCY_MILLIS = 1000L

        const val DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS = 10000L
        const val DELAY_NEW_ROUND_TO_ROUND_IN_PROGRESS_MILLIS = 20000L
        const val DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS = 10000L
        const val DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS = 10000L
    }

    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<Player>()
    private var wordToGuess: String? = null
    private var curWords: List<String>? = null
    private var drawingPlayerIndex = 0
    private var gamePhaseStartTimeMillis = 0L  // for score keeping

    // Track players removing/reconnecting
    private var playerRemoveJobs = ConcurrentHashMap<ClientId, Job>()
    private val exitingPlayers = ConcurrentHashMap<String, ExitingPlayer>()  // leftPlayers todo remove

    // Track drawing data
    private var curRoundDrawData: List<String> = listOf()
    var lastDrawData: DrawData? = null  // prevent bug where the player is touch_down and the round has ended before the touch_up

    ////// GAME STATE MACHINE ///////

    init {
        // When the gamePhase is set, this listener calls the appropriate func
        setGamePhaseChangeListener { newGamePhase ->
            when(newGamePhase) {
                GamePhase.INITIAL_STATE -> { /* do nothing */ }
                GamePhase.WAITING_FOR_PLAYERS -> waitingForPlayersPhase()
                GamePhase.WAITING_FOR_START -> waitingForStartPhase()
                GamePhase.NEW_ROUND -> newRoundPhase()
                GamePhase.ROUND_IN_PROGRESS -> roundInProgressPhase()
                GamePhase.ROUND_ENDED -> roundEndedPhase()
            }
        }
    }

    private var gamePhaseChangeListener: ((GamePhase) -> Unit)? = null
    var gamePhase = GamePhase.INITIAL_STATE
        private set(newGamePhase) {
            synchronized(field) {  // to allow for concurrent access
                field = newGamePhase
                println("Changing game phase to $newGamePhase")
                gamePhaseChangeListener?.let { gamePhaseFunction ->
                    gamePhaseFunction(newGamePhase)  // set the next phase of the game
                }
            }
        }

    private fun setGamePhaseChangeListener(listener: (GamePhase) -> Unit) {
        gamePhaseChangeListener = listener
    }


    /// GAME PHASES ///

    private fun waitingForPlayersPhase(){
        GlobalScope.launch {
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase = GamePhase.WAITING_FOR_PLAYERS
            )
            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    private fun waitingForStartPhase(){
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotifyPlayers(
                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            )

            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase = GamePhase.WAITING_FOR_START,
                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            )
            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    private fun newRoundPhase() {  // newRound // todo remove at end
        curRoundDrawData = listOf() // reset the drawing data
        curWords = getRandomWords(3)
        val newWordsToGuess = NewWords(curWords!!)

        proceedToNextDrawingPlayer()
        GlobalScope.launch {
            sendToOnePlayer(gson.toJson(newWordsToGuess), drawingPlayer)
            startGamePhaseCountdownTimerAndNotifyPlayers(DELAY_NEW_ROUND_TO_ROUND_IN_PROGRESS_MILLIS)
            broadcastAllPlayersData()
        }
    }

    private fun roundInProgressPhase(){ // game_running gameRunning  // todo remove at end
        winningPlayers = listOf() // reset the winning players
        val wordToSend = wordToGuess ?: curWords?.random() ?: words.random()
        val wordAsUnderscores = wordToSend.transformToUnderscores()
        val drawingPlayerName = (drawingPlayer ?: players.random()).playerName

        // Drawing player gets the word to guess
        val gameStateForDrawingPlayer = GameState(
            drawingPlayerName,
            wordToSend
        )

        // Other players get the word to guess as underscores
        val gameStateForGuessingPlayers = GameState(
            drawingPlayerName,
            wordAsUnderscores
        )

        // Send the new GameState to all players
        GlobalScope.launch {
            broadcastToAllExceptOneClientId(
                gson.toJson(gameStateForGuessingPlayers),
                drawingPlayer?.clientId ?: players.random().clientId
            )
            sendToOnePlayer(gson.toJson(gameStateForDrawingPlayer), drawingPlayer)
        }

        startGamePhaseCountdownTimerAndNotifyPlayers(
            DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS
        )

        println("Starting ROUND_IN_PROGRESS phase for room `$roomName`,\n" +
                "drawing player: $drawingPlayerName\n" +
                "word to guess: $wordToSend\n" +
                "Timer set to ${DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS / 1000} seconds\n")

    }

    private fun roundEndedPhase(){
        // showWord, show_word // todo remove at end

        GlobalScope.launch {

            // Reduce the drawing player's score by the penalty for not guessing the word
            if(winningPlayers.isEmpty()) {
                drawingPlayer?.let {player ->
                    player.score -= SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
                }
            }

            // Score has possibly changed
            broadcastAllPlayersData()

            // Broadcast the wordToGuess to all players
            wordToGuess?.let { wordToGuess ->
                val word = SetWordToGuess(
                    wordToGuess = wordToGuess,
                    roomName = roomName
                )
                broadcast(gson.toJson(word))
            }

            startGamePhaseCountdownTimerAndNotifyPlayers(DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS)
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase = GamePhase.ROUND_ENDED,
                DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS
            )

            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    // GAME STATE UTILS //

    fun setWordToGuessAndStartRound(wordToGuess: String) {
        this.wordToGuess = wordToGuess
        gamePhase = GamePhase.ROUND_IN_PROGRESS
    }

    fun proceedToNextDrawingPlayer() {
        drawingPlayer?.isDrawing = false
        if(players.isEmpty()) return

        drawingPlayer = if(drawingPlayerIndex <= players.size - 1) {
            players[drawingPlayerIndex]
        } else {
            players.last()
        }

        if(drawingPlayerIndex <= players.size - 1) {
            drawingPlayerIndex++
        } else {
            drawingPlayerIndex = 0
        }
    }

    // Check chat message for correct guess and also that the guess is *not* from a
    //   winning player (ie: no cheating for entering the word multiple times!)
    fun isGuessCorrect(guessChatMessage: ChatMessage): Boolean {
        return guessChatMessage.containsWord(wordToGuess ?: return false)
                && !winningPlayers.containsPlayerClientId(guessChatMessage.fromClientId)
                && guessChatMessage.fromClientId != drawingPlayer?.clientId
                && gamePhase == GamePhase.ROUND_IN_PROGRESS
    }

    // Returns true if all the players have guessed the word and the round is over
    private fun addWinningPlayer(player: Player): Boolean {
        winningPlayers = winningPlayers + player

        if(winningPlayers.size == players.size - 1) {
            gamePhase = GamePhase.NEW_ROUND
            return true
        }

        return false
    }

    // Returns true if the player has guessed the word
    suspend fun checkWordThenScoreAndNotifyPlayers(message: ChatMessage): Boolean {
        if(isGuessCorrect(message)) {
            val winningPlayer = getPlayerByClientId(message.fromClientId) ?: return false

            // Calc score for winning player
            val guessTimeMillis = System.currentTimeMillis() - gamePhaseStartTimeMillis
            val percentTimeLeft = 1f -
                    (guessTimeMillis / DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS.toFloat())
            val score = SCORE_GUESS_CORRECT_DEFAULT +
                    (SCORE_GUESS_CORRECT_MULTIPLIER * percentTimeLeft).toInt()
            winningPlayer.score += score

            // Calc score for the drawingPlayer
            drawingPlayer?.let {player ->
                player.score += SCORE_FOR_DRAWING_PLAYER_WHEN_OTHER_PLAYER_CORRECT / players.size
            }

            // Tell other players a winner has occurred
            var announcement = Announcement(
                message = "${winningPlayer.playerName} guessed the word correctly!",
                timestamp = System.currentTimeMillis(),
                Announcement.TYPE_PLAYER_GUESSED_CORRECTLY
            )
            broadcast(gson.toJson(announcement))

            // Check if the round is completed (true if everyone guessed it)
            val isRoundOver = addWinningPlayer(winningPlayer)
            if (isRoundOver) {
                announcement = Announcement(
                    message = "EVERYBODY GUESSED IT! Round over! New round starting...",
                    timestamp = System.currentTimeMillis(),
                    Announcement.TYPE_EVERYBODY_GUESSED_CORRECTLY
                )
                broadcast(gson.toJson(announcement))
            }

            // Update all players about current scores
            broadcastAllPlayersData()

            return true
        }

        return false

    }

    // When a player joins a room (connect or reconnects)
    // Inform the player of the word to guess and the current phase
    suspend fun sendWordToPlayer(player: Player) {
        val delay = when(gamePhase) {
            GamePhase.WAITING_FOR_START -> DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            GamePhase.NEW_ROUND -> DELAY_NEW_ROUND_TO_ROUND_IN_PROGRESS_MILLIS
            GamePhase.ROUND_IN_PROGRESS -> DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS
            GamePhase.ROUND_ENDED -> DELAY_ROUND_ENDED_TO_NEW_ROUND_MILLIS
            else -> 0L
        }

        // Send the current phase and the drawing player
        val gamePhaseUpdate = GamePhaseUpdate(gamePhase, delay, drawingPlayer?.playerName)
        sendToOnePlayer(gson.toJson(gamePhaseUpdate), player)

        // If there is a word to guess, send it in the GameState
        wordToGuess?.let {curWordToGuess ->
            drawingPlayer?.let { drawingPlayer ->
                val gameState = GameState(
                    drawingPlayer.playerName,
                    if(player.isDrawing || gamePhase == GamePhase.ROUND_ENDED) {
                        curWordToGuess
                    } else {
                        curWordToGuess.transformToUnderscores()
                    }
                )

                sendToOnePlayer(gson.toJson(gameState), player)
            }
        }

    }

    // Send data of players (score, rank, name, etc) to all the players
    suspend fun broadcastAllPlayersData() {  // broadcastPlayerStates // todo remove at end
        // Collect the data for all players
        val playersList = players.sortedByDescending { it.score }.map { player ->
            PlayerData(player.playerName, player.isDrawing, player.score, player.rank)
        }

        // set the ranking for each player
        playersList.forEachIndexed { index, playerData ->
            playerData.rank = index + 1
        }

        broadcast(gson.toJson(PlayersList(playersList)))
    }


    //////  GAME PHASE TIMERS & NOTIFICATIONS //////

    // 1. Set and Start the phase countdown timer
    // 2. Notify the players that the phase has started
    // 3. Wait for the phase to end
    // 4. Notify the players that the phase has ended
    // 5. Proceed to the next phase of the game
    // timeAndNotify  todo remove at end
    private fun startGamePhaseCountdownTimerAndNotifyPlayers(startPhaseTimerMillis: Long) {

        timerJob?.cancel()
        timerJob = GlobalScope.launch {

            // Set and Start the game phase countdown timer
            gamePhaseStartTimeMillis = System.currentTimeMillis()
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase,
                startPhaseTimerMillis,
                drawingPlayer?.playerName
            )

            // Notify the players that the phase has started
            broadcast(gson.toJson(gamePhaseUpdate))

            // Send players the current countdown time
            repeat( (startPhaseTimerMillis / UPDATE_TIME_FREQUENCY_MILLIS).toInt() ) { count ->

                // Notify the players of the current countdown time for this game phase
                if(count != 0) {
                    // After the phase has started, set the `gamePhase` field to null bc
                    //   we don't want to send a phase change, just update the time.
                    //   (note: null values are not serialized by gson)
                    gamePhaseUpdate.gamePhase = null

                    broadcast(gson.toJson(gamePhaseUpdate))
                }

                println("count = $count, gamePhaseUpdate.gamePhase=${gamePhaseUpdate.gamePhase}")

                // Decrement the countdown time
                gamePhaseUpdate.countdownTimerMillis -= UPDATE_TIME_FREQUENCY_MILLIS
                delay(UPDATE_TIME_FREQUENCY_MILLIS)
            }

            // Go to the next phase of the game
            proceedToNextGamePhase()
        }
    }

    private suspend fun proceedToNextGamePhase() {
        gamePhase = when (gamePhase) {
            GamePhase.WAITING_FOR_START ->
                GamePhase.NEW_ROUND
            GamePhase.ROUND_IN_PROGRESS -> {
                finishOffDrawing()  // make sure the drawing is finished
                GamePhase.ROUND_ENDED
            }
            GamePhase.ROUND_ENDED ->
                GamePhase.NEW_ROUND
            GamePhase.NEW_ROUND -> {
                wordToGuess = null  // reset the word to guess to force a new word to be picked
                GamePhase.ROUND_IN_PROGRESS
            }
            else ->
                GamePhase.WAITING_FOR_PLAYERS
        }
    }


    ////// DRAWING ///////

    // Finish off the drawing
    private suspend fun finishOffDrawing() {
        lastDrawData?.let { drawData ->
            if(curRoundDrawData.isNotEmpty() && drawData.motionEvent == DRAW_MOTION_EVENT_ACTION_MOVE) {
                val finishDrawData = drawData.copy(motionEvent = DRAW_MOTION_EVENT_ACTION_UP)
                broadcast(gson.toJson(finishDrawData))
            }
        }
    }

    // Collect the serialized drawing data to be able to send it to all the players
    //  (so they can recreate the drawing)
    fun addSerializedDrawActionJson(drawActionJson: String) {
        curRoundDrawData = curRoundDrawData + drawActionJson
    }

    // Send the serialized drawing data to all the players
    private suspend fun sendCurRoundDrawDataToPlayer(player: Player) {
        if(gamePhase == GamePhase.ROUND_IN_PROGRESS || gamePhase == GamePhase.ROUND_ENDED) {
            sendToOnePlayer(gson.toJson(curRoundDrawData), player)
        }
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////


    ////// ROOM DATABASE OPERATIONS ///////

    suspend fun addPlayer(
        clientId: ClientId,
        playerName: String,
        socketSession: WebSocketSession
    ): Player {

        // default to add the player at the end of the list of players
        var indexToAddPlayerAt = players.size - 1

        // check if this is a rejoining player
        val newPlayer = if(exitingPlayers.containsKey(clientId)) {

            val rejoiningPlayer = exitingPlayers[clientId]

            rejoiningPlayer?.let { (rejoinedPlayer, indexOfPlayer) ->
                rejoinedPlayer.socket = socketSession
                rejoinedPlayer.isDrawing = drawingPlayer?.clientId == clientId  // is this the same as the drawing player?
                indexToAddPlayerAt = indexOfPlayer

                // Cancel and remove the "Remove Exiting Player After Delay" jobs
                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                exitingPlayers.remove(clientId)

                rejoinedPlayer
            } ?: Player(playerName, socketSession, clientId) // should never happen bc we checked
        } else {
            Player(playerName, socketSession, clientId)
        }

        // Check if the index to add the player at is in bounds.
        //   (in case other players have also disconnected and the list is smaller than when the player joined.)
        indexToAddPlayerAt = when {
            players.isEmpty() -> 0
            indexToAddPlayerAt >= players.size -> players.size - 1
            else -> indexToAddPlayerAt
        }

        // Insert the new player at a particular index
        val tmpPlayers = players.toMutableList()
        tmpPlayers.add(indexToAddPlayerAt, newPlayer)
        players = tmpPlayers.toList() // convert back to an immutable list


        // we use `x=x+y` instead of `x+=y`, because we want to
        //   use a copy of the immutable list to work in concurrent environments.
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

        sendWordToPlayer(newPlayer)

        // Send announcement to all players
        val announcement = Announcement( "Player $playerName has joined the game",
            System.currentTimeMillis(),
            Announcement.TYPE_PLAYER_JOINED
        )
        broadcast(gson.toJson(announcement))

        // Send the all current players' data to all players
        broadcastAllPlayersData()

        // Send the drawing data to the new player
        sendCurRoundDrawDataToPlayer(newPlayer)

        return newPlayer
    }

    // Flow is:
    // removePlayer(removeImmediately == false)
    //   -> Add player to exiting List
    //   -> wait PLAYER_REMOVE_DELAY_MILLIS ms
    //   -> finally remove player from room & exiting list
    //   -> remove the player from the server
    fun removePlayer(removeClientId: ClientId, isImmediateRemoval: Boolean = false) {
        val playerToRemove = getPlayerByClientId(removeClientId) ?: return
        val index = players.indexOf(playerToRemove)

        if (!isImmediateRemoval) {
            // Delayed removal of player (allows for reconnects with 60s)

            // Add player to exiting list
            exitingPlayers[removeClientId] = ExitingPlayer(playerToRemove, index)
            //players = players - player // phillip mistake? todo

            // Launch the "final" remove player job that will happen in PLAYER_REMOVE_DELAY_MILLIS from now.
            playerRemoveJobs[removeClientId] = GlobalScope.launch {
                delay(PLAYER_REMOVE_DELAY_MILLIS)  // will be cancelled if the player re-joins

                val removePlayer = exitingPlayers[removeClientId]

                // remove this player from our exitingPlayers list
                exitingPlayers.remove(removeClientId)

                // FINALLY remove the player from the room
                removePlayer?.let { exitingPlayer ->
                    players = players - exitingPlayer.player
                }

                // And remove this job
                playerRemoveJobs -= removeClientId

                // Check if there is only one player
                if(players.size == 1) {
                    gamePhase = GamePhase.WAITING_FOR_PLAYERS
                    timerJob?.cancel()
                }

                // Check if there are no players
                if(players.isEmpty()) {
                    killRoom()
                }
            }
        } else {
            // Removing Player Immediately
            println("Removing player ${playerToRemove.playerName}")

            // Remove the player from this room
            players = players - playerToRemove

            // Remove the player from the server
            serverDB.removePlayerFromServer(removeClientId)
        }

        // Tell all players that a player left
        val announcement = Announcement(
            message = "Player ${playerToRemove.playerName} has left the room.",
            timestamp = System.currentTimeMillis(),
            announcementType = TYPE_PLAYER_EXITED_ROOM
        )
        GlobalScope.launch {
            broadcast(gson.toJson(announcement))
            broadcastAllPlayersData()
        }
    }

    private fun killRoom() {
        playerRemoveJobs.values.forEach { it.cancel() }
        timerJob?.cancel()
        serverDB.removeRoomFromServer(roomName)
    }

    //////// MESSAGING /////////

    suspend fun broadcast(messageJson: String) {
        println("messageJson: $messageJson")
        players.forEach {player ->
          if(player.socket.isActive) {
            player.socket.send(Frame.Text(messageJson))
          }
        }
    }

    suspend fun broadcastToAllExceptOneClientId(messageJson: String, clientIdToExclude: ClientId) {
        players.forEach {player ->
            if(player.clientId != clientIdToExclude && player.socket.isActive) {
                player.socket.send(Frame.Text(messageJson))
            }
        }
    }

    suspend fun sendToOnePlayer(messageJson: String, player: Player?) {
        player?.let {
            if(it.socket.isActive) {
                it.socket.send(Frame.Text(messageJson))
            }
        }
    }

    //////// UTILITIES /////////

    fun containsPlayerName(playerName: String): Boolean {
        return players.find { it.playerName == playerName } != null
    }

    fun containsPlayerClientId(clientId: ClientId): Boolean {
        return players.find { it.clientId == clientId } != null
    }

    fun List<Player>.containsPlayerClientId(clientId: ClientId): Boolean {
        return find { it.clientId == clientId } != null
    }

    fun getPlayerByClientId(clientId: ClientId): Player? {
        return players.find { it.clientId == clientId }
    }
}








///////////////////////// TESTING //////////////////////

fun main() {

    data class PlayerA(
        val playerName: String,
        var socket: WebSocketSession? = null,
        val clientId: ClientId,
        var isDrawing: Boolean = false,
        var score: Int = 0,
        var rank: Int = 0,
    )


    val player1 = PlayerA("Player 1", null, "1")
    val player2 = PlayerA("Player 1", null, "1")

    println(player1 == player2)
}










































