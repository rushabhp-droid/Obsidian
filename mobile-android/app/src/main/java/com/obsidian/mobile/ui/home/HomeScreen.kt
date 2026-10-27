package com.obsidian.mobile.ui.home

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.GppMaybe
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.browser.Browsers
import com.obsidian.mobile.firewall.FirewallVpnService
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.ScanResult
import com.obsidian.mobile.security.AppLock
import com.obsidian.mobile.ui.MainTab
import com.obsidian.mobile.ui.components.Draft
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.MonoText
import com.obsidian.mobile.ui.components.ReportSheet
import com.obsidian.mobile.ui.components.ScanResultCard
import com.obsidian.mobile.ui.components.SectionCard
import com.obsidian.mobile.ui.components.hostOf
import com.obsidian.mobile.ui.components.relativeTime
import com.obsidian.mobile.ui.components.reportOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activity: FragmentActivity,
    snackbar: SnackbarHostState,
    onOpenNotifications: () -> Unit,
    onOpenTab: (MainTab) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by AppGraph.session.profile.collectAsState()
    val settings by AppGraph.session.settings.collectAsState()
    val hasPin by AppGraph.session.hasPin.collectAsState()
    val firewallRunning by FirewallVpnService.running.collectAsState()
    val connectivity by AppGraph.protection.connectivity.collectAsState()
    val alerts by AppGraph.alerts.alerts.collectAsState()
    var resumeTick by remember { mutableIntStateOf(0) }
    var reportDraft by remember { mutableStateOf<Draft?>(null) }

    // Permissions and default apps change outside the app: look again on every return.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            AppGraph.protection.checkConnectivity()
            delay(30_000)
        }
    }

    val checks = remember(resumeTick, settings, hasPin, firewallRunning, connectivity) {
        protectionChecks(context, settings, hasPin, firewallRunning, connectivity)
    }
    val allDone = checks.all { it.done }
    LaunchedEffect(allDone) {
        if (allDone && !ProtectedToast.shown) {
            ProtectedToast.shown = true
            Toast.makeText(context, "Your phone is protected", Toast.LENGTH_LONG).show()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumeTick++ }
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resumeTick++ }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (FirewallVpnService.consentIntent(context) == null) {
            AppGraph.session.setFirewallWanted(true)
            FirewallVpnService.start(context)
        }
    }
    fun fix(kind: ProtectionCheck.Kind) {
        when (kind) {
            ProtectionCheck.Kind.MESSAGES -> {
                AppGraph.session.setNotificationAuditor(true)
                AppLock.expectReturn()
                external.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            ProtectionCheck.Kind.ALERTS -> permission.launch(Manifest.permission.POST_NOTIFICATIONS)
            ProtectionCheck.Kind.CONTACTS -> permission.launch(Manifest.permission.READ_CONTACTS)
            ProtectionCheck.Kind.FIREWALL -> {
                val consent = FirewallVpnService.consentIntent(context)
                if (consent == null) {
                    AppGraph.session.setFirewallWanted(true)
                    FirewallVpnService.start(context)
                } else {
                    AppLock.expectReturn()
                    vpnConsent.launch(consent)
                }
            }
            ProtectionCheck.Kind.LINKS -> {
                AppGraph.session.setBrowserDefense(true)
                AppLock.expectReturn()
                external.launch(Browsers.defaultBrowserIntent(context))
            }
            ProtectionCheck.Kind.LOCK -> onOpenTab(MainTab.SETTINGS)
            ProtectionCheck.Kind.SERVER -> scope.launch { AppGraph.protection.checkConnectivity() }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                val unread = alerts.count { !it.read }
                IconButton(onClick = onOpenNotifications) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else "$unread") } }) {
                        Icon(Icons.Outlined.Notifications, "Notifications, $unread unread")
                    }
                }
            },
            title = {
                Column {
                    Text("Obsidian", style = MaterialTheme.typography.titleLarge)
                    profile?.let {
                        Text("Hi, ${it.displayName.substringBefore(' ')}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ProtectionCard(checks, onFix = ::fix) }
            item {
                UrlAnalyzer(snackbar, onReport = { reportDraft = it })
            }
            item { FileAnalyzer(onReport = { reportDraft = it }) }
            item { ConnectivityCard(settings.serverUrl, connectivity, onRefresh = { scope.launch { AppGraph.protection.checkConnectivity() } }) }
        }
    }

    reportDraft?.let { draft ->
        ReportSheet(draft, onDismiss = { reportDraft = null }, onReported = { report ->
            reportDraft = null
            scope.launch { snackbar.showSnackbar(reportOutcome(report)) }
        })
    }
}

@Composable
private fun ProtectionCard(checks: List<ProtectionCheck>, onFix: (ProtectionCheck.Kind) -> Unit) {
    val done = checks.count { it.done }
    val all = done == checks.size
    val c = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(
        containerColor = if (all) c.primaryContainer else c.surfaceContainerLow,
        contentColor = if (all) c.onPrimaryContainer else c.onSurface,
    )) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (all) Icons.Outlined.VerifiedUser else Icons.Outlined.GppMaybe, null, Modifier.size(36.dp),
                    tint = if (all) c.onPrimaryContainer else c.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (all) "Your phone is protected" else "Finish setting up protection",
                        style = MaterialTheme.typography.titleLarge)
                    Text("$done of ${checks.size} protections on", style = MaterialTheme.typography.bodyMedium)
                }
            }
            LinearProgressIndicator(progress = { done / checks.size.toFloat() }, Modifier.fillMaxWidth())
            checks.forEach { check ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (check.done) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, null,
                        tint = if (check.done) c.primary else c.tertiary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(check.title, style = MaterialTheme.typography.titleSmall)
                        Text(check.detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                    }
                    if (!check.done) {
                        TextButton(onClick = { onFix(check.kind) }) {
                            Text(if (check.kind == ProtectionCheck.Kind.SERVER) "Retry" else "Turn on")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UrlAnalyzer(snackbar: SnackbarHostState, onReport: (Draft) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var checked by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun check() {
        if (url.isBlank()) return
        scope.launch {
            busy = true
            error = null
            result = null
            try {
                result = AppGraph.protection.scanUrl(url)
                checked = url.trim()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busy = false
            }
        }
    }

    SectionCard("Check a link", icon = Icons.Outlined.Link, subtitle = "Paste a link from a message or email before you open it") {
        OutlinedTextField(
            value = url, onValueChange = { url = it }, singleLine = true,
            placeholder = { Text("https://…") },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { check() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = ::check, enabled = !busy && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Check link")
        }
        ErrorText(error)
        val current = result
        val subject = checked
        if (current != null && subject != null) {
            ScanResultCard(
                subject, current,
                onReport = { onReport(Draft(IndicatorType.DOMAIN, hostOf(subject) ?: subject)) },
                onTrust = hostOf(subject)?.let { host ->
                    {
                        scope.launch {
                            try {
                                AppGraph.protection.addRule(IndicatorType.DOMAIN, host, ListType.WHITELIST)
                                result = AppGraph.protection.scanUrl(subject)
                                snackbar.showSnackbar("$host is on your allow list")
                            } catch (e: ApiException) {
                                error = e.message
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun FileAnalyzer(onReport: (Draft) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf<String?>(null) }
    var sha256 by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            error = null
            result = null
            try {
                var size: Long? = null
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { if (it.moveToFirst()) { name = it.getString(0); size = it.getLong(1) } }
                val hash = withContext(Dispatchers.IO) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            digest.update(buffer, 0, read)
                        }
                    } ?: error("Can't read that file.")
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                sha256 = hash
                result = AppGraph.protection.scanFile(hash, name, size)
            } catch (e: ApiException) {
                error = e.message
            } catch (e: Exception) {
                error = e.message ?: "Can't read that file."
            } finally {
                busy = false
            }
        }
    }

    SectionCard("Check a file", icon = Icons.Outlined.Description,
        subtitle = "The file stays on your phone. Only its fingerprint (SHA-256) is checked.") {
        OutlinedButton(
            onClick = {
                AppLock.expectReturn()
                picker.launch(arrayOf("*/*"))
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.UploadFile, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Choose a file")
            }
        }
        ErrorText(error)
        val current = result
        val hash = sha256
        if (current != null && hash != null) {
            ScanResultCard(name ?: hash, current,
                onReport = { onReport(Draft(IndicatorType.FILE_HASH, hash)) }, onTrust = null)
            MonoText("SHA-256 ${hash.take(16)}…${hash.takeLast(8)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectivityCard(
    serverUrl: String,
    connectivity: com.obsidian.mobile.data.ProtectionRepository.Connectivity?,
    onRefresh: () -> Unit,
) {
    SectionCard(
        "Server and database",
        icon = Icons.Outlined.Storage,
        subtitle = serverUrl.ifBlank { "No server set" },
        action = { IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Check again") } },
    ) {
        val status = connectivity?.status
        StatusLine("Server", when {
            connectivity == null -> null
            status != null -> true
            else -> false
        }, connectivity?.latencyMs?.let { "$it ms" } ?: connectivity?.error)
        HorizontalDivider()
        StatusLine("Database", status?.let { it.database == "UP" }, null)
        StatusLine("Cache", status?.let { it.cache == "UP" }, null)
        StatusLine("VirusTotal", status?.virusTotalConfigured,
            status?.takeIf { !it.virusTotalConfigured }?.let { "unknown links stay \"checking\"" })
        connectivity?.let {
            Text("Checked ${relativeTime(it.checkedAt)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusLine(label: String, up: Boolean?, detail: String?) {
    val c = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(96.dp))
        Text(
            when (up) { null -> "…"; true -> "Connected"; false -> "Not connected" },
            style = MaterialTheme.typography.labelLarge,
            color = when (up) { true -> c.primary; false -> c.error; null -> c.onSurfaceVariant },
        )
        if (detail != null) {
            Spacer(Modifier.width(8.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, maxLines = 1,
                modifier = Modifier.weight(1f))
        }
    }
    Spacer(Modifier.height(2.dp))
}
