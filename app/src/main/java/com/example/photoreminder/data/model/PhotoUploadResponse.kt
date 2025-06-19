package com.example.photoreminder.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PhotoUploadResponse(
    val photos: List<PhotoUploadItem>
)
