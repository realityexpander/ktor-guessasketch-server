package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_CUR_ROUND_DRAW_DATA

data class CurRoundDrawData(
    val data: List<String>
): BaseMessageType(TYPE_CUR_ROUND_DRAW_DATA)
