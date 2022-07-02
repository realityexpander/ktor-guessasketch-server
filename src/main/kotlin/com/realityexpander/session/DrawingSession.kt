package com.realityexpander.session

import com.realityexpander.common.ClientId
import com.realityexpander.common.SessionId

data class DrawingSession(
    val clientId: ClientId,
    val sessionId: SessionId
)
