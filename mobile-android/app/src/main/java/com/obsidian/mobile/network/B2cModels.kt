package com.obsidian.mobile.network

import kotlinx.serialization.Serializable

// Request and response shapes of /api/b2c/v1 (docs/protocol.md, "B2C app API").

@Serializable
enum class IndicatorType { EMAIL, PHONE, URL, DOMAIN, IP, FILE_HASH }

@Serializable
enum class ListType { BLACKLIST, WHITELIST }

@Serializable
enum class ThreatCategory { SAFE, SPAM, PHISHING, MALWARE, MALICIOUS }

/** PENDING means "not known yet" and is never shown as safe. BYPASSED: the user's own whitelist decided. */
@Serializable
enum class Verdict { MALICIOUS, SUSPICIOUS, PENDING, CLEAN, BYPASSED }

@Serializable
enum class ActivityKind { URL_SCAN, FILE_SCAN, LINK_OPENED, MESSAGE, REPORT }

@Serializable
enum class ReportStatus { WAITING, PROTECTED, BLOCKED, DISMISSED }

// ---- auth -----------------------------------------------------------------------------------------

@Serializable
data class RegisterRequest(val name: String, val email: String, val password: String)

@Serializable
data class VerifyEmailRequest(val email: String, val code: String)

@Serializable
data class EmailRequest(val email: String)

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class GoogleRequest(val idToken: String)

@Serializable
data class ResetPasswordRequest(val email: String, val code: String, val newPassword: String)

@Serializable
data class CodeSent(val email: String, val expiresInSeconds: Long)

@Serializable
data class ConsumerProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val emailVerified: Boolean,
    val hasPassword: Boolean,
    val googleLinked: Boolean,
    val createdAt: String? = null,
)

@Serializable
data class SignedIn(val token: String, val expiresAt: String, val consumer: ConsumerProfile)

@Serializable
data class ServerStatus(
    val server: String,
    val database: String,
    val cache: String,
    val virusTotalConfigured: Boolean,
    val googleSignInConfigured: Boolean,
    val googleClientId: String? = null,
    val emailConfigured: Boolean,
    val serverTime: String,
)

// ---- scanning -------------------------------------------------------------------------------------

@Serializable
data class UrlScanRequest(val url: String, val source: ActivityKind? = null)

@Serializable
data class FileScanRequest(val sha256: String, val fileName: String?, val sizeBytes: Long?)

/** Saved contacts: [text] must be null; only the links are sent. */
@Serializable
data class MessageScanRequest(
    val app: String,
    val sender: String?,
    val savedContact: Boolean,
    val text: String? = null,
    val urls: List<String> = emptyList(),
)

@Serializable
data class IndicatorResult(
    val indicatorType: IndicatorType,
    val value: String,
    val verdict: Verdict,
    val category: ThreatCategory? = null,
    val vtDetections: Int = 0,
    val source: String,
    val ruleId: String? = null,
)

@Serializable
data class ScanResult(
    val verdict: Verdict,
    val category: ThreatCategory? = null,
    val riskScore: Int? = null,
    val vtDetections: Int = 0,
    val trusted: Boolean = false,
    /** PERSONAL_RULE, SHARED_RULE, VIRUSTOTAL or NONE. */
    val decidedBy: String,
    val indicators: List<IndicatorResult> = emptyList(),
    val activityId: String? = null,
)

// ---- activity, rules, reports ---------------------------------------------------------------------

@Serializable
data class ActivityItem(
    val id: String,
    val kind: ActivityKind,
    val subject: String,
    val detail: String? = null,
    val verdict: Verdict,
    val threatCategory: ThreatCategory? = null,
    val riskScore: Int? = null,
    val vtDetections: Int = 0,
    val createdAt: String,
)

@Serializable
data class RuleRequest(val indicatorType: IndicatorType, val indicatorValue: String, val listType: ListType)

@Serializable
data class PersonalRule(
    val id: String,
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val listType: ListType,
    val createdAt: String? = null,
)

@Serializable
data class Blocklist(
    val domains: List<String>,
    val ips: List<String>,
    val personal: List<PersonalRule>,
    val generatedAt: String,
)

@Serializable
data class ReportRequest(
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val threatCategory: ThreatCategory?,
    val note: String?,
)

@Serializable
data class Report(
    val id: String,
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val threatCategory: ThreatCategory,
    val note: String? = null,
    val status: ReportStatus,
    val reporters: Long,
    val reportersNeeded: Int,
    val createdAt: String,
)
