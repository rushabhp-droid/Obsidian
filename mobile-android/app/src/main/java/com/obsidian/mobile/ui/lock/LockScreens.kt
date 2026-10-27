package com.obsidian.mobile.ui.lock

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.auth.BiometricAuthManager
import com.obsidian.mobile.auth.BiometricAuthManager.Availability
import com.obsidian.mobile.security.AppLock
import com.obsidian.mobile.security.PinHasher
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.ObsidianMark
import com.obsidian.mobile.ui.components.PinDots
import com.obsidian.mobile.ui.components.PinPad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Required after sign-in. With a fingerprint on the phone, it must be set up
 * (and confirmed once). A 6-digit app PIN is always created too: it is the
 * fallback, and the only lock on phones without a fingerprint.
 */
@Composable
fun LockSetupFlow(activity: FragmentActivity) {
    val biometrics = remember { BiometricAuthManager(activity) }
    var availability by remember { mutableStateOf(biometrics.availability()) }
    var fingerprintDone by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Coming back from Settings after enrolling a fingerprint.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) availability = biometrics.availability()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LockFrame {
        if (availability == Availability.READY && !fingerprintDone) {
            BigIcon(Icons.Outlined.Fingerprint)
            Text("Use your fingerprint", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "You'll unlock Obsidian with your fingerprint. Next you'll create a 6-digit PIN as a backup.",
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ErrorText(error)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    error = null
                    biometrics.prompt(activity, "Set up fingerprint unlock", "Touch the sensor to confirm",
                        onSuccess = {
                            AppGraph.session.setFingerprintLock(true)
                            fingerprintDone = true
                        },
                        onFallback = { message -> error = message ?: "Touch the sensor to finish setting up." })
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Set up fingerprint") }
        } else {
            if (!fingerprintDone) {
                NoFingerprintNote(availability, onEnroll = {
                    AppLock.expectReturn()
                    activity.startActivity(enrollIntent())
                })
                LaunchedEffect(Unit) { AppGraph.session.setFingerprintLock(false) }
            }
            CreatePin(title = if (fingerprintDone) "Create a backup PIN" else "Create an app PIN",
                onCreated = { pin ->
                    AppGraph.session.savePin(PinHasher.hash(pin))
                    AppLock.unlock()
                })
        }
    }
}

@Composable
private fun NoFingerprintNote(availability: Availability, onEnroll: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (availability == Availability.NOT_ENROLLED) {
                    "No fingerprint is saved on this phone yet. Add one in Settings to unlock with it, or use a PIN."
                } else {
                    "This phone can't use fingerprint unlock, so your PIN will lock the app."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (availability == Availability.NOT_ENROLLED) {
                OutlinedButton(onClick = onEnroll) { Text("Add a fingerprint") }
            }
        }
    }
}

@Composable
private fun CreatePin(title: String, onCreated: (String) -> Unit) {
    var first by remember { mutableStateOf<String?>(null) }
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    BigIcon(Icons.Outlined.Pin)
    Text(if (first == null) title else "Enter the PIN again", style = MaterialTheme.typography.headlineSmall)
    Text(
        if (first == null) "6 digits. It unlocks Obsidian when your fingerprint can't." else "To make sure it's right.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    PinDots(entry.length, error != null)
    ErrorText(error)
    PinPad(
        enabled = true,
        onDigit = { digit ->
            if (entry.length >= PinHasher.PIN_LENGTH) return@PinPad
            error = null
            entry += digit
            if (entry.length == PinHasher.PIN_LENGTH) {
                val pin = entry
                entry = ""
                when {
                    first == null && PinHasher.isTooSimple(pin) -> error = "Too easy to guess. Try a less obvious PIN."
                    first == null -> first = pin
                    first == pin -> onCreated(pin)
                    else -> {
                        first = null
                        error = "The PINs didn't match. Start again."
                    }
                }
            }
        },
        onDelete = { entry = entry.dropLast(1) },
    )
}

/** Fingerprint first when it's on; the PIN pad is always there as the fallback. */
@Composable
fun LockScreen(activity: FragmentActivity) {
    val scope = rememberCoroutineScope()
    val biometrics = remember { BiometricAuthManager(activity) }
    val settings by AppGraph.session.settings.collectAsState()
    val fingerprint = settings.fingerprintLock && biometrics.availability() == Availability.READY
    var entry by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(AppGraph.session.pinLockedUntil) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirmSignOut by remember { mutableStateOf(false) }

    val promptFingerprint = {
        biometrics.prompt(activity, "Unlock Obsidian", "Use your fingerprint",
            onSuccess = { AppLock.unlock() },
            onFallback = { message -> error = message })
    }
    LaunchedEffect(Unit) { if (fingerprint) promptFingerprint() }
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val waitSeconds = ((lockedUntil - now) / 1000).coerceAtLeast(0)

    LockFrame {
        ObsidianMark()
        Text("Obsidian is locked", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (waitSeconds > 0) "Too many wrong PINs. Try again in $waitSeconds s." else "Enter your PIN",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PinDots(entry.length, error != null)
        ErrorText(error)
        PinPad(
            enabled = waitSeconds == 0L && !checking,
            onDigit = { digit ->
                if (entry.length >= PinHasher.PIN_LENGTH) return@PinPad
                error = null
                entry += digit
                if (entry.length == PinHasher.PIN_LENGTH) {
                    val pin = entry
                    checking = true
                    scope.launch {
                        val ok = withContext(Dispatchers.Default) {
                            AppGraph.session.pinHash?.let { PinHasher.verify(pin, it) } ?: false
                        }
                        checking = false
                        entry = ""
                        if (ok) {
                            AppGraph.session.pinFailures = 0
                            AppLock.unlock()
                        } else {
                            val failures = AppGraph.session.pinFailures + 1
                            AppGraph.session.pinFailures = failures
                            val wait = PinHasher.lockoutMillis(failures)
                            if (wait > 0) {
                                lockedUntil = System.currentTimeMillis() + wait
                                AppGraph.session.pinLockedUntil = lockedUntil
                            }
                            error = "Wrong PIN"
                        }
                    }
                }
            },
            onDelete = { entry = entry.dropLast(1) },
            onFingerprint = if (fingerprint) promptFingerprint else null,
        )
        TextButton(onClick = { confirmSignOut = true }) { Text("Forgot your PIN?") }
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out to reset your PIN?") },
            text = { Text("You'll sign in again and choose a new PIN. Your settings on this phone will be reset.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    scope.launch { AppGraph.account.signOut() }
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LockFrame(content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Box(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) { content() }
        }
    }
}

@Composable
private fun BigIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(Modifier.size(88.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

private fun enrollIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
        Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG,
    )
} else {
    Intent(Settings.ACTION_SECURITY_SETTINGS)
}
