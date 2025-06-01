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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPhotoMarkerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.addPhotoMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        latLng = LatLng(args.lat.toDouble(), args.lng.toDouble())
        binding.mapViewMarkerDetail.onCreate(savedInstanceState)
        binding.mapViewMarkerDetail.getMapAsync(this)

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
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val icon = when (position) {
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
            R.array.fstop_values to binding.fStopSpinner,
            R.array.iso_values to binding.isoSpinner
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
        if (title.isEmpty()) {
            binding.namePhotoEditTextView.error = "Required"
            binding.namePhotoEditTextView.requestFocus()
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        val latitude       = latLng.latitude
        val longitude      = latLng.longitude
        val genre          = binding.genreSpinner.selectedItem as String
        val shutterSpeed   = binding.shutterSpeedSpinner.selectedItem as String
        val aperture       = binding.fStopSpinner.selectedItem as String
        val iso            = binding.isoSpinner.selectedItem as String
        val focalLength    = binding.focalLengthEditText.text.toString().toIntOrNull() ?: 0
        val tag            = binding.tagEditText.text.toString().trim().takeIf { it.isNotBlank() }
        val notes          = binding.noteEditText.text.toString().takeIf { it.isNotBlank() }
        val angle          = binding.rotationSlider.value

        viewLifecycleOwner.lifecycleScope.launch {
            val username = DataStoreManager.getUsername(requireContext())
            if (username.isNullOrEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "User not found, please log in again",
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val marker = MarkerEntity(
                id           = UUID.randomUUID().toString(),
                username     = username,          // << cambiato
                lat          = latitude,
                lng          = longitude,
                title        = title,
                genre        = genre,
                shutterSpeed = shutterSpeed,
                aperture     = aperture,
                iso          = iso,
                focalLength  = focalLength,
                tag          = tag,
                notes        = notes,
                photoUrl     = null,
                angle        = angle,
                createdAt    = System.currentTimeMillis(),
                updatedAt    = System.currentTimeMillis(),
                syncStatus   = SyncStatus.LOCAL_ONLY
            )

            viewModel.addMarker(marker)

            /* Avvio sync manuale immediata */
            val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
                .setInputData(workDataOf("isManual" to true))
                .build()

            val wm = WorkManager.getInstance(requireContext())
            wm.enqueue(req)

            wm.getWorkInfoByIdLiveData(req.id)
                .observe(viewLifecycleOwner) { info ->
                    info?.let {
                        if (it.state.isFinished) {
                            val msg = it.outputData.getString("message")
                                ?: it.outputData.getString("errorMessage")
                            if (!msg.isNullOrBlank()) {
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

            findNavController().navigateUp()
        }
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
    }

    /* -------------------------- ciclo vita MapView -------------------------- */
    override fun onStart()  { super.onStart();  binding.mapViewMarkerDetail.onStart() }
    override fun onResume() { super.onResume(); binding.mapViewMarkerDetail.onResume() }
    override fun onPause()  { binding.mapViewMarkerDetail.onPause();  super.onPause() }
    override fun onStop()   { binding.mapViewMarkerDetail.onStop();   super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapViewMarkerDetail.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapViewMarkerDetail.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.mapViewMarkerDetail.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}
