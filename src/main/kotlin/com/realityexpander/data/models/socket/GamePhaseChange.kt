package com.realityexpander.data.models.socket

import com.realityexpander.data.Room

data class GamePhaseChange(
    var gamePhase: Room.GamePhase?,
    var countdownTimerMillis: Long = 0L,
    val drawingPlayerName: String? = null,
): BaseSocketType(TYPE_GAME_PHASE_CHANGE)
