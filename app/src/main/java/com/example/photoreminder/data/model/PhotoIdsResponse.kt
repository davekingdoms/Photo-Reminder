package com.example.photoreminder.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PhotoIdsResponse(
    val photoIds: List<String>
)
