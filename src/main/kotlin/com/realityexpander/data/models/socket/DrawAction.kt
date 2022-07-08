package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_DRAW_ACTION


data class DrawAction(
    val action: String
): BaseMessageType(TYPE_DRAW_ACTION) {

    companion object {
        const val DRAW_ACTION_UNDO = "ACTION_UNDO"
        const val DRAW_ACTION_DRAW = "ACTION_DRAW"
        const val DRAW_ACTION_ERASE = "ACTION_ERASE"
    }
}
