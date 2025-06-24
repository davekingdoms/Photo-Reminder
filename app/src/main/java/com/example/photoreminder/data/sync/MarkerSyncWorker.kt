package com.example.photoreminder.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.work.*
import com.example.photoreminder.data.api.ApiService
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.model.PhotoUploadResponse
import com.example.photoreminder.data.model.toDto
import com.example.photoreminder.data.model.toEntity
import com.example.photoreminder.data.repository.MarkerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException


/** sync Room ↔ backend */
class MarkerSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        /* nomi code WorkManager */
        const val QUEUE_PERIODIC = "marker_sync_periodic"
        const val QUEUE_MANUAL   = "marker_sync_manual"

        private const val TAG = "MarkerSyncWorker"
        private const val INPUT_IS_MANUAL = "isManual"

        private const val PREFS_NAME = "marker_sync_prefs"
        private const val KEY_LAST_SYNC_TEMPLATE = "last_sync_time_%s"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val manual = inputData.getBoolean(INPUT_IS_MANUAL, false)
        Log.d(TAG, "Worker started (manual=$manual)")

        val dao  = MarkerDatabase.getDatabase(applicationContext).markerDao()
        val repo = MarkerRepository(dao)
        val api  = RetrofitInstance.api
        val prefs: SharedPreferences =
            applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val username = DataStoreManager.getUsername(applicationContext)
            ?: return@withContext handleFailure(
                IllegalStateException("Username not found; please log in again"), manual
            )

        val lastSyncKey = KEY_LAST_SYNC_TEMPLATE.format(username)
        val lastSync    = prefs.getLong(lastSyncKey, 0L)

        kotlin.runCatching {
            pushLocalChanges(repo, api)
            pullServerChanges(repo, api, lastSync)

            prefs.edit { putLong(lastSyncKey, System.currentTimeMillis()) }
        }.fold(
            onSuccess = { success("Sync completed successfully", manual) },
            onFailure = { handleFailure(it, manual) }
        )
    }

    /* ───────────── PUSH ───────────── */
    private suspend fun pushLocalChanges(
        repo: MarkerRepository,
        api : ApiService
    ) {
        val ctx = applicationContext

        for (entity0 in repo.getPendingForSync()) {
            var entity = entity0
            kotlin.runCatching {

                if (entity.syncStatus == SyncStatus.LOCAL_ONLY) {
                    val serverId = api.createMarker(entity.toDto().copy(photoIds = emptyList()))
                        .bodyOrThrow()
                        .marker.id!!

                    if (serverId != entity.id) {
                        /* rename dir thumbnail + path */
                        val oldDir = File(ctx.filesDir, "thumbnails/${entity.id}")
                        val newDir = File(ctx.filesDir, "thumbnails/$serverId")
                        if (oldDir.exists()) oldDir.renameTo(newDir)

                        val updatedPhotos = entity.photos.map { pr ->
                            pr.copy(thumbPath = pr.thumbPath.replace("/${entity.id}/", "/$serverId/"))
                        }
                        repo.replaceId(entity.id, serverId)
                        entity = entity.copy(id = serverId, photos = updatedPhotos)
                    }
                }

                val notSynced = entity.photos.filter { !it.synced }
                if (notSynced.isNotEmpty()) {

                    val parts = notSynced.map { pr ->
                        val bytes = ctx.contentResolver
                            .openInputStream(pr.localUri!!.toUri())
                            ?.readBytes()
                            ?: throw IOException("Cannot read ${pr.localUri}")

                        val body = okhttp3.RequestBody.create(
                            okhttp3.MediaType.parse("image/jpeg"), bytes
                        )
                        MultipartBody.Part.createFormData(
                            "files",
                            File(pr.thumbPath).name,
                            body
                        )
                    }

                    val resp: PhotoUploadResponse =
                        api.uploadPhotos(entity.id, parts).bodyOrThrow()

                    val idByName = resp.photos.associate { it.filename to it.id }

                    val updatedPhotos = entity.photos.map { pr ->
                        if (pr.synced) pr
                        else {
                            val newId = idByName[File(pr.thumbPath).name]
                            pr.copy(
                                remoteId = newId,
                                synced   = newId != null,
                                localUri = null
                            )
                        }
                    }
                    entity = entity.copy(photos = updatedPhotos)
                }

                when (entity.syncStatus) {
                    SyncStatus.LOCAL_ONLY, SyncStatus.DIRTY -> {
                        api.updateMarker(entity.id, entity.toDto()).bodyOrThrow()
                        repo.markSynced(entity.id)
                    }
                    SyncStatus.PENDING_DELETE -> {
                        api.deleteMarker(entity.id).bodyOrThrow()
                        repo.remove(entity.id)
                        File(ctx.filesDir, "thumbnails/${entity.id}").deleteRecursively()
                    }
                    else -> Unit
                }

            }.onFailure { e ->
                Log.e(TAG, "Push failed on id=${entity.id}", e)
            }
        }
    }

    /* ───────────── PULL ───────────── */
    private suspend fun pullServerChanges(
        repo        : MarkerRepository,
        api         : ApiService,
        updatedSince: Long
    ) {
        val list = api.getMarkers(updatedSince).bodyOrThrow().markers
        list.forEach { dto ->
            if (dto.deleted) {
                dto.id?.let {
                    repo.remove(it)
                    File(applicationContext.filesDir, "thumbnails/$it").deleteRecursively()
                }
                Log.d(TAG, "Server DELETE → id ${dto.id}")
            } else {
                repo.upsert(dto.toEntity())
            }
        }
    }

    /* ───────── helper Result util ───────── */
    private fun success(msg: String, manual: Boolean): Result =
        if (manual) Result.success(workDataOf("message" to msg)) else Result.success()

    private fun handleFailure(t: Throwable, manual: Boolean): Result {
        val msg = t.message ?: "Unknown error"
        return if (manual) {
            Result.failure(workDataOf("errorMessage" to "Sync failed: $msg"))
        } else {
            Result.retry()          // WorkManager menages back-off
        }
    }

    private fun <T> retrofit2.Response<T>.bodyOrThrow(): T =
        body() ?: throw HttpException(this)
}
