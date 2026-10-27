package com.obsidian.mobile.network

import kotlinx.serialization.Serializable

@Serializable
enum class ChannelType {
    SMS,
    WHATSAPP,
    EMAIL
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
    val sender: String,
    val isSavedContact: Boolean,
    val text: String? = null,
    val urls: List<String> = emptyList(),
    val fileHashes: List<String> = emptyList()
)

@Serializable
data class UnifiedScanResponse(
    val verdict: ThreatVerdict,
    val category: ThreatCategory = ThreatCategory.SAFE,
    val vtDetections: Int = 0,
    val indicatorChecked: String? = null,
    val details: String? = null
)

@Serializable
data class ThreatRule(
    val id: String,
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val listType: ListType,
    val threatCategory: ThreatCategory = ThreatCategory.MALICIOUS,
    val source: String, // 'USER_REPORT', 'USER_BYPASS', 'ADMIN_POLICY', 'VIRUSTOTAL'
    val confidenceScore: Int = 100,
    val expiresAt: String? = null, // ISO-8601 string for TTL bypasses
    val updatedAt: String? = null
)

@Serializable
data class ReportRequest(
    val indicatorType: IndicatorType,
    val indicatorValue: String,
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
    val totpCode: String? = null
)

@Serializable
data class AuthLoginResponse(
    val token: String,
    val userEmail: String,
    val orgId: String? = null,
    val role: String
)
