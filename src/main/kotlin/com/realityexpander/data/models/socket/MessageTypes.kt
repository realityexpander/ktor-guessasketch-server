package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.getMessageTypeStringForMessageTypeClass

object SocketMessageType {

    // socket message data types
    const val TYPE_DRAW_DATA           = "TYPE_DRAW_DATA"
    const val TYPE_CHAT_MESSAGE        = "TYPE_CHAT_MESSAGE"
    const val TYPE_ANNOUNCEMENT        = "TYPE_ANNOUNCEMENT"
    const val TYPE_ADD_ROOM            = "TYPE_ADD_ROOM"
    const val TYPE_JOIN_ROOM_HANDSHAKE = "TYPE_JOIN_ROOM_HANDSHAKE"
    const val TYPE_GAME_ERROR          = "TYPE_GAME_ERROR"
    const val TYPE_GAME_PHASE_UPDATE   = "TYPE_GAME_PHASE_UPDATE"  // was TYPE_GAME_PHASE_CHANGE
    const val TYPE_WORDS_TO_PICK       = "TYPE_WORDS_TO_PICK" // contains NewWords
    const val TYPE_SET_WORD_TO_GUESS   = "TYPE_SET_WORD_TO_GUESS"
    const val TYPE_GAME_STATE          = "TYPE_GAME_STATE" // contains player & wordToGuess
    const val TYPE_PLAYERS_LIST        = "TYPE_PLAYERS_LIST" // contains list of player data (score, rank, etc)
    const val TYPE_PING                = "TYPE_PING"
    const val TYPE_DISCONNECT_REQUEST  = "TYPE_DISCONNECT_REQUEST"
    const val TYPE_DRAW_ACTION         = "TYPE_DRAW_ACTION"
    const val TYPE_CUR_ROUND_DRAW_DATA = "TYPE_CUR_ROUND_DRAW_DATA"

    val messageTypeMap: MutableMap<String, Class<out BaseMessageType>> = mutableMapOf()

    init {
        messageTypeMap[TYPE_DRAW_DATA]           = DrawData::class.java
        messageTypeMap[TYPE_CHAT_MESSAGE]        = ChatMessage::class.java
        messageTypeMap[TYPE_ANNOUNCEMENT]        = Announcement::class.java
        messageTypeMap[TYPE_ADD_ROOM]            = AddRoom::class.java
        messageTypeMap[TYPE_JOIN_ROOM_HANDSHAKE] = JoinRoomHandshake::class.java
        messageTypeMap[TYPE_GAME_PHASE_UPDATE]   = GamePhaseUpdate::class.java
        messageTypeMap[TYPE_WORDS_TO_PICK]       = WordsToPick::class.java
        messageTypeMap[TYPE_SET_WORD_TO_GUESS]   = SetWordToGuess::class.java
        messageTypeMap[TYPE_GAME_STATE]          = GameState::class.java
        messageTypeMap[TYPE_PLAYERS_LIST]        = PlayersList::class.java
        messageTypeMap[TYPE_PING]                = Ping::class.java
        messageTypeMap[TYPE_DISCONNECT_REQUEST]  = DisconnectRequest::class.java
        messageTypeMap[TYPE_DRAW_ACTION]         = DrawAction::class.java
        messageTypeMap[TYPE_CUR_ROUND_DRAW_DATA] = CurRoundDrawData::class.java
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

    // Won't compile - which is correct bc `Unknown::class.java` is not a subclass of BaseMessageType
    //println("class Unknown::class.java ->" +
    //        "${getMessageTypeStringForMessageTypeClass(Unknown::class.java)}"
    //)
}