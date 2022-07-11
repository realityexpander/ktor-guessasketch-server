package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_GAME_PHASE_UPDATE

data class GamePhaseUpdate(
    var gamePhase: GamePhase? = null,  // if NOT null, causes a phase change. If null, it's not serialized.
    var countdownTimerMillis: Long = 0L,
    val drawingPlayerName: String? = null,  // todo add drawingPlayerClientId
): BaseMessageType(TYPE_GAME_PHASE_UPDATE) {

    enum class GamePhase(val phaseDurationMillis: Long = INDETERMINATE_DURATION_MILLIS) {
        INITIAL_STATE (INDETERMINATE_DURATION_MILLIS),
        WAITING_FOR_PLAYERS (INDETERMINATE_DURATION_MILLIS),
        WAITING_FOR_START (10000L),
        NEW_ROUND (20000L),
        ROUND_IN_PROGRESS (10000L),
        ROUND_ENDED (10000L),
    }

    companion object {
        const val INDETERMINATE_DURATION_MILLIS = -1L
    }

}
