package ru.mirea.securenote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class BackendHealthResponse(
    val status: String,
    val service: String
)

data class AuthRequest(
    val email: String,
    val password: String
)

data class AuthUser(
    val id: Int,
    val email: String,
    @SerializedName("created_at")
    val createdAt: String
)

data class AuthTokenResponse(
    @SerializedName("access_token")
    val accessToken: String,
    @SerializedName("token_type")
    val tokenType: String,
    @SerializedName("expires_in")
    val expiresIn: Int
)

interface BackendApi {
    @GET("health")
    suspend fun health(): BackendHealthResponse

    @POST("auth/register")
    suspend fun register(@Body request: AuthRequest): AuthUser

    @POST("auth/login")
    suspend fun login(@Body request: AuthRequest): AuthTokenResponse

    @GET("auth/me")
    suspend fun me(
        @Header("Authorization") authorization: String
    ): AuthUser

    @POST("auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String
    )
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
