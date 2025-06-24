package com.example.photoreminder.ui.viewmodel

import androidx.lifecycle.*
import com.example.photoreminder.data.local.MarkerEntity
import com.example.photoreminder.data.repository.MarkerRepository
import kotlinx.coroutines.launch

class MarkerViewModel(
    private val repository: MarkerRepository
) : ViewModel() {

    val markers: LiveData<List<MarkerEntity>> =
        repository.observeAll().asLiveData()

    fun addMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            repository.upsert(marker)
        }
    }
    fun updateMarker(marker: MarkerEntity) {
        viewModelScope.launch {
            repository.updateMarker(marker)
        }
    }

    fun deleteMarker(id: String) {
        viewModelScope.launch {
            repository.deleteLocal(id)
        }
    }

}
