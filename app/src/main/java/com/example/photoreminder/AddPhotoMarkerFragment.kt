package com.example.photoreminder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.*
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.model.PhotoRef
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentAddPhotoMarkerBinding
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import androidx.core.graphics.scale
import com.example.photoreminder.data.sync.PhotoSyncWorker

class AddPhotoMarkerFragment : Fragment(), OnMapReadyCallback {

    /* ---------- binding & args ---------- */
    private var _binding: FragmentAddPhotoMarkerBinding? = null
    private val binding get() = _binding!!
    private val args: AddPhotoMarkerFragmentArgs by navArgs()

    /* ---------- mappa ---------- */
    private lateinit var latLng: LatLng
    private lateinit var googleMap: GoogleMap
    private lateinit var photoMarker: Marker

    /* ---------- ViewModel ---------- */
    private val viewModel: MarkerViewModel by viewModels {
        MarkerViewModelFactory(
            MarkerRepository(
                MarkerDatabase.getDatabase(requireContext()).markerDao()
            )
        )
    }

    /* ---------- id marker OFFLINE già generato ---------- */
    private val markerId: String = UUID.randomUUID().toString()

    /* ---------- lista foto in memoria ---------- */
    private val photoRefs = mutableListOf<PhotoRef>()

    /* ================= lifecycle ================= */

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPhotoMarkerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* Toolbar back */
        binding.addPhotoMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        /* MAPPA */
        latLng = LatLng(args.lat.toDouble(), args.lng.toDouble())
        binding.mapViewMarker.onCreate(savedInstanceState)
        binding.mapViewMarker.getMapAsync(this)

        /* Spinners */
        setupSpinners()

        /* Ricevo le Uri dal CameraFragment */
        parentFragmentManager.setFragmentResultListener(
            CameraFragment.RESULT_KEY, viewLifecycleOwner
        ) { _, bundle ->
            val uris = bundle.getStringArrayList(CameraFragment.BUNDLE_URIS)
                ?.map(Uri::parse)
                ?: return@setFragmentResultListener
            Log.d("AddPhoto", "Ricevute ${uris.size} uri dal picker: $uris")

            lifecycleScope.launch {
                for (u in uris) {
                    val thumb = createThumbnail(u, markerId)   // usa id specifico, NON più "tmp"
                    photoRefs += PhotoRef(
                        localUri = u.toString(),
                        thumbPath = thumb,
                        synced = false
                    )
                }
                Toast.makeText(requireContext(),
                    "Aggiunte ${uris.size} foto", Toast.LENGTH_SHORT).show()
            }
        }

        /* Pulsante per aprire CameraFragment */
        binding.addPhotoButton.setOnClickListener {
            val action = AddPhotoMarkerFragmentDirections
                .actionAddPhotoMarkerFragmentToCameraFragment()
            findNavController().navigate(action)
        }

