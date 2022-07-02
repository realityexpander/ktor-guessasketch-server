package com.realityexpander

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.ROOM_MAX_NUM_PLAYERS
import com.realityexpander.common.RoomName
import com.realityexpander.data.Player
import com.realityexpander.data.Room
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DrawingServer {

    val rooms = ConcurrentHashMap<RoomName, Room>()  // uses ConcurrentHashMap to avoid ConcurrentModificationException, ie: many threads can access the same room at the same time
    val players = ConcurrentHashMap<ClientId, Player>()

    fun addRoom(roomName: RoomName) {
        if(!rooms.containsKey(roomName))
            rooms[roomName] = Room(roomName, ROOM_MAX_NUM_PLAYERS)

        println("Rooms=$rooms, Players=$players")
    }

    fun addPlayerToRoom(newPlayer: Player, room: Room) {
        GlobalScope.launch {
            // Add the player to the server
            players[newPlayer.clientId] = newPlayer

            // Add player to room
            if (!room.containsPlayerClientId(newPlayer.clientId)) {
                room.addPlayer(newPlayer.clientId, newPlayer.playerName, newPlayer.socket)
            }

            println("Rooms=$rooms, Players=$players")
        }
    }

    fun removePlayerFromRoom(removeClientId: ClientId, disconnectImmediately: Boolean = false) {
        // Find the room for the player
        val roomOfPlayer = getRoomContainsClientId(removeClientId)

        if(disconnectImmediately) {
            println("Now Closing connection to ${players[removeClientId]}")
        }

        roomOfPlayer?.removePlayer(removeClientId, disconnectImmediately)
    }

    fun removePlayerFromServer(removeClientId: ClientId) {
        players -= removeClientId
    }

    fun removeRoomFromServer(removeRoomName: RoomName) {
        rooms -= removeRoomName
    }

    fun getRoomContainsClientId(clientId: ClientId): Room? {
        return rooms.values.firstOrNull { room ->
            room.players.find{ player ->
                player.clientId == clientId
            } != null
        }
    }
}