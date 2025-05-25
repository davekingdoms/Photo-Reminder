package com.example.photoreminder.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.repository.MarkerRepository
import kotlinx.coroutines.launch

/**
 * ViewModel per i marker fotografici.
 * Espone i marker locali e operazioni di aggiunta/eliminazione.
 */
class MarkerViewModel(
    private val repository: MarkerRepository
) : ViewModel() {

    /** LiveData che osserva tutti i marker non cancellati */
    val markers: LiveData<List<MarkerEntity>> =
        repository.observeAll().asLiveData()

    /** Aggiunge o aggiorna un marker in locale */
    fun addMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            repository.upsert(marker)
            // In futuro puoi lanciare qui un WorkManager per sync immediato
        }
    }

    /** Marca un marker come cancellato localmente */
    fun deleteMarker(id: String) {
        viewModelScope.launch {
            repository.deleteLocal(id)
            // Eventuale sync differito via WorkManager
        }
    }

    /** Fornisce un hook per sincronizzare (push/pull) */
    fun refresh() {
        viewModelScope.launch {
            repository.sync()
        }
    }
}

/**
 * Factory per istanziare MarkerViewModel con il repository.
 */
class MarkerViewModelFactory(
    private val repository: MarkerRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MarkerViewModel::class.java)) {
            return MarkerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
