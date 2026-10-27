package com.obsidian.mobile.network

import com.obsidian.mobile.auth.TokenStorage
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
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

    @GET("/api/v1/auth/me")
    suspend fun me(): SessionInfo

    @POST("/api/v1/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("/api/v1/scan/unified")
    suspend fun scanUnified(@Body request: UnifiedScanRequest): UnifiedScanResponse

    @GET("/api/v1/scans")
    suspend fun getScans(): List<ScanLogDto>

    @GET("/api/v1/rules")
    suspend fun getRules(): List<ThreatRule>

    @POST("/api/v1/rules/report")
    suspend fun reportThreat(@Body request: ReportRequest): ThreatRule

    @POST("/api/v1/rules/bypass")
    suspend fun bypassThreat(@Body request: BypassRequest): ThreatRule

    @DELETE("/api/v1/rules/{id}")
    suspend fun deleteRule(@Path("id") ruleId: String): Response<Unit>
}

class ApiClient(private val tokenStorage: TokenStorage) {

    private var retrofit: Retrofit? = null
    private var currentBaseUrl: String? = null

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

    val service: ObsidianApiService
        get() {
            val url = getFormattedBaseUrl(tokenStorage.serverUrl)
            if (retrofit == null || currentBaseUrl != url) {
                currentBaseUrl = url
                retrofit = Retrofit.Builder()
                    .baseUrl(url)
                    .client(okHttpClient)
                    .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                    .build()
            }
            return retrofit!!.create(ObsidianApiService::class.java)
        }

    fun setCustomBaseUrl(url: String) {
        tokenStorage.serverUrl = url
        retrofit = null
    }

    private fun getFormattedBaseUrl(url: String): String {
        val trimmed = url.trim()
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "http://$trimmed"
        } else {
            trimmed
        }
        return if (withScheme.endsWith("/")) withScheme else "$withScheme/"
    }
}
