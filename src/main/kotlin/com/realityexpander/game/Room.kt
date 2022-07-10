package com.realityexpander.game

import com.realityexpander.common.*
import com.realityexpander.common.Constants.PLAYER_EXIT_REMOVE_DELAY_MILLIS
import com.realityexpander.common.Constants.SCORE_FOR_DRAWING_PLAYER_WHEN_OTHER_PLAYER_CORRECT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_DEFAULT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_MULTIPLIER
import com.realityexpander.common.Constants.SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
import com.realityexpander.data.ExitingPlayer
import com.realityexpander.data.models.socket.*
import com.realityexpander.data.models.socket.Announcement.Companion.ANNOUNCEMENT_PLAYER_EXITED_ROOM
import com.realityexpander.data.models.socket.DrawData.Companion.DRAW_DATA_MOTION_EVENT_ACTION_DOWN
import com.realityexpander.data.models.socket.DrawData.Companion.DRAW_DATA_MOTION_EVENT_ACTION_UP
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
        const val TIMER_UPDATE_FREQUENCY_MILLIS = 1000L

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
                else -> {}
            }
        }
    }

    // Change game phase to a new phase and call the phase change function
    private var gamePhaseChangeListener: ((GamePhase) -> Unit)? = null
    var gamePhase = GamePhase.INITIAL_STATE
        private set(newGamePhase) {
            synchronized(field) {  // to allow for concurrent access
                field = newGamePhase
                println("Changing game phase to --> $newGamePhase")
                gamePhaseChangeListener?.let { gamePhaseFunction ->
                    gamePhaseFunction(newGamePhase)  // set the next phase of the game
                }
            }
        }

    private fun setGamePhaseChangeListener(listener: (GamePhase) -> Unit) {
        gamePhaseChangeListener = listener
    }


    /// GAME PHASES ///

    // Waiting for more than 1 player to join the room
    private fun waitingForPlayersPhase(){
        GlobalScope.launch {
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase = GamePhase.WAITING_FOR_PLAYERS
            )
            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    // Waiting for more players to join the room
    private fun waitingForStartPhase(){
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotifyPlayers(
                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
            )

//            val gamePhaseUpdate = GamePhaseUpdate(
//                gamePhase = GamePhase.WAITING_FOR_START,
//                DELAY_WAITING_FOR_START_TO_NEW_ROUND_MILLIS
//            )
//            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    // Starting the round, pick a drawing player and let them choose a word from the list of words
    private fun newRoundPhase() {  // newRound // todo remove at end
        curRoundDrawData = listOf() // reset the drawing data
        curWords = getRandomWords(3)
        val wordsToPickHolder = WordsToPickHolder(curWords!!)
        this.wordToGuess = null // reset the word to guess

        proceedToNextDrawingPlayer()

        // Send the list of words to guess to the drawing player to pick one
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotifyPlayers(DELAY_NEW_ROUND_TO_ROUND_IN_PROGRESS_MILLIS)

            // Send the drawing player the list of words to pick from
            sendToOnePlayer(gson.toJson(wordsToPickHolder), drawingPlayer)

            // Send the stats for all players
            broadcastAllPlayersData()
        }
    }

    private fun roundInProgressPhase(){ // game_running gameRunning  // todo remove at end
        drawingPlayer ?: throw IllegalStateException("drawingPlayer is null")

        winningPlayers = listOf() // reset the list of winning players

        // define the word that players must guess, and if the drawing player didn't set it, pick one at random
        val wordToGuessToSendDrawingPlayer = wordToGuess ?: curWords?.random() ?: words.random()
        val wordToGuessToSendAsUnderscores = wordToGuessToSendDrawingPlayer.transformToUnderscores()

        // Drawing player gets sent the word to guess
        val gameStateForDrawingPlayer = GameState(
            drawingPlayer?.playerName!!,
            drawingPlayer?.clientId!!,
            wordToGuessToSendDrawingPlayer
        )

        // Other players get sent the word to guess AS UNDERSCORES
        val gameStateForGuessingPlayers = GameState(
            drawingPlayer?.playerName!!,
            drawingPlayer?.clientId!!,
            wordToGuessToSendAsUnderscores
        )

        // Send the new GameState to all players
        GlobalScope.launch {
            // send the word to guess (as underscores) to everyone except the drawing player
            broadcastToAllExceptOneClientId(
                gson.toJson(gameStateForGuessingPlayers),
                drawingPlayer?.clientId!!
            )

            // Send the actual word to guess to the drawing player
            sendToOnePlayer(
                gson.toJson(gameStateForDrawingPlayer),
                drawingPlayer
            )
        }

        startGamePhaseCountdownTimerAndNotifyPlayers(
            DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS
        )

        println("Starting ROUND_IN_PROGRESS phase for room `$roomName`,\n" +
                " ┡--> drawingPlayer: ${drawingPlayer?.playerName!!}\n" +
                " ┡--> wordToGuess to drawingPlayer: $wordToGuessToSendDrawingPlayer\n" +
                " ┡--> wordToGuess to guessing players: $wordToGuessToSendAsUnderscores\n" +
                " ┕--> Timer set to ${DElAY_ROUND_IN_PROGRESS_TO_ROUND_ENDED_MILLIS / 1000} seconds\n")
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

            // Broadcast the unmasked wordToGuess to all players
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

    fun drawingPlayerSetWordToGuessAndStartRound(wordToGuess: String) {
        this.wordToGuess = wordToGuess
        gamePhase = GamePhase.ROUND_IN_PROGRESS
    }

    fun proceedToNextDrawingPlayer() {
        if(players.isEmpty()) return

        // Reset all the players `isDrawing` status, just to be safe, :)
        for (player in players) {
            player.isDrawing = false
        }
        drawingPlayer?.isDrawing = false // reset the current drawing player status

        // Pick the new drawing player
        drawingPlayer = if(drawingPlayerIndex <= players.size - 1) {
            players[drawingPlayerIndex]
        } else {
            players.last()
        }
        drawingPlayer?.isDrawing = true

        // Setup for the next new drawing player
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
                message = "Player '${winningPlayer.playerName}' guessed the word correctly!",
                timestamp = System.currentTimeMillis(),
                Announcement.ANNOUNCEMENT_PLAYER_GUESSED_CORRECTLY
            )
            broadcast(gson.toJson(announcement))

            // Check if the round is completed (true if everyone guessed it)
            val isRoundOver = addWinningPlayer(winningPlayer)
            if (isRoundOver) {
                announcement = Announcement(
                    message = "EVERYBODY GUESSED IT! Round over! New round starting...",
                    timestamp = System.currentTimeMillis(),
                    Announcement.ANNOUNCEMENT_EVERYBODY_GUESSED_CORRECTLY
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
    suspend fun sendWordToGuessToPlayer(player: Player) {
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
                    drawingPlayer.clientId,
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
    private fun startGamePhaseCountdownTimerAndNotifyPlayers(gamePhaseDurationMillis: Long) {

        timerJob?.cancel()
        timerJob = GlobalScope.launch {

            // Set and Start the game phase countdown timer
            gamePhaseStartTimeMillis = System.currentTimeMillis()
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase,  // new phase of the game to change to
                gamePhaseDurationMillis,
                drawingPlayer?.playerName
            )

            // Notify the players that the phase has started
            broadcast(gson.toJson(gamePhaseUpdate))

            // Send players the current countdown time
            repeat( (gamePhaseDurationMillis / TIMER_UPDATE_FREQUENCY_MILLIS).toInt() ) { count ->

                // Notify the players of the current countdown time for this game phase
                if(count != 0) {
                    // After the phase has started, set the `gamePhase` field to null bc
                    //   we don't want to send a phase change, just update the time.
                    //   (note: null values are not serialized by gson)
                    gamePhaseUpdate.gamePhase = null

                    broadcast(gson.toJson(gamePhaseUpdate))
                }

                // Decrement the countdown time
                gamePhaseUpdate.countdownTimerMillis -= TIMER_UPDATE_FREQUENCY_MILLIS
                delay(TIMER_UPDATE_FREQUENCY_MILLIS)
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
            if(curRoundDrawData.isNotEmpty() && drawData.motionEvent == DRAW_DATA_MOTION_EVENT_ACTION_DOWN) {
                val finishDrawData = drawData.copy(motionEvent = DRAW_DATA_MOTION_EVENT_ACTION_UP)
                broadcast(gson.toJson(finishDrawData))
            }
        }
    }

    // Collect the serialized drawing data to be able to send it to all the players
    //  (so they can recreate the drawing)
    fun addSerializedDrawDataJson(drawDataJson: String) {
        curRoundDrawData = curRoundDrawData + drawDataJson
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

        // Default behavior is to add the player at the end of the list of players
        var indexToAddPlayerAt = players.size - 1

        // Check if this is a rejoining player
        val newPlayer = if(exitingPlayers.containsKey(clientId)) {

            val rejoiningPlayer = exitingPlayers[clientId]

            // If the player is rejoining, remove it from the exitingPlayers map
            rejoiningPlayer?.let { (rejoinedPlayer, indexOfPlayer) ->
                rejoinedPlayer.socket = socketSession
                rejoinedPlayer.isDrawing = drawingPlayer?.clientId == clientId  // is this the same as the drawing player?
                indexToAddPlayerAt = indexOfPlayer

                // Cancel and remove the "Remove Exiting Player After Delay" jobs
                playerRemoveJobs[clientId]?.cancel()
                playerRemoveJobs.remove(clientId)
                exitingPlayers.remove(clientId)

                rejoinedPlayer
            } ?: Player(playerName, socketSession, clientId) // should never get here bc we checked
        } else {
            // This is a new player
            Player(playerName, socketSession, clientId)
        }

        // Check if the index where to add the player is in bounds.
        //   (in case other players have also disconnected and the list is smaller than when the player joined.)
        indexToAddPlayerAt =
            when {
                players.isEmpty()                   -> 0
                indexToAddPlayerAt >= players.size  -> players.size - 1
                else                                -> indexToAddPlayerAt
            }

        // Insert the new player at the particular index
        val tmpPlayers = players.toMutableList()
        tmpPlayers.add(indexToAddPlayerAt, newPlayer)

        // we use `x = x + y` instead of `x+=y`, because we want to
        //   use a copy of the immutable list to work in concurrent environments.
        players = tmpPlayers.toList() // convert back to an immutable list



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

        sendWordToGuessToPlayer(newPlayer)

        // Send announcement to all players
        val announcement = Announcement( "Player '$playerName' has joined the game",
            System.currentTimeMillis(),
            Announcement.ANNOUNCEMENT_PLAYER_JOINED_ROOM
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
            //players = players - player // phillip mistake? todo remove at end

            // Launch the "final" remove player job that will happen in PLAYER_EXIT_REMOVE_DELAY_MILLIS from now.
            playerRemoveJobs[removeClientId] = GlobalScope.launch {
                delay(PLAYER_EXIT_REMOVE_DELAY_MILLIS)  // will be cancelled if the player re-joins

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
            serverDB.removePlayerFromServerDB(removeClientId)
        }

        // Tell all players that a player left
        val announcement = Announcement(
            message = "Player ${playerToRemove.playerName} has left the room.",
            timestamp = System.currentTimeMillis(),
            announcementType = ANNOUNCEMENT_PLAYER_EXITED_ROOM
        )
        GlobalScope.launch {
            broadcast(gson.toJson(announcement))
            broadcastAllPlayersData()
        }
    }

    fun cancelRemovePlayerJob(clientId: ClientId) {
        playerRemoveJobs[clientId]?.cancel()
        playerRemoveJobs -= clientId
    }

    private fun killRoom() {
        playerRemoveJobs.values.forEach { it.cancel() }
        timerJob?.cancel()
        serverDB.removeRoomFromServerDB(roomName)
    }

    //////// MESSAGING /////////

    suspend fun broadcast(messageJson: String) {
        println("Broadcast messageJson: $messageJson")

        players.forEach { player ->
          if(player.socket.isActive) {
            player.socket.send(Frame.Text(messageJson))
          }
        }
    }

    suspend fun broadcastToAllExceptOneClientId(
        messageJson: String,
        clientIdToExclude: ClientId
    ) {
        println("Broadcasting to all except ${getPlayerByClientId(clientIdToExclude)?.playerName}:")

        players.forEach { player ->
            if(player.clientId != clientIdToExclude && player.socket.isActive) {
                println("   ┡--> sending to ${player.playerName}:\n" +
                        "   ┕----> $messageJson\n")
                player.socket.send(Frame.Text(messageJson))
            }
        }
    }

    suspend fun sendToOnePlayer(messageJson: String, sendToPlayer: Player?) {
        sendToPlayer ?: run {
            println("sendToOnePlayer: sendToPlayer is null")
            return
        }

        sendToPlayer?.let { player ->
            if(player.socket.isActive) {
                player.socket.send(Frame.Text(messageJson))
                println("Send to one player: '${sendToPlayer.playerName}', message: $messageJson")
            } else {
                println("sendToOnePlayer - Player ${player.playerName} is not active, cannot send message")
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










































