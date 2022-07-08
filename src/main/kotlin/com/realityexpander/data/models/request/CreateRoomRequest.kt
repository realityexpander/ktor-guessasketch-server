package com.realityexpander.data.models.request

data class CreateRoomRequest(
    val roomName: String,
    val maxPlayers: Int
)
