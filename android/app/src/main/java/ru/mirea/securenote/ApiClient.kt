package ru.mirea.securenote

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import java.util.concurrent.TimeUnit

data class BackendHealthResponse(
    val status: String,
    val service: String
)

interface BackendApi {
    @GET("health")
    suspend fun health(): BackendHealthResponse
}

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: BackendApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }
}
