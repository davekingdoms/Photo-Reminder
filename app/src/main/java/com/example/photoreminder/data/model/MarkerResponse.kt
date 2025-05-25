package com.example.photoreminder.data.model

import com.squareup.moshi.JsonClass
//wrapper
@JsonClass(generateAdapter = true)
data class MarkerResponse(
    val marker: MarkerDto
)
