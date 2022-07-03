package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_DRAW_ACTION

const val DRAW_ACTION_MOTION_TYPE_DRAW = 1
const val DRAW_ACTION_MOTION_TYPE_ERASE = 2
const val DRAW_ACTION_MOTION_TYPE_CLEAR = 3
const val DRAW_ACTION_MOTION_TYPE_UNDO = 4
const val DRAW_ACTION_MOTION_TYPE_REDO = 5


data class DrawAction(
    val action: String
): BaseMessageType(TYPE_DRAW_ACTION) {

    companion object {
        const val ACTION_UNDO = "ACTION_UNDO"
        const val ACTION_DRAW = "ACTION_DRAW"
        const val ACTION_ERASE = "ACTION_ERASE"
    }
}