        /* Salva / annulla */
        binding.saveButton.setOnClickListener { saveMarker() }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
    }

    /* ================= SPINNERS ================= */
    private fun setupSpinners() {
        ArrayAdapter.createFromResource(
            requireContext(), R.array.photography_genre,
            android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.genreSpinner.adapter = it
        }

        binding.genreSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    val icon = when (pos) {
                        0 -> R.drawable.street_icon
                        1 -> R.drawable.drone_icon
                        2 -> R.drawable.landscape_icon
                        3 -> R.drawable.seascape_icon
                        4 -> R.drawable.cityscape_icon
                        5 -> R.drawable.woodland_icon
                        6 -> R.drawable.astro_icon
                        7 -> R.drawable.star_trail_icon
                        else -> R.drawable.street_icon
                    }
                    binding.genreIconImageView.setImageResource(icon)
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }

        arrayOf(
            R.array.shutter_speeds to binding.shutterSpeedSpinner,
            R.array.fstop_values   to binding.fStopSpinner,
            R.array.iso_values     to binding.isoSpinner
        ).forEach { (arrayId, spinner) ->
            ArrayAdapter.createFromResource(
                requireContext(), arrayId, android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = it
            }
        }
    }

    /* ================ Salvataggio ================ */
    private fun saveMarker() {
        val title = binding.namePhotoEditTextView.text.toString().trim()
        if (title.isBlank()) {
            binding.namePhotoEditTextView.error = "Required"
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val username = DataStoreManager.getUsername(requireContext())
            if (username.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "User not found, please log in again", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val marker = MarkerEntity(
                id           = markerId,
                username     = username,
                lat          = latLng.latitude,
                lng          = latLng.longitude,
                title        = title,
                genre        = binding.genreSpinner.selectedItem as String,
                shutterSpeed = binding.shutterSpeedSpinner.selectedItem as String,
                aperture     = binding.fStopSpinner.selectedItem as String,
                iso          = binding.isoSpinner.selectedItem as String,
                focalLength  = binding.focalLengthEditText.text.toString().toIntOrNull() ?: 0,
                tag          = binding.tagEditText.text.toString().trim().takeIf { it.isNotBlank() },
                notes        = binding.noteEditText.text.toString().takeIf { it.isNotBlank() },
                photos       = photoRefs.toList(),
                angle        = binding.rotationSlider.value,
                createdAt    = System.currentTimeMillis(),
                updatedAt    = System.currentTimeMillis(),
                syncStatus   = SyncStatus.LOCAL_ONLY
            )

            viewModel.addMarker(marker)
            enqueueSync()
            Toast.makeText(requireContext(),
                "Marker salvato; sync avviata", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    /* ---------- enqueue sync manuale ---------- */
    private fun enqueueSync() {
        val ctx = requireContext()
        val wm = WorkManager.getInstance(ctx)
        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
            .setInputData(workDataOf("isManual" to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        val photoReq = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()
        wm.beginUniqueWork(MarkerSyncWorker.QUEUE_MANUAL,
            ExistingWorkPolicy.KEEP,
            req).then(photoReq).enqueue()

    }

    /* ================= MAPPA ================= */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 20f))

        val bmp = BitmapFactory.decodeResource(resources, R.drawable.icon_fov).scale(
            (48 * resources.displayMetrics.density).roundToInt(),
            (48 * resources.displayMetrics.density).roundToInt(),
            false
        )

        photoMarker = map.addMarker(
            MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                .anchor(0.5f, 0.5f)
                .rotation(binding.rotationSlider.value)
                .flat(true)
        )!!

        binding.rotationSlider.addOnChangeListener { _, value, _ ->
            photoMarker.rotation = value
        }

        binding.mapTypeFAB2.setOnClickListener {
            map.mapType = if (map.mapType == GoogleMap.MAP_TYPE_NORMAL)
                GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        }
        binding.threeDimensionFAB2.setOnClickListener {
            map.isBuildingsEnabled = !map.isBuildingsEnabled
        }
    }

    /* ============== Thumbnail helper ============== */
    private suspend fun createThumbnail(uri: Uri, markerId: String): String =
        withContext(Dispatchers.IO) {

            /* ---- new: height in px from 170 dp ---- */
            val targetHpx = (170 * resources.displayMetrics.density).roundToInt()

            /*   keep the rest identical, using targetHpx instead of 200  */
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            opts.inSampleSize = (opts.outHeight / targetHpx).coerceAtLeast(1)
            opts.inJustDecodeBounds = false

            val bmp = requireContext().contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: error("decode failed")

            val ratio   = targetHpx.toFloat() / bmp.height
            val widthPx = (bmp.width * ratio).roundToInt()
            val thumb   = bmp.scale(widthPx, targetHpx)

            val dir  = File(requireContext().filesDir, "thumbnails/$markerId").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }

            bmp.recycle(); if (thumb !== bmp) thumb.recycle()
            file.absolutePath
        }


    /* ============ lifecycle MapView ============ */
    override fun onStart()  { super.onStart();  binding.mapViewMarker.onStart() }
    override fun onResume() { super.onResume(); binding.mapViewMarker.onResume() }
    override fun onPause()  { binding.mapViewMarker.onPause();  super.onPause() }
    override fun onStop()   { binding.mapViewMarker.onStop();   super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapViewMarker.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapViewMarker.onSaveInstanceState(outState)
    }
    override fun onDestroyView() {
        binding.mapViewMarker.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}
