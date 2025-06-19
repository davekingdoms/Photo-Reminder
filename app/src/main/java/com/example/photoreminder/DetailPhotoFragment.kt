package com.example.photoreminder

import android.annotation.SuppressLint
import android.graphics.Bitmap
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

class DetailPhotoFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDetailPhotoBinding? = null
    private val binding get() = _binding!!
    private val args: DetailPhotoFragmentArgs by navArgs()
    private lateinit var googleMap: GoogleMap
    private var mapIsReady: Boolean = false
    private lateinit var photoMarker: Marker

    // Il marker caricato dal database
    private var currentMarker: MarkerEntity? = null
    private val thumbAdapter by lazy { ThumbnailAdapter() }

    // ViewModel per operazioni locali (delete, ecc.)
    private val viewModel: MarkerViewModel by lazy {
        val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
        MarkerViewModelFactory(MarkerRepository(dao))
            .create(MarkerViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDetailPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar back
        binding.detailPhotoMarkerToolBar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Inizializza MapView
        binding.markerDetailMapView.onCreate(savedInstanceState)
        binding.markerDetailMapView.getMapAsync(this)

        // Carica i dettagli del marker da Room
        loadMarkerDetails(args.id)
        binding.thumbRecyclerView.apply {
            adapter = thumbAdapter
            layoutManager = LinearLayoutManager(
                requireContext(), LinearLayoutManager.HORIZONTAL, false
            )
        }

        // Delete button
        binding.deleteButton.setOnClickListener {
            currentMarker?.let { marker ->
                // Mostra un dialogo di conferma
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Conferma Eliminazione")
                    .setMessage("Sei sicuro di voler eliminare questo marker? L'eliminazione verrà sincronizzata con il server.")
                    .setPositiveButton("Elimina") { dialog, which ->
                        // Imposta lo stato del marker a PENDING_DELETE localmente
                        viewModel.deleteMarker(marker.id)

                        // Avvia sync one-time per propagare la DELETE
                        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>().build()
                        val wm = WorkManager.getInstance(requireContext())
                        wm.enqueue(req)

                        // Osserva lo stato del worker per feedback e navigazione
                        wm.getWorkInfoByIdLiveData(req.id)
                            .observe(viewLifecycleOwner) { info ->
                                info?.let {
                                    if (it.state.isFinished) {
                                        val msg = it.outputData.getString("message")
                                            ?: it.outputData.getString("errorMessage")
                                        if (!msg.isNullOrBlank()) {
                                            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                                        } else {
                                            // Messaggio di default se non c'è un messaggio specifico
                                            if (it.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                                                Toast.makeText(requireContext(), "Marker eliminato con successo!", Toast.LENGTH_SHORT).show()
                                            } else if (it.state == androidx.work.WorkInfo.State.FAILED) {
                                                Toast.makeText(requireContext(), "Errore durante l'eliminazione del marker.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        // Naviga solo DOPO che il worker ha completato/fallito e fornito feedback
                                        findNavController().navigateUp()
                                    }
                                }
                            }
                    }
                    .setNegativeButton("Annulla", null) // Non fa nulla, chiude il dialogo
                    .show()
            }
        }


        binding.editButton.setOnClickListener {
           currentMarker?.let {
               val action = DetailPhotoFragmentDirections.actionDetailPhotoFragmentToEditMarkerFragment(it.id)
               findNavController().navigate(action)
           }
        }
    }

    /** Carica da Room il MarkerEntity con l’ID fornito */
    private fun loadMarkerDetails(id: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = MarkerDatabase.getDatabase(requireContext()).markerDao()
            val marker = dao.getMarkerById(id)
            if (marker != null) {
                currentMarker = marker
                launch(Dispatchers.Main) {
                    populateFields(marker)
                    thumbAdapter.submit(marker.photos.map { it.thumbPath })
                    if (mapIsReady) {
                        drawMarkerOnMap(marker)
                    }
                }
            } else {
                launch(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Marker not found", Toast.LENGTH_LONG).show()
                    findNavController().navigateUp()
                }
            }
        }
    }

    /** Popola tutti i campi del layout con i dati di [marker] */

    @SuppressLint("SetTextI18n")
    private fun populateFields(marker: MarkerEntity) {
        binding.nameDetailFragmentTextView.text = marker.title

        // Genre e icona
        binding.genreDetailFragmentTextView.text = marker.genre
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
        binding.genreIconImageView.setImageDrawable(
            ResourcesCompat.getDrawable(resources, iconRes, null)
        )

        // Shutter Speed, F-stop, ISO
        binding.shutterTextView.text = marker.shutterSpeed
        binding.fStopDetailFragmentTextView.text = marker.aperture
        binding.isoDetailFragmentTextView.text = marker.iso

        // Focal Length
        binding.focalLenDetailFragmentTextView.text = marker.focalLength.toString()

        // Tag
        binding.tagDetailFragmentTextView.text = marker.tag

        // Notes
        binding.noteEditText.setText(marker.notes)
    }

    /** Chiamato quando GoogleMap è pronto */
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        mapIsReady = true

        // Opzioni base della mappa
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        googleMap.mapType = GoogleMap.MAP_TYPE_HYBRID
        binding.editButton.isEnabled = false
        binding.deleteButton.isEnabled = false
        // Se abbiamo già caricato il marker, disegniamolo ora
        currentMarker?.let { drawMarkerOnMap(it) }
        binding.editButton.isEnabled = true
        binding.deleteButton.isEnabled = true
    }

    /** Posiziona un pin sulla mappa e centra la camera su [marker] */
    private fun drawMarkerOnMap(marker: MarkerEntity) {
        val position = LatLng(marker.lat, marker.lng)
        val bmp = Bitmap.createScaledBitmap(
            BitmapFactory.decodeResource(resources, R.drawable.icon_fov),
            (48 * resources.displayMetrics.density).roundToInt(),
            (48 * resources.displayMetrics.density).roundToInt(),
            false
        )
        googleMap.clear()
        photoMarker = googleMap.addMarker(
            MarkerOptions()
                .position(position)
                .icon(BitmapDescriptorFactory.fromBitmap(bmp))
                .anchor(0.5f, 0.5f)
                .rotation(marker.angle)
                .flat(true)
        )!!
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 18f))
    }

    // Lifecycle della MapView
    override fun onStart() { super.onStart(); binding.markerDetailMapView.onStart() }
    override fun onResume() { super.onResume(); binding.markerDetailMapView.onResume() }
    override fun onPause() { binding.markerDetailMapView.onPause(); super.onPause() }
    override fun onStop() { binding.markerDetailMapView.onStop(); super.onStop() }
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