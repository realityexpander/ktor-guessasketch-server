package com.realityexpander.data.models.socket

import com.realityexpander.data.models.socket.SocketMessageType.TYPE_WORDS_TO_PICK_HOLDER

data class WordsToPickHolder(
    val words: List<String>  // because this is a complex object, we cant send it as a json object, we need to wrap it
): BaseMessageType(TYPE_WORDS_TO_PICK_HOLDER)
