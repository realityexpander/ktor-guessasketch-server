package com.realityexpander.data.models.response

interface BaseResponse {
    val isSuccessful: Boolean
    val message: String?
}