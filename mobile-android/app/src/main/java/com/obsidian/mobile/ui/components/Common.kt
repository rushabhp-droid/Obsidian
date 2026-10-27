package com.obsidian.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.network.ThreatCategory
import com.obsidian.mobile.network.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ---- words ------------------------------------------------------------------------------------------

fun verdictLabel(verdict: Verdict): String = when (verdict) {
    Verdict.MALICIOUS -> "Dangerous"
    Verdict.SUSPICIOUS -> "Suspicious"
    Verdict.PENDING -> "Checking"
    Verdict.CLEAN -> "No threats found"
    Verdict.BYPASSED -> "Trusted by you"
}

fun categoryLabel(category: ThreatCategory?): String = when (category) {
    ThreatCategory.PHISHING -> "Phishing"
    ThreatCategory.MALWARE -> "Malware"
    ThreatCategory.SPAM -> "Spam"
    ThreatCategory.MALICIOUS -> "Scam / fraud"
    ThreatCategory.SAFE, null -> "No threat"
}

/** "just now", "5 min ago", "3 h ago", or a date. Accepts ISO-8601 or epoch millis. */
fun relativeTime(iso: String?): String = iso?.let { runCatching { relativeTime(Instant.parse(it).toEpochMilli()) }.getOrNull() } ?: ""

fun relativeTime(epochMillis: Long): String {
    val seconds = (System.currentTimeMillis() - epochMillis) / 1000
    return when {
        seconds < 45 -> "just now"
        seconds < 3600 -> "${maxOf(1, seconds / 60)} min ago"
        seconds < 86_400 -> "${seconds / 3600} h ago"
        else -> DateTimeFormatter.ofPattern("d MMM").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))
    }
}

fun clockTime(epochMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(epochMillis))

/** Dots replaced so a scam address can't be tapped or auto-linked when shown. */
fun defang(value: String) = value.replace("http", "hxxp").replace(".", "[.]")

// ---- verdicts -----------------------------------------------------------------------------------------

data class Tone(val container: Color, val content: Color, val icon: ImageVector)

@Composable
fun verdictTone(verdict: Verdict): Tone {
    val c = MaterialTheme.colorScheme
    return when (verdict) {
        Verdict.MALICIOUS -> Tone(c.errorContainer, c.onErrorContainer, Icons.Outlined.Dangerous)
        Verdict.SUSPICIOUS -> Tone(c.tertiaryContainer, c.onTertiaryContainer, Icons.Outlined.Warning)
        Verdict.PENDING -> Tone(c.surfaceVariant, c.onSurfaceVariant, Icons.Outlined.HourglassTop)
        Verdict.CLEAN -> Tone(c.surfaceVariant, c.onSurfaceVariant, Icons.Outlined.CheckCircle)
        Verdict.BYPASSED -> Tone(c.secondaryContainer, c.onSecondaryContainer, Icons.Outlined.VerifiedUser)
    }
}

@Composable
fun VerdictChip(verdict: Verdict, modifier: Modifier = Modifier) {
    val tone = verdictTone(verdict)
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tone.container)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(tone.icon, null, Modifier.size(14.dp), tint = tone.content)
        Spacer(Modifier.width(4.dp))
        Text(verdictLabel(verdict), style = MaterialTheme.typography.labelMedium, color = tone.content)
    }
}

/** Risk score 0–100 as a small pill; "–" while unknown. */
@Composable
fun ScorePill(score: Int?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when {
        score == null -> c.surfaceVariant to c.onSurfaceVariant
        score >= 70 -> c.error to c.onError
        score >= 40 -> c.tertiary to c.onTertiary
        else -> c.surfaceVariant to c.onSurfaceVariant
    }
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(score?.toString() ?: "–", style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

// ---- layout ---------------------------------------------------------------------------------------------

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                action?.invoke()
            }
            content()
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun MonoText(text: String, modifier: Modifier = Modifier, maxLines: Int = 1, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier,
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = color,
    )
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    if (message != null) {
        Text(message, modifier, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** The shield mark used on the sign-in and lock screens. */
@Composable
fun ObsidianMark(size: Dp = 56.dp) {
    Surface(
        Modifier.size(size),
        shape = RoundedCornerShape(size / 3.5f),
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Shield, null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
