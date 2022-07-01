package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.Constants.TYPE_GAME_ERROR

data class GameError(
    val errorType: Int
): BaseModel(TYPE_GAME_ERROR) {

    companion object {
        const val ERROR_TYPE_ROOM_NOT_FOUND = 1
    }
}