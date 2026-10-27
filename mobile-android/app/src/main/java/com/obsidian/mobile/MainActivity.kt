package com.obsidian.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.fragment.app.FragmentActivity
import com.obsidian.mobile.firewall.FirewallVpnService
import com.obsidian.mobile.theme.ObsidianTheme
import com.obsidian.mobile.ui.MainTab
import com.obsidian.mobile.ui.ObsidianRoot

/** The app's one screen host. A FragmentActivity because BiometricPrompt needs one. */
class MainActivity : FragmentActivity() {

    /** A tab another part of the app asked for (a threat notification, the firewall notification). */
    private val requestedTab = mutableStateOf<MainTab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTab(intent)
        resumeFirewall()
        setContent {
            val settings by AppGraph.session.settings.collectAsState()
            ObsidianTheme(settings.theme, settings.darkMode) {
                ObsidianRoot(
                    activity = this,
                    requestedTab = requestedTab.value,
                    onTabHandled = { requestedTab.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTab(intent)
    }

    private fun readTab(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> requestedTab.value = MainTab.entries.firstOrNull { it.name == name } }
    }

    /** The firewall was on when the app last ran (or the phone restarted): turn it back on if Android still allows it. */
    private fun resumeFirewall() {
        val session = AppGraph.session
        if (session.isSignedIn && session.settings.value.firewallWanted && !FirewallVpnService.running.value &&
            FirewallVpnService.consentIntent(this) == null) {
            FirewallVpnService.start(this)
        }
    }

    companion object {
        const val EXTRA_TAB = "com.obsidian.mobile.TAB"
    }
}
