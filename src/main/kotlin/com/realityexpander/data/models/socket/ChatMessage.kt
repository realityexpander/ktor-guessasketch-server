package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_CHAT_MESSAGE

data class ChatMessage(
    val fromClientId: String,
    val fromPlayerName: String,
    val roomName: String,
    val message: String,
    val timestamp: Long
) : BaseMessageType(TYPE_CHAT_MESSAGE) {

//    override fun toMap(): Map<String, Any> {
//        return mapOf(
//            "from" to from,
//            "roomName" to roomName,
//            "message" to message,
//            "timestamp" to timestamp
//        )
//    }
}
