package com.example.data.remote.api

import android.content.Context
import android.content.SharedPreferences
import com.example.data.repository.SyncLogCollector
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val TAG = "WeHabita:ApiClient"
    private const val PREFS_NAME = "wehabita_api_prefs"
    private const val KEY_AUTH_TOKEN = "auth_token"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_USER_ID = "saved_user_id"
    private const val KEY_HOME_ID = "saved_home_id"
    private const val DEFAULT_BASE_URL = "https://wehabita.mayorealo.com/api/"

    @Volatile
    private var currentBaseUrl: String = DEFAULT_BASE_URL

    @Volatile
    private var authToken: String? = null

    private var prefs: SharedPreferences? = null

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val builder = original.newBuilder()
        
        val token = getAuthToken()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        builder.header("Accept", "application/json")
        chain.proceed(builder.build())
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        if (message.contains("Authorization") || message.contains("password")) {
            // Mask sensitive headers in logs if desired or log summary
            SyncLogCollector.d(TAG, message.take(160))
        } else {
            SyncLogCollector.d(TAG, message.take(200))
        }
    }.apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    fun getOkHttpClient(): OkHttpClient = okHttpClient

    private var retrofit: Retrofit = buildRetrofit(currentBaseUrl)
    var service: WeHabitaApiService = retrofit.create(WeHabitaApiService::class.java)
        private set

    /**
     * Initializes the ApiClient with Android context to persist JWT token, Base URL, and session.
     */
    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // Restore Base URL if saved, upgrading any legacy local IP
            val savedBaseUrl = prefs?.getString(KEY_BASE_URL, null)
            if (!savedBaseUrl.isNullOrBlank() && !savedBaseUrl.contains("192.168.0.105")) {
                currentBaseUrl = savedBaseUrl
                retrofit = buildRetrofit(currentBaseUrl)
                service = retrofit.create(WeHabitaApiService::class.java)
            } else {
                setBaseUrl(DEFAULT_BASE_URL)
            }

            // Restore Auth Token if saved
            val savedToken = prefs?.getString(KEY_AUTH_TOKEN, null)
            if (!savedToken.isNullOrBlank()) {
                authToken = savedToken
                SyncLogCollector.i(TAG, "Token JWT restaurado de almacenamiento seguro (${savedToken.take(12)}...)")
            } else {
                SyncLogCollector.d(TAG, "No hay token JWT previo guardado.")
            }
        }
    }

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
            prefs?.edit()?.putString(KEY_BASE_URL, formatted)?.apply()
            retrofit = buildRetrofit(currentBaseUrl)
            service = retrofit.create(WeHabitaApiService::class.java)
            SyncLogCollector.i(TAG, "Base URL actualizada y persistida: $formatted")
        }
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun setAuthToken(token: String?) {
        authToken = token
        prefs?.let { p ->
            val editor = p.edit()
            if (token.isNullOrBlank()) {
                editor.remove(KEY_AUTH_TOKEN)
            } else {
                editor.putString(KEY_AUTH_TOKEN, token)
            }
            editor.commit()
        }
        if (token != null) {
            SyncLogCollector.s(TAG, "Token JWT actualizado y persistido (${token.take(12)}...)")
        } else {
            SyncLogCollector.i(TAG, "Token JWT eliminado (sesión cerrada)")
        }
    }

    fun getAuthToken(): String? {
        val stored = prefs?.getString(KEY_AUTH_TOKEN, null)
        if (!stored.isNullOrBlank()) {
            authToken = stored
            return stored
        }
        return authToken
    }

    fun saveUserId(userId: Int?) {
        prefs?.let { p ->
            val editor = p.edit()
            if (userId == null) {
                editor.remove(KEY_USER_ID)
            } else {
                editor.putInt(KEY_USER_ID, userId)
            }
            editor.commit()
        }
    }

    fun getSavedUserId(): Int? {
        val id = prefs?.getInt(KEY_USER_ID, -1) ?: -1
        return if (id != -1) id else null
    }

    fun saveHomeId(homeId: Int?) {
        prefs?.let { p ->
            val editor = p.edit()
            if (homeId == null) {
                editor.remove(KEY_HOME_ID)
            } else {
                editor.putInt(KEY_HOME_ID, homeId)
            }
            editor.commit()
        }
    }

    fun getSavedHomeId(): Int? {
        val id = prefs?.getInt(KEY_HOME_ID, -1) ?: -1
        return if (id != -1) id else null
    }

    fun clearSession() {
        authToken = null
        prefs?.edit()
            ?.remove(KEY_AUTH_TOKEN)
            ?.remove(KEY_USER_ID)
            ?.remove(KEY_HOME_ID)
            ?.commit()
        SyncLogCollector.i(TAG, "Sesión y credenciales borradas del almacenamiento local.")
    }

    fun getMoshi(): Moshi = moshi
}
