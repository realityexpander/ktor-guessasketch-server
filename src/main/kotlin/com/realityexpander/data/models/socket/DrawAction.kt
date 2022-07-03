package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_DRAW_ACTION

const val DRAW_MOTION_EVENT_ACTION_UP = 1
const val DRAW_MOTION_EVENT_ACTION_MOVE = 2

data class DrawAction(
    val action: String
): BaseMessageType(TYPE_DRAW_ACTION) {

    companion object {
        const val ACTION_UNDO = "ACTION_UNDO"
        const val ACTION_DRAW = "ACTION_DRAW"
        const val ACTION_ERASE = "ACTION_ERASE"
    }
}
