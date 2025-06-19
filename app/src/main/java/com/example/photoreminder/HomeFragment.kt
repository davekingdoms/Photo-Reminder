package com.example.photoreminder

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.sync.MarkerSyncWorker
import com.example.photoreminder.data.sync.PhotoSyncWorker
import com.example.photoreminder.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    /* ---------------------------------------------------------------- */

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        /* ---------- logout ---------- */
        binding.logoutButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val ctx = requireContext()
                val username = DataStoreManager.getUsername(ctx)

                // 1) azzera il timestamp lastSync
                ctx.getSharedPreferences("marker_sync_prefs", Context.MODE_PRIVATE)
                    .edit { remove("last_sync_time_${'$'}username") }

                // 2) cancella credenziali + DB locale
                DataStoreManager.clearToken(ctx)
                DataStoreManager.clearUsername(ctx)
                MarkerDatabase.getDatabase(ctx).markerDao().clearAllMarkers()

                // 3) annulla i worker
                WorkManager.getInstance(ctx).apply {
                    cancelUniqueWork(MarkerSyncWorker.QUEUE_MANUAL)
                    cancelUniqueWork(MarkerSyncWorker.QUEUE_PERIODIC)
                }

                // 4) torna al login
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
        }

        /* ---------- observer sullo stato della sync MANUALE ---------- */
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(MarkerSyncWorker.QUEUE_MANUAL)
            .observe(viewLifecycleOwner) { infos ->
                val info = infos.firstOrNull() ?: return@observe
                if (info.state.isFinished) {
                    val data = info.outputData
                    val msg = data.getString("message") ?: data.getString("errorMessage")
                    if (!msg.isNullOrBlank())
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            }

        /* ---------- pulsante Sync manuale ---------- */
        binding.syncButton.setOnClickListener { enqueueManualSync() }

        /* ---------- navigazione ---------- */
        binding.cardViewMap.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_mapsFragment)
        }
        binding.cardViewList.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_listPhotoFragment)
        }
    }

    /* ================= helpers ================= */

    private fun enqueueManualSync() {
        val ctx = requireContext()
        val wm = WorkManager.getInstance(ctx)

        val markerReq = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
            .setInputData(workDataOf("isManual" to true))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        val photoReq = OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .build()

        // serializza i job nella coda MANUAL
        wm.beginUniqueWork(
            MarkerSyncWorker.QUEUE_MANUAL,
            ExistingWorkPolicy.REPLACE,
            markerReq
        ).then(photoReq).enqueue()

        // feedback immediato se è già in RUNNING
        val infos = wm.getWorkInfosForUniqueWork(MarkerSyncWorker.QUEUE_MANUAL).get()
        val alreadyRunning = infos.any { it.state == WorkInfo.State.RUNNING }
        if (alreadyRunning) {
            Toast.makeText(ctx, "Sync in progress...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
