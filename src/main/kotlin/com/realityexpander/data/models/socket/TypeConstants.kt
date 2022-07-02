package com.realityexpander.data.models.socket


//object TypeConstants {
    // socket data types
    const val TYPE_CHAT_MESSAGE = "TYPE_CHAT_MESSAGE"
    const val TYPE_DRAW_DATA = "TYPE_DRAW_DATA"
    const val TYPE_ANNOUNCEMENT = "TYPE_ANNOUNCEMENT"
    const val TYPE_JOIN_ROOM_HANDSHAKE = "TYPE_JOIN_ROOM_HANDSHAKE"
    const val TYPE_GAME_ERROR = "TYPE_GAME_ERROR"
    const val TYPE_GAME_PHASE_CHANGE = "TYPE_GAME_PHASE_CHANGE"
    const val TYPE_WORD_TO_GUESS = "TYPE_WORD_TO_GUESS"
    const val TYPE_GAME_STATE = "TYPE_GAME_STATE" // contains player & wordToGuess
    const val TYPE_NEW_WORDS = "TYPE_NEW_WORDS" // contains new words
    const val TYPE_PLAYERS_LIST = "TYPE_PLAYERS_LIST" // contains list of player data (score, rank, etc)
//}

data class TypeHolder(val socketTypes: MutableMap<String, Class<out BaseSocketType>> = mutableMapOf() ) {

    init {
        socketTypes[TYPE_CHAT_MESSAGE]        = ChatMessage::class.java
        socketTypes[TYPE_DRAW_DATA]           = DrawData::class.java
        socketTypes[TYPE_ANNOUNCEMENT]        = Announcement::class.java
        socketTypes[TYPE_JOIN_ROOM_HANDSHAKE] = JoinRoomHandshake::class.java
        socketTypes[TYPE_GAME_PHASE_CHANGE]   = GamePhaseChange::class.java
        socketTypes[TYPE_WORD_TO_GUESS]       = WordToGuess::class.java
        socketTypes[TYPE_GAME_STATE]          = GameState::class.java
        socketTypes[TYPE_NEW_WORDS]           = NewWords::class.java
        socketTypes[TYPE_PLAYERS_LIST]        = PlayersList::class.java
    }
}