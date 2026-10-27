package com.obsidian.mobile

import android.content.Context
import com.obsidian.mobile.data.AccountRepository
import com.obsidian.mobile.data.AlertStore
import com.obsidian.mobile.data.ProtectionRepository
import com.obsidian.mobile.data.SessionStore
import com.obsidian.mobile.firewall.FirewallLog
import com.obsidian.mobile.firewall.FirewallVpnService
import com.obsidian.mobile.network.ApiClient
import java.io.File

/** The app's singletons, shared by the activities and the background services. */
object AppGraph {

    lateinit var session: SessionStore
        private set
    lateinit var client: ApiClient
        private set
    lateinit var account: AccountRepository
        private set
    lateinit var protection: ProtectionRepository
        private set
    lateinit var alerts: AlertStore
        private set

    fun init(context: Context) {
        val app = context.applicationContext
        session = SessionStore(app)
        client = ApiClient(session)
        protection = ProtectionRepository(client, File(app.filesDir, "blocklist.json"))
        alerts = AlertStore(File(app.filesDir, "alerts.json"))
        val forgetDevice = {
            FirewallVpnService.stop(app)
            protection.clear()
            alerts.clear()
            FirewallLog.clear()
        }
        account = AccountRepository(session, client, forgetDevice)
        client.onUnauthorized = {
            session.signOut()
            forgetDevice()
        }
    }
}
