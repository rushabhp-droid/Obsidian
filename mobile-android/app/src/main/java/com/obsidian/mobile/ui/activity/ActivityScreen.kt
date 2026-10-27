package com.obsidian.mobile.ui.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.network.ActivityItem
import com.obsidian.mobile.network.ActivityKind
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.Report
import com.obsidian.mobile.network.ReportStatus
import com.obsidian.mobile.network.ThreatCategory
import com.obsidian.mobile.network.Verdict
import com.obsidian.mobile.ui.components.Draft
import com.obsidian.mobile.ui.components.EmptyState
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.MonoText
import com.obsidian.mobile.ui.components.ReportSheet
import com.obsidian.mobile.ui.components.ScorePill
import com.obsidian.mobile.ui.components.VerdictChip
import com.obsidian.mobile.ui.components.categoryLabel
import com.obsidian.mobile.ui.components.hostOf
import com.obsidian.mobile.ui.components.indicatorLabel
import com.obsidian.mobile.ui.components.relativeTime
import com.obsidian.mobile.ui.components.reportOutcome
import com.obsidian.mobile.ui.components.verdictLabel
import kotlinx.coroutines.launch

/** How the Activity list is grouped. */
private enum class Grouping(val label: String) { THREAT("Threat"), STATUS("Status"), SCORE("Score") }

private data class Group(val order: Int, val title: String)

private fun groupOf(item: ActivityItem, grouping: Grouping): Group = when (grouping) {
    Grouping.THREAT -> when (item.threatCategory) {
        ThreatCategory.PHISHING -> Group(0, "Phishing")
        ThreatCategory.MALICIOUS -> Group(1, "Scam / fraud")
        ThreatCategory.MALWARE -> Group(2, "Malware")
        ThreatCategory.SPAM -> Group(3, "Spam")
        ThreatCategory.SAFE, null -> if (item.verdict == Verdict.PENDING) Group(4, "Not known yet") else Group(5, "No threat found")
    }
    Grouping.STATUS -> Group(item.verdict.ordinal, verdictLabel(item.verdict))
    Grouping.SCORE -> when (val score = item.riskScore) {
        null -> Group(4, "Score not known yet")
        in 70..100 -> Group(0, "High risk · 70–100")
        in 40..69 -> Group(1, "Medium risk · 40–69")
        in 1..39 -> Group(2, "Low risk · 1–39")
        else -> Group(3, "No risk found · $score")
    }
}

private fun kindLabel(kind: ActivityKind) = when (kind) {
    ActivityKind.URL_SCAN -> "Link check"
    ActivityKind.LINK_OPENED -> "Link opened"
    ActivityKind.FILE_SCAN -> "File check"
    ActivityKind.MESSAGE -> "Message"
    ActivityKind.REPORT -> "Your report"
}

private fun kindIcon(kind: ActivityKind): ImageVector = when (kind) {
    ActivityKind.URL_SCAN -> Icons.Outlined.Link
    ActivityKind.LINK_OPENED -> Icons.AutoMirrored.Outlined.OpenInNew
    ActivityKind.FILE_SCAN -> Icons.Outlined.Description
    ActivityKind.MESSAGE -> Icons.Outlined.Sms
    ActivityKind.REPORT -> Icons.Outlined.Flag
}

