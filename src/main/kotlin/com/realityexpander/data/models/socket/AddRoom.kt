package com.realityexpander.data.models.socket

import com.realityexpander.common.RoomName
import com.realityexpander.data.models.socket.SocketMessageType.TYPE_ADD_ROOM

data class  AddRoom(
    val roomName: RoomName,
    val playerCapacity: Int
): BaseMessageType(TYPE_ADD_ROOM)
