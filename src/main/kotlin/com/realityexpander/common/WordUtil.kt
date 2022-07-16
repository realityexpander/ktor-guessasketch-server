package com.realityexpander.common

import java.io.File

val words = readWordList("./resources/programmers_wordlist.txt")
//val words = readWordList("./resources/wordlist.txt") // alternate list

fun readWordList(filePath: String): List<String> {
    val file = File(filePath)
    return file.readLines()
}

fun getRandomWord(): String {
    val randomIndex = (0 until words.size).random()
    return words[randomIndex]
}

fun getRandomWords(amount: Int): List<String> {
    var curAmount = 0
    val result = mutableListOf<String>()

    while (curAmount < amount) {
        val word = getRandomWord()
        if (!result.contains(word)) {
            result.add(word)
            curAmount++
        }
    }

    return result
}


// apple juice
// -> _ _ _ _ _  _ _ _ _ _
fun String.transformToUnderscores() = this.replace("[a-zA-Z0-9]".toRegex(), "_ ").trim()
// in code:
//toCharArray().map {
//    if(it != ' ') '_' else ' ' }
//.joinToString(" ")


//FOR TESTING ONLY: val words = readWordList("src/main/resources/programmers_wordlist.txt")
fun main() {
    val word = getRandomWord()
    println(word)
    println(word.transformToUnderscores())

    println("apple juice")
    println("apple juice".transformToUnderscores())
    println("5g".transformToUnderscores())
}