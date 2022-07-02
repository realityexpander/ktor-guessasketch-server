package com.realityexpander.data.models.socket

data class WordToGuess(
    val wordToGuess: String,
    val roomName: String
): BaseSocketType(TYPE_WORD_TO_GUESS)
