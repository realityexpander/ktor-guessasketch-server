package com.realityexpander

// Course Idea: https://elopage.com/payer/s/philipplackner/courses/doodlekong?course_session_id=3501878&lesson_id=1055226

// Ktor generator
// https://start.ktor.io/#/final?name=ktor-drawing-server&website=realityexpander.com&artifact=com.realityexpander.ktor-drawing-server&kotlinVersion=1.7.0&ktorVersion=1.5.3&buildSystem=GRADLE&engine=NETTY&configurationIn=HOCON&addSampleCode=true&plugins=content-negotiation%2Crouting%2Cktor-gson%2Cktor-websockets%2Cshutdown-url%2Ccall-logging

import com.google.gson.Gson
import io.ktor.application.*
import com.realityexpander.plugins.*
import com.realityexpander.routes.createRoomRoute
import com.realityexpander.routes.gameWebSocketRoute
import com.realityexpander.routes.getRoomsRoute
import com.realityexpander.routes.joinRoomRoute
import com.realityexpander.session.DrawingSession
import io.ktor.features.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.sessions.*
import io.ktor.util.*
import io.ktor.websocket.*
import org.slf4j.event.Level

// Globals
val serverDB = SketchServer() // represent the database
val gson = Gson()

fun main(args: Array<String>): Unit =
    io.ktor.server.netty.EngineMain.main(args)

@Suppress("unused") // application.conf references the main function. This annotation prevents the IDE from marking it as unused.
fun Application.module() {

    install(Sessions) {
        cookie<DrawingSession>("SESSION")
    }

    // Set up the sessions
    intercept(ApplicationCallPipeline.Features) {
        call.sessions.get<DrawingSession>() ?: run {
            val clientId = call.parameters["clientId"] ?: "" // throw IllegalArgumentException("clientId is required")

            call.sessions.set(DrawingSession(clientId, generateNonce()))
        }
    }

    install(WebSockets)

    install(Routing) {
        createRoomRoute()
        getRoomsRoute()
        joinRoomRoute()
        gameWebSocketRoute()
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/") }
    }

    // Setup the GSON serializer
    configureSerialization()

    // Setup the shutdown url
    configureAdministration()
}
