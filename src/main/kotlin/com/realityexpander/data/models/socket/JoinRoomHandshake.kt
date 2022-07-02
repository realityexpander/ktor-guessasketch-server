package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_JOIN_ROOM_HANDSHAKE

data class JoinRoomHandshake(
    val playerName: String,
    val roomName: String,
    val clientId: ClientId
): BaseMessageType(TYPE_JOIN_ROOM_HANDSHAKE)
