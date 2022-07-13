package com.realityexpander.game

import com.realityexpander.common.*
import com.realityexpander.common.Constants.PLAYER_EXIT_REMOVE_PERMANENTLY_DELAY_MILLIS
import com.realityexpander.common.Constants.SCORE_FOR_DRAWING_PLAYER_WHEN_OTHER_PLAYER_CORRECT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_DEFAULT
import com.realityexpander.common.Constants.SCORE_GUESS_CORRECT_MULTIPLIER
import com.realityexpander.common.Constants.SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
import com.realityexpander.data.models.socket.*
import com.realityexpander.data.models.socket.Announcement.Companion.ANNOUNCEMENT_GENERAL_MESSAGE
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

const val TIMER_UPDATE_FREQUENCY_MILLIS = 1000L

class Room(
    val roomName: RoomName,
    val maxPlayers: Int,
    var players: List<Player> = listOf(),
) {
    private var timerJob: Job? = null
    private var drawingPlayer: Player? = null
    private var winningPlayers = listOf<Player>()
    private var wordToGuess: String? = null
    private var curWords: List<String>? = null
    private var drawingPlayerIndex = 0
    private var gamePhaseStartTimeMillis = 0L  // for score keeping

    // Track players removing/reconnecting
    private var removePlayerPermanentlyJobs = ConcurrentHashMap<ClientId, Job>()
    private val exitingPlayers = ConcurrentHashMap<String, ExitingPlayer>()  // leftPlayers todo remove

    // Track drawing data
    private var curRoundDrawData: List<String> = listOf()  // list of json strings of DrawData and DrawAction objects
    var lastDrawData: DrawData? = null  // prevent bug where the player is touch_down and the round has ended before the touch_up

    ////// GAME STATE MACHINE ///////

    init {
        // When a new gamePhaseUpdate is set, this listener calls the appropriate func
        setGamePhaseChangeListener { newGamePhase ->
            when(newGamePhase) {
                GamePhaseUpdate.GamePhase.INITIAL_STATE -> { /* do nothing */ }
                GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS -> waitingForPlayersPhase()
                GamePhaseUpdate.GamePhase.WAITING_FOR_START -> waitingForStartPhase()
                GamePhaseUpdate.GamePhase.NEW_ROUND -> newRoundPhase()
                GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS -> roundInProgressPhase()
                GamePhaseUpdate.GamePhase.ROUND_ENDED -> roundEndedPhase()
                else -> {
                    println("ERROR: setGamePhaseChangeListener - Unknown game phase: $newGamePhase")
                }
            }
        }
    }

    // Change game phase to a new phase and call the phase change function
    private var gamePhaseChangeListener: ((GamePhaseUpdate.GamePhase) -> Unit)? = null
    var gamePhase = GamePhaseUpdate.GamePhase.INITIAL_STATE
        private set(newGamePhase) {
            synchronized(field) {  // to allow for concurrent access
                field = newGamePhase
                println("Changing game phase to --> $newGamePhase")
                gamePhaseChangeListener?.let { gamePhaseChange ->
                    gamePhaseChange(newGamePhase)  // set the next phase of the game
                }
            }
        }

    private fun setGamePhaseChangeListener(listener: (GamePhaseUpdate.GamePhase) -> Unit) {
        gamePhaseChangeListener = listener
    }


    ///////////////////
    /// GAME PHASES ///
    ///////////////////

    // Waiting for more than 1 player to join the room
    private fun waitingForPlayersPhase() {
        GlobalScope.launch {

            // Since this phase has indeterminate duration, we just send the message here.
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase = GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS
            )
            broadcast(gson.toJson(gamePhaseUpdate))
        }
    }

    // Waiting for more players to join the room
    private fun waitingForStartPhase() {
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotifyPlayers()
        }
    }

    // Starting the round, choose a drawing player and let them pick a word from the list of 3 words
    private fun newRoundPhase() {  // newRound // todo remove at end
        curRoundDrawData = listOf() // reset the drawing data
        curWords = getRandomWords(3)
        val wordsToPickHolder = WordsToPick(curWords!!)
        this.wordToGuess = null // reset the word to guess

        // Pick a drawing player
        proceedToNextDrawingPlayer()

        // Send the list of words to guess to the drawing player to pick one
        GlobalScope.launch {
            startGamePhaseCountdownTimerAndNotifyPlayers()

            // Send the drawing player the list of words to pick from
            sendToOnePlayer(gson.toJson(wordsToPickHolder), drawingPlayer)

            // Send the stats for all players
            broadcastAllPlayersData()
        }
    }

    private fun roundInProgressPhase() { // game_running gameRunning  // todo remove at end
        drawingPlayer ?: throw IllegalStateException("drawingPlayer is null")

        winningPlayers = listOf() // reset the list of winning players

        // define the word that players must guess, and if the drawing player didn't set it, pick one at random
        val wordToGuessToSendDrawingPlayer = wordToGuess ?: curWords?.random() ?: words.random()
        wordToGuess = wordToGuessToSendDrawingPlayer  // in case the drawing player didn't set it
        val wordToGuessToSendAsUnderscores = wordToGuessToSendDrawingPlayer.transformToUnderscores()

        // Drawing player is sent the actual word to guess
        val gameStateForDrawingPlayer = GameState(
            drawingPlayer?.playerName!!,
            drawingPlayer?.clientId!!,
            wordToGuessToSendDrawingPlayer
        )

        // Other players are sent the word to guess AS UNDERSCORES
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

            startGamePhaseCountdownTimerAndNotifyPlayers()

            println("Starting ROUND_IN_PROGRESS phase for room `$roomName`,\n" +
                    " ┡--> drawingPlayer: '${drawingPlayer?.playerName!!}'\n" +
                    " ┡--> wordToGuess to drawingPlayer: '$wordToGuessToSendDrawingPlayer'\n" +
                    " ┡--> wordToGuess to guessing players: '$wordToGuessToSendAsUnderscores'\n" +
                    " ┕--> Countdown Timer set to ${gamePhase.phaseDurationMillis / 1000} seconds\n")

            // Announce to players to guess now!
            broadcast(gson.toJson(Announcement(
                message = "Round started - Try to guess the word!",
                System.currentTimeMillis(),
                announcementType = Announcement.ANNOUNCEMENT_GENERAL_MESSAGE
            )))
        }

    }

    private fun roundEndedPhase(){
        // showWord, show_word // todo remove at end

        GlobalScope.launch {

            // Broadcast the unmasked wordToGuess to all players
            wordToGuess?.let { wordToGuess ->
                val gameState = GameState(
                    drawingPlayer?.playerName!!,
                    drawingPlayer?.clientId!!,
                    wordToGuess = wordToGuess
                )
                broadcast(gson.toJson(gameState))
            }


            // Reduce the drawing player's score by the penalty for not guessing the word
            if(winningPlayers.isEmpty()) {
                drawingPlayer?.let {player ->
                    player.score -= SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD
                }

                // Announce no players got it
                broadcast(gson.toJson(Announcement(
                            message = "No players guessed the word: $wordToGuess",
                            System.currentTimeMillis(),
                            announcementType = Announcement.ANNOUNCEMENT_NOBODY_GUESSED_CORRECTLY
                )))
            }

            // Score has possibly changed
            broadcastAllPlayersData()

            startGamePhaseCountdownTimerAndNotifyPlayers()
        }
    }

    //////////////////////////////
    /// GAME PHASE/STATE UTILS ///
    //////////////////////////////

    fun drawingPlayerSetWordToGuessAndStartRound(wordToGuess: String) {
        this.wordToGuess = wordToGuess
        gamePhase = GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS
    }

    private fun proceedToNextDrawingPlayer() {
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
    private fun isGuessCorrect(guessChatMessage: ChatMessage): Boolean {
        return guessChatMessage.containsWord(wordToGuess ?: return false)
                && !winningPlayers.containsPlayerClientId(guessChatMessage.fromClientId)
                && guessChatMessage.fromClientId != drawingPlayer?.clientId
                && gamePhase == GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS
    }

    // Returns true if all the players have guessed the word and the round is over
    private fun addWinningPlayer(player: Player): Boolean {
        winningPlayers = winningPlayers + player

        if(winningPlayers.size == players.size - 1) {
            gamePhase = GamePhaseUpdate.GamePhase.ROUND_ENDED
            return true
        }

        return false
    }

    // Returns true if the player has guessed the word
    suspend fun checkChatMessageContainsWordToGuessThenScoreAndNotifyPlayers(message: ChatMessage): Boolean {
        if(isGuessCorrect(message)) {
            val winningPlayer = getPlayerByClientId(message.fromClientId) ?: return false

            // Calc score for winning player
            val guessTimeMillis = System.currentTimeMillis() - gamePhaseStartTimeMillis
            val percentTimeLeft = 1f -
                    (guessTimeMillis / gamePhase.phaseDurationMillis.toFloat())
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
    private suspend fun sendWordToGuessToPlayer(player: Player) {

        val delay = gamePhase.phaseDurationMillis

        // Send the current phase and the drawing player
        val gamePhaseUpdate = GamePhaseUpdate(gamePhase, delay, drawingPlayer?.playerName)
        sendToOnePlayer(gson.toJson(gamePhaseUpdate), player)

        // If there is a word to guess, send it in the GameState
        wordToGuess?.let {curWordToGuess ->
            drawingPlayer?.let { drawingPlayer ->
                val gameState = GameState(
                    drawingPlayer.playerName,
                    drawingPlayer.clientId,
                    if(player.isDrawing || gamePhase == GamePhaseUpdate.GamePhase.ROUND_ENDED) {
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
    private suspend fun broadcastAllPlayersData() {  // broadcastPlayerStates // todo remove at end
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
    private fun startGamePhaseCountdownTimerAndNotifyPlayers() {

        // If its indeterminate duration, no need for the timer.
        if(gamePhase.phaseDurationMillis == GamePhaseUpdate.INDETERMINATE_DURATION_MILLIS) {
            return
        }

        timerJob?.cancel()
        timerJob = GlobalScope.launch {

            // Set and Start the game phase countdown timer
            gamePhaseStartTimeMillis = System.currentTimeMillis()
            val gamePhaseUpdate = GamePhaseUpdate(
                gamePhase,  // change to new phase of the game
                //gamePhaseDurationMillis,
                gamePhase.phaseDurationMillis,
                drawingPlayer?.playerName
            )

            // Notify the players that the phase has started
            broadcast(gson.toJson(gamePhaseUpdate))

            // Send players the current countdown time
            repeat( (gamePhase.phaseDurationMillis / TIMER_UPDATE_FREQUENCY_MILLIS).toInt() ) { count ->

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
            GamePhaseUpdate.GamePhase.WAITING_FOR_START ->
                GamePhaseUpdate.GamePhase.NEW_ROUND
            GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS -> {
                finishOffDrawing()  // make sure the drawing is finished
                GamePhaseUpdate.GamePhase.ROUND_ENDED
            }
            GamePhaseUpdate.GamePhase.ROUND_ENDED ->
                GamePhaseUpdate.GamePhase.NEW_ROUND
            GamePhaseUpdate.GamePhase.NEW_ROUND -> {
                wordToGuess = null  // reset the word to guess to force a new word to be picked
                GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS
            }
            else ->
                GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS
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

    // Send the serialized drawing data to one players
    private suspend fun sendCurRoundDrawDataToPlayer(player: Player) {
        if(gamePhase == GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS ||
            gamePhase == GamePhaseUpdate.GamePhase.ROUND_ENDED
        ) {
            sendToOnePlayer(gson.toJson(CurRoundDrawData(curRoundDrawData)), player)
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
        var joinMessage = "joined"

        // Check if this is a re-joining player
        val newPlayer = if(exitingPlayers.containsKey(clientId)) {
            val rejoiningPlayer = exitingPlayers[clientId]

            // If the player is rejoining, remove it from the exitingPlayers list
            rejoiningPlayer?.let { (rejoinedPlayer, indexOfPlayer) ->
                // Assign the new socket session to the player
                rejoinedPlayer.socket = socketSession

                // Set if the player is the drawingPlayer
                rejoinedPlayer.isDrawing = drawingPlayer?.clientId == clientId  // is this the same as the drawing player?

                // Set the position to add them back to (if possible)
                indexToAddPlayerAt = indexOfPlayer

                // Cancel and remove the "Remove Exiting Player After Delay" jobs
                removePlayerPermanentlyJobs[clientId]?.cancel()
                removePlayerPermanentlyJobs.remove(clientId)
                exitingPlayers.remove(clientId)

                // Send the drawing data to the re-joining player
                sendCurRoundDrawDataToPlayer(rejoinedPlayer)

                // Modify join message
                joinMessage = "re-joined"

                println("Player '${rejoinedPlayer.playerName}' has re-joined room '$roomName'")

                rejoinedPlayer
            } ?: throw IllegalStateException("Rejoining player was null")
        } else {
            // This is a new player
            println("Player '$playerName' has joined room '$roomName'")

            Player(playerName, socketSession, clientId)
        }

        newPlayer.startPinging()

        // If player is not currently in room, update the players list and put them back in the correct position
        if (players.containsPlayerClientId(clientId)) {
            println("Player '$playerName' is already in room '$roomName'")
        } else {
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
        }


        // Only player in the room?  -> keep waiting for more players
        if (players.size == ONE_PLAYER) {
            gamePhase = GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS
        }

        // Two players in the room?  -> ready to start the game
        if (players.size == TWO_PLAYERS && gamePhase == GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS) {
            gamePhase = GamePhaseUpdate.GamePhase.WAITING_FOR_START
            players = players.shuffled()
        }

        // Max number of players in the room?  -> start the game
        if (players.size == maxPlayers && gamePhase == GamePhaseUpdate.GamePhase.WAITING_FOR_START) {
            gamePhase = GamePhaseUpdate.GamePhase.NEW_ROUND
            players = players.shuffled()
        }

        sendWordToGuessToPlayer(newPlayer)

        // Send announcement to all players
        val announcement = Announcement( "Player '$playerName' has $joinMessage the room",
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
    // scheduleRemovePlayer(when isImmediateRemoval == false)
    //   -> Add player to exiting player List
    //   -> wait PLAYER_EXIT_REMOVE_DELAY_MILLIS ms
    //   -> finally permanently remove player from room & exiting list
    //   -> remove the player from the server
    fun scheduleRemovePlayer(removeClientId: ClientId, isImmediateRemoval: Boolean = false) {
        val removePlayer = getPlayerByClientId(removeClientId) ?: return
        val index = players.indexOf(removePlayer)

        println("scheduleRemovePlayer - Player '${removePlayer.playerName}' in room '$roomName' " +
                "scheduled for ${if(isImmediateRemoval) "immediate removal" else "removal after a delay"}")

        // Remove the player from the room & stop pinging
        players = players - removePlayer
        removePlayer.stopPinging()

        if (isImmediateRemoval) {
            removePlayerPermanently(removePlayer)
        } else {
            // Delayed removal of player (allows for reconnects within 60s)
            println("scheduleRemovePlayer - permanent removal scheduled in ${PLAYER_EXIT_REMOVE_PERMANENTLY_DELAY_MILLIS / 1000L} seconds " +
                    "for player: '${removePlayer.playerName}'")

            // Add player to exiting list
            exitingPlayers[removeClientId] = ExitingPlayer(removePlayer, index)

            println("scheduleRemovePlayer - list of exitingPlayers: ")
            exitingPlayers.forEach { (_, u) -> println(" ┡--> ${u.player.playerName}\n") }

            // Launch the "final" remove player job that will happen in PLAYER_EXIT_REMOVE_PERMANENTLY_DELAY_MILLIS from now.
            removePlayerPermanentlyJobs[removeClientId] = GlobalScope.launch {
                delay(PLAYER_EXIT_REMOVE_PERMANENTLY_DELAY_MILLIS)  // will be cancelled if the player re-joins

                val exitingPlayer = exitingPlayers[removeClientId]
                exitingPlayer ?: throw IllegalStateException("Exiting Player was null")

                // remove this player from list of exitingPlayers
                exitingPlayers -= exitingPlayer.player.clientId

                // Remove the player from the server
                serverDB.removePlayerFromServerDB(removeClientId)

                // remove this job
                removePlayerPermanentlyJobs -= removeClientId

                removePlayerPermanently(exitingPlayer.player)
            }
        }

        GlobalScope.launch {
            // Tell all players that a player has been removed
            val announcement = Announcement(
                message = "Player '${removePlayer.playerName}' has left the room.",
                timestamp = System.currentTimeMillis(),
                announcementType = ANNOUNCEMENT_PLAYER_EXITED_ROOM
            )

            broadcast(gson.toJson(announcement))
            broadcastAllPlayersData()
        }
    }

    private fun removePlayerPermanently(removePlayer: Player) {
        println("removePlayerPermanently - IMMEDIATELY Removing player ${removePlayer.playerName}")

        // Remove the player from the server
        serverDB.removePlayerFromServerDB(removePlayer.clientId)

        // Check if there is only one player
        if(players.size == 1) {
            println("removePlayerPermanently - Only one player, changing to phase WAITING_FOR_PLAYERS")

            gamePhase = GamePhaseUpdate.GamePhase.WAITING_FOR_PLAYERS
            timerJob?.cancel()

            GlobalScope.launch {
                broadcast(
                    gson.toJson(
                        Announcement(
                            message = "No other players in the room, now waiting for players to join...",
                            timestamp = System.currentTimeMillis(),
                            announcementType = ANNOUNCEMENT_GENERAL_MESSAGE
                        )
                    )
                )
            }

        }

        // Check if there are no players
        if(players.isEmpty()) {
            println("removePlayerPermanently - No players left in the room, killing the room")

            GlobalScope.launch {
                broadcast(
                    gson.toJson(
                        Announcement(
                            message = "No players left, The room has been killed.",
                            timestamp = System.currentTimeMillis(),
                            announcementType = ANNOUNCEMENT_GENERAL_MESSAGE
                        )
                    )
                )
            }

            killRoom()
        }
    }

    fun cancelRemovePlayerPermanently(clientId: ClientId) {
        removePlayerPermanentlyJobs[clientId]?.cancel()
        removePlayerPermanentlyJobs -= clientId
        exitingPlayers -= clientId

        println("cancelRemovePlayerPermanentlyJob: cancelled job for player: ${getPlayerByClientId(clientId)?.playerName}")
    }

    private fun killRoom() {
        removePlayerPermanentlyJobs.values.forEach { it.cancel() }
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
        println("Broadcasting to all except '${getPlayerByClientId(clientIdToExclude)?.playerName}':")

        players.forEach { player ->
            if(player.clientId != clientIdToExclude && player.socket.isActive) {
                println("   ┡--> sending to '${player.playerName}':\n" +
                        "   ┕----> $messageJson\n")
                player.socket.send(Frame.Text(messageJson))
            }
        }
    }

    private suspend fun sendToOnePlayer(messageJson: String, sendToPlayer: Player?) {
        sendToPlayer ?: run {
            println("sendToOnePlayer: sendToPlayer is null")
            return
        }

        sendToPlayer.let { player ->
            if(player.socket.isActive) {
                player.socket.send(Frame.Text(messageJson))
                println("Sending to one player: '${sendToPlayer.playerName}', message: $messageJson")
            } else {
                println("sendToOnePlayer - Player '${player.playerName}' is not active, cannot send message")
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

    private fun List<Player>.containsPlayerClientId(clientId: ClientId): Boolean {
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










































