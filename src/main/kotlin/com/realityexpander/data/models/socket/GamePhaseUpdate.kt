package com.realityexpander.data.models.socket

import com.realityexpander.game.Room
import com.realityexpander.data.models.socket.SocketMessageType.TYPE_GAME_PHASE_UPDATE

data class GamePhaseUpdate(
    var gamePhase: Room.GamePhase? = null,  // if NOT null, causes a phase change. If null, it's not serialized.
    var countdownTimerMillis: Long = 0L,
    val drawingPlayerName: String? = null,  // todo add drawingPlayerClientId
): BaseMessageType(TYPE_GAME_PHASE_UPDATE)
