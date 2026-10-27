package com.obsidian.mobile

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obsidian.mobile.auth.TokenStorage
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageCheckStatus
import com.obsidian.mobile.data.MessageRepository
import com.obsidian.mobile.data.RuleRepository
import com.obsidian.mobile.intercept.UrlExtractor
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.network.ThreatScanner
import com.obsidian.mobile.notify.ThreatAlertManager
import com.obsidian.mobile.theme.ButtonShape
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.ObsidianMobileTheme
import com.obsidian.mobile.theme.OnPrimary
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.ScamError
import com.obsidian.mobile.theme.ScamErrorContainer
import com.obsidian.mobile.theme.Surface
import com.obsidian.mobile.theme.SuspiciousTertiary
import com.obsidian.mobile.theme.SuspiciousTertiaryContainer
import com.obsidian.mobile.ui.components.M3FilledButton
import com.obsidian.mobile.ui.components.M3OutlinedButton
import com.obsidian.mobile.ui.components.M3TonalButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UrlCheckActivity : ComponentActivity() {

    private lateinit var tokenStorage: TokenStorage
    private lateinit var apiClient: ApiClient
    private lateinit var threatScanner: ThreatScanner
    private val ruleRepository = RuleRepository.instance
    private val messageRepository = MessageRepository.instance
    private val currentTargetUrl = androidx.compose.runtime.mutableStateOf("")

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent.dataString ?: ""
        if (newUrl.isNotBlank()) {
            currentTargetUrl.value = newUrl
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStorage = TokenStorage(this)
        apiClient = ApiClient(tokenStorage)
        val threatAlertManager = ThreatAlertManager(this)
        threatScanner = ThreatScanner(
            apiClient = apiClient,
            messageRepository = messageRepository,
            ruleRepository = ruleRepository,
            threatAlertManager = threatAlertManager
        )

        val targetUrl = intent.dataString ?: ""
        if (targetUrl.isBlank()) {
            finish()
            return
        }
        currentTargetUrl.value = targetUrl

        setContent {
            ObsidianMobileTheme {
                val activeUrl = currentTargetUrl.value
                if (activeUrl.isNotBlank()) {
                    UrlCheckScreen(
                        targetUrl = activeUrl,
                        onSafeUrl = { url ->
                            openInExternalBrowser(this, url)
                            finish()
                        },
                        onBlockAndReport = { domain ->
                            ruleRepository.reportScam(apiClient, domain)
                            finish()
                        },
                        onTrustAndBypass = { domain, url ->
                            ruleRepository.bypassThreat(apiClient, domain)
                            openInExternalBrowser(this, url)
                            finish()
                        },
                        onDismiss = {
                            finish()
                        },
                        threatScanner = threatScanner
                    )
                }
            }
        }
    }

    companion object {
        private val KNOWN_BROWSERS = listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.sec.android.app.sbrowser",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.duckduckgo.mobile.android"
        )

        fun openInExternalBrowser(context: Context, url: String) {
            val uri = Uri.parse(url)
            val pm = context.packageManager

            // 1. Try launching with a known installed browser
            for (pkg in KNOWN_BROWSERS) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (intent.resolveActivity(pm) != null) {
                        context.startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    // Try next
                }
            }

            // 2. Query any other installed browser
            val genericIntent = Intent(Intent.ACTION_VIEW, uri)
            val resolveList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(
                    genericIntent,
                    PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(genericIntent, PackageManager.MATCH_ALL)
            }

            val target = resolveList.firstOrNull { it.activityInfo.packageName != context.packageName }
            if (target != null) {
                val launchIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setClassName(target.activityInfo.packageName, target.activityInfo.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(launchIntent)
            } else {
                try {
                    val chooser = Intent.createChooser(genericIntent, "Open with").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
}

@Composable
fun UrlCheckScreen(
    targetUrl: String,
    onSafeUrl: (String) -> Unit,
    onBlockAndReport: suspend (String) -> Unit,
    onTrustAndBypass: suspend (String, String) -> Unit,
    onDismiss: () -> Unit,
    threatScanner: ThreatScanner
) {
    val coroutineScope = rememberCoroutineScope()
    var scanResult by remember { mutableStateOf<MessageCheckItem?>(null) }
    var isAnalyzing by remember { mutableStateOf(true) }
    var actionPending by remember { mutableStateOf(false) }

    val displayDomain = remember(targetUrl) {
        UrlExtractor.extractDomain(targetUrl)
    }

    LaunchedEffect(targetUrl) {
        isAnalyzing = true
        val result = threatScanner.scanUrl(
            url = targetUrl,
            channel = ChannelType.BROWSER,
            sender = "Browser Navigation"
        )
        scanResult = result
        isAnalyzing = false

        // If completely safe, wait a brief moment to show green confirmation then forward
        if (result.status == MessageCheckStatus.NO_THREATS_FOUND) {
            delay(650)
            onSafeUrl(targetUrl)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Top Brand Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_obsidian_logo),
                                contentDescription = "Obsidian Logo",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Obsidian Browser Defense",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel", tint = OnSurfaceVariant)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Scanning State
                    if (isAnalyzing) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = Primary,
                                strokeWidth = 3.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = "Inspecting Web Link",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Checking threat rules, official domain registries, and VirusTotal v3...",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = displayDomain,
                            style = MonospaceBodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Scanned State
                    scanResult?.let { result ->
                        val isScam = result.status == MessageCheckStatus.SCAM
                        val isSuspicious = result.status == MessageCheckStatus.SUSPICIOUS
                        val isSafe = result.status == MessageCheckStatus.NO_THREATS_FOUND

                        if (isSafe) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Link Verified Safe",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF166534)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "No threats found across security intelligence feeds. Opening browser...",
                                fontSize = 13.sp,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Text(
                                text = displayDomain,
                                style = MonospaceBodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            M3FilledButton(
                                onClick = { onSafeUrl(targetUrl) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Open Now")
                            }
                        } else {
                            // Dangerous or Suspicious Alert
                            val bannerColor = if (isScam) ScamError else SuspiciousTertiary
                            val containerBg = if (isScam) ScamErrorContainer else SuspiciousTertiaryContainer

                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(containerBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isScam) Icons.Filled.ReportProblem else Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = bannerColor,
                                    modifier = Modifier.size(40.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = if (isScam) "Dangerous Link Blocked" else "Suspicious Link Warning",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFF1F5F9))
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayDomain,
                                    style = MonospaceBodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = result.explanation,
                                fontSize = 13.sp,
                                color = OnSurfaceVariant,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = result.checkedBy,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = bannerColor,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            // Action Buttons
                            M3FilledButton(
                                onClick = {
                                    actionPending = true
                                    coroutineScope.launch {
                                        onBlockAndReport(displayDomain)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = ScamError,
                                    contentColor = Color.White
                                ),
                                enabled = !actionPending
                            ) {
                                Text(if (actionPending) "Updating blocklist..." else "Report & Block Threat")
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                M3OutlinedButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Text("Back to Safety", fontSize = 13.sp, maxLines = 1)
                                }

                                M3TonalButton(
                                    onClick = {
                                        actionPending = true
                                        coroutineScope.launch {
                                            onTrustAndBypass(displayDomain, targetUrl)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                    enabled = !actionPending
                                ) {
                                    Text("Trust (15 min)", fontSize = 13.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
