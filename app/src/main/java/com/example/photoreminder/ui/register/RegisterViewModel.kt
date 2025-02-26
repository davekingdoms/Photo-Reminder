package com.example.photoreminder.ui.register

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoreminder.data.model.AuthResponse
import com.example.photoreminder.data.repository.AuthRepository
import kotlinx.coroutines.launch
import retrofit2.Response

class RegisterViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _registerResponse = MutableLiveData<Response<AuthResponse>?>()
    val registerResponse: LiveData<Response<AuthResponse>?> = _registerResponse

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun doRegister(username: String, password: String) {
        viewModelScope.launch {
            try {
                val response = repository.registerUser(username, password)
                _registerResponse.value = response
            } catch (e: Exception) {
                Log.e("RegisterViewModel", "Error during registration", e)
                _errorMessage.value = e.message
            }
        }
    }

    fun clearRegisterResponse() {
        _registerResponse.value = null
        _errorMessage.value = null
    }
}
