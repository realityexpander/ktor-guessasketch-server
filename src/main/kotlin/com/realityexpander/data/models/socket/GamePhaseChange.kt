package com.realityexpander.data.models.socket

import com.realityexpander.data.Room
import com.realityexpander.data.models.socket.TypeConstants.TYPE_GAME_PHASE_CHANGE

data class GamePhaseChange(
    var gamePhase: Room.GamePhase?,
    var countdownTimerMillis: Long = 0L,
    val drawingPlayerName: String? = null,
): BaseModel(TYPE_GAME_PHASE_CHANGE)