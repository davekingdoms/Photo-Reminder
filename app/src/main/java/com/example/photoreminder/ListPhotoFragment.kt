package com.example.photoreminder

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.local.SyncStatus
import com.example.photoreminder.data.repository.MarkerRepository
import com.example.photoreminder.databinding.FragmentListPhotoBinding
import com.example.photoreminder.ui.adapter.MarkerListAdapter
import com.example.photoreminder.ui.adapter.TagAdapter
import com.example.photoreminder.ui.viewmodel.MarkerViewModel
import com.example.photoreminder.ui.viewmodel.MarkerViewModelFactory
import java.util.*

class ListPhotoFragment : Fragment() {

    private var _binding: FragmentListPhotoBinding? = null
    private val binding get() = _binding!!

    /* ViewModel */
    private val viewModel: MarkerViewModel by viewModels {
        MarkerViewModelFactory(
            MarkerRepository(
                MarkerDatabase.getDatabase(requireContext()).markerDao()
            )
        )
    }

    /* Adapters */
    private lateinit var tagAdapter: TagAdapter
    private lateinit var markerAdapter: MarkerListAdapter

    private var selectedTag = "All"
    private var allMarkers = listOf<com.example.photoreminder.data.local.MarkerEntity>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListPhotoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* Toolbar back */
        binding.materialToolbar3.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        /* Tag Recycler (orizzontale) */
        tagAdapter = TagAdapter(listOf("All"), "All") { tag ->
            selectedTag = tag
            refreshList()
        }
        binding.tagListrecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext(),
                LinearLayoutManager.HORIZONTAL, false)
            adapter = tagAdapter
        }

        /* Marker Recycler (verticale) */
        markerAdapter = MarkerListAdapter(emptyList()) { marker ->
            val action = ListPhotoFragmentDirections
                .actionListPhotoFragmentToDetailPhotoFragment(marker.id)
            findNavController().navigate(action)
        }
        binding.markerRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = markerAdapter
        }

        /* Osserva Room */
        viewModel.markers.observe(viewLifecycleOwner) { list ->
            allMarkers = list.filterNot { it.syncStatus == SyncStatus.PENDING_DELETE }
            updateTagBar()
            refreshList()
        }
    }

    /* ---------------- helper ---------------- */

    private fun updateTagBar() {
        val genres = allMarkers.mapNotNull { it.genre.ifBlank { null } }
        val tags   = allMarkers.mapNotNull { it.tag }
        val unique = (genres + tags)
            .map { it.lowercase(Locale.getDefault()) }
            .toSet()
            .map { it.replaceFirstChar { c -> c.uppercase() } }
            .sorted()
        tagAdapter.updateTags(listOf("All") + unique)
    }

    private fun refreshList() {
        val filtered = if (selectedTag.equals("All", true)) {
            allMarkers
        } else {
            allMarkers.filter {
                it.genre.equals(selectedTag, true) ||
                        it.tag?.equals(selectedTag, true) == true
            }
        }.sortedByDescending { it.updatedAt }

        markerAdapter.submit(filtered)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
