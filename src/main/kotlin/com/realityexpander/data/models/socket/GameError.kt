package com.realityexpander.data.models.socket

data class GameError(
    val errorType: Int,
    val errorMessage: String? = null
): BaseSocketType(TYPE_GAME_ERROR) {

    companion object {
        const val ERROR_TYPE_ROOM_NOT_FOUND = 1
        val ERROR_TYPE_ROOM_NOT_FOUND_MSG = GameError(1,"Room not found")
    }
}