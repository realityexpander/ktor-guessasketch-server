package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_PLAYERS_LIST

data class PlayersList(
    val players: List<PlayerData>
): BaseMessageType(TYPE_PLAYERS_LIST)
