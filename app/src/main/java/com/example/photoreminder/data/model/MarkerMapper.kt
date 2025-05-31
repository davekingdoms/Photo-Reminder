package com.example.photoreminder.data.model

import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import java.util.UUID

/** DTO → Room */
fun MarkerDto.toEntity(): MarkerEntity = MarkerEntity(
    id          = id ?: UUID.randomUUID().toString(),
    username    = username,
    lat         = lat,
    lng         = lng,
    title       = title,
    genre       = genre.orEmpty(),
    shutterSpeed= shutterSpeed.orEmpty(),
    aperture    = aperture.orEmpty(),
    iso         = iso.orEmpty(),
    focalLength = focalLength ?: 0,
    tag         = tag,
    notes       = notes,
    photoUrl    = photoUrl,
    angle       = angle,
    createdAt   = createdAt,
    updatedAt   = updatedAt,
    syncStatus  = if (deleted) SyncStatus.PENDING_DELETE else SyncStatus.SYNCED
)

/** Room → DTO */
fun MarkerEntity.toDto(): MarkerDto = MarkerDto(
    id          = id,
    username    = username,
    lat         = lat,
    lng         = lng,
    title       = title,
    genre       = genre,
    shutterSpeed= shutterSpeed,
    aperture    = aperture,
    iso         = iso,
    focalLength = focalLength,
    tag         = tag,
    notes       = notes,
    photoUrl    = photoUrl,
    angle       = angle,
    createdAt   = createdAt,
    updatedAt   = updatedAt,
    deleted     = (syncStatus == SyncStatus.PENDING_DELETE)
)
