package com.obsidian.mobile.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Dangerous
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.data.AlertStore
import com.obsidian.mobile.ui.components.EmptyState
import com.obsidian.mobile.ui.components.relativeTime

/** The bell's list: what the app warned about, newest first. Opening it marks everything read. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val alerts by AppGraph.alerts.alerts.collectAsState()
    DisposableEffect(Unit) { onDispose { AppGraph.alerts.markAllRead() } }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Notifications") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (alerts.isNotEmpty()) {
                    IconButton(onClick = { AppGraph.alerts.clear() }) { Icon(Icons.Outlined.ClearAll, "Clear all") }
                }
            },
        )
        if (alerts.isEmpty()) {
            EmptyState(Icons.Outlined.NotificationsNone, "Nothing yet",
                "When Obsidian stops a scam message or a dangerous link, you'll see it here.")
        } else {
            LazyColumn {
                items(alerts, key = { it.id }) { alert ->
                    val c = MaterialTheme.colorScheme
                    ListItem(
                        leadingContent = {
                            when (alert.severity) {
                                AlertStore.Severity.DANGER -> Icon(Icons.Outlined.Dangerous, null, tint = c.error)
                                AlertStore.Severity.WARNING -> Icon(Icons.Outlined.Warning, null, tint = c.tertiary)
                                AlertStore.Severity.INFO -> Icon(Icons.Outlined.Info, null, tint = c.primary)
                            }
                        },
                        headlineContent = {
                            Text(alert.title, fontWeight = if (alert.read) FontWeight.Normal else FontWeight.SemiBold)
                        },
                        supportingContent = { Text(alert.body, maxLines = 3) },
                        trailingContent = { Text(relativeTime(alert.at), style = MaterialTheme.typography.labelSmall) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
