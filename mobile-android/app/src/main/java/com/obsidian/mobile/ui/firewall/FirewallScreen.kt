package com.obsidian.mobile.ui.firewall

import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.firewall.FirewallLog
import com.obsidian.mobile.firewall.FirewallPolicy
import com.obsidian.mobile.firewall.FirewallVpnService
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.PersonalRule
import com.obsidian.mobile.security.AppLock
import com.obsidian.mobile.ui.components.EmptyState
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.MonoText
import com.obsidian.mobile.ui.components.clockTime
import com.obsidian.mobile.ui.components.hostOf
import com.obsidian.mobile.ui.components.indicatorLabel
import com.obsidian.mobile.ui.components.relativeTime
import kotlinx.coroutines.launch

private enum class TrafficFilter(val label: String) { ALL("All"), OUTGOING("Outgoing"), INCOMING("Incoming"), BLOCKED("Blocked") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirewallScreen(activity: FragmentActivity, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by FirewallVpnService.running.collectAsState()
    val blocklist by AppGraph.protection.blocklist.collectAsState()
    val policy by AppGraph.protection.policy.collectAsState()
    var page by rememberSaveable { mutableStateOf(0) }
    var addRule by remember { mutableStateOf<RuleDraft?>(null) }
    var syncing by remember { mutableStateOf(false) }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FirewallVpnService.consentIntent(context) == null) {
            AppGraph.session.setFirewallWanted(true)
            FirewallVpnService.start(context)
        } else {
            scope.launch { snackbar.showSnackbar("The firewall needs permission to set up a local VPN.") }
        }
    }
    fun setFirewall(on: Boolean) {
        if (!on) {
            AppGraph.session.setFirewallWanted(false)
            FirewallVpnService.stop(context)
            return
        }
        val intent = FirewallVpnService.consentIntent(context)
        if (intent == null) {
            AppGraph.session.setFirewallWanted(true)
            FirewallVpnService.start(context)
        } else {
            AppLock.expectReturn()
            consent.launch(intent)
        }
    }
    fun sync() = scope.launch {
        syncing = true
        try {
            AppGraph.protection.syncBlocklist()
        } catch (e: ApiException) {
            snackbar.showSnackbar(e.message ?: "Couldn't update the rules")
        } finally {
            syncing = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Firewall") },
                actions = { IconButton(onClick = { sync() }, enabled = !syncing) { Icon(Icons.Outlined.Sync, "Update rules") } },
            )
            FirewallSwitchCard(running, policy, blocklist?.generatedAt, onToggle = ::setFirewall)
            PrivateDnsWarning()
            PrimaryTabRow(selectedTabIndex = page) {
                Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("Rules") })
                Tab(selected = page == 1, onClick = { page = 1 }, text = { Text("Requests") })
            }
            if (page == 0) {
                RulesPage(blocklist?.personal, blocklist?.domains.orEmpty(), blocklist?.ips.orEmpty(), snackbar)
            } else {
                RequestsPage(running, onRule = { addRule = it })
            }
        }
        if (page == 0) {
            ExtendedFloatingActionButton(
                onClick = { addRule = RuleDraft(IndicatorType.DOMAIN, "", ListType.BLACKLIST) },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text("Add rule") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }

    addRule?.let { draft ->
        RuleDialog(draft, onDismiss = { addRule = null }, onSaved = { saved ->
            addRule = null
            scope.launch {
                snackbar.showSnackbar(if (saved.listType == ListType.BLACKLIST) "${saved.value} is blocked" else "${saved.value} is allowed")
            }
        })
    }
}

@Composable
private fun FirewallSwitchCard(running: Boolean, policy: FirewallPolicy, syncedAt: String?, onToggle: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (running) c.primaryContainer else c.surfaceContainerLow,
            contentColor = if (running) c.onPrimaryContainer else c.onSurface,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(32.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (running) "Firewall is on" else "Firewall is off", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (running) "Blocking ${policy.blockedCount} ${if (policy.blockedCount == 1) "site or address" else "sites and addresses"} in every app" else
                        "Blocks scam and malware sites in every app, using a local VPN that only handles DNS",
                    style = MaterialTheme.typography.bodySmall,
                )
                syncedAt?.let { Text("Rules updated ${relativeTime(it)}", style = MaterialTheme.typography.bodySmall) }
            }
            Switch(checked = running, onCheckedChange = onToggle)
        }
    }
}

