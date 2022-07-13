package com.realityexpander.routes

import com.google.gson.JsonParser
import com.realityexpander.common.ClientId
import com.realityexpander.game.Player
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
                messageJson,  // payload encoded as a JSON string
                payload ->

            when(payload) {
                is JoinRoomHandshake -> {
                    val room = serverDB.roomsDB[payload.roomName]

                    if (room == null) {
                        val gameError = GameError(GameError.ERROR_TYPE_ROOM_NOT_FOUND, "Room not on found on server.")
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

                    println("Player '${newPlayer.playerName}' performed JoinRoomHandshake for room '${room.roomName}'")
                }
                is DrawData -> {
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    //println("DrawData from player=${room.getPlayerByClientId(clientId)?.playerName}\n" +
                    //        "   ⎿__ payload: $payload\n" +
                    //        "   ⎿__ messageJson: $messageJson"
                    //)

                    if( room.gamePhase == GamePhaseUpdate.GamePhase.ROUND_IN_PROGRESS ||
                        room.gamePhase == GamePhaseUpdate.GamePhase.ROUND_ENDED
                    ) {
                        room.broadcastToAllExceptOneClientId(messageJson, clientId)
                        room.addSerializedDrawDataJson(messageJson)
                    }
                    room.lastDrawData = payload // used to finishOffDrawing (prevents a bug)
                }
                is DrawAction -> {
                    val room = serverDB.getRoomForPlayerClientId(clientId) ?: return@standardWebSocket
                    room.broadcastToAllExceptOneClientId(messageJson, clientId)

                    println("DrawAction: $payload")

                    // Just need to save the JSON strings, no need to parse it again as it
                    //   will just be sent again to the client as JSON.
                    room.addSerializedDrawDataJson(messageJson)
                }
                is SetWordToGuess -> {  // Drawing player has set the word to guess
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    println("TYPE_SET_WORD_TO_GUESS - Drawing player has set the word to guess: \"${payload.wordToGuess}\", for room: '${room.roomName}'")

                    room.drawingPlayerSetWordToGuessAndStartRound(payload.wordToGuess)
                }
                is ChatMessage -> {
                    val room = serverDB.roomsDB[payload.roomName] ?: return@standardWebSocket

                    // Does this message text contain the correct guess for the word?
                    if(!room.checkChatMessageContainsWordToGuessThenScoreAndNotifyPlayers(payload)) {

                        // No winning guess, so just send the chat message as normal
                        room.broadcast(messageJson)
                    }
                }
                is Ping -> {
                    //println("Ping received: playerName=${payload.playerName}")
                    serverDB.playersDB[clientId]?.receivedPong()
                }
                is DisconnectRequest -> {
                    val room = serverDB.roomsDB[clientId] ?: return@standardWebSocket
                    println("Disconnect request received: playerName='${room.getPlayerByClientId(clientId)?.playerName}'")

                    serverDB.scheduleRemovePlayerFromRoom(clientId, isImmediateRemoval = true)
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
                    val messageJsonObject = JsonParser.parseString(messageJson).asJsonObject

                    // Extract the type of message
                    val typeStr = messageJsonObject["type"].asString
                        ?: throw IllegalArgumentException("Error: 'type' field not found in $messageJson")

                    // Convert "type" to a socket message class to be used for gson deserialization
                    val type = SocketMessageType.messageTypeMap[typeStr]
                        ?: let {
                            println("Error: Unknown socketType: $typeStr for $messageJson")
                            BaseMessageType::class.java // throw IllegalArgumentException("Unknown message type")
                        }

                    // convert messageJson string to the "type" from the frame and use this as the payload
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
                println("standardWebSocket - Player '${it.playerName}' disconnected")
                serverDB.scheduleRemovePlayerFromRoom(session.clientId)
            }

            //close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Closing")) // needed? remove soon todo
        }
    }
}





























