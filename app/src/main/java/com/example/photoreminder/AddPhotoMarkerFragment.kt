package com.example.photoreminder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentAddPhotoMarkerBinding
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class AddPhotoMarkerFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentAddPhotoMarkerBinding? = null
    private val binding get() = _binding!!

    private val args: AddPhotoMarkerFragmentArgs by navArgs()
    private lateinit var latLng: LatLng
    private lateinit var googleMap: GoogleMap
    private lateinit var photoMarker: Marker

    private val viewModel: MarkerViewModel by viewModels {
        MarkerViewModelFactory(
            MarkerRepository(
                MarkerDatabase.getDatabase(requireContext()).markerDao()
            )
        )
    }

    /* -------------------------- lifecycle -------------------------- */

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPhotoMarkerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.addPhotoMarkerToolBar.setNavigationOnClickListener { findNavController().navigateUp() }

        latLng = LatLng(args.lat.toDouble(), args.lng.toDouble())
        binding.mapViewMarker.onCreate(savedInstanceState)
        binding.mapViewMarker.getMapAsync(this)

        setupSpinners()

        binding.saveButton.setOnClickListener { saveMarker() }
        binding.cancelButton.setOnClickListener { findNavController().navigateUp() }
    }

    /* ------------------------------- spinners ------------------------------- */
    private fun setupSpinners() {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.photography_genre,
            android.R.layout.simple_spinner_item
        ).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.genreSpinner.adapter = it
        }

        binding.genreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
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
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        arrayOf(
            R.array.shutter_speeds to binding.shutterSpeedSpinner,
            R.array.fstop_values   to binding.fStopSpinner,
            R.array.iso_values     to binding.isoSpinner
        ).forEach { (arrayId, spinner) ->
            ArrayAdapter.createFromResource(
                requireContext(),
                arrayId,
                android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = it
            }
        }
    }

    /* --------------------------- salvataggio marker -------------------------- */
    private fun saveMarker() {
        val title = binding.namePhotoEditTextView.text.toString().trim()
        if (title.isBlank()) {
            binding.namePhotoEditTextView.error = "Required"
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val username = DataStoreManager.getUsername(requireContext())
            if (username.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "User not found, please log in again", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val marker = MarkerEntity(
                id           = UUID.randomUUID().toString(),
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
                photoUrl     = null,
                angle        = binding.rotationSlider.value,
                createdAt    = System.currentTimeMillis(),
                updatedAt    = System.currentTimeMillis(),
                syncStatus   = SyncStatus.LOCAL_ONLY
            )

            viewModel.addMarker(marker)
            enqueueSync()                       // serializza sync manuale
            Toast.makeText(requireContext(), "Marker salvato; sync avviata", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()    // torniamo alla schermata precedente
        }
    }

    /* -------------- enqueue serializzata della sync manuale --------------- */
    private fun enqueueSync() {
        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
            .setInputData(workDataOf("isManual" to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS
            )
            .build()

        WorkManager.getInstance(requireContext()).enqueueUniqueWork(
            MarkerSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            req
        )
    }

    /* ----------------------------- Google Map ----------------------------- */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 20f))

        val bmp = Bitmap.createScaledBitmap(
            BitmapFactory.decodeResource(resources, R.drawable.icon_fov),
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

    /* -------------------------- ciclo vita MapView -------------------------- */
    override fun onStart()  { super.onStart();  binding.mapViewMarker.onStart()  }
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
