package com.obsidian.mobile.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.R
import com.obsidian.mobile.auth.GoogleSignIn
import com.obsidian.mobile.network.ApiException
import com.obsidian.mobile.ui.components.ErrorText
import com.obsidian.mobile.ui.components.ObsidianMark
import com.obsidian.mobile.ui.settings.ServerDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class AuthStep { SIGN_IN, CREATE, VERIFY, FORGOT, RESET }

private const val MIN_PASSWORD = 10

/** Sign in with Google or email + password, create an account (name required), and verify the email with a code. */
@Composable
fun AuthFlow(activity: FragmentActivity) {
    var step by rememberSaveable { mutableStateOf(AuthStep.SIGN_IN) }
    var email by rememberSaveable { mutableStateOf("") }
    var showServer by remember { mutableStateOf(false) }

    BackHandler(enabled = step != AuthStep.SIGN_IN) {
        step = if (step == AuthStep.RESET) AuthStep.FORGOT else AuthStep.SIGN_IN
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when (step) {
                    AuthStep.SIGN_IN -> SignIn(
                        activity = activity,
                        email = email,
                        onEmail = { email = it },
                        onCreate = { step = AuthStep.CREATE },
                        onForgot = { step = AuthStep.FORGOT },
                        onNeedsVerification = { step = AuthStep.VERIFY },
                    )
                    AuthStep.CREATE -> CreateAccount(
                        email = email,
                        onEmail = { email = it },
                        onBack = { step = AuthStep.SIGN_IN },
                        onCodeSent = { step = AuthStep.VERIFY },
                    )
                    AuthStep.VERIFY -> VerifyEmail(email, onBack = { step = AuthStep.SIGN_IN })
                    AuthStep.FORGOT -> ForgotPassword(
                        email = email,
                        onEmail = { email = it },
                        onBack = { step = AuthStep.SIGN_IN },
                        onCodeSent = { step = AuthStep.RESET },
                    )
                    AuthStep.RESET -> ResetPassword(email, onBack = { step = AuthStep.FORGOT })
                }
                Spacer(Modifier.height(8.dp))
                ServerLine(onChange = { showServer = true })
            }
        }
    }
    if (showServer) ServerDialog(onDismiss = { showServer = false })
}

@Composable
private fun SignIn(
    activity: FragmentActivity,
    email: String,
    onEmail: (String) -> Unit,
    onCreate: () -> Unit,
    onForgot: () -> Unit,
    onNeedsVerification: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(24.dp))
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        ObsidianMark()
        Spacer(Modifier.height(12.dp))
        Text("Obsidian", style = MaterialTheme.typography.headlineMedium)
        Text("Protect your phone from scams", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))

    OutlinedButton(
        onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    val status = AppGraph.account.status()
                    val clientId = status.googleClientId
                    if (!status.googleSignInConfigured || clientId == null) {
                        error = "Google sign-in isn't set up on this server. Use email instead."
                    } else {
                        when (val result = GoogleSignIn.requestIdToken(activity, clientId)) {
                            is GoogleSignIn.Result.Token -> AppGraph.account.google(result.idToken)
                            is GoogleSignIn.Result.Failed -> error = result.message
                            GoogleSignIn.Result.Cancelled -> Unit
                        }
                    }
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Image(painterResource(R.drawable.ic_google), null, Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text("Continue with Google")
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f))
        Text("  or  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(Modifier.weight(1f))
    }

    EmailField(email, onEmail, imeAction = ImeAction.Next)
    PasswordField(password, { password = it }, "Password", imeAction = ImeAction.Done)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onForgot) { Text("Forgot password?") }
    }
    ErrorText(error)
    Button(
        onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    AppGraph.account.login(email, password)
                } catch (e: ApiException) {
                    if (e.code == "EMAIL_NOT_VERIFIED") onNeedsVerification() else error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && email.isNotBlank() && password.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Sign in")
    }
    TextButton(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("New here? Create an account") }
}

