package com.realityexpander.routes

import com.google.gson.JsonParser
import com.realityexpander.common.ClientId
import com.realityexpander.game.Player
import com.realityexpander.game.Room
import com.realityexpander.data.models.socket.*
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
        standardWebSocket {
                socket,
                clientId,
                messageJson,
                payload ->

            when(payload) {
                is AddRoom -> {
                    serverDB.addRoom(payload.roomName)
                }
                is JoinRoomHandshake -> {
                    val room = serverDB.roomsDB[payload.roomName]

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
                    serverDB.addPlayerToRoom(newPlayer, room, socket)
                }
                is DrawData -> {
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    if(room.gamePhase == Room.GamePhase.ROUND_IN_PROGRESS) {
                        room.broadcastToAllExceptOneClientId(messageJson, clientId)
                        room.addSerializedDrawActionJson(messageJson)
                    }
                    room.lastDrawData = payload // used to finishOffDrawing
                }
                is DrawAction -> {
                    val room = serverDB.getRoomForPlayerClientId(clientId) ?: return@standardWebSocket
                    room.broadcastToAllExceptOneClientId(messageJson, clientId)

                    // Just need to save the json strings, no need to parse it again as it
                    //   will just be sent again to the client.
                    room.addSerializedDrawActionJson(messageJson)
                }
                is SetWordToGuess -> {
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    room.setWordToGuessAndStartRound(payload.wordToGuess)
                }
                is ChatMessage -> {
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    // Does this message text contain the correct guess for the word?
                    if(!room.checkWordThenScoreAndNotifyPlayers(payload)) {
                        room.broadcast(messageJson)
                    }
                }
                is Ping -> {
                    serverDB.playersDB[clientId]?.receivedPong()
                }
                is DisconnectRequest -> {
                    val room = serverDB.roomsDB[clientId] ?: return@standardWebSocket

                    serverDB.removePlayerFromRoom(clientId, isImmediateDisconnect = true)
                }
                else -> {
                    println("Unknown socketType for $payload")
                }
            }
        }
    }
}

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,  // connection of 1 client to server
        clientId: ClientId, // clientId is a unique identifier for this player (client)
        messageJson: String,  // json message is the text sent by the client (json)
        payload: BaseMessageType, // wrapper around message (unserialized from json `message`)
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
                    // get the raw JSON message
                    val messageJson = frame.readText()

                    // Convert the messageJson to a JSON Object for easier handling
                    val jsonObject = JsonParser.parseString(messageJson).asJsonObject

                    // Extract the type of message
                    val typeStr = jsonObject["type"].asString
                        ?: throw IllegalArgumentException("Error: 'type' field not found in $messageJson")

                    // Convert "type" to a socket message class to be used for gson deserialization
                    val type = SocketMessageType.messageTypeMap[typeStr]
                        ?: let {
                            println("Error: Unknown socketType: $typeStr for $messageJson")
                            BaseMessageType::class.java // throw IllegalArgumentException("Unknown message type")
                        }

                    // convert payload JSON string to the type from the webSocket message
                    val payload = gson.fromJson(messageJson, type)
                    handleFrame(this, session.clientId, messageJson, payload)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Error: $e"))
        } finally {
            // HANDLE DISCONNECTS

            // Find the player that disconnected
            val player = serverDB.getRoomForPlayerClientId(session.clientId)
                ?.players
                ?.find { player ->
                    player.clientId == session.clientId
                }
            // Remove the player
            player?.let {
                serverDB.removePlayerFromRoom(session.clientId)
            }

            //close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Closing")) // remove soon todo
        }
    }
}





























