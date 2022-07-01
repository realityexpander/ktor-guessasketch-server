package com.realityexpander.routes

import com.google.gson.JsonParser
import com.realityexpander.DrawingServer
import com.realityexpander.common.Constants.TYPE_CHAT_MESSAGE
import com.realityexpander.data.models.BaseModel
import com.realityexpander.data.models.ChatMessage
import com.realityexpander.gson
import com.realityexpander.session.DrawingSession
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.consumeEach

fun Route.standardWebSocket(
    handleFrame: suspend (
        socket: DefaultWebSocketServerSession,  // connection of 1 client to server
        clientId: String, // clientId is a unique identifier for this player (client)
        message: String,  // json message is the text sent by the client (json)
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
                    val message = frame.readText()  // gets raw JSON
                    val jsonObject = JsonParser.parseString(message).asJsonObject // parses JSON

                    val type = when(jsonObject.get("type").asString) { // gets type of message
                            TYPE_CHAT_MESSAGE -> ChatMessage::class.java
                            else -> throw IllegalArgumentException("Unknown message type")
                        }

                    val payload = gson.fromJson(message, type) // converts JSON to the type from the webSocket message
                    handleFrame(this, session.clientId, message, payload)
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