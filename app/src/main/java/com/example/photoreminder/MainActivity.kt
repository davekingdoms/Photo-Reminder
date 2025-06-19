package com.example.photoreminder

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.work.*
import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.data.datastore.DataStoreManager
import com.example.photoreminder.data.sync.MarkerSyncWorker
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        RetrofitInstance.init(this)
        super.onCreate(savedInstanceState)

        // 1) monta il layout PRIMA di cercare il NavController
        setContentView(R.layout.activity_main)

        // 2) definisci il nav graph in base al token asincrono
        lifecycleScope.launch {
            val token = DataStoreManager.getToken(this@MainActivity)
            Log.d("TOKEN", token.toString())

            val navController = findNavController(R.id.fragmentContainerView)
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (token != null) R.id.homeFragment else R.id.loginFragment
            )
            navController.graph = navGraph

            // 3) se l’utente è loggato, schedule periodic sync ogni 3 ore
            if (token != null) {
                val periodic = PeriodicWorkRequestBuilder<MarkerSyncWorker>(
                    3, TimeUnit.HOURS
                ).build()

                WorkManager.getInstance(this@MainActivity).enqueueUniquePeriodicWork(
                    MarkerSyncWorker.QUEUE_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodic
                )
            }
        }
    }
}