@Composable
private fun CreateAccount(email: String, onEmail: (String) -> Unit, onBack: () -> Unit, onCodeSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var submitted by remember { mutableStateOf(false) }

    val nameError = if (submitted && name.isBlank()) "Your name is required" else null
    val passwordError = if (password.isNotEmpty() && password.length < MIN_PASSWORD) "At least $MIN_PASSWORD characters" else null
    val confirmError = if (confirm.isNotEmpty() && confirm != password) "Passwords don't match" else null

    Header("Create account", "Your name is required. We'll email you a code to confirm your address.", onBack)
    OutlinedTextField(
        value = name, onValueChange = { name = it }, label = { Text("Full name *") }, singleLine = true,
        isError = nameError != null, supportingText = nameError?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth(),
    )
    EmailField(email, onEmail, label = "Email *", imeAction = ImeAction.Next)
    PasswordField(password, { password = it }, "Password *", error = passwordError, imeAction = ImeAction.Next,
        hint = "At least $MIN_PASSWORD characters")
    PasswordField(confirm, { confirm = it }, "Confirm password *", error = confirmError, imeAction = ImeAction.Done)
    ErrorText(error)
    Button(
        onClick = {
            submitted = true
            if (name.isBlank() || password.length < MIN_PASSWORD || confirm != password || email.isBlank()) return@Button
            scope.launch {
                busy = true
                error = null
                try {
                    AppGraph.account.register(name, email, password)
                    onCodeSent()
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Send code to my email")
    }
}

@Composable
private fun VerifyEmail(email: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var resendIn by remember { mutableIntStateOf(45) }

    LaunchedEffect(resendIn) {
        if (resendIn > 0) {
            delay(1000)
            resendIn--
        }
    }

    Header("Check your email", "We sent a 6-digit code to $email. It expires in 10 minutes.", onBack)
    CodeField(code) { code = it }
    ErrorText(error)
    info?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    TextButton(
        onClick = {
            scope.launch {
                error = null
                try {
                    AppGraph.account.resendCode(email)
                    info = "A new code is on its way."
                    resendIn = 45
                } catch (e: ApiException) {
                    error = e.message
                }
            }
        },
        enabled = resendIn == 0,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(if (resendIn > 0) "Resend code in 0:%02d".format(resendIn) else "Resend code") }
    Button(
        onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    AppGraph.account.verifyEmail(email, code)
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && code.length == 6,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Verify")
    }
}

@Composable
private fun ForgotPassword(email: String, onEmail: (String) -> Unit, onBack: () -> Unit, onCodeSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Header("Reset your password", "Enter your account's email. If it has an account, we'll send it a code.", onBack)
    EmailField(email, onEmail, imeAction = ImeAction.Done)
    ErrorText(error)
    Button(
        onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    AppGraph.account.forgotPassword(email)
                    onCodeSent()
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && email.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Send code") }
}

@Composable
private fun ResetPassword(email: String, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Header("Choose a new password", "Enter the code we sent to $email.", onBack)
    CodeField(code) { code = it }
    PasswordField(password, { password = it }, "New password",
        error = if (password.isNotEmpty() && password.length < MIN_PASSWORD) "At least $MIN_PASSWORD characters" else null)
    PasswordField(confirm, { confirm = it }, "Confirm new password",
        error = if (confirm.isNotEmpty() && confirm != password) "Passwords don't match" else null)
    ErrorText(error)
    Button(
        onClick = {
            scope.launch {
                busy = true
                error = null
                try {
                    AppGraph.account.resetPassword(email, code, password)
                } catch (e: ApiException) {
                    error = e.message
                } finally {
                    busy = false
                }
            }
        },
        enabled = !busy && code.length == 6 && password.length >= MIN_PASSWORD && confirm == password,
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Save and sign in") }
}

// ---- pieces -----------------------------------------------------------------------------------------

@Composable
private fun Header(title: String, subtitle: String, onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EmailField(value: String, onChange: (String) -> Unit, label: String = "Email", imeAction: ImeAction) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.trim()) }, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Email, imeAction = imeAction),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String? = null,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        keyboardActions = KeyboardActions.Default,
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    if (visible) "Hide password" else "Show password")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(6)) },
        label = { Text("6-digit code") },
        singleLine = true,
        textStyle = TextStyle(fontSize = 24.sp, letterSpacing = 8.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ServerLine(onChange: () -> Unit) {
    val settings by AppGraph.session.settings.collectAsState()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Icon(Icons.Outlined.Dns, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(
            "Server: " + settings.serverUrl.ifBlank { "not set" }.removePrefix("https://").removePrefix("http://"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onChange) { Text("Change") }
    }
}
