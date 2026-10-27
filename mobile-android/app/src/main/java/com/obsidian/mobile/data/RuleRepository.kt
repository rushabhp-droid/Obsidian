package com.obsidian.mobile.data

import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.ThreatRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    fun addBlockedRule(indicatorType: IndicatorType, value: String, source: String = "Reported by you · Today") {
        val rule = UiThreatRule(
            indicatorType = indicatorType,
            indicatorValue = value,
            listType = ListType.BLACKLIST,
            subtitle = source
        )
        // Remove from trusted if present
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
        // Remove from blocked if present
        _blockedRules.value = _blockedRules.value.filter { it.indicatorValue != value }
        _trustedRules.value = listOf(rule) + _trustedRules.value.filter { it.indicatorValue != value }
    }

    fun removeBlockedRule(id: String) {
        _blockedRules.value = _blockedRules.value.filter { it.id != id }
    }

    fun removeTrustedRule(id: String) {
        _trustedRules.value = _trustedRules.value.filter { it.id != id }
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
                subtitle = "Trusted by you · ",
                expiresAtText = "Expires in 15 min"
            )
        )
    }
}
