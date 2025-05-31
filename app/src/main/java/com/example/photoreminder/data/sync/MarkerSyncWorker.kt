package com.example.photoreminder.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.model.toDto
import com.example.photoreminder.data.model.toEntity
import com.example.photoreminder.data.repository.MarkerRepository
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class MarkerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val PREFS_NAME = "marker_sync_prefs"
        private const val KEY_LAST_SYNC_TEMPLATE = "last_sync_time_%s"  // username-scoped
    }

    override suspend fun doWork(): Result = withContext(coroutineContext) {
        val isManual = inputData.getBoolean("isManual", false)

        /* ---------- dipendenze ---------- */
        val dao         = MarkerDatabase.getDatabase(applicationContext).markerDao()
        val repository  = MarkerRepository(dao)
        val api         = RetrofitInstance.api
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /* ---------- username dall’archivio locale ---------- */
        val username = DataStoreManager.getUsername(applicationContext)
            ?: return@withContext Result.failure(
                workDataOf("errorMessage" to "Username not found; please log in again")
            )

        val keyLastSync = KEY_LAST_SYNC_TEMPLATE.format(username)
        val lastSync    = prefs.getLong(keyLastSync, 0L)

        try {
            /* 1) PUSH locale → server */
            repository.getPendingForSync().forEach { entity ->
                when (entity.syncStatus) {
                    SyncStatus.LOCAL_ONLY -> {
                        val resp = api.createMarker(entity.toDto())
                        if (resp.isSuccessful) {
                            repository.replaceId(entity.id, resp.body()!!.marker.id!!)
                        } else throw HttpException(resp)
                    }
                    SyncStatus.DIRTY -> {
                        val resp = api.updateMarker(entity.id, entity.toDto())
                        if (resp.isSuccessful) {
                            repository.markSynced(entity.id)
                        } else throw HttpException(resp)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        val resp = api.deleteMarker(entity.id)
                        if (resp.isSuccessful) {
                            repository.remove(entity.id)                        // eliminazione fisica
                        } else throw HttpException(resp)
                    }
                    else -> {} // SYNCED
                }
            }

            /* 2) PULL server → locale */
            val pullResp = api.getMarkers(updatedSince = lastSync)
            if (pullResp.isSuccessful) {
                pullResp.body()!!.markers.forEach { dto ->
                    repository.upsert(dto.toEntity())
                }
                prefs.edit().putLong(keyLastSync, System.currentTimeMillis()).apply()
            } else throw HttpException(pullResp)

            if (isManual) Result.success(workDataOf("message" to "Sync completata con successo"))
            else          Result.success()

        } catch (e: HttpException) {
            if (isManual) Result.failure(workDataOf("errorMessage" to "Sync fallita: ${e.message()}"))
            else          Result.retry()
        } catch (e: Exception) {
            if (isManual) Result.failure(workDataOf("errorMessage" to "Sync fallita: ${e.message}"))
            else          Result.failure()
        }
    }
}
