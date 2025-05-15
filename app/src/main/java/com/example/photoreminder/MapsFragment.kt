package com.example.photoreminder

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.photoreminder.databinding.FragmentMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import androidx.navigation.fragment.findNavController
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.activity.result.contract.ActivityResultContracts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.CameraPosition

class MapsFragment : Fragment(), OnMapReadyCallback {

    // ──────────────────────────── view binding ────────────────────────────
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    // ───────────────────────── location & map ─────────────────────────────
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap

    private enum class LocationMode { LAST_KNOWN, CURRENT_BALANCED, CURRENT_HIGH }


    private val fineLocation = Manifest.permission.ACCESS_FINE_LOCATION

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        fused = LocationServices.getFusedLocationProviderClient(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
        binding.threeDimensionFAB.visibility = View.INVISIBLE
        binding.mapMaterialToolbar.setNavigationOnClickListener{findNavController().navigateUp()}


    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID

        if (hasLocationPermission()) {
            enableMyLocation(LocationMode.LAST_KNOWN)
        } else {
            askLocationPermission()
        }

        googleMap.setOnCameraMoveListener {
            val bearing = googleMap.cameraPosition.bearing
            binding.compassFAB.animate().rotation(-bearing).start()
        }
        binding.currentLocationFAB.setOnClickListener {
            if (hasLocationPermission()) {
                enableMyLocation(LocationMode.CURRENT_HIGH)
            } else {
                askLocationPermission()   // lo richiede solo se non già concesso
            }
        }
        binding.mapTypeFAB.setOnClickListener{
            if(googleMap.mapType == GoogleMap.MAP_TYPE_NORMAL){
                googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
                binding.threeDimensionFAB.visibility = View.INVISIBLE

            }else{
                googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                binding.threeDimensionFAB.visibility = View.VISIBLE
            }
        }

        binding.threeDimensionFAB.setOnClickListener{
            googleMap.isBuildingsEnabled = !googleMap.isBuildingsEnabled
        }

        binding.compassFAB.setOnClickListener{
            val currentCameraPosition = googleMap.cameraPosition
            val northBearing = CameraPosition.builder()
                .target(currentCameraPosition.target)
                .zoom(currentCameraPosition.zoom)
                .tilt(currentCameraPosition.tilt)
                .bearing(0f)
                .build()
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(northBearing))
        }
    }


    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                enableMyLocation(LocationMode.CURRENT_BALANCED)
            } else {
                if (!hasLocationPermission()) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Permission needed")
                        .setMessage("Your location is needed to show you on the map. Please, allow the permission from the app settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            val uri = Uri.fromParts("package", requireContext().packageName, null)
                            intent.data = uri
                            startActivity(intent)
                        }.setNegativeButton("Close") { _, _ -> showFallback() }.show()
                } else{showFallback()}
            }
        }
    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            requireContext(), fineLocation
        ) == PackageManager.PERMISSION_GRANTED

    private fun askLocationPermission() {
        if (shouldShowRequestPermissionRationale(fineLocation)) {
            AlertDialog.Builder(requireContext())
                .setTitle("Posizione necessaria")
                .setMessage("La tua posizione serve per mostrarti sulla mappa.")
                .setPositiveButton("Concedi") { _, _ ->
                    requestPermissionLauncher.launch(fineLocation)
                }
                .setNegativeButton("Close") { _, _ ->
                    showFallback()
                }
                .show()
        } else { requestPermissionLauncher.launch(fineLocation)
        }
    }


    @SuppressLint("MissingPermission")
    private fun enableMyLocation(mode: LocationMode) {
        if (!::googleMap.isInitialized) return   // mappa non pronta

        googleMap.isMyLocationEnabled = true

        when (mode) {
            LocationMode.LAST_KNOWN -> { fused.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) moveCamera(loc, 17f) else {
                        enableMyLocation(LocationMode.CURRENT_BALANCED)
                    }
                }
            }
            LocationMode.CURRENT_BALANCED,
            LocationMode.CURRENT_HIGH   -> {
                val priority = when (mode) {
                    LocationMode.CURRENT_BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    LocationMode.CURRENT_HIGH     -> Priority.PRIORITY_HIGH_ACCURACY
                    else                          -> Priority.PRIORITY_PASSIVE

                }
                fused.getCurrentLocation(priority, CancellationTokenSource().token)
                    .addOnSuccessListener { loc ->
                        if (loc != null) moveCamera(loc, 20f) else showFallback()
                    }
            }
        }
    }

    // ───────────────────────── camera helper ──────────────────────────────
    private fun moveCamera(loc: Location, zoom: Float) {
        val latLng = LatLng(loc.latitude, loc.longitude)
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom))
    }

    private fun showFallback() {
        val unipr = LatLng(44.7651628, 10.3117204)
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(unipr, 17f))
        Toast.makeText(requireContext(), "Position not available", Toast.LENGTH_LONG).show()
    }


    override fun onStart()        { super.onStart();  binding.mapView.onStart() }
    override fun onResume()       { super.onResume(); binding.mapView.onResume() }
    override fun onPause()        { binding.mapView.onPause();  super.onPause() }
    override fun onStop()         { binding.mapView.onStop();   super.onStop() }
    override fun onLowMemory()    { super.onLowMemory(); binding.mapView.onLowMemory() }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.mapView.onDestroy()
        _binding = null
    }
}
