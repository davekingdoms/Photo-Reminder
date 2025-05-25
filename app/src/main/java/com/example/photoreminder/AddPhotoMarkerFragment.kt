package com.example.photoreminder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.photoreminder.databinding.FragmentAddPhotoMarkerBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlin.math.roundToInt

class AddPhotoMarkerFragment : Fragment(), OnMapReadyCallback {

    /* -------------------- View binding & nav args ------------------------ */
    private var _binding: FragmentAddPhotoMarkerBinding? = null
    private val binding get() = _binding!!

    private val args: AddPhotoMarkerFragmentArgs by navArgs()

    /* -------------------- Map / marker state ----------------------------- */
    private lateinit var latLng: LatLng
    private lateinit var googleMap: GoogleMap
    private lateinit var photoMarker: Marker

    /* -------------------------------------------------------------------- */
    /*                              LIFECYCLE                               */
    /* -------------------------------------------------------------------- */

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

        /* Toolbar “up” */
        binding.addPhotoMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        /* Coordinate dal fragment precedente */
        latLng = LatLng(args.lat.toDouble(), args.lng.toDouble())

        /* MapView */
        binding.mapViewMarkerDetail.onCreate(savedInstanceState)
        binding.mapViewMarkerDetail.getMapAsync(this)

        /* Spinner & validazione */
        setupSpinners()
        binding.saveButton.setOnClickListener { saveMarker() }
    }

    /* -------------------------------------------------------------------- */
    /*                          UI INITIALISATION                           */
    /* -------------------------------------------------------------------- */

    /** Popola gli spinner e collega genere ↔ anteprima icona */
    private fun setupSpinners() {

        /* --- Genre spinner ------------------------------------------------ */
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.photography_genre,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.genreSpinner.adapter = adapter
        }

        /* Listener: aggiorna immagine all’item selezionato */
        binding.genreSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val drawableRes = when (position) {
                    0 -> R.drawable.street_icon
                    1 -> R.drawable.drone_icon
                    2 -> R.drawable.landscape_icon
                    3 -> R.drawable.seascape_icon
                    4 -> R.drawable.cityscape_icon
                    5 -> R.drawable.woodland_icon
                    6 -> R.drawable.astro_icon
                    7 -> R.drawable.star_trail_icon
                    else -> R.drawable.street_icon // Fallback
                }
                binding.genreIconImageView.setImageResource(drawableRes)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        /* --- Shutter speed spinner --------------------------------------- */
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.shutter_speeds,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.shutterSpeedSpinner.adapter = adapter
        }

        /* --- F-stop spinner ---------------------------------------------- */
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.fstop_values,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.fStopSpinner.adapter = adapter
        }

        /* --- ISO spinner -------------------------------------------------- */
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.iso_values,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.isoSpinner.adapter = adapter
        }
    }

    /** Check: il nome è obbligatorio */
    private fun saveMarker() {
        val name = binding.namePhotoEditTextView.text.toString().trim()
        if (name.isEmpty()) {
            binding.namePhotoEditTextView.error = "Required"
            binding.namePhotoEditTextView.requestFocus()
            Toast.makeText(requireContext(), "Name is required", Toast.LENGTH_SHORT).show()
            return
        }

        // TODO: logica di salvataggio
        Toast.makeText(requireContext(), "Marker saved!", Toast.LENGTH_SHORT).show()
    }

    /* -------------------------------------------------------------------- */
    /*                               MAP                                     */
    /* -------------------------------------------------------------------- */

    override fun onMapReady(map: GoogleMap) {
        googleMap = map.apply {
            /* UI settings */
            uiSettings.isZoomControlsEnabled     = true
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.isCompassEnabled          = false

            /* Camera */
            moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 20f))

            /* Crea icona ridimensionata */
            val sizePx = (48 * resources.displayMetrics.density).roundToInt()
            val iconBmp = Bitmap.createScaledBitmap(
                BitmapFactory.decodeResource(resources, R.drawable.icon_fov),
                sizePx, sizePx, false
            )

            /* Marker */
            val markerOptions = MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.fromBitmap(iconBmp))
                .anchor(0.5f, 0.5f)
                .rotation(binding.rotationSlider.value)
                .flat(true)

            photoMarker = addMarker(markerOptions)!!
        }

        /* Slider → rotazione */
        binding.rotationSlider.addOnChangeListener { _, value, _ ->
            photoMarker.rotation = value
        }

        /* 3D buildings toggle */
        binding.threeDimensionFAB2.setOnClickListener {
            googleMap.isBuildingsEnabled = !googleMap.isBuildingsEnabled
        }

        /* Map type toggle */
        binding.mapTypeFAB2.setOnClickListener {
            googleMap.mapType = when (googleMap.mapType) {
                GoogleMap.MAP_TYPE_NORMAL -> GoogleMap.MAP_TYPE_HYBRID
                else                      -> GoogleMap.MAP_TYPE_NORMAL
            }
        }

        /* Nessuna finestra info default */
        googleMap.setOnMarkerClickListener { true }
    }

    /* -------------------------------------------------------------------- */
    /*                         MAPVIEW LIFECYCLE                             */
    /* -------------------------------------------------------------------- */

    override fun onStart() {
        super.onStart()
        binding.mapViewMarkerDetail.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapViewMarkerDetail.onResume()
    }

    override fun onPause() {
        binding.mapViewMarkerDetail.onPause()
        super.onPause()
    }

    override fun onStop() {
        binding.mapViewMarkerDetail.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapViewMarkerDetail.onLowMemory()
    }

    override fun onDestroyView() {
        binding.mapViewMarkerDetail.onDestroy()
        _binding = null
        super.onDestroyView()
    }
}
