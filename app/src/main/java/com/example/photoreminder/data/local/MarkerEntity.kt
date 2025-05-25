package com.example.photoreminder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "markers",
    indices = [
        Index(value = ["syncStatus"]),
        Index(value = ["userId"])
    ]
)
data class MarkerEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val lat: Double,
    val lng: Double,
    val angle: Float,
    val title: String,
    val genre: String,
    val shutterSpeed: String,
    val aperture: String,
    val iso: String,
    val focalLength: Int,
    val tag: String?,
    val notes: String?,
    val photoUrl: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
