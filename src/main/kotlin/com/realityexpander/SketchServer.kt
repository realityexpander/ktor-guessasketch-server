package com.realityexpander

import com.realityexpander.common.ClientId
import com.realityexpander.common.RoomName
import com.realityexpander.game.Player
import com.realityexpander.game.Room
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SketchServer {  // DrawingServer todo remove at end

    val roomsDB = ConcurrentHashMap<RoomName, Room>()  // uses ConcurrentHashMap to avoid ConcurrentModificationException, ie: many threads can access the same room at the same time
    val playersDB = ConcurrentHashMap<ClientId, Player>()


    fun addPlayerToRoom(newPlayer: Player, room: Room, socket: DefaultWebSocketServerSession) {
        GlobalScope.launch {
            // Add the player to the server DB
            addPlayerToServerDB(newPlayer)

            // Add player to room (will check for re-joining))
            room.addPlayer(newPlayer.clientId, newPlayer.playerName, newPlayer.socket)
            println("addPlayerToRoom - roomsDB=$roomsDB, playersDB=$playersDB")
        }
    }

    // Player exiting (either on purpose or a ping timeout) // playerLeft // todo remove at end
    fun scheduleRemovePlayerFromRoom(removeClientId: ClientId, isImmediateRemoval: Boolean = false) {
        // Find the room for the player
        val roomOfPlayer = getRoomForPlayerClientId(removeClientId)

        //if(isImmediateDisconnect || playersDB[removeClientId]?.isOnline?.get() == false) {  // todo remove soon
        if(isImmediateRemoval || playersDB[removeClientId]?.isOnline() == false) {
            println("Now Closing connection to ${playersDB[removeClientId]}")

            playersDB[removeClientId]?.stopPinging()
            roomOfPlayer?.removePlayer(removeClientId, isImmediateRemoval)
            removePlayerFromServerDB(removeClientId)
        }

        roomOfPlayer?.removePlayer(removeClientId, isImmediateRemoval)
    }

    fun addRoomToServer(roomName: RoomName, maxPlayers: Int) {
        val room = Room(roomName, maxPlayers)
        serverDB.roomsDB[roomName] = room
    }

    fun removeRoomFromServerDB(removeRoomName: RoomName) {
        roomsDB -= removeRoomName
    }

    private fun addPlayerToServerDB(newPlayer: Player) {
        playersDB[newPlayer.clientId] = newPlayer
    }

    fun removePlayerFromServerDB(removeClientId: ClientId) {
        playersDB -= removeClientId
    }

    fun getRoomForPlayerClientId(clientId: ClientId): Room? {
        return roomsDB.values.firstOrNull { room ->
            room.players.find{ player ->
                player.clientId == clientId
            } != null
        }
    }

}