package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.TypeConstants.TYPE_GAME_STATE

data class GameState(
    val drawingPlayer: String,
    val wordToGuess: String
): BaseModel(TYPE_GAME_STATE)
