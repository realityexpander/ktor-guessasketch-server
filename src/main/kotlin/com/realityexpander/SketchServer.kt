package com.realityexpander

import com.realityexpander.common.ClientId
import com.realityexpander.common.Constants.ROOM_MAX_NUM_PLAYERS
import com.realityexpander.common.RoomName
import com.realityexpander.data.models.socket.Announcement
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
            // Add the player to the server
            addPlayerToServerDB(newPlayer)

            // Add player to room
            if (!room.containsPlayerClientId(newPlayer.clientId)) {
                room.addPlayer(newPlayer.clientId, newPlayer.playerName, newPlayer.socket)
                newPlayer.startPinging()
            } else {
                // Player has disconnected then quickly reconnected,
                //   so just update the socket
                //   and remove the "player exit" job.
                val playerInRoom = room.getPlayerByClientId(newPlayer.clientId)

                playerInRoom?.let { rejoiningPlayer ->
                    room.cancelRemovePlayerJob(rejoiningPlayer.clientId)
                    rejoiningPlayer.socket = socket  // update the socket of the new connection
                    rejoiningPlayer.startPinging()

                    // Send the drawing data to the re-joining player
                    room.sendCurRoundDrawDataToPlayer(rejoiningPlayer)

                    // Send announcement to all players that player rejoined
                    val announcement = Announcement( "Player '${rejoiningPlayer.playerName}' has re-joined the room",
                        System.currentTimeMillis(),
                        Announcement.ANNOUNCEMENT_PLAYER_JOINED_ROOM
                    )
                    room.broadcast(gson.toJson(announcement))
                }
            }

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