package com.realityexpander

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.ROOM_MAX_NUM_PLAYERS
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

//    fun addRoom(roomName: RoomName) {  // remove - was only used for testing
//        if(!roomsDB.containsKey(roomName))
//            roomsDB[roomName] = Room(roomName, ROOM_MAX_NUM_PLAYERS)
//
//        println("roomsDB=$roomsDB, playersDB=$playersDB")
//    }

    fun addPlayerToRoom(newPlayer: Player, room: Room, socket: DefaultWebSocketServerSession) {
        GlobalScope.launch {
            // Add the player to the server
            addPlayerToServerDB(newPlayer)

            // Add player to room
            if (!room.containsPlayerClientId(newPlayer.clientId)) {
                // XXX IGNORE XXXXX Room does not have this player yet, add it XXXXXX  ??? todo remove at end
                // NOTE: player is first added by the JoinRoomHandshake socket message, not this method! ??? todo remove at end
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

    // Player exiting (either on purpose or a ping timeout) // playerLeft // todo remove at end
    fun removePlayerFromRoom(removeClientId: ClientId, isImmediateDisconnect: Boolean = false) {
        // Find the room for the player
        val roomOfPlayer = getRoomForPlayerClientId(removeClientId)

        if(isImmediateDisconnect || playersDB[removeClientId]?.isOnline == false) {
            println("Now Closing connection to ${playersDB[removeClientId]}")

            playersDB[removeClientId]?.stopPinging()
            roomOfPlayer?.removePlayer(removeClientId, isImmediateDisconnect)
            removePlayerFromServerDB(removeClientId)
        }

        roomOfPlayer?.removePlayer(removeClientId, isImmediateDisconnect)
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