/** Private DNS in "hostname" mode sends lookups over TLS, around the firewall. */
@Composable
private fun PrivateDnsWarning() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val strict = remember {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.activeNetwork?.let { cm.getLinkProperties(it)?.privateDnsServerName } != null
    }
    if (!strict) return
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null)
            Spacer(Modifier.width(8.dp))
            Text("Private DNS is set to a provider, so lookups go around the firewall. Set it to Automatic in Settings.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                AppLock.expectReturn()
                context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }) { Text("Open") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RulesPage(personal: List<PersonalRule>?, sharedDomains: List<String>, sharedIps: List<String>, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var list by rememberSaveable { mutableStateOf(ListType.BLACKLIST) }
    var showShared by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<PersonalRule?>(null) }
    val rules = personal.orEmpty().filter { it.listType == list }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                listOf(ListType.BLACKLIST, ListType.WHITELIST).forEachIndexed { i, option ->
                    val count = personal.orEmpty().count { it.listType == option }
                    SegmentedButton(selected = list == option, onClick = { list = option },
                        shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(if (option == ListType.BLACKLIST) "Blacklist ($count)" else "Whitelist ($count)")
                    }
                }
            }
        }
        if (list == ListType.BLACKLIST) {
            item {
                ListItem(
                    modifier = Modifier.clickable { showShared = !showShared },
                    leadingContent = { Icon(Icons.Outlined.Public, null) },
                    headlineContent = { Text("Shared blocklist") },
                    supportingContent = {
                        Text("${sharedDomains.size} sites and ${sharedIps.size} addresses reported by Obsidian users and " +
                            "confirmed. ${if (showShared) "Tap to hide." else "Tap to see them."}")
                    },
                )
                if (showShared) {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (sharedDomains + sharedIps).forEach { MonoText(it, Modifier.padding(vertical = 2.dp)) }
                    }
                }
                HorizontalDivider()
            }
        }
        if (personal != null && rules.isEmpty()) {
            item {
                EmptyState(if (list == ListType.BLACKLIST) Icons.Outlined.Block else Icons.Outlined.CheckCircle,
                    if (list == ListType.BLACKLIST) "Nothing blocked by you yet" else "Nothing allowed by you yet",
                    if (list == ListType.BLACKLIST) "Add a site or address to block it in every app, or report one from Activity."
                    else "Allow a site that Obsidian blocks by mistake. Your allow list beats the shared blocklist on your phone.")
            }
        }
        items(rules, key = { it.id }) { rule ->
            ListItem(
                leadingContent = { Icon(if (rule.listType == ListType.BLACKLIST) Icons.Outlined.Block else Icons.Outlined.CheckCircle, null) },
                headlineContent = { MonoText(rule.indicatorValue) },
                supportingContent = { Text("${indicatorLabel(rule.indicatorType)} · added ${relativeTime(rule.createdAt)}") },
                trailingContent = { IconButton(onClick = { deleting = rule }) { Icon(Icons.Outlined.Delete, "Remove") } },
            )
        }
    }

    deleting?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove this rule?") },
            text = { Text("${rule.indicatorValue} will no longer be ${if (rule.listType == ListType.BLACKLIST) "blocked" else "allowed"} " +
                "by your own rules. The shared blocklist still applies.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch {
                        try {
                            AppGraph.protection.deleteRule(rule.id)
                        } catch (e: ApiException) {
                            snackbar.showSnackbar(e.message ?: "Couldn't remove it")
                        }
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RequestsPage(running: Boolean, onRule: (RuleDraft) -> Unit) {
    val entries by FirewallLog.entries.collectAsState()
    var filter by rememberSaveable { mutableStateOf(TrafficFilter.ALL) }
    val shown = entries.filter {
        when (filter) {
            TrafficFilter.ALL -> true
            TrafficFilter.OUTGOING -> it.direction == FirewallLog.Direction.OUTGOING
            TrafficFilter.INCOMING -> it.direction == FirewallLog.Direction.INCOMING
            TrafficFilter.BLOCKED -> it.blocked
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TrafficFilter.entries.forEach {
                    FilterChip(selected = filter == it, onClick = { filter = it }, label = { Text(it.label) })
                }
            }
            if (entries.isNotEmpty()) IconButton(onClick = { FirewallLog.clear() }) { Icon(Icons.Outlined.ClearAll, "Clear") }
        }
        if (shown.isEmpty()) {
            EmptyState(Icons.Outlined.Public,
                if (running) "Nothing here yet" else "The firewall is off",
                if (running) "As apps look up sites, each request and its answer shows up here. The list stays on your phone."
                else "Turn on the firewall to see which sites your apps contact, and block the ones you don't want.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { entry ->
                    val c = MaterialTheme.colorScheme
                    ListItem(
                        modifier = Modifier.clickable {
                            val ip = entry.host.any { it == ':' } || entry.host.all { it.isDigit() || it == '.' }
                            onRule(RuleDraft(if (ip) IndicatorType.IP else IndicatorType.DOMAIN, entry.host,
                                if (entry.blocked) ListType.WHITELIST else ListType.BLACKLIST))
                        },
                        leadingContent = {
                            Icon(
                                if (entry.direction == FirewallLog.Direction.OUTGOING) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward,
                                if (entry.direction == FirewallLog.Direction.OUTGOING) "Outgoing" else "Incoming",
                                tint = if (entry.blocked) c.error else c.onSurfaceVariant,
                            )
                        },
                        headlineContent = { MonoText(entry.host) },
                        supportingContent = {
                            Text(listOfNotNull(entry.app ?: "Unknown app", clockTime(entry.at), entry.detail).joinToString(" · "),
                                maxLines = 2)
                        },
                        trailingContent = {
                            Text(
                                if (entry.blocked) "Blocked" else "Allowed",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (entry.blocked) c.error else c.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

data class RuleDraft(val type: IndicatorType, val value: String, val listType: ListType)

@Composable
private fun RuleDialog(initial: RuleDraft, onDismiss: () -> Unit, onSaved: (RuleDraft) -> Unit) {
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initial.type) }
    var value by remember { mutableStateOf(initial.value) }
    var list by remember { mutableStateOf(initial.listType) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.value.isEmpty()) "Add a rule" else if (list == ListType.BLACKLIST) "Block this?" else "Allow this?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(IndicatorType.DOMAIN, IndicatorType.IP).forEachIndexed { i, option ->
                        SegmentedButton(selected = type == option, onClick = { type = option },
                            shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(indicatorLabel(option)) }
                    }
                }
                OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(autoCorrectEnabled = false,
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                    label = { Text(indicatorLabel(type)) },
                    placeholder = { Text(if (type == IndicatorType.DOMAIN) "ads.example.com" else "203.0.113.7") })
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(ListType.BLACKLIST, ListType.WHITELIST).forEachIndexed { i, option ->
                        SegmentedButton(selected = list == option, onClick = { list = option },
                            shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (option == ListType.BLACKLIST) "Block" else "Allow") }
                    }
                }
                Text(
                    if (type == IndicatorType.DOMAIN) "Covers its subdomains too, such as login.${value.ifBlank { "example.com" }}."
                    else "IPv4 addresses are blocked for every app; IPv6 addresses only in link checks.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && value.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        val cleaned = if (type == IndicatorType.DOMAIN) hostOf(value.trim()) ?: value.trim() else value.trim()
                        try {
                            AppGraph.protection.addRule(type, cleaned, list)
                            onSaved(RuleDraft(type, cleaned, list))
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
