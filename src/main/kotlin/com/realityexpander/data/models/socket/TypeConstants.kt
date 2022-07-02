package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.getMessageTypeStringForMessageTypeClass

object SocketMessageType {

    // socket message data types
    const val TYPE_CHAT_MESSAGE        = "TYPE_CHAT_MESSAGE"
    const val TYPE_DRAW_DATA           = "TYPE_DRAW_DATA"
    const val TYPE_ANNOUNCEMENT        = "TYPE_ANNOUNCEMENT"
    const val TYPE_JOIN_ROOM_HANDSHAKE = "TYPE_JOIN_ROOM_HANDSHAKE"
    const val TYPE_GAME_ERROR          = "TYPE_GAME_ERROR"
    const val TYPE_GAME_PHASE_CHANGE   = "TYPE_GAME_PHASE_CHANGE"
    const val TYPE_WORD_TO_GUESS       = "TYPE_WORD_TO_GUESS"
    const val TYPE_GAME_STATE          = "TYPE_GAME_STATE" // contains player & wordToGuess
    const val TYPE_NEW_WORDS           = "TYPE_NEW_WORDS" // contains new words
    const val TYPE_PLAYERS_LIST        = "TYPE_PLAYERS_LIST" // contains list of player data (score, rank, etc)

    val messageTypeMap: MutableMap<String, Class<out BaseMessageType>> = mutableMapOf()

    init {
        messageTypeMap[TYPE_CHAT_MESSAGE]        = ChatMessage::class.java
        messageTypeMap[TYPE_DRAW_DATA]           = DrawData::class.java
        messageTypeMap[TYPE_ANNOUNCEMENT]        = Announcement::class.java
        messageTypeMap[TYPE_JOIN_ROOM_HANDSHAKE] = JoinRoomHandshake::class.java
        messageTypeMap[TYPE_GAME_PHASE_CHANGE]   = GamePhaseChange::class.java
        messageTypeMap[TYPE_WORD_TO_GUESS]       = WordToGuess::class.java
        messageTypeMap[TYPE_GAME_STATE]          = GameState::class.java
        messageTypeMap[TYPE_NEW_WORDS]           = NewWords::class.java
        messageTypeMap[TYPE_PLAYERS_LIST]        = PlayersList::class.java
    }

    fun getMessageTypeStringForMessageTypeClass(messageTypeClass: Class<out BaseMessageType>): String? {
        return messageTypeMap.entries.firstOrNull {
            it.value == messageTypeClass
        }?.key
    }
}


///////////////// TEST ///////////////////

fun main() {
    println("class ChatMessage::class.java -> " +
            "${getMessageTypeStringForMessageTypeClass(ChatMessage::class.java)}")

    // Won't compile - which is correct bc not a subclass of BaseMessageType
    //println("class Unknown::class.java ->" +
    //        "${getMessageTypeStringForMessageTypeClass(Unknown::class.java)}"
    //)
}