package com.obsidian.mobile.ui.components

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageCheckStatus
import com.obsidian.mobile.data.RuleRepository
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.network.ThreatScanner
import com.obsidian.mobile.theme.ButtonShape
import com.obsidian.mobile.theme.CardShape
import com.obsidian.mobile.theme.MonospaceBodyMedium
import com.obsidian.mobile.theme.OnPrimary
import com.obsidian.mobile.theme.OnSurface
import com.obsidian.mobile.theme.OnSurfaceVariant
import com.obsidian.mobile.theme.OutlineVariant
import com.obsidian.mobile.theme.Primary
import com.obsidian.mobile.theme.ScamError
import com.obsidian.mobile.theme.ScamErrorContainer
import com.obsidian.mobile.theme.SuspiciousTertiary
import com.obsidian.mobile.theme.SuspiciousTertiaryContainer
import kotlinx.coroutines.launch

@Composable
fun ManualCheckLinkDialog(
    threatScanner: ThreatScanner,
    ruleRepository: RuleRepository,
    apiClient: ApiClient,
    onDismiss: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var urlInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<MessageCheckItem?>(null) }
    var actionFeedback by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Link,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Check a link",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Clear, contentDescription = "Close", tint = OnSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Paste a URL before opening to check if it's safe, suspicious, or malicious.",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Input Field
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = {
                        urlInput = it
                        scanResult = null
                        actionFeedback = null
                    },
                    placeholder = { Text("https://example.com/login", fontSize = 14.sp) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { urlInput = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        } else {
                            IconButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    urlInput = clip.trim()
                                }
                            }) {
                                Icon(Icons.Filled.ContentPaste, contentDescription = "Paste", tint = Primary)
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Scan Action Button
                if (scanResult == null) {
                    M3FilledButton(
                        onClick = {
                            if (urlInput.isNotBlank()) {
                                isScanning = true
                                actionFeedback = null
                                coroutineScope.launch {
                                    val formattedUrl = if (!urlInput.startsWith("http://") && !urlInput.startsWith("https://")) {
                                        "https://$urlInput"
                                    } else {
                                        urlInput
                                    }
                                    val result = threatScanner.scanUrl(
                                        url = formattedUrl,
                                        channel = ChannelType.BROWSER,
                                        sender = "Manual Check"
                                    )
                                    scanResult = result
                                    isScanning = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = urlInput.isNotBlank() && !isScanning
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = OnPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Analyzing link...")
                        } else {
                            Text("Analyze URL")
                        }
                    }
                }

                // Scan Result View
                scanResult?.let { result ->
                    val isScam = result.status == MessageCheckStatus.SCAM
                    val isSuspicious = result.status == MessageCheckStatus.SUSPICIOUS
                    val isSafe = result.status == MessageCheckStatus.NO_THREATS_FOUND

                    val bannerBg = when {
                        isScam -> ScamErrorContainer
                        isSuspicious -> SuspiciousTertiaryContainer
                        else -> Color(0xFFF0FDF4)
                    }
                    val bannerBorder = when {
                        isScam -> ScamError.copy(alpha = 0.4f)
                        isSuspicious -> SuspiciousTertiary.copy(alpha = 0.4f)
                        else -> Color(0xFFBBF7D0)
                    }
                    val bannerText = when {
                        isScam -> ScamError
                        isSuspicious -> SuspiciousTertiary
                        else -> Color(0xFF166534)
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bannerBg)
                            .border(1.dp, bannerBorder, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        isScam -> Icons.Filled.ReportProblem
                                        isSuspicious -> Icons.Filled.Warning
                                        else -> Icons.Filled.CheckCircle
                                    },
                                    contentDescription = null,
                                    tint = bannerText,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = when {
                                        isScam -> "Threat Detected (Blocked)"
                                        isSuspicious -> "Suspicious Link"
                                        else -> "Safe Link Verified"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    color = bannerText,
                                    fontSize = 15.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = result.displayDomain,
                                style = MonospaceBodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = result.explanation,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                lineHeight = 16.sp
                            )

                            actionFeedback?.let { feedback ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = feedback,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Result Actions
                    if (isSafe) {
                        M3FilledButton(
                            onClick = {
                                onOpenUrl(result.fullUrl)
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null, tint = OnPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Open Link Safely")
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            M3FilledButton(
                                onClick = {
                                    coroutineScope.launch {
                                        ruleRepository.reportScam(apiClient, result.displayDomain)
                                        actionFeedback = "Added to organization blocklist."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = ScamError,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("Report & Block", fontSize = 13.sp, maxLines = 1)
                            }

                            M3TonalButton(
                                onClick = {
                                    coroutineScope.launch {
                                        ruleRepository.bypassThreat(apiClient, result.displayDomain)
                                        onOpenUrl(result.fullUrl)
                                        onDismiss()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text("Trust & Open", fontSize = 13.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
