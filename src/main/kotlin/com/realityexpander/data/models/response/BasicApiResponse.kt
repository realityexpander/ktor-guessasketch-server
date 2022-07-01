package com.realityexpander.data.models.response

data class BasicApiResponse(
    override val isSuccessful: Boolean,
    override val message: String? = null
) : BaseResponse

