package com.obsidian.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.security.AppLock
import com.obsidian.mobile.ui.activity.ActivityScreen
import com.obsidian.mobile.ui.auth.AuthFlow
import com.obsidian.mobile.ui.firewall.FirewallScreen
import com.obsidian.mobile.ui.home.HomeScreen
import com.obsidian.mobile.ui.home.NotificationsScreen
import com.obsidian.mobile.ui.lock.LockScreen
import com.obsidian.mobile.ui.lock.LockSetupFlow
import com.obsidian.mobile.ui.settings.SettingsScreen

enum class MainTab(val label: String, val selected: ImageVector, val unselected: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    ACTIVITY("Activity", Icons.Filled.Timeline, Icons.Outlined.Timeline),
    FIREWALL("Firewall", Icons.Filled.Shield, Icons.Outlined.Shield),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
}

/** Signed out → sign-in; no app lock yet → lock setup; locked → lock screen; otherwise the four tabs. */
@Composable
fun ObsidianRoot(activity: FragmentActivity, requestedTab: MainTab?, onTabHandled: () -> Unit) {
    val profile by AppGraph.session.profile.collectAsState()
    val hasPin by AppGraph.session.hasPin.collectAsState()
    val locked by AppLock.locked.collectAsState()
    // Kept here so unlocking returns to the tab the user was on.
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }

    val stage = when {
        profile == null -> "auth"
        !hasPin -> "setup"
        locked -> "locked"
        else -> "main"
    }
    AnimatedContent(stage, label = "root") { current ->
        when (current) {
            "auth" -> AuthFlow(activity)
            "setup" -> LockSetupFlow(activity)
            "locked" -> LockScreen(activity)
            else -> MainScaffold(activity, tab, { tab = it }, requestedTab, onTabHandled)
        }
    }
}

@Composable
private fun MainScaffold(
    activity: FragmentActivity,
    tab: MainTab,
    onTab: (MainTab) -> Unit,
    requestedTab: MainTab?,
    onTabHandled: () -> Unit,
) {
    var showNotifications by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            onTab(requestedTab)
            showNotifications = false
            onTabHandled()
        }
    }
    LaunchedEffect(Unit) {
        // Keep the phone firewall's rules current; a failure just means the cached list is used.
        runCatching { AppGraph.protection.syncBlocklist() }
        runCatching { AppGraph.account.refreshProfile() }
    }

    if (showNotifications) {
        BackHandler { showNotifications = false }
        NotificationsScreen(onBack = { showNotifications = false })
        return
    }
    BackHandler(enabled = tab != MainTab.HOME) { onTab(MainTab.HOME) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { onTab(item) },
                        icon = { Icon(if (tab == item) item.selected else item.unselected, null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            when (tab) {
                MainTab.HOME -> HomeScreen(activity, snackbar, onOpenNotifications = { showNotifications = true },
                    onOpenTab = onTab)
                MainTab.ACTIVITY -> ActivityScreen(snackbar)
                MainTab.FIREWALL -> FirewallScreen(activity, snackbar)
                MainTab.SETTINGS -> SettingsScreen(activity, snackbar)
            }
        }
    }
}
