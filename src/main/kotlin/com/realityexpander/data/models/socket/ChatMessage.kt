package com.realityexpander.data.models.socket

import com.realityexpander.common.Constants.TYPE_CHAT_MESSAGE

data class ChatMessage(
    val from: String,  // sender clientId
    val roomName: String,
    val message: String,
    val timestamp: Long
) : BaseModel(TYPE_CHAT_MESSAGE) {

//    override fun toMap(): Map<String, Any> {
//        return mapOf(
//            "from" to from,
//            "roomName" to roomName,
//            "message" to message,
//            "timestamp" to timestamp
//        )
//    }
}
