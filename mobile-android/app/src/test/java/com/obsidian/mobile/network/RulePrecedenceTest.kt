package com.obsidian.mobile.network

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

data class MockRule(
    val indicator: String,
    val listType: ListType,
    val expiresAt: Instant? = null
)

class RulePrecedenceTest {

    private fun resolveVerdict(
        indicator: String,
        rules: List<MockRule>,
        currentTime: Instant = Instant.now()
    ): ThreatVerdict {
        val lowerIndicator = indicator.lowercase()

        // 1. Whitelist check (active / unexpired)
        val whitelistMatch = rules.firstOrNull {
            it.listType == ListType.WHITELIST &&
                    it.indicator.equals(lowerIndicator, ignoreCase = true) &&
                    (it.expiresAt == null || it.expiresAt.isAfter(currentTime))
        }
        if (whitelistMatch != null) {
            return ThreatVerdict.CLEAN
        }

        // 2. Blacklist check
        val blacklistMatch = rules.firstOrNull {
            it.listType == ListType.BLACKLIST &&
                    it.indicator.equals(lowerIndicator, ignoreCase = true)
        }
        if (blacklistMatch != null) {
            return ThreatVerdict.MALICIOUS
        }

        // 3. Fallback to scanning
        return ThreatVerdict.PENDING
    }

    @Test
    fun testActiveWhitelistOverridesBlacklist() {
        val rules = listOf(
            MockRule("secure-login-verify.co", ListType.WHITELIST, Instant.now().plusSeconds(900)),
            MockRule("secure-login-verify.co", ListType.BLACKLIST)
        )
        val verdict = resolveVerdict("secure-login-verify.co", rules)
        assertEquals(ThreatVerdict.CLEAN, verdict)
    }

    @Test
    fun testExpiredWhitelistFallsBackToBlacklist() {
        val rules = listOf(
            // Expired 10 seconds ago
            MockRule("secure-login-verify.co", ListType.WHITELIST, Instant.now().minusSeconds(10)),
            MockRule("secure-login-verify.co", ListType.BLACKLIST)
        )
        val verdict = resolveVerdict("secure-login-verify.co", rules)
        assertEquals(ThreatVerdict.MALICIOUS, verdict)
    }

    @Test
    fun testBlacklistMatchesMaliciousDomain() {
        val rules = listOf(
            MockRule("paypa1-support.net", ListType.BLACKLIST)
        )
        val verdict = resolveVerdict("paypa1-support.net", rules)
        assertEquals(ThreatVerdict.MALICIOUS, verdict)
    }

    @Test
    fun testUnmatchedDomainReturnsPendingForScan() {
        val rules = listOf(
            MockRule("paypa1-support.net", ListType.BLACKLIST)
        )
        val verdict = resolveVerdict("unknown-domain.com", rules)
        assertEquals(ThreatVerdict.PENDING, verdict)
    }
}
