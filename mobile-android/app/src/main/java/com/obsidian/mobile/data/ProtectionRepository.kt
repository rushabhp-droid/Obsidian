package com.obsidian.mobile.data

import com.obsidian.mobile.firewall.FirewallPolicy
import com.obsidian.mobile.network.ActivityItem
import com.obsidian.mobile.network.ActivityKind
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.Blocklist
import com.obsidian.mobile.network.FileScanRequest
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.MessageScanRequest
import com.obsidian.mobile.network.Report
import com.obsidian.mobile.network.ReportRequest
import com.obsidian.mobile.network.RuleRequest
import com.obsidian.mobile.network.ScanResult
import com.obsidian.mobile.network.ServerStatus
import com.obsidian.mobile.network.ThreatCategory
import com.obsidian.mobile.network.UrlScanRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Scans, Activity, reports and the firewall's rules. The blocklist is cached in
 * a private file so the firewall keeps blocking when the server can't be reached.
 */
class ProtectionRepository(private val client: ApiClient, private val blocklistFile: File) {

    private val json = Json { ignoreUnknownKeys = true }

    private val _activity = MutableStateFlow<List<ActivityItem>?>(null)
    val activity: StateFlow<List<ActivityItem>?> = _activity.asStateFlow()

    private val _reports = MutableStateFlow<List<Report>?>(null)
    val reports: StateFlow<List<Report>?> = _reports.asStateFlow()

    private val _blocklist = MutableStateFlow(loadCachedBlocklist())
    val blocklist: StateFlow<Blocklist?> = _blocklist.asStateFlow()

    private val _policy = MutableStateFlow(policyOf(_blocklist.value))
    /** What the firewall and link checks enforce on the phone right now. */
    val policy: StateFlow<FirewallPolicy> = _policy.asStateFlow()

    /** Server and database reachability for the Home connectivity card; null until first checked. */
    data class Connectivity(val status: ServerStatus?, val error: String?, val latencyMs: Long?, val checkedAt: Long)

    private val _connectivity = MutableStateFlow<Connectivity?>(null)
    val connectivity: StateFlow<Connectivity?> = _connectivity.asStateFlow()

    suspend fun checkConnectivity(): Connectivity {
        val started = System.currentTimeMillis()
        val result = try {
            val status = client.call { status() }
            Connectivity(status, null, System.currentTimeMillis() - started, System.currentTimeMillis())
        } catch (e: Exception) {
            Connectivity(null, e.message, null, System.currentTimeMillis())
        }
        _connectivity.value = result
        return result
    }

    suspend fun scanUrl(url: String, source: ActivityKind = ActivityKind.URL_SCAN): ScanResult =
        client.call { scanUrl(UrlScanRequest(url.trim(), source)) }.also { refreshActivityQuietly() }

    suspend fun scanFile(sha256: String, name: String?, size: Long?): ScanResult =
        client.call { scanFile(FileScanRequest(sha256, name, size)) }.also { refreshActivityQuietly() }

    suspend fun scanMessage(request: MessageScanRequest): ScanResult = client.call { scanMessage(request) }

    suspend fun refreshActivity() {
        _activity.value = client.call { activity() }
    }

    suspend fun refreshReports() {
        _reports.value = client.call { reports() }
    }

    suspend fun report(type: IndicatorType, value: String, category: ThreatCategory?, note: String?): Report {
        val report = client.call { report(ReportRequest(type, value.trim(), category, note?.trim()?.ifEmpty { null })) }
        refreshActivityQuietly()
        runCatching { refreshReports() }
        runCatching { syncBlocklist() }
        return report
    }

    suspend fun addRule(type: IndicatorType, value: String, list: ListType) {
        client.call { addRule(RuleRequest(type, value.trim(), list)) }
        syncBlocklist()
    }

    suspend fun deleteRule(id: String) {
        client.call { deleteRule(id) }
        syncBlocklist()
    }

    /** Fetches the shared blocklist and personal rules, caches them and updates [policy]. */
    suspend fun syncBlocklist(): Blocklist {
        val blocklist = client.call { blocklist() }
        _blocklist.value = blocklist
        _policy.value = policyOf(blocklist)
        runCatching { blocklistFile.writeText(json.encodeToString(Blocklist.serializer(), blocklist)) }
        return blocklist
    }

    fun clear() {
        _activity.value = null
        _reports.value = null
        _blocklist.value = null
        _policy.value = FirewallPolicy.EMPTY
        _connectivity.value = null
        blocklistFile.delete()
    }

    private suspend fun refreshActivityQuietly() {
        runCatching { refreshActivity() }
    }

    private fun loadCachedBlocklist(): Blocklist? =
        runCatching { json.decodeFromString(Blocklist.serializer(), blocklistFile.readText()) }.getOrNull()

    private fun policyOf(blocklist: Blocklist?) = blocklist?.let { FirewallPolicy(it.domains, it.ips, it.personal) }
        ?: FirewallPolicy.EMPTY
}
