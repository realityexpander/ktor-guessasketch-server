package com.realityexpander.data.models.socket

data class PlayersList(
    val players: List<PlayerData>
): BaseSocketType("TYPE_PLAYERS_LIST")
