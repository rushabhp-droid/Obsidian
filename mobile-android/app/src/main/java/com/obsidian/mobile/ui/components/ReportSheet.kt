package com.obsidian.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.Report
import com.obsidian.mobile.network.ReportStatus
import com.obsidian.mobile.network.ThreatCategory
import kotlinx.coroutines.launch

/** What the user typed, made into the indicator a report or rule needs (a link's site for a website report). */
data class Draft(val type: IndicatorType, val value: String)

fun indicatorLabel(type: IndicatorType) = when (type) {
    IndicatorType.DOMAIN -> "Website"
    IndicatorType.URL -> "Link"
    IndicatorType.IP -> "IP address"
    IndicatorType.EMAIL -> "Email sender"
    IndicatorType.PHONE -> "Phone number"
    IndicatorType.FILE_HASH -> "File fingerprint"
}

/** The host of a link, for blocking the whole site. */
fun hostOf(url: String): String? =
    runCatching { java.net.URI(if ("://" in url) url else "https://$url").host?.lowercase()?.removePrefix("www.") }.getOrNull()

fun reportOutcome(report: Report): String = when (report.status) {
    ReportStatus.BLOCKED -> "Blocked for everyone. Thanks for reporting."
    ReportStatus.WAITING -> "Blocked on your phone. It's blocked for everyone once security scanners confirm it or " +
        "${report.reportersNeeded} people report it (${report.reporters} so far)."
    ReportStatus.PROTECTED -> "Blocked on your phone. This is a well-known site, so an Obsidian admin will review it " +
        "before it's blocked for anyone else."
    ReportStatus.DISMISSED -> "Blocked on your phone. An admin decided not to block it for everyone."
}

/**
 * The report panel: block something on this phone right away and report it.
 * It's blocked for everyone once VirusTotal confirms it, enough people report it, or an admin approves it.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportSheet(initial: Draft?, onDismiss: () -> Unit, onReported: (Report) -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(initial?.type ?: IndicatorType.DOMAIN) }
    var value by remember { mutableStateOf(initial?.value ?: "") }
    var category by remember { mutableStateOf(ThreatCategory.PHISHING) }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Report something malicious", style = MaterialTheme.typography.titleLarge)
            Text(
                "It's blocked on your phone right away. Once security scanners confirm it, or a few people report it, " +
                    "it's blocked for every Obsidian user and on protected company networks.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("What is it?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(IndicatorType.DOMAIN, IndicatorType.URL, IndicatorType.IP, IndicatorType.EMAIL, IndicatorType.PHONE,
                    IndicatorType.FILE_HASH).forEach {
                    FilterChip(selected = type == it, onClick = { type = it }, label = { Text(indicatorLabel(it)) })
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(indicatorLabel(type)) },
                placeholder = { Text(placeholder(type)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = when (type) {
                    IndicatorType.PHONE -> KeyboardType.Phone
                    IndicatorType.EMAIL -> KeyboardType.Email
                    IndicatorType.URL, IndicatorType.DOMAIN -> KeyboardType.Uri
                    else -> KeyboardType.Text
                }),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("What's wrong with it?", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(ThreatCategory.PHISHING, ThreatCategory.MALICIOUS, ThreatCategory.MALWARE, ThreatCategory.SPAM).forEach {
                    FilterChip(selected = category == it, onClick = { category = it }, label = { Text(categoryLabel(it)) })
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500) }, label = { Text("What happened? (optional)") },
                minLines = 2, modifier = Modifier.fillMaxWidth(),
            )
            ErrorText(error)
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            val cleaned = if (type == IndicatorType.DOMAIN) hostOf(value) ?: value else value
                            val report = AppGraph.protection.report(type, cleaned, category, note)
                            onReported(report)
                        } catch (e: ApiException) {
                            error = e.message
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = !busy && value.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Report and block") }
        }
    }
}

private fun placeholder(type: IndicatorType) = when (type) {
    IndicatorType.DOMAIN -> "parcel-fee.example"
    IndicatorType.URL -> "https://parcel-fee.example/pay"
    IndicatorType.IP -> "203.0.113.7"
    IndicatorType.EMAIL -> "billing@invoice.example"
    IndicatorType.PHONE -> "+15550100"
    IndicatorType.FILE_HASH -> "64 hexadecimal characters"
}
