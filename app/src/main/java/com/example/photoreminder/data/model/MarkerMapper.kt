package com.example.photoreminder.data.model

import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import java.util.UUID

/**
 * Converte un DTO di rete in Entity Room.
 */
fun MarkerDto.toEntity(): MarkerEntity = MarkerEntity(
    id = id ?: UUID.randomUUID().toString(),
    userId = userId,
    lat = lat,
    lng = lng,
    title = title,
    genre = genre.orEmpty(),
    shutterSpeed = shutterSpeed.orEmpty(),
    aperture = aperture.orEmpty(),
    iso = iso.orEmpty(),
    focalLength = focalLength ?: 0,
    tag = tag.orEmpty(),
    notes = notes.orEmpty(),
    photoUrl = photoUrl,
    angle = angle,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncStatus = if (deleted) SyncStatus.PENDING_DELETE else SyncStatus.SYNCED
)

/**
 * Converte un Entity Room in DTO per l’API.
 */
fun MarkerEntity.toDto(): MarkerDto = MarkerDto(
    id = id,
    userId = userId,
    lat = lat,
    lng = lng,
    title = title,
    genre = genre,
    shutterSpeed = shutterSpeed,
    aperture = aperture,
    iso = iso,
    focalLength = focalLength,
    tag = tag,
    notes = notes,
    photoUrl = photoUrl,
    angle = angle,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deleted = (syncStatus == SyncStatus.PENDING_DELETE)
)