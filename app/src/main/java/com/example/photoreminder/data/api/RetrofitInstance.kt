package com.example.photoreminder.data.api

import android.content.Context
import android.os.Build
import com.example.photoreminder.data.datastore.DataStoreManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object RetrofitInstance {

    private lateinit var appContext: Context
    fun init(context: Context) {            // chiama da Application.onCreate()
        appContext = context.applicationContext
    }

    private const val EMULATOR_IP = "http://10.0.2.2:5000/"
    private const val SERVER_IP   = "http://10.46.49.197:5000/"
    internal val BASE_URL = if (
        Build.FINGERPRINT.contains("generic")    || Build.MODEL.contains("Emulator") ||
        Build.MANUFACTURER.contains("Genymotion")|| Build.BRAND.contains("google") && Build.DEVICE.startsWith("generic") ||
        Build.PRODUCT.contains("sdk_gphone")     || Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu")        || Build.BOARD.contains("goldfish")
    ) EMULATOR_IP else SERVER_IP

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val token = runBlocking { DataStoreManager.getToken(appContext) }
        val req = chain.request().newBuilder()
        if (!token.isNullOrBlank()) {
            req.addHeader("Authorization", "Bearer $token")
        }
        chain.proceed(req.build())
    }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    val api: ApiService by lazy { retrofit.create(ApiService::class.java) }
}
