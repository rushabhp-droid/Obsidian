package com.obsidian.mobile.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.BuildConfig
import com.obsidian.mobile.auth.BiometricAuthManager
import com.obsidian.mobile.browser.Browsers
import com.obsidian.mobile.data.DarkMode
import com.obsidian.mobile.data.SessionStore
import com.obsidian.mobile.data.ThemeChoice
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.security.AppLock
import com.obsidian.mobile.security.PinHasher
import com.obsidian.mobile.theme.dynamicColorAvailable
import com.obsidian.mobile.theme.themePreview
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.PinDots
import com.obsidian.mobile.ui.components.SectionHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(activity: FragmentActivity, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by AppGraph.session.settings.collectAsState()
    val profile by AppGraph.session.profile.collectAsState()
    val biometrics = remember { BiometricAuthManager(activity) }
    var tick by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val isDefaultBrowser = remember(tick) { Browsers.isDefault(context) }
    val listenerAllowed = remember(tick) { NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName) }
    val fingerprintAvailability = remember(tick) { biometrics.availability() }
    val browsers = remember(tick) { Browsers.installed(context) }
    val external = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") })
        LazyColumn(Modifier.fillMaxSize()) {
            // ---- protection ----
            item { SectionHeader("App settings") }
            item {
                SwitchRow(Icons.Outlined.OpenInBrowser, "Default browser defense",
                    "Intercepts every web link you open and checks it before your browser loads it",
                    settings.browserDefense) { AppGraph.session.setBrowserDefense(it) }
                if (settings.browserDefense && !isDefaultBrowser) {
                    ActionRow("Obsidian isn't your default browser yet, so links skip the check.", "Make default") {
                        AppLock.expectReturn()
                        external.launch(Browsers.defaultBrowserIntent(context))
                    }
                }
                if (settings.browserDefense) {
                    ListItem(
                        modifier = Modifier.clickable { dialog = "browser" },
                        leadingContent = { Spacer(Modifier.width(24.dp)) },
                        headlineContent = { Text("Open checked links in") },
                        supportingContent = {
                            Text(browsers.firstOrNull { it.packageName == settings.preferredBrowser }?.label
                                ?: browsers.firstOrNull()?.let { "${it.label} (automatic)" } ?: "No other browser installed")
                        },
                    )
                }
            }
            item {
                SwitchRow(Icons.Outlined.NotificationsActive, "Notification auditor",
                    "Continuously checks SMS and WhatsApp notifications for scam links. Texts from saved contacts never leave your phone.",
                    settings.notificationAuditor) { AppGraph.session.setNotificationAuditor(it) }
                if (settings.notificationAuditor && !listenerAllowed) {
                    ActionRow("Notification access is off, so messages aren't checked.", "Allow") {
                        AppLock.expectReturn()
                        external.launch(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                }
            }

            // ---- security ----
            item { SectionHeader("Security") }
            item {
                val ready = fingerprintAvailability == BiometricAuthManager.Availability.READY
                SwitchRow(Icons.Outlined.Fingerprint, "Lock with fingerprint",
                    when (fingerprintAvailability) {
                        BiometricAuthManager.Availability.READY -> "Unlock Obsidian with your fingerprint"
                        BiometricAuthManager.Availability.NOT_ENROLLED -> "Add a fingerprint in your phone's settings first"
                        BiometricAuthManager.Availability.UNAVAILABLE -> "This phone can't use fingerprint unlock"
                    },
                    settings.fingerprintLock && ready, enabled = ready) { on ->
                    if (!on) {
                        AppGraph.session.setFingerprintLock(false)
                    } else {
                        biometrics.prompt(activity, "Turn on fingerprint unlock", "Touch the sensor to confirm",
                            onSuccess = { AppGraph.session.setFingerprintLock(true) }, onFallback = {})
                    }
                }
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Password, null) },
                    headlineContent = { Text("PIN backup") },
                    supportingContent = { Text("Always on. It unlocks Obsidian when your fingerprint can't.") },
                    trailingContent = { TextButton(onClick = { dialog = "pin" }) { Text("Change PIN") } },
                )
                ListItem(
                    modifier = Modifier.clickable { AppLock.lock() },
                    leadingContent = { Icon(Icons.Outlined.Lock, null) },
                    headlineContent = { Text("Lock now") },
                )
            }

            // ---- server ----
            item { SectionHeader("Backend service") }
            item {
                ListItem(
                    modifier = Modifier.clickable { dialog = "server" },
                    leadingContent = { Icon(Icons.Outlined.Dns, null) },
                    headlineContent = { Text("Server address") },
                    supportingContent = { Text(settings.serverUrl.ifBlank { "Not set" }) },
                    trailingContent = { Text("Change", color = MaterialTheme.colorScheme.primary) },
                )
            }

            // ---- appearance ----
            item { SectionHeader("Appearance") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Palette, null) },
                    headlineContent = { Text("Mode") },
                    supportingContent = {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            DarkMode.entries.forEachIndexed { i, mode ->
                                SegmentedButton(selected = settings.darkMode == mode, onClick = { AppGraph.session.setDarkMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(i, DarkMode.entries.size)) { Text(mode.label) }
                            }
                        }
                    },
                )
                ThemeChoice.entries.forEach { choice ->
                    ThemeRow(choice, settings)
                }
            }

            // ---- account ----
            item { SectionHeader("Account") }
            item {
                profile?.let { p ->
                    ListItem(
                        leadingContent = {
                            Surface(Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(p.displayName.take(1).uppercase(), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        },
                        headlineContent = { Text(p.displayName) },
                        supportingContent = {
                            Text("${p.email}\n" + when {
                                p.googleLinked && p.hasPassword -> "Google, or email and password"
                                p.googleLinked -> "Signs in with Google"
                                else -> "Signs in with email and password"
                            })
                        },
                    )
                }
                ListItem(
                    modifier = Modifier.clickable { dialog = "signout" },
                    leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text("Sign out", color = MaterialTheme.colorScheme.error) },
                )
            }

            // ---- about ----
            item { SectionHeader("About") }
            item {
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    headlineContent = { Text("Obsidian ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("Scam and malware protection for your phone. One report protects everyone.") },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.PrivacyTip, null) },
                    headlineContent = { Text("Your privacy") },
                    supportingContent = {
                        Text("• Texts from saved contacts never leave your phone; only their links are checked.\n" +
                            "• Files are checked by their fingerprint (SHA-256); the file is never uploaded.\n" +
                            "• The firewall's request list stays on this phone.\n" +
                            "• Unknown links are shown as \"checking\", never as safe.")
                    },
                )
                ListItem(
                    leadingContent = { Icon(Icons.Outlined.Language, null) },
                    headlineContent = { Text("How the firewall works") },
                    supportingContent = {
                        Text("A local VPN that only handles DNS. Other traffic never passes through Obsidian. " +
                            "Android doesn't let apps see other apps' incoming connections, so the request list shows " +
                            "outgoing lookups and the answers that come back.")
                    },
                )
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    when (dialog) {
        "server" -> ServerDialog(onDismiss = { dialog = null })
        "pin" -> ChangePinDialog(onDismiss = { dialog = null }, onChanged = {
            dialog = null
            scope.launch { snackbar.showSnackbar("PIN changed") }
        })
        "browser" -> BrowserPicker(browsers, settings.preferredBrowser, onDismiss = { dialog = null })
        "signout" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Sign out?") },
            text = { Text("This turns off the firewall and removes your PIN, settings and saved rules from this phone. " +
                "Your reports and account stay on the server.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch { AppGraph.account.signOut() }
                }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, enabled: Boolean = true,
    onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
        leadingContent = { Icon(icon, null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}

@Composable
private fun ActionRow(message: String, action: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 56.dp, end = 16.dp, bottom = 8.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.small).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f))
        TextButton(onClick = onClick) { Text(action) }
    }
}

@Composable
private fun ThemeRow(choice: ThemeChoice, settings: SessionStore.Settings) {
    val available = choice != ThemeChoice.MATERIAL_YOU || dynamicColorAvailable
    val dark = settings.darkMode == DarkMode.DARK
    ListItem(
        modifier = Modifier.selectable(selected = settings.theme == choice, enabled = available, role = Role.RadioButton) {
            AppGraph.session.setTheme(choice)
        },
        leadingContent = { RadioButton(selected = settings.theme == choice, onClick = null, enabled = available) },
        headlineContent = { Text(choice.label) },
        supportingContent = {
            Text(when (choice) {
                ThemeChoice.MATERIAL_YOU -> if (available) "Colours from your wallpaper" else "Needs Android 12 or later"
                ThemeChoice.OBSIDIAN -> "Obsidian teal (default)"
                ThemeChoice.CATPPUCCIN -> "Latte and Mocha"
                ThemeChoice.NORD -> "Snow Storm and Polar Night"
                ThemeChoice.DRACULA -> "Alucard and Dracula"
                ThemeChoice.SOLARIZED -> "Solarized light and dark"
            })
        },
        trailingContent = {
            if (choice != ThemeChoice.MATERIAL_YOU) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    themePreview(choice, dark).forEach { color ->
                        Box(Modifier.size(14.dp).background(color, CircleShape))
                    }
                }
            }
        },
    )
}

