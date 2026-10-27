package com.obsidian.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.obsidian.mobile.auth.TokenStorage
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageRepository
import com.obsidian.mobile.data.RuleRepository
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.AuthLoginRequest
import com.obsidian.mobile.network.IndicatorType
import com.obsidian.mobile.network.ListType
import com.obsidian.mobile.network.ThreatCategory
import com.obsidian.mobile.ui.components.NavTab
import com.obsidian.mobile.ui.components.ObsidianBottomNavBar
import com.obsidian.mobile.ui.screens.AddRuleDialog
import com.obsidian.mobile.ui.screens.HomeScreen
import com.obsidian.mobile.ui.screens.HomeSetupScreen
import com.obsidian.mobile.ui.screens.LockScreen
import com.obsidian.mobile.ui.screens.MessageCheckDetailScreen
import com.obsidian.mobile.ui.screens.MessagesScreen
import com.obsidian.mobile.ui.screens.ReportConfirmedScreen
import com.obsidian.mobile.ui.screens.RulesScreen
import com.obsidian.mobile.ui.screens.RulesTab
import com.obsidian.mobile.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import com.obsidian.mobile.UrlCheckActivity
import com.obsidian.mobile.network.ThreatScanner
import com.obsidian.mobile.notify.ThreatAlertManager
import com.obsidian.mobile.ui.components.ManualCheckLinkDialog

sealed class ScreenState {
    object Lock : ScreenState()
    object HomeSetup : ScreenState()
    data class Main(val tab: NavTab = NavTab.HOME) : ScreenState()
    data class MessageDetail(val message: MessageCheckItem) : ScreenState()
    data class ReportConfirmed(val message: MessageCheckItem) : ScreenState()
}

