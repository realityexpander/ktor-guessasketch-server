package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_WORD_TO_GUESS

data class WordToGuess(
    val wordToGuess: String,
    val roomName: String
): BaseMessageType(TYPE_WORD_TO_GUESS)
