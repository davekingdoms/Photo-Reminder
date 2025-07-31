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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.photoreminder.databinding.FragmentCameraBinding
import java.io.File
import java.util.*

class CameraFragment : Fragment() {

    companion object {
        const val RESULT_KEY  = "photosResult"
        const val BUNDLE_URIS = "uris"
    }

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private lateinit var imageCapture: ImageCapture

    private val cameraPermission = Manifest.permission.CAMERA
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(
                requireContext(),
                "Camera permission not granted",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val pickImagesLauncher =
        registerForActivityResult(PickMultipleVisualMedia()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) returnWithUris(uris)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        when {
            ContextCompat.checkSelfPermission(requireContext(), cameraPermission)
                    == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(cameraPermission) ->
                showPermissionRationale()
            else ->
                requestPermissionLauncher.launch(cameraPermission)
        }

        binding.floatingActionButton2.setOnClickListener { launchPicker() }
        binding.floatingActionButton.setOnClickListener { takePhoto() }
        binding.cameraToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun launchPicker() {
        val req = PickVisualMediaRequest.Builder()
            .setMediaType(PickVisualMedia.ImageOnly)
            .build()
        pickImagesLauncher.launch(req)
    }

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
        parentFragmentManager.popBackStack()
    }

    private fun startCamera() {
        val provFuture = ProcessCameraProvider.getInstance(requireContext())
        provFuture.addListener({
            val provider = provFuture.get()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }

            imageCapture = ImageCapture.Builder()
                .setTargetRotation(binding.previewView.display.rotation)
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

    private fun takePhoto() {
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
                        "Error taking photo: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                    returnWithUris(listOf(file.toUri()))
                }
            }
        )
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(requireContext(),R.style.AlertDialogCustom)
            .setTitle("Camera permission required")
            .setMessage("This app needs camera permission to function")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(cameraPermission)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
