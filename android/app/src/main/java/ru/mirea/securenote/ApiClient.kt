package ru.mirea.securenote

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
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

data class IncidentCreateRequest(
    val title: String,
    val description: String,
    val category: String,
    val severity: String
)

data class IncidentUpdateRequest(
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val status: String
)

data class IncidentResponse(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val severity: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("updated_at") val updatedAt: String
) {
    fun toIncident(): Incident {
        check(id > 0 && title.isNotBlank() && description.isNotBlank())
        check(category in listOf("Фишинг", "Подозрительный вход", "Утечка данных", "Другое"))
        check(severity in listOf("Низкий", "Средний", "Высокий"))
        check(status in listOf("Новый", "В работе", "Закрыт"))
        check(createdAt.isNotBlank() && updatedAt.isNotBlank())
        return Incident(id, title, description, category, severity, status, createdAt, updatedAt)
    }
}

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

    @GET("incidents")
    suspend fun incidents(
        @Header("Authorization") authorization: String
    ): List<IncidentResponse>

    @GET("incidents/{id}")
    suspend fun incident(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    ): IncidentResponse

    @POST("incidents")
    suspend fun createIncident(
        @Header("Authorization") authorization: String,
        @Body request: IncidentCreateRequest
    ): IncidentResponse

    @PUT("incidents/{id}")
    suspend fun updateIncident(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int,
        @Body request: IncidentUpdateRequest
    ): IncidentResponse

    @DELETE("incidents/{id}")
    suspend fun deleteIncident(
        @Header("Authorization") authorization: String,
        @Path("id") id: Int
    )
}

object ApiClient {
    private const val BASE_URL = "http://10.0.2.2:8000/"

    val api: BackendApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .connectionPool(
                okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS)
            )
            .retryOnConnectionFailure(false)
            .eventListener(object : okhttp3.EventListener() {
                override fun callFailed(
                    call: okhttp3.Call,
                    ioe: java.io.IOException
                ) {
                    android.util.Log.w(
                        "SecureNoteNetwork",
                        "Ошибка подключения к backend",
                        ioe
                    )
                }
            })
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }
}
