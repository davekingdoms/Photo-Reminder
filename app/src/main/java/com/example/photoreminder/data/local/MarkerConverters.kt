package com.example.photoreminder.data.local

import androidx.room.TypeConverter


object MarkerConverters {


    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

}
