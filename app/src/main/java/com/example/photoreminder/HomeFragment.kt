package com.example.photoreminder

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.logoutButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                // Rimuove dati salvati
                DataStoreManager.clearToken(requireContext())
                DataStoreManager.clearUsername(requireContext())
                MarkerDatabase.getDatabase(requireContext()).markerDao().clearAllMarkers()
                val username = DataStoreManager.getUsername(requireContext())
                requireContext()
                    .getSharedPreferences("marker_sync_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("last_sync_time_${username}")
                    .apply()
                // Torna al login
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
        }

        binding.syncButton.setOnClickListener {
            val syncReq = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
                .setInputData(workDataOf("isManual" to true))
                .build()

            val workManager = WorkManager.getInstance(requireContext())
            workManager.enqueue(syncReq)

            workManager.getWorkInfoByIdLiveData(syncReq.id)
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
        }

        binding.cardViewMap.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
