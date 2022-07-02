package com.realityexpander.routes

import com.google.gson.JsonParser
import com.realityexpander.data.models.socket.TypeConstants.TYPE_ANNOUNCEMENT
import com.realityexpander.data.models.socket.TypeConstants.TYPE_CHAT_MESSAGE
import com.realityexpander.data.models.socket.TypeConstants.TYPE_DRAW_DATA
import com.realityexpander.data.models.socket.TypeConstants.TYPE_JOIN_ROOM_HANDSHAKE
import com.realityexpander.data.Player
import com.realityexpander.data.Room
import com.realityexpander.data.models.socket.*
import com.realityexpander.data.models.socket.TypeConstants.TYPE_CHOSEN_WORD
import com.realityexpander.data.models.socket.TypeConstants.TYPE_GAME_PHASE_CHANGE
import com.realityexpander.data.models.socket.TypeConstants.TYPE_GAME_STATE
import com.realityexpander.gson
import com.realityexpander.serverDB
import com.realityexpander.session.DrawingSession
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.gameWebSocketRoute() {
    route("/ws/draw") {
        standardWebSocket { socket, clientId, messageJson, payload ->
            when(payload) {
                is JoinRoomHandshake -> {
                    val room = serverDB.rooms[payload.roomName]

                    if (room == null) {
                        val gameError = GameError(GameError.ERROR_TYPE_ROOM_NOT_FOUND)
                        // val gameError = GameError.ERROR_TYPE_ROOM_NOT_FOUND_MSG
                        socket.send(Frame.Text(gson.toJson(gameError))) // send error message to specific user who tried to join
                        return@standardWebSocket
                    }

                    // Create the new player
                    val newPlayer = Player(
                        payload.playerName,
                        socket,
                        payload.clientId
                    )

                    // Add player to room
                    serverDB.playerJoined(newPlayer)
                    if(!room.containsPlayerName(newPlayer.playerName)) {
                        room.addPlayer(newPlayer.clientId, newPlayer.playerName, newPlayer.socket)
                    }
                }
                is DrawData -> {
                    val room = serverDB.rooms[payload.roomName] ?: return@standardWebSocket

                    if(room.gamePhase == Room.GamePhase.ROUND_IN_PROGRESS) {
                        room.broadcastToAllExceptOneClientId(messageJson, clientId)
                    }
                }
                is WordToGuess -> {
                    val room = serverDB.rooms[payload.roomName] ?: return@standardWebSocket

                    room.setWordToGuessAndStartRound(payload.wordToGuess)
                }
                is ChatMessage -> {
                    val room = serverDB.rooms[payload.roomName] ?: return@standardWebSocket

                    // Does this message text contain the correct guess for the word?
                    if(!room.checkWordThenScoreAndNotifyPlayers(payload)) {
                        room.broadcast(messageJson)
                    }
                }
                else -> {

                }
            }
        }
    }
}

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,  // connection of 1 client to server
        clientId: String, // clientId is a unique identifier for this player (client)
        messageJson: String,  // json message is the text sent by the client (json)
        payload: BaseModel, // wrapper around message (unserialized from json `message`)
    ) -> Unit
) {
    webSocket {
        val session = call.sessions.get<DrawingSession>()

        if(session == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "No session found"))
            return@webSocket
        }

        try {
            incoming.consumeEach { frame ->
                if(frame is Frame.Text) {
                    val messageJson = frame.readText()  // gets raw JSON
                    val jsonObject = JsonParser.parseString(messageJson).asJsonObject

                    // Get the type of message
                    val type = when(jsonObject.get("type").asString) {
                            TYPE_CHAT_MESSAGE -> ChatMessage::class.java
                            TYPE_DRAW_DATA -> DrawData::class.java
                            TYPE_ANNOUNCEMENT -> Announcement::class.java
                            TYPE_JOIN_ROOM_HANDSHAKE -> JoinRoomHandshake::class.java
                            TYPE_GAME_PHASE_CHANGE -> GamePhaseChange::class.java
                            TYPE_CHOSEN_WORD -> WordToGuess::class.java
                            TYPE_GAME_STATE -> GameState::class.java
                            else -> BaseModel::class.java   //throw IllegalArgumentException("Unknown message type")
                        }

                    val payload = gson.fromJson(messageJson, type) // converts JSON to the type from the webSocket message
                    handleFrame(this, session.clientId, messageJson, payload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Error: $e"))
        } finally {
            // HANDLE DISCONNECTS //close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Closing"))
        }
    }
}





























