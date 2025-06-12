package com.example.photoreminder.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.*
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

/** Sincronizza Room ↔ backend; unica coda “marker_sync_global” */
class MarkerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "marker_sync_global"
        private const val TAG = "MarkerSyncWorker"

        private const val INPUT_IS_MANUAL = "isManual"
        private const val PREFS_NAME = "marker_sync_prefs"
        private const val KEY_LAST_SYNC_TEMPLATE = "last_sync_time_%s"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manual = inputData.getBoolean(INPUT_IS_MANUAL, false)
        Log.d(TAG, "Worker started (manual=$manual)")

        /* ───── dipendenze (no DI) ───── */
        val dao        = MarkerDatabase.getDatabase(applicationContext).markerDao()
        val repo       = MarkerRepository(dao)
        val api        = RetrofitInstance.api
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val username = DataStoreManager.getUsername(applicationContext)
            ?: return@withContext handleFailure(
                IllegalStateException("Username not found; please log in again"),
                manual
            )

        val lastSyncKey = KEY_LAST_SYNC_TEMPLATE.format(username)
        val lastSync    = prefs.getLong(lastSyncKey, 0L)

        kotlin.runCatching {
            pushLocalChanges(repo, api)
            pullServerChanges(repo, api, lastSync)

            prefs.edit().putLong(lastSyncKey, System.currentTimeMillis()).apply()
        }.fold(
            onSuccess = { success("Sync completata con successo", manual) },
            onFailure = { handleFailure(it, manual) }
        )
    }

    /* ───────────────── PUSH ───────────────── */

    private suspend fun pushLocalChanges(
        repo: MarkerRepository,
        api: com.example.photoreminder.data.api.ApiService
    ) {
        for (entity in repo.getPendingForSync()) {
            kotlin.runCatching {
                when (entity.syncStatus) {
                    SyncStatus.LOCAL_ONLY -> {
                        val dto = entity.toDto().copy(id = null)      // non inviare UUID locale
                        val serverId = api.createMarker(dto).bodyOrThrow().marker.id

                        if (serverId != null && serverId != entity.id) {
                            repo.replaceId(entity.id, serverId)
                        } else {
                            repo.markSynced(entity.id)
                        }
                        Log.d(TAG, "POST ok → id $serverId")
                    }

                    SyncStatus.DIRTY -> {
                        api.updateMarker(entity.id, entity.toDto()).bodyOrThrow()
                        repo.markSynced(entity.id)
                        Log.d(TAG, "PUT ok → id ${entity.id}")
                    }

                    SyncStatus.PENDING_DELETE -> {
                        api.deleteMarker(entity.id).bodyOrThrow()
                        repo.remove(entity.id)            // delete fisico
                        Log.d(TAG, "DELETE ok → id ${entity.id}")
                    }

                    else -> Unit
                }
            }.onFailure {
                Log.e(TAG, "Push failed on id=${entity.id}", it)
                // lasciamo il syncStatus invariato → riproverà
            }
        }
    }

    /* ───────────────── PULL ───────────────── */

    private suspend fun pullServerChanges(
        repo: MarkerRepository,
        api: com.example.photoreminder.data.api.ApiService,
        updatedSince: Long
    ) {
        val list = api.getMarkers(updatedSince).bodyOrThrow().markers
        list.forEach { dto ->
            if (dto.deleted) {
                dto.id?.let { repo.remove(it) }
                Log.d(TAG, "Server says DELETE → id ${dto.id}")
            } else {
                repo.upsert(dto.toEntity())
                Log.d(TAG, "Upsert da server → id ${dto.id}")
            }
        }
    }

    /* ───────────── helper & Result util ───────────── */

    private fun success(msg: String, manual: Boolean): Result =
        if (manual) Result.success(workDataOf("message" to msg)) else Result.success()

    private fun handleFailure(t: Throwable, manual: Boolean): Result {
        val msg = t.message ?: "Errore sconosciuto"
        return if (manual) {
            Result.failure(workDataOf("errorMessage" to "Sync fallita: $msg"))
        } else {
            Result.retry()        // lascia WorkManager gestire il back-off
        }
    }

    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T =
        body() ?: throw HttpException(this)
}
