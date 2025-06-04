package com.example.photoreminder.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Sincronizza i marker tra Room e backend.
 *
 * 1. **Push**: LOCAL_ONLY ▸ POST, DIRTY ▸ PUT, PENDING_DELETE ▸ DELETE.
 * 2. **Pull**: GET /markers?updatedSince=lastSync, quindi upsert in Room.
 *    Se l’oggetto arriva con `deleted=true`, lo eliminiamo fisicamente dal DB locale.
 *
 * Parametri d’ingresso:
 *   - "isManual" (Boolean) → se true, restituisce un messaggio in outputData per la UI.
 */
class MarkerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "MarkerSyncWorker"
        private const val PREFS_NAME = "marker_sync_prefs"
        private const val KEY_LAST_SYNC_TEMPLATE = "last_sync_time_%s"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {

        val manual = inputData.getBoolean("isManual", false)

        /* ---------- dipendenze ---------- */
        val dao        = MarkerDatabase.getDatabase(applicationContext).markerDao()
        val repository = MarkerRepository(dao)
        val api        = RetrofitInstance.api
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /* ---------- username ---------- */
        val username = DataStoreManager.getUsername(applicationContext)
            ?: return@withContext failure("Username not found; please log in again", manual)

        val keyLastSync = KEY_LAST_SYNC_TEMPLATE.format(username)
        val lastSync    = prefs.getLong(keyLastSync, 0L)

        try {
            /* ---------------- 1) PUSH locale → server ---------------- */
            for (entity in repository.getPendingForSync()) {
                try {
                    when (entity.syncStatus) {
                        SyncStatus.LOCAL_ONLY -> {
                            val resp = api.createMarker(entity.toDto())
                            if (resp.isSuccessful) {
                                val serverId = resp.body()?.marker?.id
                                if (serverId != null) {
                                    repository.replaceId(entity.id, serverId)
                                    Log.d(TAG, "POST ok → id $serverId")
                                } else {
                                    Log.w(TAG, "POST ok ma body nullo")
                                }
                            } else throw HttpException(resp)
                        }

                        SyncStatus.DIRTY -> {
                            val resp = api.updateMarker(entity.id, entity.toDto())
                            if (resp.isSuccessful) {
                                repository.markSynced(entity.id)
                                Log.d(TAG, "PUT ok → id ${entity.id}")
                            } else throw HttpException(resp)
                        }

                        SyncStatus.PENDING_DELETE -> {
                            val resp = api.deleteMarker(entity.id)
                            if (resp.isSuccessful) {
                                repository.remove(entity.id)          // delete fisico
                                Log.d(TAG, "DELETE ok → id ${entity.id}")
                            } else throw HttpException(resp)
                        }

                        else -> Unit
                    }
                } catch (e: HttpException) {
                    Log.e(TAG, "Push failed on id=${entity.id}: ${e.code()}", e)
                    /* lasciamo LOCAL_ONLY / DIRTY / PENDING_DELETE invariato
                       così riproverà al prossimo giro */
                }
            }

            /* ---------------- 2) PULL server → locale ---------------- */
            val pullResp = api.getMarkers(updatedSince = lastSync)
            if (!pullResp.isSuccessful) throw HttpException(pullResp)

            pullResp.body()?.markers?.forEach { dto ->
                if (dto.deleted) {
                    // è stato cancellato da un altro device: rimuoviamo anche localmente
                    repository.remove(dto.id ?: return@forEach)
                    Log.d(TAG, "Server says DELETED → id ${dto.id}")
                } else {
                    repository.upsert(dto.toEntity())
                    Log.d(TAG, "Upsert da server → id ${dto.id}")
                }
            }

            prefs.edit()
                .putLong(keyLastSync, System.currentTimeMillis())
                .apply()

            return@withContext success("Sync completata con successo", manual)

        } catch (e: HttpException) {
            Log.e(TAG, "Sync failed: HTTP ${e.code()}", e)
            return@withContext failure("Sync fallita: ${e.message()}", manual)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ${e.message}", e)
            return@withContext failure("Sync fallita: ${e.message}", manual, retry = !manual)
        }
    }

    /* ---------- helper ---------- */
    private fun success(msg: String, manual: Boolean): Result =
        if (manual) Result.success(workDataOf("message" to msg))
        else         Result.success()

    private fun failure(msg: String, manual: Boolean, retry: Boolean = false): Result =
        if (manual) Result.failure(workDataOf("errorMessage" to msg))
        else if (retry) Result.retry()
        else Result.failure()
}
