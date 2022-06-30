package com.realityexpander.data.models

data class BasicApiResponse(
    val isSuccessful: Boolean,
    val message: String? = null
)


data class BasicApiResponseWithData<T>(
    val isSuccessful: Boolean,
    val message: String? = null,
    val data: T? = null
)