/** Sets the server address after checking that an Obsidian server answers there. */
@Composable
fun ServerDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val current = AppGraph.session.settings.value.serverUrl
    var url by remember { mutableStateOf(current) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Obsidian server") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The address of the Obsidian backend this app talks to.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = url, onValueChange = { url = it }, singleLine = true,
                    placeholder = { Text("https://obsidian.example.com") },
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri))
                Text("On the Android emulator, your computer is http://10.0.2.2:8080. Plain http only works for local addresses.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url.isNotBlank(), onClick = {
                val target = SessionStore.normalizeServerUrl(url)
                scope.launch {
                    busy = true
                    error = null
                    message = null
                    try {
                        val status = AppGraph.account.status(target)
                        if (target != current && AppGraph.session.isSignedIn) {
                            // A token belongs to the server that issued it.
                            AppGraph.account.signOut()
                        }
                        AppGraph.session.setServerUrl(target)
                        message = "Connected. Database ${status.database.lowercase()}."
                        onDismiss()
                    } catch (e: ApiException) {
                        error = e.message
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (AppGraph.session.isSignedIn && SessionStore.normalizeServerUrl(url) != current) "Save and sign out" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BrowserPicker(browsers: List<Browsers.Browser>, selected: String?, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Open checked links in") },
        text = {
            Column {
                if (browsers.isEmpty()) Text("Install a browser such as Chrome or Firefox first.")
                browsers.forEach { browser ->
                    Row(
                        Modifier.fillMaxWidth().selectable(selected = selected == browser.packageName, role = Role.RadioButton) {
                            AppGraph.session.setPreferredBrowser(browser.packageName)
                            onDismiss()
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == browser.packageName, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(browser.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ChangePinDialog(onDismiss: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    @Composable
    fun PinField(label: String, value: String, onValue: (String) -> Unit) {
        OutlinedTextField(
            value = value, onValueChange = { onValue(it.filter(Char::isDigit).take(PinHasher.PIN_LENGTH)) },
            label = { Text(label) }, singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField("Current PIN", current) { current = it }
                PinField("New PIN", next) { next = it }
                PinField("New PIN again", confirm) { confirm = it }
                PinDots(next.length)
                ErrorText(error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.length == 6 && next.length == 6 && confirm.length == 6,
                onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            AppGraph.session.pinHash?.let { PinHasher.verify(current, it) } ?: false
                        }
                        when {
                            !ok -> error = "The current PIN is wrong."
                            next != confirm -> error = "The new PINs don't match."
                            PinHasher.isTooSimple(next) -> error = "Too easy to guess. Try a less obvious PIN."
                            else -> {
                                AppGraph.session.savePin(withContext(Dispatchers.Default) { PinHasher.hash(next) })
                                onChanged()
                            }
                        }
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
