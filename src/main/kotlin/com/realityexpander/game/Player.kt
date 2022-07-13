package com.realityexpander.game

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.PING_TIMEOUT_LIMIT_MILLIS
import com.realityexpander.data.models.socket.Ping
import com.realityexpander.gson
import com.realityexpander.serverDB
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

data class Player(
    val playerName: String,
    var socket: WebSocketSession,
    val clientId: ClientId,
    var isDrawing: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0,
) {
    private var pingJob: Job? = null

    data class OnlinePingPongTime(
        var pingTimeMillis: Long = 0L,
        var pongTimeMillis: Long = 0L,
        var isOnline: Boolean = true,
    )

    companion object {
        // note: only atomic references held in companion objects (or regular objects?) can be read AND modified by multiple threads
        val atomicMapOfClientToPingTime = AtomicReference<MutableMap<ClientId, OnlinePingPongTime>>(mutableMapOf())
    }

    fun startPinging() {
        // println("startPinging for $playerName")

        stopPinging() // cancel previous job, if any
        pingJob = GlobalScope.launch {
            while (true) {
                sendPing()

                delay(PING_TIMEOUT_LIMIT_MILLIS)  // a little breathing room between pings
            }
        }
    }

    private suspend fun sendPing() {
        // Set the ping start time for this client
        val pingTimeMillis = System.currentTimeMillis()
        setPingTimeMillis(pingTimeMillis)

        socket.send(Frame.Text(gson.toJson(Ping())))
        println("Player '$playerName' SENT ping\n" +
                " ┡--- pingTime=${pingTimeMillis}")

        delay(PING_TIMEOUT_LIMIT_MILLIS)  // wait for response

        // Get the pong "turnaround" time for this client
        val pongTimeMillis = getPongTimeMillis()

        println(
            "Player '$playerName' TURNAROUND for ping->pong\n" +
                    " ┡--- pong-ping turnaround : ${pongTimeMillis - pingTimeMillis}ms\n"
//                  +
//                    " ┡--- last ping sent at    : ${pingTimeMillis},\n" +
//                    " ┡--- last pong received at: ${pongTimeMillis}\n" +
//                    " ┡--- last ping sent       : ${System.currentTimeMillis() - pingTimeMillis}ms ago\n" +
//                    " ┡--- last pong received   : ${System.currentTimeMillis() - pongTimeMillis}ms ago\n" +
//                    " ┡--- Online?              : ${isOnline()}\n" +
//                    " ┡--- clientId=${this@Player.clientId}"
        )

        // Check for timeout
        if (abs(pongTimeMillis - pingTimeMillis) > PING_TIMEOUT_LIMIT_MILLIS) {
            if (isOnline()) {
                println("Player '$playerName' is OFFLINE - no ping received in over ${PING_TIMEOUT_LIMIT_MILLIS}ms\n")

                setIsOnline(false)

                serverDB.scheduleRemovePlayerFromRoom(clientId)
                stopPinging()  // not needed? do we want to keep pinging until the player is back online or removed permanently
            }
        }
    }

    fun receivedPong() {
        val pingTimeMillis = getPingTimeMillis()

        // set the pong time for this client
        val pongTimeMillis = System.currentTimeMillis()
        setPongTimeMillis(pongTimeMillis)

        //    println(
        //        "Player '$playerName' RECEIVED pong \n" +
        //                " ┡--- pingTime=${pingTimeMillis} \n" +
        //                " ┡--- pongTime=${pongTimeMillis} \n" +
        //                " ┡--- turnaround time: ${pongTimeMillis - pingTimeMillis}ms\n" +
        //                " ┡--- clientId=${this@Player.clientId})"
        //    )

        // If player responds to the last ping, they are still online.
        // This happens if the socket connection was not broken and there was too much network traffic and pong
        //   was received more than PING_TIMEOUT_LIMIT_MILLIS milliseconds ago, ie: very late.
        // This is unlikely to happen, but it is possible.
        if(!isOnline()) {
            println("Player '$playerName' is back online")

            // Add the player back to the room
            val room = serverDB.getRoomForPlayerClientId(clientId)
            GlobalScope.launch {
                room?.addPlayer(clientId, playerName, socket)
            }
        }

        setIsOnline(true)
    }

    fun stopPinging() {
        pingJob?.cancel()
    }


    //// UTILITIES ////

    fun isOnline(): Boolean {
        return atomicMapOfClientToPingTime.get().getValue(clientId).isOnline
    }

    private fun setIsOnline(isOnline: Boolean) {
        atomicMapOfClientToPingTime.getAndUpdate { map ->
            map[clientId] = map[clientId]?.copy(isOnline = isOnline)
                ?: OnlinePingPongTime(isOnline = isOnline)
            map
        }
    }

    fun getOnlinePingPongTime(): OnlinePingPongTime {
        return atomicMapOfClientToPingTime.get().getValue(clientId)
    }

    private fun getPingTimeMillis(): Long {
        return atomicMapOfClientToPingTime.get().getValue(clientId).pingTimeMillis
    }

    private fun getPongTimeMillis(): Long {
        return atomicMapOfClientToPingTime.get().getValue(clientId).pongTimeMillis
    }

    private fun setPingTimeMillis(pingTimeMillis: Long) {
        atomicMapOfClientToPingTime.getAndUpdate { map ->
            map[clientId] = map[clientId]?.copy(pingTimeMillis = pingTimeMillis)
                ?: OnlinePingPongTime(pingTimeMillis = pingTimeMillis)
            map
        }
    }

    private fun setPongTimeMillis(pongTimeMillis: Long) {
        atomicMapOfClientToPingTime.getAndUpdate { map ->
            map[clientId] = map[clientId]?.copy(pongTimeMillis = pongTimeMillis)
                ?: OnlinePingPongTime(pongTimeMillis = pongTimeMillis)
            map
        }
    }
}
