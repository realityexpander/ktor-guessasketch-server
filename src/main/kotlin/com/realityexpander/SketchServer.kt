package com.realityexpander

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.ROOM_MAX_NUM_PLAYERS
import com.realityexpander.common.RoomName
import com.realityexpander.data.Player
import com.realityexpander.data.Room
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SketchServer {  // DrawingServer todo remove at end

    val roomsDB = ConcurrentHashMap<RoomName, Room>()  // uses ConcurrentHashMap to avoid ConcurrentModificationException, ie: many threads can access the same room at the same time
    val playersDB = ConcurrentHashMap<ClientId, Player>()

    fun addRoom(roomName: RoomName) {
        if(!roomsDB.containsKey(roomName))
            roomsDB[roomName] = Room(roomName, ROOM_MAX_NUM_PLAYERS)

        println("roomsDB=$roomsDB, playersDB=$playersDB")
    }

    fun addPlayerToRoom(newPlayer: Player, room: Room, socket: DefaultWebSocketServerSession) {
        GlobalScope.launch {
            // Add the player to the server
            playersDB[newPlayer.clientId] = newPlayer

            // Add player to room
            if (!room.containsPlayerClientId(newPlayer.clientId)) {
                // Room does not have this player yet, add it
                room.addPlayer(newPlayer.clientId, newPlayer.playerName, newPlayer.socket)
            } else {
                // player has quickly disconnected then reconnected, so just update the socket
                val playerInRoom = room.getPlayerByClientId(newPlayer.clientId)
                playerInRoom?.socket = socket  // update the socket
                playerInRoom?.startPinging()
            }

            newPlayer.startPinging()

            println("roomsDB=$roomsDB, playersDB=$playersDB")
        }
    }

    // playerLeft // todo remove at end
    fun removePlayerFromRoom(removeClientId: ClientId, isImmediateDisconnect: Boolean = false) {
        // Find the room for the player
        val roomOfPlayer = getRoomContainsClientId(removeClientId)

        if(isImmediateDisconnect || playersDB[removeClientId]?.isOnline == false) {
            println("Now Closing connection to ${playersDB[removeClientId]}")

            playersDB[removeClientId]?.stopPinging()
            roomOfPlayer?.removePlayer(removeClientId, isImmediateDisconnect)
            removePlayerFromServer(removeClientId)
        }

        roomOfPlayer?.removePlayer(removeClientId, isImmediateDisconnect)
    }

    fun removePlayerFromServer(removeClientId: ClientId) {
        playersDB -= removeClientId
    }

    fun removeRoomFromServer(removeRoomName: RoomName) {
        roomsDB -= removeRoomName
    }

    fun getRoomContainsClientId(clientId: ClientId): Room? {
        return roomsDB.values.firstOrNull { room ->
            room.players.find{ player ->
                player.clientId == clientId
            } != null
        }
    }
}