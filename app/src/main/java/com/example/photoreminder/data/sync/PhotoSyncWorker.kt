package com.example.photoreminder.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import kotlinx.coroutines.flow.first
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import androidx.core.graphics.scale
import kotlin.math.roundToInt

class PhotoSyncWorker(
    appContext: Context,
    params: WorkerParameters
): CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val username = DataStoreManager.getUsername(context)
            ?: return Result.failure()

        val dao = MarkerDatabase.getDatabase(context).markerDao()
        val markers = dao.getMarkersByUsername(username).first()

        val toProcess = markers.flatMap { marker ->
            marker.photos.filter { ref ->
                ref.remoteId != null && ref.thumbPath.isBlank()
            }.map { ref -> Pair(marker.id, ref) }
        }

        if (toProcess.isEmpty()) {
            Log.d("PhotoSyncWorker", "Empty")
            return Result.success()
        }

        // For each image -> download and create thumbnail
        toProcess.forEach { (markerId, ref) ->
            try {
                val resp = RetrofitInstance.api.downloadPhoto(ref.remoteId!!)
                if (!resp.isSuccessful) {
                    Log.e("PhotoSyncWorker", "Download error ${ref.remoteId}: ${resp.code()}")
                    return@forEach
                }
                val body: ResponseBody = resp.body()!!
                val inputStream = body.byteStream()

                // Decode and resize
                val originalBmp = BitmapFactory.decodeStream(inputStream)
                val targetHpx = (170 * applicationContext.resources.displayMetrics.density).roundToInt()
                val ratio     = targetHpx.toFloat() / originalBmp.height
                val targetW   = (originalBmp.width * ratio).toInt()
                val thumbBmp  = originalBmp.scale(targetW, targetHpx)
                originalBmp.recycle()

                // Save thumbnail
                val thumbDir = File(context.filesDir, "thumbnails/$markerId").apply { mkdirs() }
                val outFile = File(thumbDir, "${ref.remoteId}.jpg")
                FileOutputStream(outFile).use { fos ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                thumbBmp.recycle()

                // Update marker
                val updatedMarker = dao.getMarkerById(markerId)?.let { m ->
                    val updatedPhotos = m.photos.map { p ->
                        if (p.remoteId == ref.remoteId) {
                            p.copy(thumbPath = outFile.absolutePath, synced = true)
                        } else p
                    }
                    m.copy(photos = updatedPhotos)
                }
                if (updatedMarker != null) {
                    dao.insert(updatedMarker)
                }
            } catch (e: Exception) {
                Log.e("PhotoSyncWorker", "Error sync ${ref.remoteId}", e)
            }
        }

        return Result.success()
    }
}
