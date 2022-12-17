package com.realityexpander.plugins

import com.realityexpander.routes.createRoomRoute
import com.realityexpander.routes.gameWebSocketRoute
import com.realityexpander.routes.getRoomsRoute
import com.realityexpander.routes.joinRoomRoute
import io.ktor.application.*
import io.ktor.routing.*

fun Application.configureRouting() {
    install(Routing) {
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }
}