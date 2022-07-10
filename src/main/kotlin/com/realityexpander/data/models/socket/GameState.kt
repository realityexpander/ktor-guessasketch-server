package com.realityexpander.data.models.socket

import com.realityexpander.common.ClientId
import com.realityexpander.data.models.socket.SocketMessageType.TYPE_GAME_STATE

data class GameState(
    val drawingPlayerName: String,
    val drawingPlayerClientId: ClientId,
    val wordToGuess: String
): BaseMessageType(TYPE_GAME_STATE)
