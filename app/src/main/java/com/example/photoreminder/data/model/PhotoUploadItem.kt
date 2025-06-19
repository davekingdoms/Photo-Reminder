package com.example.photoreminder.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PhotoUploadItem(
    val filename: String,
    @Json(name = "_id") val id: String
)
