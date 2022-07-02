package com.realityexpander.data

import com.realityexpander.common.ClientId
import io.ktor.http.cio.websocket.*

data class Player(
    val playerName: String,
    var socket: WebSocketSession,
    val clientId: ClientId,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0,
)
