package com.realityexpander.common

import com.realityexpander.data.models.socket.ChatMessage

fun ChatMessage.containsWord(word: String): Boolean {
    return this.message.lowercase().trim().contains(word.lowercase())
}