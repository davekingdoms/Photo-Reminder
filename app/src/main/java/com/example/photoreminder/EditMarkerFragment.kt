package com.example.photoreminder

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentEditMarkerBinding
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class EditMarkerFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentEditMarkerBinding? = null
    private val binding get() = _binding!!
    private val args: EditMarkerFragmentArgs by navArgs()
    private lateinit var googleMap: GoogleMap
    private lateinit var photoMarker: Marker
    private var currentMarker: MarkerEntity? = null

    private val viewModel: MarkerViewModel by lazy {
        val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
        MarkerViewModelFactory(MarkerRepository(dao))
            .create(MarkerViewModel::class.java)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditMarkerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.EditMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.mapViewEditFragment.onCreate(savedInstanceState)
        binding.mapViewEditFragment.getMapAsync(this)
        binding.cancelEditFragmentButton.setOnClickListener{findNavController().navigateUp()}
        setUpSpinners()
        loadMarker(args.markerId)
    }

    private fun loadMarker(id: String){
        lifecycleScope.launch(Dispatchers.IO){
            val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
            val marker = dao.getMarkerById(id)
            if (marker != null){
                currentMarker = marker
                launch(Dispatchers.Main){
                    fillForm(marker)
                }
            } else {
                launch(Dispatchers.Main){
                    Toast.makeText(requireContext(), "Marker not found", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            }
        }

    }

    @SuppressLint("SetTextI18n")
    private fun fillForm(m: MarkerEntity){
        binding.namePhotoEditFragmentTextView.text = m.title

        fun Spinner.select(value: String?) {
            val idx = (0 until adapter.count).firstOrNull {
                adapter.getItem(it)?.toString()?.equals(value, true) == true
            } ?: 0
            setSelection(idx)
        }

        binding.genreEditFragmentSpinner.select(m.genre)
        binding.shutterSpeedEditFragmentSpinner.select(m.shutterSpeed)
        binding.fStopEditFragmentSpinner.select(m.aperture)
        binding.isoEditFragmentSpinner.select(m.iso)
        binding.focalLengthEditFragmentEditText.setText(m.focalLength.toString())
        binding.tagEditFragmentEditText.setText(m.tag)
        binding.noteEditFragmentEditText.setText(m.notes)
        binding.rotationEditSlider.value = m.angle
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
                requireContext(),
                arrayId,
                android.R.layout.simple_spinner_item
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = it
            }
        }

        // cambia l'icona quando selezioni il genere
        binding.genreEditFragmentSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, v: View?, pos: Int, id: Long) {
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
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
    }

    private fun drawMarker(m: MarkerEntity) {
        val bmp = Bitmap.createScaledBitmap(
            BitmapFactory.decodeResource(resources, R.drawable.icon_fov),
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

    /* ─────────────────────────── salva modifiche ────────────────────────── */
    private fun updateMarker() {
        val base = currentMarker ?: return

        val updated = base.copy(
            // title invariato
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


        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
            .setInputData(workDataOf("isManual" to true))
            .build()

        val wm = WorkManager.getInstance(requireContext())
        wm.enqueue(req)

        wm.getWorkInfoByIdLiveData(req.id).observe(viewLifecycleOwner) { info ->
            info?.let {
                if (it.state.isFinished) {
                    val msg = it.outputData.getString("message")
                        ?: it.outputData.getString("errorMessage")
                    if (!msg.isNullOrBlank()) Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()

                    val action = EditMarkerFragmentDirections
                        .actionEditMarkerFragmentToMapsFragment(updated.lat.toFloat(),updated.lng.toFloat())
                    findNavController().navigate(action)
                }
            }
        }
    }



    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false

        currentMarker?.let { drawMarker(it) }
        binding.rotationEditSlider.addOnChangeListener { _, value, _ ->
            if (::photoMarker.isInitialized) photoMarker.rotation = value
        }
        binding.mapTypeEditFAB.setOnClickListener {
            googleMap.mapType = if (googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL)
                GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        }
        binding.threeDimensionEditFAB.setOnClickListener {
            googleMap.isBuildingsEnabled = !googleMap.isBuildingsEnabled
        }
        binding.saveEditButton.setOnClickListener { updateMarker() }
    }



}