/** What a report or trust action from an Activity entry should be about. */
private fun draftOf(item: ActivityItem): Draft? = when (item.kind) {
    ActivityKind.URL_SCAN, ActivityKind.LINK_OPENED -> hostOf(item.subject)?.let { Draft(IndicatorType.DOMAIN, it) }
    ActivityKind.FILE_SCAN -> item.detail?.let { Draft(IndicatorType.FILE_HASH, it) }
    ActivityKind.MESSAGE -> item.subject.filter { it.isDigit() || it == '+' }.takeIf { it.length >= 6 }
        ?.let { Draft(IndicatorType.PHONE, it) }
        ?: item.subject.takeIf { '@' in it }?.let { Draft(IndicatorType.EMAIL, it) }
    ActivityKind.REPORT -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    var page by rememberSaveable { mutableStateOf(0) }
    var grouping by rememberSaveable { mutableStateOf(Grouping.THREAT) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reportDraft by remember { mutableStateOf<Draft?>(null) }
    var showReport by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ActivityItem?>(null) }
    val activity by AppGraph.protection.activity.collectAsState()
    val reports by AppGraph.protection.reports.collectAsState()

    suspend fun refresh() {
        refreshing = true
        error = null
        try {
            AppGraph.protection.refreshActivity()
            AppGraph.protection.refreshReports()
        } catch (e: ApiException) {
            error = e.message
        } finally {
            refreshing = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(title = { Text("Activity") })
            PrimaryTabRow(selectedTabIndex = page) {
                Tab(selected = page == 0, onClick = { page = 0 }, text = { Text("Activity") })
                Tab(selected = page == 1, onClick = { page = 1 }, text = { Text("My reports") })
            }
            ErrorText(error, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            PullToRefreshBox(isRefreshing = refreshing, onRefresh = { scope.launch { refresh() } }, modifier = Modifier.weight(1f)) {
                if (page == 0) {
                    ActivityList(activity, grouping, onGrouping = { grouping = it }, onSelect = { selected = it })
                } else {
                    ReportList(reports)
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { reportDraft = null; showReport = true },
            icon = { Icon(Icons.Outlined.Flag, null) },
            text = { Text("Report") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    selected?.let { item ->
        ActivityDetail(
            item,
            onDismiss = { selected = null },
            onReport = draftOf(item)?.let { draft -> { selected = null; reportDraft = draft; showReport = true } },
            onTrust = draftOf(item)?.takeIf { it.type == IndicatorType.DOMAIN || it.type == IndicatorType.PHONE ||
                it.type == IndicatorType.EMAIL }?.let { draft ->
                {
                    selected = null
                    scope.launch {
                        try {
                            AppGraph.protection.addRule(draft.type, draft.value, ListType.WHITELIST)
                            snackbar.showSnackbar("${draft.value} is on your allow list")
                        } catch (e: ApiException) {
                            snackbar.showSnackbar(e.message ?: "Couldn't save")
                        }
                    }
                }
            },
        )
    }
    if (showReport) {
        ReportSheet(reportDraft, onDismiss = { showReport = false }, onReported = { report ->
            showReport = false
            page = 1
            scope.launch { snackbar.showSnackbar(reportOutcome(report)) }
        })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityList(
    items: List<ActivityItem>?,
    grouping: Grouping,
    onGrouping: (Grouping) -> Unit,
    onSelect: (ActivityItem) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                Grouping.entries.forEachIndexed { i, option ->
                    SegmentedButton(
                        selected = grouping == option,
                        onClick = { onGrouping(option) },
                        shape = SegmentedButtonDefaults.itemShape(i, Grouping.entries.size),
                    ) { Text("By ${option.label.lowercase()}") }
                }
            }
        }
        if (items != null && items.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.History, "No activity yet",
                    "Links you check, links you open and messages Obsidian looks at will show up here.")
            }
        }
        (items ?: emptyList()).groupBy { groupOf(it, grouping) }.toSortedMap(compareBy { it.order }).forEach { (group, rows) ->
            stickyHeader(key = "h-${group.title}") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(group.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f))
                    Text("${rows.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(rows, key = { it.id }) { item ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(item) },
                    leadingContent = { Icon(kindIcon(item.kind), null) },
                    headlineContent = {
                        if (item.kind == ActivityKind.MESSAGE) Text(item.subject, maxLines = 1) else MonoText(item.subject)
                    },
                    supportingContent = {
                        Text(listOfNotNull(kindLabel(item.kind), relativeTime(item.createdAt),
                            item.threatCategory?.let(::categoryLabel)).joinToString(" · "))
                    },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            VerdictChip(item.verdict)
                            Spacer(Modifier.height(4.dp))
                            ScorePill(item.riskScore)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ReportList(reports: List<Report>?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
        if (reports != null && reports.isEmpty()) {
            item {
                EmptyState(Icons.Outlined.Flag, "No reports yet",
                    "Report a scam site, number or file with the Report button. It's blocked on your phone right away.")
            }
        }
        items(reports ?: emptyList(), key = { it.id }) { report ->
            ListItem(
                leadingContent = { Icon(Icons.Outlined.Flag, null) },
                headlineContent = { MonoText(report.indicatorValue) },
                supportingContent = {
                    Column {
                        Text("${indicatorLabel(report.indicatorType)} · ${categoryLabel(report.threatCategory)} · " +
                            relativeTime(report.createdAt))
                        report.note?.let { Text("“$it”", maxLines = 2) }
                    }
                },
                trailingContent = { ReportStatusChip(report) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReportStatusChip(report: Report) {
    val c = MaterialTheme.colorScheme
    val (text, bg, fg) = when (report.status) {
        ReportStatus.BLOCKED -> Triple("Blocked for everyone", c.errorContainer, c.onErrorContainer)
        ReportStatus.WAITING -> Triple("${report.reporters} of ${report.reportersNeeded} reports", c.surfaceVariant, c.onSurfaceVariant)
        ReportStatus.PROTECTED -> Triple("Admin review", c.tertiaryContainer, c.onTertiaryContainer)
        ReportStatus.DISMISSED -> Triple("Only on your phone", c.surfaceVariant, c.onSurfaceVariant)
    }
    Text(text, style = MaterialTheme.typography.labelMedium, color = fg,
        modifier = Modifier.background(bg, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityDetail(item: ActivityItem, onDismiss: () -> Unit, onReport: (() -> Unit)?, onTrust: (() -> Unit)?) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(kindIcon(item.kind), null)
                Spacer(Modifier.width(8.dp))
                Text(kindLabel(item.kind), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                VerdictChip(item.verdict)
            }
            MonoText(item.subject, maxLines = 4)
            Detail("When", relativeTime(item.createdAt))
            Detail("Threat", categoryLabel(item.threatCategory))
            Detail("Risk score", item.riskScore?.let { "$it / 100" } ?: "Not known yet")
            if (item.vtDetections > 0) Detail("VirusTotal", "${item.vtDetections} scanners flagged it")
            item.detail?.let { Detail(if (item.kind == ActivityKind.MESSAGE) "App" else "Details", it) }
            if (item.verdict == Verdict.PENDING) {
                Text("Security scanners haven't finished with this. Treat it with care until they do.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onReport != null && item.verdict != Verdict.MALICIOUS) {
                    FilledTonalButton(onClick = onReport) { Text("Report & block") }
                }
                if (onTrust != null && item.verdict != Verdict.BYPASSED) {
                    OutlinedButton(onClick = onTrust) {
                        Icon(Icons.Outlined.VerifiedUser, null)
                        Spacer(Modifier.width(6.dp))
                        Text("I trust this")
                    }
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
