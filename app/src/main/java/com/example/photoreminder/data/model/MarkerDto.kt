package com.example.photoreminder.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class MarkerDto(
    @Json(name = "_id") val id: String?,
    val username: String,
    val lat: Double,
    val lng: Double,
    val title: String,
    val genre: String?,
    val shutterSpeed: String?,
    val aperture: String?,
    val iso: String?,
    val focalLength: Int?,
    val tag: String?,
    val notes: String?,
    val photos: List<PhotoRef> = emptyList(),
    val angle: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean
)
