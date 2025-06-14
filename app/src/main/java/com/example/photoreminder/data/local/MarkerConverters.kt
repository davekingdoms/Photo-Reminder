package com.example.photoreminder.data.local

import androidx.room.TypeConverter
import com.example.photoreminder.data.model.PhotoRef
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory


object MarkerConverters {


    @TypeConverter
    fun fromSyncStatus(status: SyncStatus): String = status.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)

    private val moshi: Moshi by lazy {
        Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
    }
    private val photoListAdapter by lazy {
        val listType = Types.newParameterizedType(List::class.java, PhotoRef::class.java)
        moshi.adapter<List<PhotoRef>>(listType)
    }

    @TypeConverter
    fun photoListToJson(list: List<PhotoRef>): String =
        photoListAdapter.toJson(list)

    @TypeConverter
    fun jsonToPhotoList(json: String): List<PhotoRef> =
        photoListAdapter.fromJson(json) ?: emptyList()

}
