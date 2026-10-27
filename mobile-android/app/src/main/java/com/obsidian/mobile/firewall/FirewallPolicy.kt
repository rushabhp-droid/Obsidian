package com.obsidian.mobile.firewall

import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.PersonalRule

/**
 * What the phone firewall blocks. Same order as the server's link checks:
 * the user's own whitelist, then their blacklist, then the shared blocklist.
 * A rule for example.com also covers login.example.com; the most specific
 * personal rule wins.
 */
class FirewallPolicy(
    sharedDomains: Collection<String>,
    sharedIps: Collection<String>,
    personal: Collection<PersonalRule>,
) {

    enum class Reason { PERSONAL_ALLOW, PERSONAL_BLOCK, SHARED_BLOCK, NONE }

    data class Decision(val blocked: Boolean, val reason: Reason, val matched: String?)

    private val sharedDomains = sharedDomains.map(::normalize).toSet()
    private val sharedIps = sharedIps.map(::normalize).toSet()
    private val personalDomains = personal.filter { it.indicatorType == IndicatorType.DOMAIN }
        .associate { normalize(it.indicatorValue) to it.listType }
    private val personalIps = personal.filter { it.indicatorType == IndicatorType.IP }
        .associate { normalize(it.indicatorValue) to it.listType }

    /** IPv4 addresses the VPN must route to itself so it can drop them. */
    val blockedIpv4: Set<String> =
        (this.sharedIps + personalIps.filterValues { it == ListType.BLACKLIST }.keys)
            .filter { it.matches(IPV4) && personalIps[it] != ListType.WHITELIST }
            .toSet()

    /** Sites and addresses blocked: the shared list plus the user's own blacklist. */
    val blockedCount: Int =
        (this.sharedDomains + personalDomains.filterValues { it == ListType.BLACKLIST }.keys).size +
            (this.sharedIps + personalIps.filterValues { it == ListType.BLACKLIST }.keys).size

    /** Changes whenever any rule does; the VPN restarts on a change so Android's DNS cache is dropped. */
    val signature: Int = listOf(this.sharedDomains, this.sharedIps, personalDomains, personalIps).hashCode()

    fun decideDomain(name: String): Decision {
        val candidates = domainAndParents(normalize(name))
        for (candidate in candidates) {
            when (personalDomains[candidate]) {
                ListType.WHITELIST -> return Decision(false, Reason.PERSONAL_ALLOW, candidate)
                ListType.BLACKLIST -> return Decision(true, Reason.PERSONAL_BLOCK, candidate)
                null -> Unit
            }
        }
        candidates.firstOrNull { it in sharedDomains }?.let { return Decision(true, Reason.SHARED_BLOCK, it) }
        return Decision(false, Reason.NONE, null)
    }

    fun decideIp(address: String): Decision {
        val ip = normalize(address)
        return when {
            personalIps[ip] == ListType.WHITELIST -> Decision(false, Reason.PERSONAL_ALLOW, ip)
            personalIps[ip] == ListType.BLACKLIST -> Decision(true, Reason.PERSONAL_BLOCK, ip)
            ip in sharedIps -> Decision(true, Reason.SHARED_BLOCK, ip)
            else -> Decision(false, Reason.NONE, null)
        }
    }

    companion object {
        val EMPTY = FirewallPolicy(emptyList(), emptyList(), emptyList())
        private val IPV4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

        private fun normalize(value: String) = value.trim().trimEnd('.').lowercase()

        /** login.example.com → [login.example.com, example.com]; stops at two labels. */
        fun domainAndParents(domain: String): List<String> {
            val values = mutableListOf(domain)
            var current = domain
            while (current.count { it == '.' } > 1) {
                current = current.substringAfter('.')
                values += current
            }
            return values
        }
    }
}
