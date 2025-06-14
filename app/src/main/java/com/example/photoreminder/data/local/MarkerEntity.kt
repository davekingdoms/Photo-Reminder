package com.example.photoreminder.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.photoreminder.data.model.PhotoRef

@Entity(
    tableName = "markers",
    indices = [
        Index(value = ["syncStatus"]),
        Index(value = ["username"])
    ]
)
data class MarkerEntity(
    @PrimaryKey val id: String,
    val username: String,
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
    val photos: List<PhotoRef> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY
)
