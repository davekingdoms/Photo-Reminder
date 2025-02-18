package com.example.photoreminder.data.api

import com.example.photoreminder.data.model.AuthResponse
import com.example.photoreminder.data.model.LoginRequest
import com.example.photoreminder.data.model.RegisterRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {

    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<AuthResponse>

    @POST("register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<AuthResponse>
}