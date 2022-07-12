package com.realityexpander.common

object Constants {
    const val ROOM_MAX_NUM_PLAYERS = 8

    const val PLAYER_EXIT_REMOVE_PERMANENTLY_DELAY_MILLIS = 60000L // Amount of wait time until player is finally removed.

    // Scoring
    const val SCORE_PER_SECOND = 1
    const val SCORE_PENALTY_NO_PLAYERS_GUESSED_WORD = 50
    const val SCORE_GUESS_CORRECT_DEFAULT = 50
    const val SCORE_GUESS_CORRECT_MULTIPLIER = 50
    const val SCORE_FOR_DRAWING_PLAYER_WHEN_OTHER_PLAYER_CORRECT = 50

    // Network
    const val PING_TIMEOUT_LIMIT_MILLIS = 3000L

    const val QUERY_PARAMETER_CLIENT_ID = "clientId"
}

typealias ClientId = String
typealias RoomName = String
typealias SessionId = String