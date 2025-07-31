package com.example.photoreminder

import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentDetailPhotoBinding
import com.example.photoreminder.ui.adapter.ThumbnailAdapter
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
import kotlin.math.roundToInt
import androidx.core.graphics.scale

class DetailPhotoFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDetailPhotoBinding? = null
    private val binding get() = _binding!!
    private val args: DetailPhotoFragmentArgs by navArgs()

    private lateinit var googleMap: GoogleMap
    private var mapIsReady = false
    private lateinit var photoMarker: Marker

    private var currentMarker: MarkerEntity? = null

    private val thumbAdapter by lazy {
        ThumbnailAdapter { pr, _ ->
            val action =
                DetailPhotoFragmentDirections
                    .actionDetailPhotoFragmentToImageFragment(
                        remoteId  = pr.remoteId ?: "",
                        thumbPath = pr.thumbPath
                    )
            findNavController().navigate(action)
        }
    }

    private val viewModel: MarkerViewModel by lazy {
        val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
        MarkerViewModelFactory(MarkerRepository(dao))
            .create(MarkerViewModel::class.java)
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.detailPhotoMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.markerDetailMapView.onCreate(savedInstanceState)
        binding.markerDetailMapView.getMapAsync(this)

        binding.thumbRecyclerView.apply {
            adapter = thumbAdapter
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
        }

        loadMarkerDetails(args.id)

        setUpDeleteButton()
        setUpEditButton()
    }

    private fun loadMarkerDetails(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
            val marker = dao.getMarkerById(id)
            if (marker != null) {
                currentMarker = marker
                launch(Dispatchers.Main) {
                    populateFields(marker)
                    thumbAdapter.submit(marker.photos)
                    if (mapIsReady) drawMarkerOnMap(marker)
                }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Marker not found", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun populateFields(marker: MarkerEntity) = with(binding) {
        nameDetailFragmentTextView.text = marker.title

        genreDetailFragmentTextView.text = marker.genre
        val iconRes = when (marker.genre.lowercase()) {
            "street"     -> R.drawable.street_icon
            "drone"      -> R.drawable.drone_icon
            "landscape"  -> R.drawable.landscape_icon
            "seascape"   -> R.drawable.seascape_icon
            "cityscape"  -> R.drawable.cityscape_icon
            "woodland"   -> R.drawable.woodland_icon
            "astro"      -> R.drawable.astro_icon
            "star trail" -> R.drawable.star_trail_icon
            else         -> R.drawable.street_icon
        }
        genreIconDetailFragmentImageView.setImageDrawable(
            ResourcesCompat.getDrawable(resources, iconRes, null)
        )

        shutterTextView.text           = marker.shutterSpeed
        fStopDetailFragmentTextView.text = marker.aperture
        isoDetailFragmentTextView.text   = marker.iso
        focalLenDetailFragmentTextView.text = marker.focalLength.toString()
        tagDetailFragmentTextView.text     = marker.tag
        if (!marker.notes.isNullOrBlank()) {
            noteEditText.visibility = View.VISIBLE
            noteEditText.setText(marker.notes)
        }

    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapIsReady = true
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        currentMarker?.let { drawMarkerOnMap(it) }
        binding.mapTypeDetailFAB.setOnClickListener {
            googleMap.mapType =
                if (googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL)
                    GoogleMap.MAP_TYPE_HYBRID else GoogleMap.MAP_TYPE_NORMAL
        }
        binding.threeDimensionalDetailFab.setOnClickListener {
            googleMap.isBuildingsEnabled = !googleMap.isBuildingsEnabled
        }
    }

    private fun drawMarkerOnMap(marker: MarkerEntity) {
        val pos = LatLng(marker.lat, marker.lng)
        val bmp = BitmapFactory.decodeResource(resources, R.drawable.icon_fov).scale(
            (48 * resources.displayMetrics.density).roundToInt(),
            (48 * resources.displayMetrics.density).roundToInt(),
            false
        )
        googleMap.clear()
        photoMarker = googleMap.addMarker(
            MarkerOptions()
                .position(pos)
                .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                .anchor(0.5f, 0.5f)
                .rotation(marker.angle)
                .flat(true)
        )!!
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 18f))
    }

    private fun setUpDeleteButton() {
        binding.deleteButton.setOnClickListener {
            currentMarker?.let { marker ->
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogCustom)
                    .setTitle("Delete marker")
                    .setMessage(
                        "Are you sure you want to delete this marker?" +
                                "The deleted marker cannot be recovered."
                    )
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deleteMarker(marker.id)

                        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>().build()
                        val wm = WorkManager.getInstance(requireContext())
                        wm.enqueue(req)

                        wm.getWorkInfoByIdLiveData(req.id)
                            .observe(viewLifecycleOwner) { info ->
                                info?.let {
                                    if (it.state.isFinished) {
                                        val msg = it.outputData.getString("message")
                                            ?: it.outputData.getString("errorMessage")
                                        if (!msg.isNullOrBlank())
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG)
                                                .show()
                                        else if (it.state == androidx.work.WorkInfo.State.SUCCEEDED)
                                            Toast.makeText(
                                                requireContext(),
                                                "Marker deleted!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        else if (it.state == androidx.work.WorkInfo.State.FAILED)
                                            Toast.makeText(
                                                requireContext(),
                                                "Error deleting marker",
                                                Toast.LENGTH_SHORT
                                            ).show()

                                        findNavController().navigateUp()
                                    }
                                }
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }


    private fun setUpEditButton() {
        binding.editButton.setOnClickListener {
            currentMarker?.let {
                val action =
                    DetailPhotoFragmentDirections
                        .actionDetailPhotoFragmentToEditMarkerFragment(it.id)
                findNavController().navigate(action)
            }
        }
    }


    override fun onStart() { super.onStart(); binding.markerDetailMapView.onStart() }
    override fun onResume() { super.onResume(); binding.markerDetailMapView.onResume() }
    override fun onPause() { binding.markerDetailMapView.onPause(); super.onPause() }
    override fun onStop() { binding.markerDetailMapView.onStop(); super.onStop() }
    @Deprecated("Deprecated in Java")
    override fun onLowMemory() { super.onLowMemory(); binding.markerDetailMapView.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.markerDetailMapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.markerDetailMapView.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}
