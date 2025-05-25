package com.example.photoreminder.data.repository

import com.example.photoreminder.data.local.MarkerDao
import com.example.photoreminder.data.local.MarkerEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository per la gestione dei marker in locale e per la successiva sincronizzazione.
 */
class MarkerRepository(
    private val dao: MarkerDao
) {
    /** Flusso di tutti i marker (esclude quelli cancellati) */
    fun observeAll(): Flow<List<MarkerEntity>> =
        dao.observeMarkers()

    /** Restituisce i marker pendenti di sincronizzazione (creati, modificati o cancellati) */
    suspend fun getPendingForSync(): List<MarkerEntity> =
        dao.getPending()

    /** Inserisce o aggiorna un marker in locale */
    suspend fun upsert(marker: MarkerEntity) =
        dao.insert(marker)

    /** Marca un marker come cancellato localmente */
    suspend fun deleteLocal(id: String) =
        dao.flagDelete(id)

    /**
     * In futuro: metodo di sincronizzazione
     * 1) push delle modifiche locali
     * 2) pull dei delta dal server
     */
    suspend fun sync() {
        // TODO: implementare push e pull
    }
}
