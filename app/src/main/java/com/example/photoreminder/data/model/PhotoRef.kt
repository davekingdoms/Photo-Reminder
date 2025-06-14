package com.example.photoreminder.data.model
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PhotoRef(
    val localUri: String? = null,
    val thumbPath: String,
    val remoteId: String? = null,
    val synced: Boolean = false
)
