package com.obsidian.mobile.data

import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.BypassRequest
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.ReportRequest
import com.obsidian.mobile.network.ThreatCategory
import com.obsidian.mobile.network.ThreatRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class UiThreatRule(
    val id: String = UUID.randomUUID().toString(),
    val indicatorType: IndicatorType,
    val indicatorValue: String,
    val listType: ListType,
    val subtitle: String,
    val expiresAtText: String? = null // e.g., "Expires in 15 min"
)

class RuleRepository {

    private val _blockedRules = MutableStateFlow<List<UiThreatRule>>(getDefaultBlockedRules())
    val blockedRules: StateFlow<List<UiThreatRule>> = _blockedRules.asStateFlow()

    private val _trustedRules = MutableStateFlow<List<UiThreatRule>>(getDefaultTrustedRules())
    val trustedRules: StateFlow<List<UiThreatRule>> = _trustedRules.asStateFlow()

    suspend fun syncRules(apiClient: ApiClient) {
        withContext(Dispatchers.IO) {
            try {
                val backendRules = apiClient.service.getRules()
                val now = Instant.now()

                val (blocked, trusted) = backendRules.partition { it.listType == ListType.BLACKLIST }

                val mappedBlocked = blocked.map { mapToUiRule(it, now) }
                val mappedTrusted = trusted.map { mapToUiRule(it, now) }

                withContext(Dispatchers.Main) {
                    _blockedRules.value = mappedBlocked
                    _trustedRules.value = mappedTrusted
                }
            } catch (e: Exception) {
                // If network fails, retain cached rules
                e.printStackTrace()
            }
        }
    }

