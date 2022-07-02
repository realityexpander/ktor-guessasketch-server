package com.realityexpander.data.models.socket

data class DrawData(
    val roomName: String,
    val color: Int,
    val thickness: Float,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val motionEvent: Int, // Move_touch_down, Move_touch_move, Move_touch_up
): BaseSocketType(TYPE_DRAW_DATA)
