package com.obsidian.mobile

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.obsidian.mobile.auth.BiometricAuthManager
import com.obsidian.mobile.auth.TokenStorage
import com.obsidian.mobile.notify.ThreatAlertManager
import com.obsidian.mobile.sim.SimChangeMonitor
import com.obsidian.mobile.theme.ObsidianMobileTheme
import com.obsidian.mobile.ui.ObsidianApp

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings

class MainActivity : FragmentActivity() {

    private val tokenStorage by lazy { TokenStorage(this) }
    private val biometricAuthManager by lazy { BiometricAuthManager(this) }
    private val threatAlertManager by lazy { ThreatAlertManager(this) }
    private val simChangeMonitor by lazy {
        SimChangeMonitor(this, tokenStorage) { reason ->
            runOnUiThread {
                threatAlertManager.showSimChangeAlert(reason)
                Toast.makeText(this, "SECURITY ALERT: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isDefaultBrowser(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_BROWSER) == true
        }
        return false
    }

    private fun requestDefaultBrowserRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                startActivityForResult(intent, 2001)
                return
            }
        }
        try {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun requestNotificationAccess() {
        try {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ObsidianMobileTheme {
                ObsidianApp(
                    tokenStorage = tokenStorage,
                    isDefaultBrowser = isDefaultBrowser(),
                    onRequestDefaultBrowser = { requestDefaultBrowserRole() },
                    onRequestNotificationAccess = { requestNotificationAccess() },
                    onTriggerBiometric = { onSuccess ->
                        if (biometricAuthManager.canAuthenticate()) {
                            biometricAuthManager.promptBiometric(
                                activity = this@MainActivity,
                                title = "Obsidian Security Lock",
                                subtitle = "Verify your identity to access threat controls",
                                onSuccess = onSuccess,
                                onError = { errMsg ->
                                    Toast.makeText(this@MainActivity, errMsg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        } else {
                            // Device has no biometrics/credential setup: allow fallback unlock for testing
                            onSuccess()
                        }
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        simChangeMonitor.startMonitoring()
    }

    override fun onPause() {
        super.onPause()
        simChangeMonitor.stopMonitoring()
    }
}
