package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_PING

class Ping(val playerName: String? = null) : BaseMessageType(TYPE_PING)
