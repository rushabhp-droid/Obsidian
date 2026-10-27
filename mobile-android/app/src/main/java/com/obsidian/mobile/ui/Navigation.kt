package com.obsidian.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.obsidian.mobile.auth.TokenStorage
import com.obsidian.mobile.data.MessageCheckItem
import com.obsidian.mobile.data.MessageRepository
import com.obsidian.mobile.data.RuleRepository
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
    messageRepository: MessageRepository = MessageRepository.instance,
    ruleRepository: RuleRepository = RuleRepository.instance,
    onTriggerBiometric: (onSuccess: () -> Unit) -> Unit
) {
    var isUnlocked by remember { mutableStateOf(!tokenStorage.isFingerprintLockEnabled) }
    var currentScreen by remember { mutableStateOf<ScreenState>(
        if (!isUnlocked) ScreenState.Lock else ScreenState.Main(NavTab.HOME)
    ) }

    val messages by messageRepository.messages.collectAsState()
    val blockedRules by ruleRepository.blockedRules.collectAsState()
    val trustedRules by ruleRepository.trustedRules.collectAsState()

    var showAddRuleDialog by remember { mutableStateOf(false) }
    var addRuleInitialTab by remember { mutableStateOf(RulesTab.BLOCKED) }

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
                            if (!tokenStorage.isNotificationAccessGranted) {
                                HomeSetupScreen(
                                    onEnableNotificationClicked = {
                                        tokenStorage.isNotificationAccessGranted = true
                                    },
                                    onLearnMoreClicked = {},
                                    onAccountClicked = {
                                        currentScreen = ScreenState.Main(NavTab.SETTINGS)
                                    }
                                )
                            } else {
                                HomeScreen(
                                    messages = messages,
                                    onMessageClick = { item ->
                                        currentScreen = ScreenState.MessageDetail(item)
                                    },
                                    onSeeAllMessagesClick = {
                                        currentScreen = ScreenState.Main(NavTab.MESSAGES)
                                    },
                                    onAccountClick = {
                                        currentScreen = ScreenState.Main(NavTab.SETTINGS)
                                    }
                                )
                            }
                        }

                        NavTab.MESSAGES -> {
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
                                    if (rule.listType == com.obsidian.mobile.network.ListType.BLACKLIST) {
                                        ruleRepository.removeBlockedRule(rule.id)
                                    } else {
                                        ruleRepository.removeTrustedRule(rule.id)
                                    }
                                }
                            )
                        }

                        NavTab.SETTINGS -> {
                            SettingsScreen(
                                userEmail = tokenStorage.userEmail,
                                isNotificationAccessOn = tokenStorage.isNotificationAccessGranted,
                                onNotificationAccessChanged = { enabled ->
                                    tokenStorage.isNotificationAccessGranted = enabled
                                },
                                isFingerprintLockOn = tokenStorage.isFingerprintLockEnabled,
                                onFingerprintLockChanged = { enabled ->
                                    tokenStorage.isFingerprintLockEnabled = enabled
                                },
                                isHighContrastBlackOn = tokenStorage.isHighContrastBlackEnabled,
                                onHighContrastBlackChanged = { enabled ->
                                    tokenStorage.isHighContrastBlackEnabled = enabled
                                },
                                onSignOutClick = {
                                    tokenStorage.clear()
                                    isUnlocked = false
                                    currentScreen = ScreenState.Lock
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
                            ruleRepository.addBlockedRule(
                                indicatorType = com.obsidian.mobile.network.IndicatorType.URL,
                                value = screen.message.displayDomain,
                                source = "Reported by you · Today"
                            )
                            currentScreen = ScreenState.ReportConfirmed(screen.message)
                        },
                        onTrustClick = {
                            messageRepository.markTrusted(screen.message.id)
                            ruleRepository.addTrustedRule(
                                indicatorType = com.obsidian.mobile.network.IndicatorType.URL,
                                value = screen.message.displayDomain,
                                source = "Trusted by you · Today",
                                ttlMinutes = 15
                            )
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
                        if (tab == RulesTab.BLOCKED) {
                            ruleRepository.addBlockedRule(type, value)
                        } else {
                            ruleRepository.addTrustedRule(type, value, ttlMinutes = ttl)
                        }
                        showAddRuleDialog = false
                    }
                )
            }
        }
    }
}
