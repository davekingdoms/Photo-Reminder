package com.example.photoreminder.data.repository

import com.example.photoreminder.data.local.MarkerDao
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import kotlinx.coroutines.flow.Flow

class MarkerRepository(
    private val dao: MarkerDao
) {
    /* tutti i marker (la UI filtra quelli deleted) */
    fun observeAll(): Flow<List<MarkerEntity>> = dao.observeMarkers()

    suspend fun getPendingForSync(): List<MarkerEntity> = dao.getPending()

    suspend fun upsert(marker: MarkerEntity) = dao.insert(marker)

    suspend fun deleteLocal(id: String) = dao.flagDelete(id)

    suspend fun replaceId(oldId: String, newId: String) =
        dao.replaceId(oldId, newId)

    suspend fun markSynced(id: String) =
        dao.updateSyncStatus(id, SyncStatus.SYNCED)

    suspend fun remove(id: String) = dao.deleteById(id)

    suspend fun clearAll() = dao.clearAllMarkers()
}
