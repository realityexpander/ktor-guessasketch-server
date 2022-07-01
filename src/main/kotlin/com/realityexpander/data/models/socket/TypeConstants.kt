package com.realityexpander.data.models.socket

object TypeConstants {
    // socket data types
    const val TYPE_CHAT_MESSAGE = "TYPE_CHAT_MESSAGE"
    const val TYPE_DRAW_DATA = "TYPE_DRAW_DATA"
    const val TYPE_ANNOUNCEMENT = "TYPE_ANNOUNCEMENT"
    const val TYPE_JOIN_ROOM_HANDSHAKE = "TYPE_JOIN_ROOM_HANDSHAKE"
    const val TYPE_GAME_ERROR = "TYPE_GAME_ERROR"
    const val TYPE_GAME_PHASE_CHANGE = "TYPE_GAME_PHASE_CHANGE"
    const val TYPE_CHOSEN_WORD = "TYPE_CHOSE_WORD"
    const val TYPE_GAME_STATE = "TYPE_GAME_STATE" // contains player & wordToGuess
    const val TYPE_NEW_WORDS = "TYPE_NEW_WORDS" // contains new words

}