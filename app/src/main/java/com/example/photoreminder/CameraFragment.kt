package com.example.photoreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.*
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

class CameraFragment : Fragment() {

    /* ===== chiavi per il risultato ===== */
    companion object {
        const val RESULT_KEY  = "photosResult"
        const val BUNDLE_URIS = "uris"
    }

    private lateinit var previewView: PreviewView
    private lateinit var captureButton: FloatingActionButton
    private lateinit var galleryButton: FloatingActionButton

    /* ------------------------------------------------------------ */
    /* 1) PERMESSO FOTOCAMERA                                       */
    /* ------------------------------------------------------------ */
    private val cameraPermission = Manifest.permission.CAMERA
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(requireContext(),
                "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }

    /* ------------------------------------------------------------ */
    /* 2) PHOTO PICKER API 33+ (selezione multipla)                  */
    /* ------------------------------------------------------------ */
    private val pickImagesLauncher =
        registerForActivityResult(PickMultipleVisualMedia()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) returnWithUris(uris)
        }

    /* ------------------------------------------------------------ */
    /* 3) FALLBACK ACTION_OPEN_DOCUMENT (multi-select)               */
    /* ------------------------------------------------------------ */
    private val openDocLauncher =
        registerForActivityResult(StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val data = res.data ?: return@registerForActivityResult
                val uris = mutableListOf<Uri>()

                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount)
                        uris += clip.getItemAt(i).uri
                } ?: data.data?.let { uris += it }

                if (uris.isNotEmpty()) returnWithUris(uris)
            }
        }

    /* ------------------------------------------------------------ */
    /* Ciclo vita                                                   */
    /* ------------------------------------------------------------ */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?
    ): View =
        inflater.inflate(R.layout.fragment_camera, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        previewView    = view.findViewById(R.id.previewView)
        captureButton  = view.findViewById(R.id.floatingActionButton)
        galleryButton  = view.findViewById(R.id.floatingActionButton2)

        /* permesso fotocamera */
        when {
            ContextCompat.checkSelfPermission(requireContext(), cameraPermission)
                    == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(cameraPermission) -> showPermissionRationale()
            else -> requestPermissionLauncher.launch(cameraPermission)
        }

        /* click GALLERY → photo-picker */
        galleryButton.setOnClickListener { launchPicker() }

        /* captureButton rimane TODO */
    }

    /* ------------------------------------------------------------ */
    /* Avvio del picker                                             */
    /* ------------------------------------------------------------ */
    private fun launchPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            /* Nuovo Photo Picker: chiede solo immagini, multipla illimitata */
            val req = PickVisualMediaRequest.Builder()
                .setMediaType(PickVisualMedia.ImageOnly)
                .build()
            pickImagesLauncher.launch(req)
        } else {
            /* SAF multipla */
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            openDocLauncher.launch(intent)
        }
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
    /* Camera preview (com'era)                                     */
    /* ------------------------------------------------------------ */
    private fun startCamera() {
        val provFuture = ProcessCameraProvider.getInstance(requireContext())
        provFuture.addListener({
            val provider = provFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            provider.unbindAll()
            provider.bindToLifecycle(
                viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview
            )
        }, ContextCompat.getMainExecutor(requireContext()))
    }

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
