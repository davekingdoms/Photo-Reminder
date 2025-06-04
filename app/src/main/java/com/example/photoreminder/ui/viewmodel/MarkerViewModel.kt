package com.example.photoreminder.ui.viewmodel

import androidx.lifecycle.*
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.repository.MarkerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MarkerViewModel(
    private val repository: MarkerRepository
) : ViewModel() {

    // Esponi i marker di Room come LiveData
    val markers: LiveData<List<MarkerEntity>> =
        repository.observeAll().asLiveData()

    /** Inserisce o aggiorna un marker localmente */
    fun addMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            repository.upsert(marker)
        }
    }

    /** Marca localmente una cancellazione */
    fun deleteMarker(id: String) {
        viewModelScope.launch {
            repository.deleteLocal(id)
        }
    }

}
