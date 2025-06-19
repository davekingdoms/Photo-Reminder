package com.example.photoreminder

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.*

class CameraFragment : Fragment() {

    /* ===== chiavi per il risultato verso AddPhotoMarkerFragment ===== */
    companion object {
        const val RESULT_KEY  = "photosResult"   // requestKey
        const val BUNDLE_URIS = "uris"           // arrayList<String>
    }

    /* ---------- view refs ---------- */
    private lateinit var previewView  : PreviewView
    private lateinit var captureButton: FloatingActionButton
    private lateinit var galleryButton: FloatingActionButton

    /* ---------- CameraX ---------- */
    private lateinit var imageCapture: ImageCapture

    /* ------------------------------------------------------------ */
    /* 1) Permesso fotocamera                                       */
    /* ------------------------------------------------------------ */
    private val cameraPermission = Manifest.permission.CAMERA
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(
                requireContext(),
                "Permesso fotocamera negato",
                Toast.LENGTH_SHORT
            ).show()
        }

    /* ------------------------------------------------------------ */
    /* 2) Photo Picker API 33+ (selezione multipla)                  */
    /* ------------------------------------------------------------ */
    private val pickImagesLauncher =
        registerForActivityResult(PickMultipleVisualMedia()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) returnWithUris(uris)
        }

    /* ------------------------------------------------------------ */
    /* 3) Fallback SAF (multi-select)                               */
    /* ------------------------------------------------------------ */
    private val openDocLauncher =
        registerForActivityResult(StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val data = res.data ?: return@registerForActivityResult
                val uris = mutableListOf<Uri>()

                /* ClipData multipla */
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) uris += clip.getItemAt(i).uri
                } ?: data.data?.let { uris += it }   // selezione singola

                if (uris.isNotEmpty()) returnWithUris(uris)
            }
        }

    /* ------------------------------------------------------------ */
    /* Ciclo vita                                                   */
    /* ------------------------------------------------------------ */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        previewView   = view.findViewById(R.id.previewView)
        captureButton = view.findViewById(R.id.floatingActionButton)
        galleryButton = view.findViewById(R.id.floatingActionButton2)

        /* permesso fotocamera */
        when {
            ContextCompat.checkSelfPermission(requireContext(), cameraPermission)
                    == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(cameraPermission) ->
                showPermissionRationale()
            else ->
                requestPermissionLauncher.launch(cameraPermission)
        }

        /* click GALLERIA → photo-picker */
        galleryButton.setOnClickListener { launchPicker() }

        /* click FOTOCAMERA → scatta e restituisci Uri */
        captureButton.setOnClickListener { takePhoto() }
    }

    /* ------------------------------------------------------------ */
    /* Avvio del picker                                             */
    /* ------------------------------------------------------------ */
    private fun launchPicker() {
        /* Nuovo Photo Picker: solo immagini, multi illimitata */
        val req = PickVisualMediaRequest.Builder()
            .setMediaType(PickVisualMedia.ImageOnly)
            .build()
        pickImagesLauncher.launch(req)
    }

    /* ------------------------------------------------------------ */
    /* Restituisce le URI al fragment chiamante                     */
    /* ------------------------------------------------------------ */
    private fun returnWithUris(list: List<Uri>) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply {
                putStringArrayList(
                    BUNDLE_URIS,
                    ArrayList(list.map { it.toString() })
                )
            }
        )
        parentFragmentManager.popBackStack()    // chiude CameraFragment
    }

    /* ------------------------------------------------------------ */
    /* Camera preview + ImageCapture                                */
    /* ------------------------------------------------------------ */
    private fun startCamera() {
        val provFuture = ProcessCameraProvider.getInstance(requireContext())
        provFuture.addListener({
            val provider = provFuture.get()

            /* Preview */
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            /* ImageCapture con rotazione corretta */
            imageCapture = ImageCapture.Builder()
                .setTargetRotation(previewView.display.rotation)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /* ------------------------------------------------------------ */
    /* Scatto foto                                                  */
    /* ------------------------------------------------------------ */
    private fun takePhoto() {
        /* temp file in cacheDir/captures */
        val file = File(
            File(requireContext().cacheDir, "captures").apply { mkdirs() },
            "CAP_${UUID.randomUUID()}.jpg"
        )
        val output = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture.takePicture(
            output,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(
                        requireContext(),
                        "Errore scatto: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                    /* Ri-usa la stessa pipeline del picker */
                    returnWithUris(listOf(file.toUri()))
                }
            }
        )
    }

    /* ------------------------------------------------------------ */
    /* Dialog permesso fotocamera                                   */
    /* ------------------------------------------------------------ */
    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext())
            .setTitle("Permesso Fotocamera Necessario")
            .setMessage("Questa funzione richiede l'accesso alla fotocamera.")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(cameraPermission)
            }
            .setNegativeButton("Annulla", null)
            .show()
    }
}
