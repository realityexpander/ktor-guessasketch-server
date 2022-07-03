package com.realityexpander.game

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.PING_FREQUENCY
import com.realityexpander.data.models.socket.Ping
import com.realityexpander.gson
import com.realityexpander.serverDB
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class Player(
    val playerName: String,
    var socket: WebSocketSession,
    val clientId: ClientId,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0,
) {
    private var pingJob: Job? = null

    private var pingTimeMillis = 0L  // send ping
    private var pongTimeMillis = 0L  // receive ping

    var isOnline = true

    fun startPinging() {
        pingJob?.cancel() // cancel previous job, if any

        pingJob = GlobalScope.launch {
            while(true) {
                sendPing()
                delay(PING_FREQUENCY)
            }
        }
    }

    private suspend fun sendPing() {
        pingTimeMillis = System.currentTimeMillis()
        socket.send(Frame.Text(gson.toJson(Ping())))

        delay(PING_FREQUENCY)  // wait for response

        if(pingTimeMillis - pongTimeMillis > PING_FREQUENCY) {
            isOnline = false
            serverDB.removePlayerFromRoom(clientId)
            pingJob?.cancel()
        }
    }

    fun receivedPong() {
        pongTimeMillis = System.currentTimeMillis()
        isOnline = true
    }

    fun stopPinging() {
        pingJob?.cancel()
    }
}
