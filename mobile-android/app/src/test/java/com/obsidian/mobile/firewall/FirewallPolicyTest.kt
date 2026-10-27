package com.obsidian.mobile.firewall

import com.obsidian.mobile.firewall.FirewallPolicy.Reason
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.PersonalRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirewallPolicyTest {

    private fun rule(type: IndicatorType, value: String, list: ListType) = PersonalRule(value, type, value, list)

    private val policy = FirewallPolicy(
        sharedDomains = listOf("scam.example", "Phish.Example."),
        sharedIps = listOf("203.0.113.7", "198.51.100.9"),
        personal = listOf(
            rule(IndicatorType.DOMAIN, "trusted.scam.example", ListType.WHITELIST),
            rule(IndicatorType.DOMAIN, "ads.example", ListType.BLACKLIST),
            rule(IndicatorType.DOMAIN, "ok.ads.example", ListType.WHITELIST),
            rule(IndicatorType.IP, "198.51.100.9", ListType.WHITELIST),
            rule(IndicatorType.IP, "192.0.2.1", ListType.BLACKLIST),
            rule(IndicatorType.URL, "https://ignored.example/x", ListType.BLACKLIST),
        ),
    )

    @Test
    fun sharedBlocklistCoversSubdomains() {
        assertEquals(Reason.SHARED_BLOCK, policy.decideDomain("login.scam.example").reason)
        assertTrue(policy.decideDomain("SCAM.example.").blocked)
        assertTrue(policy.decideDomain("phish.example").blocked)
    }

    @Test
    fun personalAllowBeatsTheSharedBlocklist() {
        val decision = policy.decideDomain("trusted.scam.example")
        assertFalse(decision.blocked)
        assertEquals(Reason.PERSONAL_ALLOW, decision.reason)
    }

    @Test
    fun theMostSpecificPersonalRuleWins() {
        assertEquals(Reason.PERSONAL_BLOCK, policy.decideDomain("tracker.ads.example").reason)
        assertEquals(Reason.PERSONAL_ALLOW, policy.decideDomain("cdn.ok.ads.example").reason)
    }

    @Test
    fun unknownNamesPass() {
        assertEquals(FirewallPolicy.Decision(false, Reason.NONE, null), policy.decideDomain("example.org"))
        // URL rules are for link checks, not DNS.
        assertFalse(policy.decideDomain("ignored.example").blocked)
    }

    @Test
    fun addresses() {
        assertTrue(policy.decideIp("203.0.113.7").blocked)
        assertEquals(Reason.PERSONAL_ALLOW, policy.decideIp("198.51.100.9").reason)
        assertEquals(Reason.PERSONAL_BLOCK, policy.decideIp("192.0.2.1").reason)
        assertEquals(setOf("203.0.113.7", "192.0.2.1"), policy.blockedIpv4)
    }

    @Test
    fun parentsStopAtTwoLabels() {
        assertEquals(listOf("a.b.example.com", "b.example.com", "example.com"), FirewallPolicy.domainAndParents("a.b.example.com"))
        assertEquals(listOf("example.com"), FirewallPolicy.domainAndParents("example.com"))
    }

    @Test
    fun countsOnlyBlocksAndNoticesRuleChanges() {
        // scam.example, phish.example, ads.example; 203.0.113.7, 198.51.100.9 (shared), 192.0.2.1
        assertEquals(6, policy.blockedCount)
        val same = FirewallPolicy(listOf("SCAM.example"), emptyList(), emptyList())
        assertEquals(same.signature, FirewallPolicy(listOf("scam.example."), emptyList(), emptyList()).signature)
        assertTrue(same.signature != FirewallPolicy(listOf("other.example"), emptyList(), emptyList()).signature)
    }
}