    suspend fun reportRule(
        apiClient: ApiClient?,
        indicatorType: IndicatorType,
        value: String,
        category: ThreatCategory = ThreatCategory.PHISHING,
        source: String = "Reported by you · Today"
    ) {
        val trimmedValue = value.trim()
        val optimisticRule = UiThreatRule(
            indicatorType = indicatorType,
            indicatorValue = trimmedValue,
            listType = ListType.BLACKLIST,
            subtitle = source
        )

        withContext(Dispatchers.Main) {
            _trustedRules.value = _trustedRules.value.filter { !it.indicatorValue.equals(trimmedValue, ignoreCase = true) }
            _blockedRules.value = listOf(optimisticRule) + _blockedRules.value.filter { !it.indicatorValue.equals(trimmedValue, ignoreCase = true) }
        }

        if (apiClient != null) {
            withContext(Dispatchers.IO) {
                try {
                    val req = ReportRequest(indicatorType, trimmedValue, category)
                    val saved = apiClient.service.reportThreat(req)
                    withContext(Dispatchers.Main) {
                        _blockedRules.value = _blockedRules.value.map {
                            if (it.indicatorValue.equals(trimmedValue, ignoreCase = true)) mapToUiRule(saved, Instant.now()) else it
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun reportScam(apiClient: ApiClient?, domain: String) {
        reportRule(apiClient, IndicatorType.DOMAIN, domain, ThreatCategory.PHISHING)
    }

    suspend fun bypassThreat(apiClient: ApiClient?, domain: String, ttlMinutes: Int = 15) {
        bypassRule(apiClient, IndicatorType.DOMAIN, domain, ttlMinutes)
    }

    suspend fun bypassRule(
        apiClient: ApiClient?,
        indicatorType: IndicatorType,
        value: String,
        ttlMinutes: Int? = null,
        source: String = "Trusted by you · Today"
    ) {
        val trimmedValue = value.trim()
        val expiresText = ttlMinutes?.let { "Expires in $it min" }
        val optimisticRule = UiThreatRule(
            indicatorType = indicatorType,
            indicatorValue = trimmedValue,
            listType = ListType.WHITELIST,
            subtitle = source,
            expiresAtText = expiresText
        )

        withContext(Dispatchers.Main) {
            _blockedRules.value = _blockedRules.value.filter { !it.indicatorValue.equals(trimmedValue, ignoreCase = true) }
            _trustedRules.value = listOf(optimisticRule) + _trustedRules.value.filter { !it.indicatorValue.equals(trimmedValue, ignoreCase = true) }
        }

        if (apiClient != null) {
            withContext(Dispatchers.IO) {
                try {
                    val req = BypassRequest(indicatorType, trimmedValue, ttlMinutes)
                    val saved = apiClient.service.bypassThreat(req)
                    withContext(Dispatchers.Main) {
                        _trustedRules.value = _trustedRules.value.map {
                            if (it.indicatorValue.equals(trimmedValue, ignoreCase = true)) mapToUiRule(saved, Instant.now()) else it
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    suspend fun deleteRule(apiClient: ApiClient?, rule: UiThreatRule) {
        withContext(Dispatchers.Main) {
            if (rule.listType == ListType.BLACKLIST) {
                _blockedRules.value = _blockedRules.value.filter { it.id != rule.id }
            } else {
                _trustedRules.value = _trustedRules.value.filter { it.id != rule.id }
            }
        }

        if (apiClient != null) {
            withContext(Dispatchers.IO) {
                try {
                    apiClient.service.deleteRule(rule.id)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Synchronous convenience helpers
    fun addBlockedRule(indicatorType: IndicatorType, value: String, source: String = "Reported by you · Today") {
        val rule = UiThreatRule(
            indicatorType = indicatorType,
            indicatorValue = value,
            listType = ListType.BLACKLIST,
            subtitle = source
        )
        _trustedRules.value = _trustedRules.value.filter { it.indicatorValue != value }
        _blockedRules.value = listOf(rule) + _blockedRules.value.filter { it.indicatorValue != value }
    }

    fun addTrustedRule(
        indicatorType: IndicatorType,
        value: String,
        source: String = "Trusted by you · Today",
        ttlMinutes: Int? = null
    ) {
        val expiresText = ttlMinutes?.let { "Expires in $it min" }
        val rule = UiThreatRule(
            indicatorType = indicatorType,
            indicatorValue = value,
            listType = ListType.WHITELIST,
            subtitle = source,
            expiresAtText = expiresText
        )
        _blockedRules.value = _blockedRules.value.filter { it.indicatorValue != value }
        _trustedRules.value = listOf(rule) + _trustedRules.value.filter { it.indicatorValue != value }
    }

    fun removeBlockedRule(id: String) {
        _blockedRules.value = _blockedRules.value.filter { it.id != id }
    }

    fun removeTrustedRule(id: String) {
        _trustedRules.value = _trustedRules.value.filter { it.id != id }
    }

    private fun mapToUiRule(rule: ThreatRule, now: Instant): UiThreatRule {
        val sourceLabel = when (rule.source) {
            "USER_REPORT" -> "Reported by you"
            "USER_BYPASS" -> "Trusted by you"
            "ADMIN_POLICY" -> "Added by your organization"
            "VIRUSTOTAL" -> "Identified by VirusTotal"
            else -> "Custom rule"
        }

        val expiresText = rule.expiresAt?.let { expiresStr ->
            try {
                val exp = Instant.parse(expiresStr)
                val minutesLeft = ChronoUnit.MINUTES.between(now, exp)
                if (minutesLeft > 0) "Expires in $minutesLeft min" else "Expired"
            } catch (e: Exception) {
                null
            }
        }

        return UiThreatRule(
            id = rule.id,
            indicatorType = rule.indicatorType,
            indicatorValue = rule.indicatorValue,
            listType = rule.listType,
            subtitle = "$sourceLabel · Today",
            expiresAtText = expiresText
        )
    }

    companion object {
        val instance by lazy { RuleRepository() }

        private fun getDefaultBlockedRules(): List<UiThreatRule> = listOf(
            UiThreatRule(
                id = "blk-1",
                indicatorType = IndicatorType.URL,
                indicatorValue = "secure-login-verify.co",
                listType = ListType.BLACKLIST,
                subtitle = "Reported by you · Today"
            ),
            UiThreatRule(
                id = "blk-2",
                indicatorType = IndicatorType.URL,
                indicatorValue = "paypa1-support.net",
                listType = ListType.BLACKLIST,
                subtitle = "Reported by you · 25 Oct"
            ),
            UiThreatRule(
                id = "blk-3",
                indicatorType = IndicatorType.PHONE,
                indicatorValue = "+91 98200 11234",
                listType = ListType.BLACKLIST,
                subtitle = "Reported by you · 24 Oct"
            ),
            UiThreatRule(
                id = "blk-4",
                indicatorType = IndicatorType.URL,
                indicatorValue = "free-recharge-offer.in",
                listType = ListType.BLACKLIST,
                subtitle = "Added by your organization · 20 Oct"
            )
        )

        private fun getDefaultTrustedRules(): List<UiThreatRule> = listOf(
            UiThreatRule(
                id = "tst-1",
                indicatorType = IndicatorType.URL,
                indicatorValue = "bank.example.in",
                listType = ListType.WHITELIST,
                subtitle = "Trusted by you · 26 Oct"
            ),
            UiThreatRule(
                id = "tst-2",
                indicatorType = IndicatorType.EMAIL,
                indicatorValue = "alerts@hdfcbank.net",
                listType = ListType.WHITELIST,
                subtitle = "Trusted by you · 22 Oct"
            ),
            UiThreatRule(
                id = "tst-3",
                indicatorType = IndicatorType.PHONE,
                indicatorValue = "+91 22 6160 6161",
                listType = ListType.WHITELIST,
                subtitle = "Trusted by you · 18 Oct"
            ),
            UiThreatRule(
                id = "tst-4",
                indicatorType = IndicatorType.URL,
                indicatorValue = "intranet.example.com",
                listType = ListType.WHITELIST,
                subtitle = "Trusted by you · Today",
                expiresAtText = "Expires in 15 min"
            )
        )
    }
}
