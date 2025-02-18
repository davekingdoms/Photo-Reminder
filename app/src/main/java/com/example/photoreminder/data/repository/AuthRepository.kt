package com.example.photoreminder.data.repository

import com.example.photoreminder.data.api.RetrofitInstance
import com.example.photoreminder.data.model.AuthResponse
import com.example.photoreminder.data.model.LoginRequest
import com.example.photoreminder.data.model.RegisterRequest
import retrofit2.Response

class AuthRepository {

    suspend fun registerUser(username: String, password: String): Response<AuthResponse> {

        val request = RegisterRequest(username = username, password = password)
        return RetrofitInstance.api.registerUser(request)
    }

    suspend fun loginUser(username: String, password: String): Response<AuthResponse> {

        val request = LoginRequest(username = username, password = password)
        return RetrofitInstance.api.loginUser(request)
    }
}