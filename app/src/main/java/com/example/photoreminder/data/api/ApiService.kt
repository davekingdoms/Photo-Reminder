package com.example.photoreminder.data.api

import com.example.photoreminder.data.model.AuthResponse
import com.example.photoreminder.data.model.LoginRequest
import com.example.photoreminder.data.model.RegisterRequest
import com.example.photoreminder.data.model.GetMarkersResponse
import com.example.photoreminder.data.model.MarkerDto
import com.example.photoreminder.data.model.MarkerResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ApiService {

    // — AUTH —
    @POST("login")
    suspend fun loginUser(@Body request: LoginRequest): Response<AuthResponse>

    @POST("register")
    suspend fun registerUser(@Body request: RegisterRequest): Response<AuthResponse>

    // — MARKERS —
    @GET("markers")
    suspend fun getMarkers(): Response<GetMarkersResponse>

    @POST("markers")
    suspend fun createMarker(@Body dto: MarkerDto): Response<MarkerResponse>

    @PUT("markers/{id}")
    suspend fun updateMarker(
        @Path("id") id: String,
        @Body dto: MarkerDto
    ): Response<MarkerResponse>

    @DELETE("markers/{id}")
    suspend fun deleteMarker(@Path("id") id: String): Response<MarkerResponse>
}
