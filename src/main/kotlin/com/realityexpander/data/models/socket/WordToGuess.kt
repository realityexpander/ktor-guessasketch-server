package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.TypeConstants.TYPE_CHOSEN_WORD

data class WordToGuess(
    val wordToGuess: String,
    val roomName: String
): BaseModel(TYPE_CHOSEN_WORD)
