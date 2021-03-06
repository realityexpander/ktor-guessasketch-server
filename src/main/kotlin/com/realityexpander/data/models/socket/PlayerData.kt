package com.realityexpander.data.models.socket

data class PlayerData(
    val playerName: String,
    var isDrawingPlayer: Boolean = false,
    var score: Int = 0,
    var rank: Int = 0
)