@Composable
fun ObsidianApp(
    tokenStorage: TokenStorage,
    apiClient: ApiClient = remember { ApiClient(tokenStorage) },
    messageRepository: MessageRepository = MessageRepository.instance,
    ruleRepository: RuleRepository = RuleRepository.instance,
    isDefaultBrowser: Boolean = false,
    onRequestDefaultBrowser: () -> Unit = {},
    onRequestNotificationAccess: () -> Unit = {},
    onTriggerBiometric: (onSuccess: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUnlocked by remember { mutableStateOf(!tokenStorage.isFingerprintLockEnabled) }
    var currentScreen by remember { mutableStateOf<ScreenState>(
        if (!isUnlocked) ScreenState.Lock else ScreenState.Main(NavTab.HOME)
    ) }

    val threatAlertManager = remember { ThreatAlertManager(context) }
    val threatScanner = remember {
        ThreatScanner(
            apiClient = apiClient,
            messageRepository = messageRepository,
            ruleRepository = ruleRepository,
            threatAlertManager = threatAlertManager
        )
    }

    var showManualCheckDialog by remember { mutableStateOf(false) }

    val messages by messageRepository.messages.collectAsState()
    val blockedRules by ruleRepository.blockedRules.collectAsState()
    val trustedRules by ruleRepository.trustedRules.collectAsState()

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var addRuleInitialTab by remember { mutableStateOf(RulesTab.BLOCKED) }

    // Backend synchronization when app is unlocked / active
    LaunchedEffect(isUnlocked) {
        if (isUnlocked) {
            // 1. Authenticate with backend if token is missing
            if (tokenStorage.bearerToken == null) {
                try {
                    val loginRes = apiClient.service.login(
                        AuthLoginRequest(
                            email = tokenStorage.userEmail.ifBlank { "priya@example.com" },
                            password = "password",
                            client = "MOBILE"
                        )
                    )
                    tokenStorage.bearerToken = loginRes.token
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 2. Fetch Session Info (/api/v1/auth/me)
            try {
                val session = apiClient.service.me()
                tokenStorage.userEmail = session.email
                session.displayName?.let { tokenStorage.userDisplayName = it }
                tokenStorage.userRole = session.role
                session.organization?.let { tokenStorage.organizationName = it.name }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 3. Sync rules from backend (/api/v1/rules)
            ruleRepository.syncRules(apiClient)

            // 4. Sync scans from backend (/api/v1/scans)
            messageRepository.syncScans(apiClient)
        }
    }

    if (!isUnlocked) {
        LockScreen(
            onUnlockClicked = {
                onTriggerBiometric {
                    isUnlocked = true
                    currentScreen = if (!tokenStorage.isNotificationAccessGranted) {
                        ScreenState.HomeSetup
                    } else {
                        ScreenState.Main(NavTab.HOME)
                    }
                }
            }
        )
        return
    }

    val currentMainTab = (currentScreen as? ScreenState.Main)?.tab ?: NavTab.HOME
    val showBottomBar = currentScreen is ScreenState.Main

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                ObsidianBottomNavBar(
                    currentTab = currentMainTab,
                    onTabSelected = { tab ->
                        currentScreen = ScreenState.Main(tab)
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val screen = currentScreen) {
                is ScreenState.Lock -> {
                    LockScreen(
                        onUnlockClicked = {
                            onTriggerBiometric {
                                isUnlocked = true
                                currentScreen = ScreenState.Main(NavTab.HOME)
                            }
                        }
                    )
                }

                is ScreenState.HomeSetup -> {
                    HomeSetupScreen(
                        onEnableNotificationClicked = {
                            tokenStorage.isNotificationAccessGranted = true
                            currentScreen = ScreenState.Main(NavTab.HOME)
                        },
                        onLearnMoreClicked = {},
                        onAccountClicked = {
                            currentScreen = ScreenState.Main(NavTab.SETTINGS)
                        }
                    )
                }

                is ScreenState.Main -> {
                    when (screen.tab) {
                        NavTab.HOME -> {
                            HomeScreen(
                                messages = messages,
                                isProtectionActive = tokenStorage.isNotificationAccessGranted,
                                onMessageClick = { item ->
                                    currentScreen = ScreenState.MessageDetail(item)
                                },
                                onSeeAllMessagesClick = {
                                    currentScreen = ScreenState.Main(NavTab.ACTIVITY)
                                },
                                onCheckLinkClick = {
                                    showManualCheckDialog = true
                                },
                                onReviewAccessClick = {
                                    onRequestNotificationAccess()
                                },
                                onNotificationBellClick = {
                                    currentScreen = ScreenState.Main(NavTab.ACTIVITY)
                                }
                            )
                        }

                        NavTab.ACTIVITY -> {
                            MessagesScreen(
                                messages = messages,
                                onMessageClick = { item ->
                                    currentScreen = ScreenState.MessageDetail(item)
                                }
                            )
                        }

                        NavTab.RULES -> {
                            RulesScreen(
                                blockedRules = blockedRules,
                                trustedRules = trustedRules,
                                onAddRuleClick = {
                                    showAddRuleDialog = true
                                },
                                onDeleteRuleClick = { rule ->
                                    coroutineScope.launch {
                                        ruleRepository.deleteRule(apiClient, rule)
                                    }
                                }
                            )
                        }

                        NavTab.SETTINGS -> {
                            SettingsScreen(
                                userEmail = tokenStorage.userEmail,
                                userDisplayName = tokenStorage.userDisplayName,
                                organizationName = tokenStorage.organizationName,
                                serverUrl = tokenStorage.serverUrl,
                                onServerUrlChanged = { newUrl ->
                                    apiClient.setCustomBaseUrl(newUrl)
                                    coroutineScope.launch {
                                        ruleRepository.syncRules(apiClient)
                                        messageRepository.syncScans(apiClient)
                                    }
                                },
                                isNotificationAccessOn = tokenStorage.isNotificationAccessGranted,
                                onNotificationAccessChanged = { enabled ->
                                    tokenStorage.isNotificationAccessGranted = enabled
                                    if (enabled) {
                                        onRequestNotificationAccess()
                                    }
                                },
                                isDefaultBrowser = isDefaultBrowser,
                                onRequestDefaultBrowserClick = onRequestDefaultBrowser,
                                isFingerprintLockOn = tokenStorage.isFingerprintLockEnabled,
                                onFingerprintLockChanged = { enabled ->
                                    tokenStorage.isFingerprintLockEnabled = enabled
                                },
                                isHighContrastBlackOn = tokenStorage.isHighContrastBlackEnabled,
                                onHighContrastBlackChanged = { enabled ->
                                    tokenStorage.isHighContrastBlackEnabled = enabled
                                },
                                onSignOutClick = {
                                    coroutineScope.launch {
                                        try {
                                            apiClient.service.logout()
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                        tokenStorage.clear()
                                        isUnlocked = false
                                        currentScreen = ScreenState.Lock
                                    }
                                },
                                onPrivacyPolicyClick = {},
                                onLicensesClick = {}
                            )
                        }
                    }
                }

                is ScreenState.MessageDetail -> {
                    MessageCheckDetailScreen(
                        messageItem = screen.message,
                        onBackClick = {
                            currentScreen = ScreenState.Main(NavTab.HOME)
                        },
                        onReportClick = {
                            messageRepository.markReported(screen.message.id)
                            coroutineScope.launch {
                                ruleRepository.reportRule(
                                    apiClient = apiClient,
                                    indicatorType = IndicatorType.URL,
                                    value = screen.message.displayDomain,
                                    category = ThreatCategory.PHISHING,
                                    source = "Reported by you · Today"
                                )
                            }
                            currentScreen = ScreenState.ReportConfirmed(screen.message)
                        },
                        onTrustClick = {
                            messageRepository.markTrusted(screen.message.id)
                            coroutineScope.launch {
                                ruleRepository.bypassRule(
                                    apiClient = apiClient,
                                    indicatorType = IndicatorType.URL,
                                    value = screen.message.displayDomain,
                                    ttlMinutes = 15,
                                    source = "Trusted by you · Today"
                                )
                            }
                            currentScreen = ScreenState.Main(NavTab.HOME)
                        }
                    )
                }

                is ScreenState.ReportConfirmed -> {
                    ReportConfirmedScreen(
                        messageItem = screen.message,
                        onBackClick = {
                            currentScreen = ScreenState.Main(NavTab.HOME)
                        },
                        onUndoReportClick = {
                            messageRepository.undoReport(screen.message.id)
                            coroutineScope.launch {
                                ruleRepository.bypassRule(
                                    apiClient = apiClient,
                                    indicatorType = IndicatorType.URL,
                                    value = screen.message.displayDomain,
                                    source = "Trusted by you · Today"
                                )
                            }
                            currentScreen = ScreenState.MessageDetail(screen.message)
                        }
                    )
                }
            }

            if (showAddRuleDialog) {
                AddRuleDialog(
                    initialTab = addRuleInitialTab,
                    onDismiss = { showAddRuleDialog = false },
                    onConfirm = { type, value, tab, ttl ->
                        coroutineScope.launch {
                            if (tab == RulesTab.BLOCKED) {
                                ruleRepository.reportRule(
                                    apiClient = apiClient,
                                    indicatorType = type,
                                    value = value
                                )
                            } else {
                                ruleRepository.bypassRule(
                                    apiClient = apiClient,
                                    indicatorType = type,
                                    value = value,
                                    ttlMinutes = ttl
                                )
                            }
                        }
                        showAddRuleDialog = false
                    }
                )
            }

            if (showManualCheckDialog) {
                ManualCheckLinkDialog(
                    threatScanner = threatScanner,
                    ruleRepository = ruleRepository,
                    apiClient = apiClient,
                    onDismiss = { showManualCheckDialog = false },
                    onOpenUrl = { url ->
                        UrlCheckActivity.openInExternalBrowser(context, url)
                    }
                )
            }
        }
    }
}
