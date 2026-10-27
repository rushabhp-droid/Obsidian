package com.obsidian.mobile.network

import com.obsidian.mobile.data.SessionStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import java.io.IOException
import java.util.concurrent.TimeUnit

/** /api/b2c/v1: the consumer app's own API. Dashboard (/api/v1) tokens don't work here. */
interface B2cApi {

    @GET("api/b2c/v1/status")
    suspend fun status(): ServerStatus

    @POST("api/b2c/v1/auth/register")
    suspend fun register(@Body body: RegisterRequest): CodeSent

    @POST("api/b2c/v1/auth/verify-email")
    suspend fun verifyEmail(@Body body: VerifyEmailRequest): SignedIn

    @POST("api/b2c/v1/auth/resend-code")
    suspend fun resendCode(@Body body: EmailRequest): CodeSent

    @POST("api/b2c/v1/auth/login")
    suspend fun login(@Body body: LoginRequest): SignedIn

    @POST("api/b2c/v1/auth/google")
    suspend fun google(@Body body: GoogleRequest): SignedIn

    @POST("api/b2c/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: EmailRequest): CodeSent

    @POST("api/b2c/v1/auth/reset-password")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): SignedIn

    @POST("api/b2c/v1/auth/logout")
    suspend fun logout()

    @GET("api/b2c/v1/me")
    suspend fun me(): ConsumerProfile

    @POST("api/b2c/v1/scan/url")
    suspend fun scanUrl(@Body body: UrlScanRequest): ScanResult

    @POST("api/b2c/v1/scan/file")
    suspend fun scanFile(@Body body: FileScanRequest): ScanResult

    @POST("api/b2c/v1/scan/message")
    suspend fun scanMessage(@Body body: MessageScanRequest): ScanResult

    @GET("api/b2c/v1/activity")
    suspend fun activity(): List<ActivityItem>

    @GET("api/b2c/v1/rules")
    suspend fun rules(): List<PersonalRule>

    @POST("api/b2c/v1/rules")
    suspend fun addRule(@Body body: RuleRequest): PersonalRule

    @DELETE("api/b2c/v1/rules/{id}")
    suspend fun deleteRule(@Path("id") id: String)

    @GET("api/b2c/v1/firewall/blocklist")
    suspend fun blocklist(): Blocklist

    @POST("api/b2c/v1/reports")
    suspend fun report(@Body body: ReportRequest): Report

    @GET("api/b2c/v1/reports")
    suspend fun reports(): List<Report>
}

/**
 * Builds the Retrofit client for the server currently set in [SessionStore],
 * rebuilding it when the user points the app somewhere else. Nothing is logged:
 * requests carry the bearer token and, for unknown senders, message text.
 */
class ApiClient(private val session: SessionStore) {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().header("Accept", "application/json")
            session.token?.let { request.header("Authorization", "Bearer $it") }
            chain.proceed(request.build())
        }
        .build()

    @Volatile private var cached: Pair<String, B2cApi>? = null

    /** Called when the server rejects our token (expired or revoked): the app signs out locally. */
    @Volatile var onUnauthorized: (() -> Unit)? = null

    private fun forServer(serverUrl: String): B2cApi {
        if (serverUrl.isBlank()) throw ApiException(null, "NO_SERVER", "Set the Obsidian server address first.")
        cached?.let { (url, api) -> if (url == serverUrl) return api }
        val api = Retrofit.Builder()
            .baseUrl("$serverUrl/")
            .client(http)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(B2cApi::class.java)
        cached = serverUrl to api
        return api
    }

    /** Runs a call on the configured server; HTTP and network failures become an [ApiException]. */
    suspend fun <T> call(block: suspend B2cApi.() -> T): T = callOn(session.settings.value.serverUrl, block)

    /** Like [call], against [serverUrl] (testing an address before saving it). */
    suspend fun <T> callOn(serverUrl: String, block: suspend B2cApi.() -> T): T = try {
        forServer(serverUrl).block()
    } catch (e: HttpException) {
        throw problem(e)
    } catch (e: IOException) {
        throw ApiException(null, "OFFLINE", "Can't reach the Obsidian server. Check your connection.")
    } catch (e: IllegalArgumentException) {
        throw ApiException(null, "BAD_SERVER", "The server address isn't valid.")
    }

    private fun problem(e: HttpException): ApiException {
        if (e.code() == 401 && session.token != null) onUnauthorized?.invoke()
        val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
        val fields = body?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val detail = fields?.get("detail")?.jsonPrimitive?.content
        val code = fields?.get("code")?.jsonPrimitive?.content
        val message = detail ?: when (e.code()) {
            401 -> "Your session has ended. Sign in again."
            403 -> "That isn't allowed."
            404 -> "Not found."
            429 -> "Too many tries. Wait a moment."
            in 500..599 -> "The server had a problem. Try again shortly."
            else -> "Request failed (${e.code()})."
        }
        return ApiException(e.code(), code, message)
    }
}

/** A failed API call. [status] is null for network failures; [code] is the problem detail's "code". */
class ApiException(val status: Int?, val code: String?, message: String) : Exception(message) {
    val isUnauthorized: Boolean get() = status == 401
}
