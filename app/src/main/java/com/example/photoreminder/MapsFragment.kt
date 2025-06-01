package com.example.photoreminder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentMapsBinding
import com.example.photoreminder.ui.adapter.TagAdapter
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import kotlinx.coroutines.launch
import java.util.Locale

class MapsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    private lateinit var googleMap: GoogleMap
    private val fineLocation = android.Manifest.permission.ACCESS_FINE_LOCATION
    private enum class LocationMode { LAST_KNOWN, CURRENT_BALANCED, CURRENT_HIGH }
    private lateinit var searchLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    private val fused by lazy {
        LocationServices.getFusedLocationProviderClient(requireContext())
    }

    // ViewModel + Repository
    private val viewModel: MarkerViewModel by viewModels {
        MarkerViewModelFactory(
            MarkerRepository(
                MarkerDatabase.getDatabase(requireContext()).markerDao()
            )
        )
    }

    // Manteniamo:
    //  • allMarkers = lista completa di MarkerEntity in Room
    //  • selectedTag = tag attualmente usato per filtrare (di default "All")
    private var allMarkers: List<MarkerEntity> = emptyList()
    private var selectedTag: String = "All"
    private lateinit var tagAdapter: TagAdapter

    // Flag per sapere quando la mappa è pronta
    private var mapIsReady: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        // Inizializzo Places se non fatto
        if (!Places.isInitialized()) {
            Places.initialize(requireContext().applicationContext, BuildConfig.MAPS_API_KEY)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1) Inizializzo MapView
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        binding.mapMaterialToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // 2) Imposto RecyclerView orizzontale per i tag/ generi
        tagAdapter = TagAdapter(listOf("All"), "All") { tag ->
            selectedTag = tag
            if (mapIsReady) {
                drawMarkersOnMap()
            }
        }
        binding.tagsRecyclerView.apply {
            layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            adapter = tagAdapter
        }

        // 3) Preparo Places Autocomplete per la barra di ricerca
        searchLauncher = registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == android.app.Activity.RESULT_OK) {
                val data = result.data ?: return@registerForActivityResult
                val place = Autocomplete.getPlaceFromIntent(data)
                place.latLng?.let { latLng ->
                    // Nascondo la tastiera
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE)
                            as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(binding.placesEditText.windowToken, 0)

                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 19f))
                    binding.placesEditText.setText(place.name)
                }
            } else if (result.resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = Autocomplete.getStatusFromIntent(result.data!!)
                Toast.makeText(
                    requireContext(),
                    "Autocomplete error: ${status.statusMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        binding.placesEditText.apply {
            isFocusable = false
            isClickable = true
            setOnClickListener {
                val fields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS
                )
                val intent = Autocomplete.IntentBuilder(
                    AutocompleteActivityMode.OVERLAY,
                    fields
                ).build(requireContext())
                searchLauncher.launch(intent)
            }
        }

        // 4) Osserva i marker da Room
        viewModel.markers.observe(viewLifecycleOwner) { list ->
            allMarkers = list

            // 4a) ricavo tutti i generi e tag unici
            val genres = allMarkers
                .mapNotNull { it.genre.ifBlank { null } }
                .map { it.lowercase(Locale.getDefault()) }
            val tags =
                allMarkers.mapNotNull { it.tag?.lowercase(Locale.getDefault()) }
            val unique = (genres + tags).toSet().map { it.capitalize(Locale.getDefault()) }

            // Lista "All" + valori unici ordinati
            val displayTags = listOf("All") + unique.sorted()
            tagAdapter.updateTags(displayTags)

            // 4b) disegno marker per il tag corrente solo se la mappa è già pronta
            if (mapIsReady) {
                drawMarkersOnMap()
            }
        }
    }

    /** Chiamato quando la MapView è pronta */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapIsReady = true

        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        if (hasLocationPermission()) {
            enableMyLocation(LocationMode.LAST_KNOWN)
        } else {
            askLocationPermission()
        }

        // Ascolto long-press per aggiungere un marker (nav a AddPhotoMarkerFragment)
        googleMap.setOnMapLongClickListener {
            val action = MapsFragmentDirections
                .actionMapsFragmentToAddPhotoMarkerFragment(
                    it.latitude.toFloat(), it.longitude.toFloat()
                )
            findNavController().navigate(action)
        }

        // Traccio i marker già disponibili su allMarkers
        drawMarkersOnMap()
    }

    /** Ridisegna i marker filtrati da [selectedTag] */
    private fun drawMarkersOnMap() {
        if (!::googleMap.isInitialized) return
        googleMap.clear()

        val toDraw = if (selectedTag.equals("All", ignoreCase = true)) {
            allMarkers
        } else {
            allMarkers.filter { marker ->
                val genreMatch = marker.genre.equals(selectedTag, ignoreCase = true)
                val tagMatch = marker.tag?.equals(selectedTag, ignoreCase = true) == true
                genreMatch || tagMatch
            }
        }

        toDraw.forEach { m ->
            googleMap.addMarker(
                MarkerOptions()
                    .position(LatLng(m.lat, m.lng))
                    .title(m.title)
                    .icon(getIconForGenre(m.genre))
                    .anchor(0.5f, 1f)
            )
        }
    }

    /** Restituisce un BitmapDescriptor ridimensionato (48dp) per il genere */
    private fun getIconForGenre(genre: String?): BitmapDescriptor {
        val resId = when (genre?.lowercase(Locale.getDefault())) {
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

        val targetPx = (48 * resources.displayMetrics.density).toInt()

        val drawable = ResourcesCompat.getDrawable(resources, resId, null)!!
        val rawBmp = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(rawBmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)

        val scaledBmp = Bitmap.createScaledBitmap(rawBmp, targetPx, targetPx, true)
        return BitmapDescriptorFactory.fromBitmap(scaledBmp)
    }

    /* ------------------------- gestori posizione / permesso ------------------------- */
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(),
            fineLocation
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun askLocationPermission() {
        if (shouldShowRequestPermissionRationale(fineLocation)) {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Posizione necessaria")
                .setMessage("La tua posizione serve per mostrarti sulla mappa.")
                .setPositiveButton("Concedi") { _, _ ->
                    requestPermissionLauncher.launch(fineLocation)
                }
                .setNegativeButton("Chiudi") { _, _ ->
                    showFallback()
                }
                .show()
        } else {
            requestPermissionLauncher.launch(fineLocation)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation(mode: LocationMode) {
        if (!::googleMap.isInitialized) return
        googleMap.isMyLocationEnabled = true

        when (mode) {
            LocationMode.LAST_KNOWN -> {
                fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) moveCamera(loc.latitude, loc.longitude, 17f)
                    else enableMyLocation(LocationMode.CURRENT_BALANCED)
                }
            }
            LocationMode.CURRENT_BALANCED,
            LocationMode.CURRENT_HIGH -> {
                val priority = if (mode == LocationMode.CURRENT_BALANCED)
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY
                else
                    Priority.PRIORITY_HIGH_ACCURACY

                fused.getCurrentLocation(priority, CancellationTokenSource().token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) moveCamera(loc.latitude, loc.longitude, 20f)
                        else showFallback()
                    }
            }
        }
    }

    private fun moveCamera(lat: Double, lng: Double, zoom: Float) {
        val latLng = LatLng(lat, lng)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    private fun showFallback() {
        val unipr = LatLng(44.7651628, 10.3117204)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(unipr, 17f))
        Toast.makeText(requireContext(), "Position not available", Toast.LENGTH_LONG).show()
    }

    /* ------------------------- permission launcher ------------------------- */
    private val requestPermissionLauncher =
        registerForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                enableMyLocation(LocationMode.CURRENT_BALANCED)
            } else {
                if (!hasLocationPermission()) {
                    androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("Permission needed")
                        .setMessage("Your location is needed to show you on the map. Please, allow the permission from the app settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = android.net.Uri.fromParts("package", requireContext().packageName, null)
                            intent.data = uri
                            startActivity(intent)
                        }
                        .setNegativeButton("Close") { _, _ -> showFallback() }
                        .show()
                } else {
                    showFallback()
                }
            }
        }

    override fun onStart() { super.onStart(); binding.mapView.onStart() }
    override fun onResume() { super.onResume(); binding.mapView.onResume() }
    override fun onPause() { binding.mapView.onPause(); super.onPause() }
    override fun onStop() { binding.mapView.onStop(); super.onStop() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        binding.mapView.onDestroy()
        _binding = null
        super.onDestroyView()
    }

    // Ripetiamo enum LocationMode

}
