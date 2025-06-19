package com.example.photoreminder

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.*
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentEditMarkerBinding
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import androidx.core.graphics.scale

class EditMarkerFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentEditMarkerBinding? = null
    private val binding get() = _binding!!
    private val args: EditMarkerFragmentArgs by navArgs()

    private lateinit var googleMap: GoogleMap
    private lateinit var photoMarker: Marker
    private var currentMarker: MarkerEntity? = null

    /* --- ViewModel “rapido” (senza DI) --- */
    private val viewModel: MarkerViewModel by lazy {
        val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
        MarkerViewModelFactory(MarkerRepository(dao))
            .create(MarkerViewModel::class.java)
    }

    /* -------------------------- lifecycle -------------------------- */

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditMarkerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* toolbar back */
        binding.EditMarkerToolBar.setNavigationOnClickListener { findNavController().navigateUp() }

        /* mappa */
        binding.mapViewEditFragment.onCreate(savedInstanceState)
        binding.mapViewEditFragment.getMapAsync(this)

        /* pulsanti */
        binding.cancelEditFragmentButton.setOnClickListener { findNavController().navigateUp() }
        binding.saveEditButton.setOnClickListener { updateMarker() }

        setUpSpinners()
        loadMarker(args.markerId)

        /* ---------- observer unico sul worker ---------- */
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(MarkerSyncWorker.QUEUE_MANUAL)
            .observe(viewLifecycleOwner) { infos ->
                val info = infos.firstOrNull() ?: return@observe
                if (info.state.isFinished) {
                    val data = info.outputData
                    val msg = data.getString("message")
                        ?: data.getString("errorMessage")
                    if (!msg.isNullOrBlank())
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

                    /* torna alla mappa centrata sul marker appena editato */
                    WorkManager.getInstance(requireContext()).pruneWork()
                    currentMarker?.let {
                        val action =
                            EditMarkerFragmentDirections
                                .actionEditMarkerFragmentToMapsFragment(
                                    it.lat.toString(),
                                    it.lng.toString()
                                )
                        findNavController().navigate(action)
                    } ?: findNavController().navigateUp()
                }
            }
    }

    /* -------------------------- data load -------------------------- */

    private fun loadMarker(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
            val marker = dao.getMarkerById(id)
            if (marker != null) {
                currentMarker = marker
                launch(Dispatchers.Main) { fillForm(marker) }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Marker not found", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            }
        }
    }

    /* -------------------------- UI helpers ------------------------- */

    @SuppressLint("SetTextI18n")
    private fun fillForm(m: MarkerEntity) = with(binding) {
        namePhotoEditFragmentTextView.text = m.title

        fun Spinner.select(value: String?) {
            val idx = (0 until adapter.count).firstOrNull {
                adapter.getItem(it)?.toString()?.equals(value, true) == true
            } ?: 0
            setSelection(idx)
        }

        genreEditFragmentSpinner.select(m.genre)
        shutterSpeedEditFragmentSpinner.select(m.shutterSpeed)
        fStopEditFragmentSpinner.select(m.aperture)
        isoEditFragmentSpinner.select(m.iso)
        focalLengthEditFragmentEditText.setText(m.focalLength.toString())
        tagEditFragmentEditText.setText(m.tag)
        noteEditFragmentEditText.setText(m.notes)
        rotationEditSlider.value = m.angle
    }

    private fun setUpSpinners() {
        val list = listOf(
            R.array.photography_genre to binding.genreEditFragmentSpinner,
            R.array.shutter_speeds     to binding.shutterSpeedEditFragmentSpinner,
            R.array.fstop_values       to binding.fStopEditFragmentSpinner,
            R.array.iso_values         to binding.isoEditFragmentSpinner
        )
        list.forEach { (arrayId, spinner) ->
            ArrayAdapter.createFromResource(
                requireContext(), arrayId, android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = it
            }
        }

        /* icona dinamica genere */
        binding.genreEditFragmentSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>, v: View?, pos: Int, id: Long) {
                    val iconRes = when (pos) {
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
                    binding.genreIconEditFragmentImageView.setImageResource(iconRes)
                }
                override fun onNothingSelected(p: AdapterView<*>) {}
            }
    }

    /* ----------------------- salvataggio --------------------------- */

    private fun updateMarker() {
        val base = currentMarker ?: return

        /* raccogli valori form */
        val updated = base.copy(
            genre        = binding.genreEditFragmentSpinner.selectedItem as String,
            shutterSpeed = binding.shutterSpeedEditFragmentSpinner.selectedItem as String,
            aperture     = binding.fStopEditFragmentSpinner.selectedItem as String,
            iso          = binding.isoEditFragmentSpinner.selectedItem as String,
            focalLength  = binding.focalLengthEditFragmentEditText.text.toString().toIntOrNull() ?: 0,
            tag          = binding.tagEditFragmentEditText.text.toString().trim().takeIf { it.isNotBlank() },
            notes        = binding.noteEditFragmentEditText.text.toString().takeIf { it.isNotBlank() },
            angle        = binding.rotationEditSlider.value,
            updatedAt    = System.currentTimeMillis(),
            syncStatus   = SyncStatus.DIRTY
        )

        viewModel.updateMarker(updated)

        /* --- enqueue sync serializzata --- */
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
            MarkerSyncWorker.QUEUE_MANUAL,
            ExistingWorkPolicy.KEEP,
            req
        )

        Toast.makeText(requireContext(), "Salvato: sincronizzazione avviata", Toast.LENGTH_SHORT).show()
    }

    /* --------------------------- MAPPA ----------------------------- */

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            mapType = GoogleMap.MAP_TYPE_HYBRID
            uiSettings.isCompassEnabled = false
            uiSettings.isMyLocationButtonEnabled = false
        }

        currentMarker?.let { drawMarker(it) }

        binding.rotationEditSlider.addOnChangeListener { _, value, _ ->
            if (::photoMarker.isInitialized) photoMarker.rotation = value
        }
        binding.mapTypeEditFAB.setOnClickListener {
            googleMap.mapType =
                if (googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL)
                    GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        }
        binding.threeDimensionEditFAB.setOnClickListener {
            googleMap.isBuildingsEnabled = !googleMap.isBuildingsEnabled
        }
    }

    private fun drawMarker(m: MarkerEntity) {
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.icon_fov).scale(
            (48 * resources.displayMetrics.density).toInt(),
            (48 * resources.displayMetrics.density).toInt(),
            false
        )
        googleMap.clear()
        photoMarker = googleMap.addMarker(
            MarkerOptions()
                .position(LatLng(m.lat, m.lng))
                .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                .anchor(0.5f, 0.5f)
                .rotation(m.angle)
                .flat(true)
        )!!
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(m.lat, m.lng), 20f))
    }

    /* ---------------------- lifecycle mapview ---------------------- */

    override fun onResume()  { super.onResume();  binding.mapViewEditFragment.onResume() }
    override fun onPause()   { binding.mapViewEditFragment.onPause();   super.onPause() }
    override fun onDestroy() { binding.mapViewEditFragment.onDestroy(); super.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapViewEditFragment.onLowMemory() }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
