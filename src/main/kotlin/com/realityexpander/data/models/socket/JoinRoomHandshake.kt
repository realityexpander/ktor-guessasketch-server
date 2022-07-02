package com.realityexpander.data.models.socket

data class JoinRoomHandshake(
    val playerName: String,
    val roomName: String,
    val clientId: String
): BaseSocketType(TYPE_JOIN_ROOM_HANDSHAKE)
