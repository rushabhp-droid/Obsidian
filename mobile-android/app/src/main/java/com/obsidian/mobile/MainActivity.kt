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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ObsidianMobileTheme {
                ObsidianApp(
                    tokenStorage = tokenStorage,
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
