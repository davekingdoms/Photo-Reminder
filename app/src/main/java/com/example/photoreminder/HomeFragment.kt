package com.example.photoreminder

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.local.MarkerDatabase
import com.example.photoreminder.data.sync.MarkerSyncWorker
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

                /* 1) rimuovi il timestamp di lastSync PRIMA di cancellare lo user */
                ctx.getSharedPreferences("marker_sync_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .remove("last_sync_time_${username}")
                    .apply()

                /* 2) cancella credenziali + DB */
                DataStoreManager.clearToken(ctx)
                DataStoreManager.clearUsername(ctx)
                MarkerDatabase.getDatabase(ctx).markerDao().clearAllMarkers()

                /* 3) torna al login */
                findNavController().navigate(R.id.action_homeFragment_to_loginFragment)
            }
        }

        /* ---------- observer unico sullo stato della sync ---------- */
        WorkManager.getInstance(requireContext())
            .getWorkInfosForUniqueWorkLiveData(MarkerSyncWorker.UNIQUE_WORK_NAME)
            .observe(viewLifecycleOwner) { infos ->
                val info = infos.firstOrNull() ?: return@observe
                if (info.state.isFinished) {
                    val msg = info.outputData.getString("message")
                        ?: info.outputData.getString("errorMessage")
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
        val wm  = WorkManager.getInstance(ctx)

        val req = OneTimeWorkRequestBuilder<MarkerSyncWorker>()
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

        /* serializza i job con nome univoco */
        wm.enqueueUniqueWork(
            MarkerSyncWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,   // se c’è già → ignora
            req
        )

        /* se una sync è già RUNNING, avvisa subito l’utente */
        val alreadyRunning = wm.getWorkInfosForUniqueWork(MarkerSyncWorker.UNIQUE_WORK_NAME)
            .get() // blocca pochi ms fuori main-thread
            .any { it.state == WorkInfo.State.RUNNING && it.id != req.id }
        if (alreadyRunning) {
            Toast.makeText(ctx, "Sincronizzazione già in corso…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
