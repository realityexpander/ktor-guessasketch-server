package com.realityexpander.data.models.socket

data class ChatMessage(
    val fromPlayerClientId: String,
    val fromPlayerName: String,
    val roomName: String,
    val message: String,
    val timestamp: Long
) : BaseSocketType(TYPE_CHAT_MESSAGE) {

//    override fun toMap(): Map<String, Any> {
//        return mapOf(
//            "from" to from,
//            "roomName" to roomName,
//            "message" to message,
//            "timestamp" to timestamp
//        )
//    }
}
