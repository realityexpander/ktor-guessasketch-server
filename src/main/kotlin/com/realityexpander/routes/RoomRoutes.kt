package com.realityexpander.routes

import com.realityexpander.common.Constants.ROOM_MAX_NUM_PLAYERS
import com.realityexpander.data.models.response.BasicApiResponse
import com.realityexpander.data.models.response.BasicApiResponseWithData
import com.realityexpander.data.models.request.CreateRoomRequest
import com.realityexpander.data.models.response.RoomDTO
import com.realityexpander.serverDB
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.createRoomRoute() {
    route("/api/createRoom") {
        post {
            val roomRequest = call.receiveOrNull<CreateRoomRequest>()

            if (roomRequest == null) {
                call.respond(HttpStatusCode.BadRequest)

                return@post
            }

            // Check if room exists
            if(serverDB.roomsDB[roomRequest.roomName] != null) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Room already exists" )
                )

                return@post
            }

            // Check min num of players
            if(roomRequest.maxPlayers < 2) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Room must have at least 2 players" )
                )

                return@post
            }

            // Check max num of players
            if(roomRequest.maxPlayers > ROOM_MAX_NUM_PLAYERS) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Room must have at most $ROOM_MAX_NUM_PLAYERS players" )
                )

                return@post
            }

            // Create the room
            serverDB.addRoomToServerDB(roomRequest.roomName, roomRequest.maxPlayers )

            println("Created room: ${roomRequest.roomName}")

            call.respond(HttpStatusCode.OK,
                BasicApiResponse(
                    true,
                    "Room created" )
            )

        }
    }

}

fun Route.getRoomsRoute() {
    route("/api/getRooms") {
        get {
            val searchQuery = call.parameters["searchQuery"]

            if(searchQuery == null) {
                call.respond(HttpStatusCode.BadRequest,
                    BasicApiResponse(
                        false,
                        "No search query provided" )
                )

                return@get
            }

            // For a blank query, return all rooms
            if(searchQuery == "") {
                val rooms = serverDB.roomsDB
                    .values.toList()
                    .map { room ->
                        RoomDTO(room.roomName, room.maxPlayers, room.players.size)
                    }

                call.respond(HttpStatusCode.OK,
                    BasicApiResponseWithData(
                        true,
                        "Rooms retrieved",
                        rooms
                    )
                )

                return@get
            }

            // find rooms matching name
            val roomsResult = serverDB.roomsDB.filterKeys { roomName ->
                roomName.contains(searchQuery, ignoreCase = true)
            }

            // collect data for response
            val rooms = roomsResult.values.map { room ->
                RoomDTO(room.roomName, room.maxPlayers, room.players.size)
            }.sortedBy {  room ->
                room.roomName
            }

            call.respond(HttpStatusCode.OK,
                BasicApiResponseWithData(
                    true,
                    "Rooms retrieved",
                    rooms
                )
            )
        }
    }
}

fun Route.joinRoomRoute() {
    route("/api/joinRoom") {
        get {
            val roomName = call.parameters["roomName"]
            val playerName = call.parameters["playerName"]

            if(roomName == null || playerName == null) {
                call.respond(HttpStatusCode.BadRequest,
                    BasicApiResponse(
                        false,
                        "No room name and/or player name provided" )
                )

                return@get
            }

            // find room
            val room = serverDB.roomsDB[roomName]

            // check if room exists
            if(room == null) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Room not found" )
                )

                return@get
            }

            // check if room is full
            if(room.players.size >= room.maxPlayers) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        false,
                        "Room is full" )
                )

                return@get
            }

            // check if player already exists in the room
            if(room.containsPlayerName(playerName)) {
                call.respond(HttpStatusCode.OK,
                    BasicApiResponse(
                        true,
                        "Player is already in room, ok to rejoin" )
                )

                return@get
            }

            // Player can be added to room
            call.respond(HttpStatusCode.OK,
                BasicApiResponse(
                    true,
                    "Player can be added to room" )
            )
        }
    }


}