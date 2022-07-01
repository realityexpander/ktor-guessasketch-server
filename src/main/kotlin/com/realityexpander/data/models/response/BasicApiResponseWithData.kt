package com.realityexpander.data.models.response

data class BasicApiResponseWithData<T>(
    override val isSuccessful: Boolean,
    override val message: String? = null,
    val data: T? = null
) : BaseResponse
