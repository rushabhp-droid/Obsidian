package com.obsidian.mobile.data

import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleRepositoryTest {

    @Test
    fun testAddBlockedRuleRemovesFromTrusted() {
        val repo = RuleRepository()
        val testUrl = "test-phish-domain.com"

        // Initially add to trusted
        repo.addTrustedRule(IndicatorType.URL, testUrl, ttlMinutes = 15)
        assertTrue(repo.trustedRules.value.any { it.indicatorValue == testUrl })

        // Now report as blocked
        repo.addBlockedRule(IndicatorType.URL, testUrl)
        assertTrue(repo.blockedRules.value.any { it.indicatorValue == testUrl })
        assertTrue(repo.trustedRules.value.none { it.indicatorValue == testUrl })
    }

    @Test
    fun testAddTrustedRuleRemovesFromBlocked() {
        val repo = RuleRepository()
        val testDomain = "secure-login-verify.co"

        // Rule is in default blocked rules
        assertTrue(repo.blockedRules.value.any { it.indicatorValue == testDomain })

        // Bypass / trust it
        repo.addTrustedRule(IndicatorType.URL, testDomain, ttlMinutes = 15)
        assertTrue(repo.trustedRules.value.any { it.indicatorValue == testDomain })
        assertTrue(repo.blockedRules.value.none { it.indicatorValue == testDomain })

        val trustedRule = repo.trustedRules.value.first { it.indicatorValue == testDomain }
        assertEquals(ListType.WHITELIST, trustedRule.listType)
        assertEquals("Expires in 15 min", trustedRule.expiresAtText)
    }

    @Test
    fun testRemoveRule() {
        val repo = RuleRepository()
        val testUrl = "custom-delete-test.org"

        repo.addBlockedRule(IndicatorType.URL, testUrl)
        val addedRule = repo.blockedRules.value.first { it.indicatorValue == testUrl }

        repo.removeBlockedRule(addedRule.id)
        assertTrue(repo.blockedRules.value.none { it.id == addedRule.id })
    }
}
