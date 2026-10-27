package com.obsidian.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.network.ScanResult
import com.obsidian.mobile.network.Verdict

/** What a check found, in words, with the next steps. */
@Composable
fun ScanResultCard(
    subject: String,
    result: ScanResult,
    onReport: (() -> Unit)?,
    onTrust: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val tone = verdictTone(if (result.trusted) Verdict.BYPASSED else result.verdict)
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tone.container, contentColor = tone.content)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tone.icon, null)
                Spacer(Modifier.width(8.dp))
                Text(headline(result), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ScorePill(result.riskScore)
            }
            MonoText(subject, maxLines = 2)
            Text(explanation(result), style = MaterialTheme.typography.bodyMedium)
            if (onReport != null || onTrust != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onReport != null && result.verdict != Verdict.MALICIOUS) {
                        FilledTonalButton(onClick = onReport) {
                            Icon(Icons.Outlined.Flag, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Report & block")
                        }
                    }
                    if (onTrust != null && !result.trusted && result.verdict != Verdict.CLEAN) {
                        OutlinedButton(onClick = onTrust) {
                            Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("I trust this")
                        }
                    }
                }
            }
        }
    }
}

private fun headline(result: ScanResult): String = when {
    result.trusted -> "You trust this"
    result.verdict == Verdict.MALICIOUS -> "Dangerous: ${categoryLabel(result.category).lowercase()}"
    result.verdict == Verdict.SUSPICIOUS -> "Suspicious"
    result.verdict == Verdict.PENDING -> "Still checking"
    else -> "No threats found"
}

private fun explanation(result: ScanResult): String = when {
    result.trusted -> "It's on your allow list, so Obsidian lets it through."
    result.verdict == Verdict.PENDING ->
        "Security scanners haven't finished with this yet. Treat it with care until they do, and check again in a few minutes."
    else -> when (result.decidedBy) {
        "PERSONAL_RULE" -> "It's on your block list."
        "SHARED_RULE" -> "Other Obsidian users reported it, and it's blocked for everyone."
        "VIRUSTOTAL" -> if (result.vtDetections > 0) {
            "${result.vtDetections} security scanners flagged it on VirusTotal."
        } else {
            "Security scanners on VirusTotal found nothing. That isn't a guarantee it's safe."
        }
        else -> "Nothing is known about it yet."
    }
}
