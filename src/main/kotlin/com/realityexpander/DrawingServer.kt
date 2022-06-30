package com.realityexpander

import com.realityexpander.data.Player
import com.realityexpander.data.Room
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {

    val rooms = ConcurrentHashMap<String, Room>()  // uses ConcurrentHashMap to avoid ConcurrentModificationException, ie: many threads can access the same room at the same time
    val players = ConcurrentHashMap<String, Player>()

    fun playerJoined(player: Player) {
        players[player.clientId] = player
    }

    fun getRoomContainsClientId(clientId: String): Room? {
        return rooms.values.firstOrNull { room ->
            room.players.find{ player ->
                player.clientId == clientId
            } != null
        }
    }
}