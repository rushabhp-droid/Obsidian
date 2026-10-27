package com.obsidian.mobile.network

import kotlinx.serialization.Serializable

@Serializable
enum class ChannelType {
    SMS,
    WHATSAPP,
    EMAIL,
    FIREWALL,
    CODE,
    BROWSER
}

@Serializable
enum class ThreatVerdict {
    CLEAN,
    SUSPICIOUS,
    MALICIOUS,
    PENDING,
    BYPASSED
}

@Serializable
enum class ThreatCategory {
    SAFE,
    SPAM,
    PHISHING,
    MALWARE
}

@Serializable
enum class IndicatorType {
    EMAIL,
    PHONE,
    URL,
    DOMAIN,
    IP,
    FILE_HASH
}

@Serializable
enum class ListType {
    BLACKLIST,
    WHITELIST
}

@Serializable
data class UnifiedScanRequest(
    val channel: ChannelType,
    val sender: String? = null,
    val isSavedContact: Boolean = false,
    val text: String? = null,
    val urls: List<String> = emptyList(),
    val fileHashes: List<String> = emptyList()
)

@Serializable
data class IndicatorResult(
    val indicatorType: IndicatorType,
    val value: String,
    val verdict: ThreatVerdict,
    val category: ThreatCategory = ThreatCategory.SAFE,
    val vtDetections: Int = 0,
    val source: String? = null,
    val ruleId: String? = null
)

@Serializable
data class UnifiedScanResponse(
    val verdict: ThreatVerdict,
    val category: ThreatCategory = ThreatCategory.SAFE,
    val vtDetections: Int = 0,
    val scanId: String? = null,
    val indicators: List<IndicatorResult> = emptyList(),
    val indicatorChecked: String? = null,
    val details: String? = null
)

@Serializable
data class ThreatRule(
    val id: String,
    val orgId: String? = null,
    val userId: String? = null,
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val listType: ListType,
    val threatCategory: ThreatCategory = ThreatCategory.PHISHING,
    val source: String = "USER_REPORT", // 'USER_REPORT', 'USER_BYPASS', 'ADMIN_POLICY', 'VIRUSTOTAL'
    val confidenceScore: Int = 100,
    val expiresAt: String? = null, // ISO-8601 string for TTL bypasses
    val updatedAt: String? = null
)

@Serializable
data class ReportRequest(
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val threatCategory: ThreatCategory? = ThreatCategory.PHISHING,
    val sender: String? = null,
    val comment: String? = null
)

@Serializable
data class BypassRequest(
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val ttlMinutes: Int? = null
)

@Serializable
data class AuthLoginRequest(
    val email: String,
    val password: String,
    val turnstileToken: String? = null,
    val client: String = "MOBILE",
    val totpCode: String? = null
)

@Serializable
data class AuthLoginResponse(
    val token: String,
    val expiresAt: String? = null,
    val userEmail: String? = null,
    val orgId: String? = null,
    val role: String? = null
)

@Serializable
data class OrganizationInfo(
    val id: String,
    val name: String,
    val policyAllowUserBypass: Boolean = true
)

@Serializable
data class SessionInfo(
    val email: String,
    val displayName: String? = null,
    val role: String,
    val organization: OrganizationInfo? = null
)

@Serializable
data class ScanLogDto(
    val id: String,
    val channel: ChannelType,
    val sender: String? = null,
    val indicatorChecked: String? = null,
    val verdict: ThreatVerdict,
    val vtPositives: Int = 0,
    val createdAt: String? = null
)
