package com.example.photoreminder.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.photoreminder.data.repository.MarkerRepository

class MarkerViewModelFactory(
    private val repository: MarkerRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MarkerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MarkerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
