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
import com.example.photoreminder.data.model.PhotoRef
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

        // 1) Recupera l'utente corrente
        val username = DataStoreManager.getUsername(context)
            ?: return Result.failure()

        // 2) Carica tutti i marker per quell'utente
        val dao = MarkerDatabase.getDatabase(context).markerDao()
        val markers = dao.getMarkersByUsername(username).first()

        // 3) Filtra tutti i PhotoRef da processare
        val toProcess = markers.flatMap { marker ->
            marker.photos.filter { ref ->
                ref.remoteId != null && ref.thumbPath.isBlank()
            }.map { ref -> Pair(marker.id, ref) }
        }

        if (toProcess.isEmpty()) {
            Log.d("PhotoSyncWorker", "Niente da sincronizzare")
            return Result.success()
        }

        // 4) Per ciascuna immagine, scarica e genera thumbnail
        toProcess.forEach { (markerId, ref) ->
            try {
                val resp = RetrofitInstance.api.downloadPhoto(ref.remoteId!!)
                if (!resp.isSuccessful) {
                    Log.e("PhotoSyncWorker", "Errore download ${ref.remoteId}: ${resp.code()}")
                    return@forEach
                }
                val body: ResponseBody = resp.body()!!
                val inputStream = body.byteStream()

                // 4a) Decodifica e ridimensiona in memoria
                val originalBmp = BitmapFactory.decodeStream(inputStream)
                val targetHpx = (60 * applicationContext.resources.displayMetrics.density).roundToInt()
                val ratio     = targetHpx.toFloat() / originalBmp.height
                val targetW   = (originalBmp.width * ratio).toInt()
                val thumbBmp  = originalBmp.scale(targetW, targetHpx)
                originalBmp.recycle()

                // 4b) Salva su disco
                val thumbDir = File(context.filesDir, "thumbnails/$markerId").apply { mkdirs() }
                val outFile = File(thumbDir, "${ref.remoteId}.jpg")
                FileOutputStream(outFile).use { fos ->
                    thumbBmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                }
                thumbBmp.recycle()

                // 4c) Aggiorna Room: cerca il marker, sostituisci il PhotoRef
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
                Log.e("PhotoSyncWorker", "Errore sync foto ${ref.remoteId}", e)
            }
        }

        return Result.success()
    }
}
