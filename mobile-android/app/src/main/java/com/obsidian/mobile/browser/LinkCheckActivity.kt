package com.obsidian.mobile.browser

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.data.AlertStore
import com.obsidian.mobile.firewall.FirewallPolicy
import com.obsidian.mobile.network.ActivityKind
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.ScanResult
import com.obsidian.mobile.network.Verdict
import com.obsidian.mobile.theme.ObsidianTheme
import com.obsidian.mobile.ui.components.ObsidianMark
import com.obsidian.mobile.ui.components.ScanResultCard
import com.obsidian.mobile.ui.components.hostOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Default browser defense. As the default browser, Obsidian receives every web
 * link the user opens, checks it, and hands it to their real browser. A link is
 * passed straight on only when nothing is wrong with it; a dangerous or
 * unknown one is held with a warning. With the feature off, links pass through.
 */
class LinkCheckActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent?.dataString
        if (url == null || !(url.startsWith("http://", true) || url.startsWith("https://", true))) {
            finish()
            return
        }
        val session = AppGraph.session
        if (!session.settings.value.browserDefense) {
            forward(url)
            return
        }
        setContent {
            val settings by session.settings.collectAsState()
            ObsidianTheme(settings.theme, settings.darkMode) {
                LinkCheck(url, signedIn = session.isSignedIn, onOpen = { forward(url) }, onClose = ::finish)
            }
        }
    }

    private fun forward(url: String) {
        if (!Browsers.open(this, url, AppGraph.session.settings.value.preferredBrowser)) {
            Toast.makeText(this, "Install a web browser to open links.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        recreate()
    }
}

@Composable
private fun LinkCheck(url: String, signedIn: Boolean, onOpen: () -> Unit, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<ScanResult?>(null) }
    var failed by remember { mutableStateOf<String?>(null) }
    var reported by remember { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (!signedIn) {
            failed = "Sign in to Obsidian to check links before they open."
            return@LaunchedEffect
        }
        // Personal allow list: no need to ask the server.
        val host = hostOf(url)
        if (host != null && AppGraph.protection.policy.value.decideDomain(host).reason == FirewallPolicy.Reason.PERSONAL_ALLOW) {
            onOpen()
            return@LaunchedEffect
        }
        val checked = withTimeoutOrNull(8_000) {
            runCatching { AppGraph.protection.scanUrl(url, ActivityKind.LINK_OPENED) }
        }
        val value = checked?.getOrNull()
        when {
            value == null -> {
                // Can't reach the server: fall back to the rules on the phone, and never call it safe.
                val blocked = host?.let { AppGraph.protection.policy.value.decideDomain(it).blocked } ?: false
                failed = if (blocked) "This site is on your block list." else
                    "Obsidian couldn't check this link (${checked?.exceptionOrNull()?.message ?: "it took too long"})."
            }
            value.verdict == Verdict.CLEAN || value.verdict == Verdict.BYPASSED || value.trusted -> onOpen()
            else -> {
                result = value
                if (value.verdict == Verdict.MALICIOUS) {
                    AppGraph.alerts.add("Dangerous link stopped", url, AlertStore.Severity.DANGER)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Card(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ObsidianMark(32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Link check", style = MaterialTheme.typography.titleMedium)
                }
                val current = result
                val error = failed
                when {
                    current != null -> {
                        ScanResultCard(url, current,
                            onReport = null,
                            onTrust = null)
                        if (current.verdict == Verdict.MALICIOUS || current.verdict == Verdict.SUSPICIOUS) {
                            Button(onClick = onClose, Modifier.fillMaxWidth()) { Text("Go back to safety") }
                            if (!reported && current.verdict == Verdict.SUSPICIOUS) {
                                TextButton(onClick = {
                                    scope.launch {
                                        hostOf(url)?.let { runCatching { AppGraph.protection.report(IndicatorType.DOMAIN, it, null, "Opened from a link") } }
                                        reported = true
                                    }
                                }, Modifier.fillMaxWidth()) { Text("Report and block this site") }
                            }
                            TextButton(onClick = onOpen, Modifier.fillMaxWidth()) {
                                Text("Open anyway", color = MaterialTheme.colorScheme.error)
                            }
                        } else {
                            // PENDING: unknown, not safe.
                            Button(onClick = onClose, Modifier.fillMaxWidth()) { Text("Don't open") }
                            TextButton(onClick = onOpen, Modifier.fillMaxWidth()) { Text("Open anyway") }
                            TextButton(onClick = {
                                scope.launch {
                                    hostOf(url)?.let { runCatching { AppGraph.protection.addRule(IndicatorType.DOMAIN, it, ListType.WHITELIST) } }
                                    onOpen()
                                }
                            }, Modifier.fillMaxWidth()) { Text("I trust this site: always open") }
                        }
                    }
                    error != null -> {
                        Text(error, style = MaterialTheme.typography.bodyMedium)
                        Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3)
                        Button(onClick = onClose, Modifier.fillMaxWidth()) { Text("Don't open") }
                        TextButton(onClick = onOpen, Modifier.fillMaxWidth()) { Text("Open anyway") }
                    }
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Checking this link before it opens…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
