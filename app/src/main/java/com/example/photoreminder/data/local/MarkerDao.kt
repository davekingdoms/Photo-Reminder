package com.example.photoreminder.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MarkerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: MarkerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(markers: List<MarkerEntity>)

    @Update
    suspend fun update(marker: MarkerEntity)

    /* soft-delete client */
    @Query("UPDATE markers SET syncStatus = :status WHERE id = :id")
    suspend fun flagDelete(id: String, status: SyncStatus = SyncStatus.PENDING_DELETE)

    @Query("SELECT * FROM markers WHERE id = :id")
    suspend fun getMarkerById(id: String): MarkerEntity?

    @Query("DELETE FROM markers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM markers WHERE username = :username")
    fun getMarkersByUsername(username: String): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers")
    fun observeMarkers(): Flow<List<MarkerEntity>>

    @Query("SELECT * FROM markers WHERE syncStatus != :synced")
    suspend fun getPending(synced: SyncStatus = SyncStatus.SYNCED): List<MarkerEntity>

    @Query("UPDATE markers SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE markers SET id = :newId, syncStatus = :status WHERE id = :oldId")
    suspend fun replaceId(oldId: String, newId: String, status: SyncStatus = SyncStatus.SYNCED)

    @Query("SELECT * FROM markers WHERE tag = :tag")
    fun getMarkersByTag(tag: String): Flow<List<MarkerEntity>>

    @Query("DELETE FROM markers")
    suspend fun clearAllMarkers()
}
