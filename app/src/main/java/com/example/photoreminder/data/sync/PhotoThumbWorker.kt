package com.example.photoreminder.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import java.io.ByteArrayInputStream
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

class PhotoThumbWorker(
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
            Log.d("PhotoThumbWorker", "Empty")
            return Result.success()
        }

        // For each image -> download and create thumbnail
        toProcess.forEach { (markerId, ref) ->
            try {
                val resp = RetrofitInstance.api.downloadPhoto(ref.remoteId!!)
                if (!resp.isSuccessful) {
                    Log.e("PhotoThumbWorker", "Download error ${ref.remoteId}: ${resp.code()}")
                    return@forEach
                }
                val body: ResponseBody = resp.body()!!
                val bytes = body.bytes()

                val orientation = ByteArrayInputStream(bytes).use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                }

                val baseBmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                val rotated = rotateBitmap(baseBmp, orientation)
                val targetHpx = (170 * applicationContext.resources.displayMetrics.density).roundToInt()
                val ratio     = targetHpx.toFloat() / rotated.height
                val targetW   = (rotated.width * ratio).toInt()
                val thumbBmp  = rotated.scale(targetW, targetHpx)
                if (rotated !== baseBmp) baseBmp.recycle()
                if (thumbBmp !== rotated) rotated.recycle()

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
                Log.e("PhotoThumbWorker", "Error sync ${ref.remoteId}", e)
            }
        }

        return Result.success()
    }

    private fun rotateBitmap(bmp: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90  -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bmp
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated != bmp) bmp.recycle()
        return rotated
    }
}