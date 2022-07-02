package com.realityexpander.data.models.socket

data class GameState(
    val drawingPlayer: String,
    val wordToGuess: String
): BaseSocketType(TYPE_GAME_STATE)
