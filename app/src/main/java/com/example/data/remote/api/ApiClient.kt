package com.example.data.remote.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val DEFAULT_BASE_URL = "http://192.168.0.105:3002/api/"
    private var currentBaseUrl = DEFAULT_BASE_URL
    private var authToken: String? = null

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        authToken?.let { token ->
            if (token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }
        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private var retrofit: Retrofit = buildRetrofit(currentBaseUrl)
    var service: WeHabitaApiService = retrofit.create(WeHabitaApiService::class.java)
        private set

    private fun buildRetrofit(baseUrl: String): Retrofit {
        val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalizedUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    fun setBaseUrl(newBaseUrl: String) {
        val trimmed = newBaseUrl.trim()
        val formatted = when {
            trimmed.endsWith("/api/") -> trimmed
            trimmed.endsWith("/api") -> "$trimmed/"
            trimmed.endsWith("/") -> "${trimmed}api/"
            else -> "$trimmed/api/"
        }
        if (formatted != currentBaseUrl) {
            currentBaseUrl = formatted
            retrofit = buildRetrofit(currentBaseUrl)
            service = retrofit.create(WeHabitaApiService::class.java)
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun setAuthToken(token: String?) {
        authToken = token
    }

    fun getAuthToken(): String? = authToken

    fun getMoshi(): Moshi = moshi

    fun getOkHttpClient(): OkHttpClient = okHttpClient
}
