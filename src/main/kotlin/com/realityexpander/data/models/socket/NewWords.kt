package com.realityexpander.data.models.socket

data class NewWords(
    val newWords: List<String>  // because this is a complex object, we cant send it as a json object, we need to wrap it
): BaseModel("TYPE_NEW_WORDS")
