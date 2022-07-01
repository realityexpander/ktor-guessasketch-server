package com.realityexpander.data.models.socket

import com.realityexpander.common.Constants.TYPE_JOIN_ROOM_HANDSHAKE

data class JoinRoomHandshake(
    val playerName: String,
    val roomName: String,
    val clientId: String
): BaseModel(TYPE_JOIN_ROOM_HANDSHAKE)
