package com.realityexpander.routes

import com.google.gson.JsonParser
import com.realityexpander.common.Constants.TYPE_ANNOUNCEMENT
import com.realityexpander.common.Constants.TYPE_CHAT_MESSAGE
import com.realityexpander.common.Constants.TYPE_DRAW_DATA
import com.realityexpander.data.Room
import com.realityexpander.data.models.socket.Announcement
import com.realityexpander.data.models.socket.BaseModel
import com.realityexpander.data.models.socket.ChatMessage
import com.realityexpander.data.models.socket.DrawData
import com.realityexpander.gson
import com.realityexpander.server
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
                is DrawData -> {
                    val room = server.rooms[payload.roomName] ?: return@standardWebSocket

                    if(room.phase == Room.GamePhase.ROUND_IN_PROGRESS) {
                        room.broadcastToAllExcept(messageJson, clientId)
                    }
                }
                is ChatMessage -> {

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





























