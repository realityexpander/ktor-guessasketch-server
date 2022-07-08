package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_PING

class Ping(val username: String? = null) : BaseMessageType(TYPE_PING)
