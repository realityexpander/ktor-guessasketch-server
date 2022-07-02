package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_SET_WORD_TO_GUESS

data class SetWordToGuess(
    val wordToGuess: String,
    val roomName: String
): BaseMessageType(TYPE_SET_WORD_TO_GUESS)
