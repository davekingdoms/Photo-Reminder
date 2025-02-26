package com.example.photoreminder

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import com.example.photoreminder.data.datastore.DataStoreManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Carichiamo layout con FragmentContainerView e nav_graph


        // Subito dopo, cambiamo la startDestination in base alla presenza del token
        // Ma DataStore è asincrono -> facciamo un "launch"
        lifecycleScope.launch {
            val token = DataStoreManager.getToken(this@MainActivity)
            Log.d("TOKEN", "Token: $token")
            setContentView(R.layout.activity_main)
            val navController = findNavController(R.id.fragmentContainerView)
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

            if (token != null) {
                // Se abbiamo un token, vai direttamente a HomeFragment
               navGraph.setStartDestination(R.id.homeFragment)
            } else {
                // Altrimenti resta su login
                navGraph.setStartDestination(R.id.loginFragment)
            }

            navController.graph = navGraph
        }
    }
}
