package com.example.photoreminder.ui.login

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.photoreminder.data.model.AuthResponse
import com.example.photoreminder.data.repository.AuthRepository
import kotlinx.coroutines.launch
import retrofit2.Response

class LoginViewModel : ViewModel() {

    private val repository = AuthRepository()
    private val _loginResponse = MutableLiveData<Response<AuthResponse>?>()
    val loginResponse: LiveData<Response<AuthResponse>?> = _loginResponse

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    fun doLogin(username: String, password: String) {
        viewModelScope.launch {
            try {
                val response = repository.loginUser(username, password)
                _loginResponse.value = response

            }
            catch (e: Exception) {
                Log.e("LoginViewModel", "Error during login", e)
                _errorMessage.value = e.message

            }
        }
    }

    fun clearLoginResponse() {
        _loginResponse.value = null
        _errorMessage.value = null
    }
}
