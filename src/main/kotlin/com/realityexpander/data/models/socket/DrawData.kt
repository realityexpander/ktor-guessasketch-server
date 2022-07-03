package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_DRAW_DATA



data class DrawData(
    val roomName: String,
    val color: Int,
    val thickness: Float,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val motionEvent: Int, // DRAW_MOTION_EVENT_ACTION_UP or DRAW_MOTION_EVENT_ACTION_MOVE
): BaseMessageType(TYPE_DRAW_DATA)
