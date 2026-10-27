package com.obsidian.mobile.network

import com.obsidian.mobile.auth.TokenStorage
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface ObsidianApiService {

    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: AuthLoginRequest): AuthLoginResponse

    @POST("/api/v1/scan/unified")
    suspend fun scanUnified(@Body request: UnifiedScanRequest): UnifiedScanResponse

    @GET("/api/v1/rules")
    suspend fun getRules(): List<ThreatRule>

    @POST("/api/v1/rules/report")
    suspend fun reportThreat(@Body request: ReportRequest): ThreatRule

    @POST("/api/v1/rules/bypass")
    suspend fun bypassThreat(@Body request: BypassRequest): ThreatRule

    @DELETE("/api/v1/rules/{id}")
    suspend fun deleteRule(@Path("id") ruleId: String)
}

class ApiClient(private val tokenStorage: TokenStorage) {

    private var baseUrl: String = "https://obsidian.example.com" // Configurable via Settings or tunnel

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                tokenStorage.bearerToken?.let { token ->
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                requestBuilder.header("Accept", "application/json")
                requestBuilder.header("Content-Type", "application/json")

                chain.proceed(requestBuilder.build())
            }
            .addInterceptor(logging)
            .build()
    }

    val service: ObsidianApiService by lazy {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ObsidianApiService::class.java)
    }

    fun setCustomBaseUrl(url: String) {
        baseUrl = if (url.endsWith("/")) url else "$url/"
    }
}
