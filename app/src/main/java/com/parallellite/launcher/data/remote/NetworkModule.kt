package com.parallellite.launcher.data.remote

import com.parallellite.launcher.data.SessionStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Builds the Retrofit stack. The auth token is only attached to requests bound
 * for the Romhacking host, so it never leaks to third-party patch CDNs — even
 * across redirects (OkHttp additionally strips auth headers on host changes).
 */
object NetworkModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun create(sessionStore: SessionStore, debug: Boolean): RomhackingApi {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request()
            val token = sessionStore.currentToken()
            val authed = if (token.isNotEmpty() && request.url.host == RomhackingApi.HOST) {
                request.newBuilder()
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/json")
                    .build()
            } else {
                request
            }
            chain.proceed(authed)
        }

        val clientBuilder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)

        if (debug) {
            clientBuilder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }

        return Retrofit.Builder()
            .baseUrl(RomhackingApi.BASE_URL)
            .client(clientBuilder.build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RomhackingApi::class.java)
    }
}
