package com.obsidian.mobile.sim

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.obsidian.mobile.auth.TokenStorage
import java.security.MessageDigest

class SimChangeMonitor(
    private val context: Context,
    private val tokenStorage: TokenStorage,
    private val onSimAlert: (String) -> Unit
) {

    private val subscriptionManager: SubscriptionManager? by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE)
        context.getSystemService(SubscriptionManager::class.java)
    }

    private val telephonyManager: TelephonyManager? by lazy {
        context.getSystemService(TelephonyManager::class.java)
    }

    private val subscriptionsChangedListener = object : SubscriptionManager.OnSubscriptionsChangedListener() {
        override fun onSubscriptionsChanged() {
            super.onSubscriptionsChanged()
            verifySimIntegrity()
        }
    }

    fun startMonitoring() {
        try {
            subscriptionManager?.addOnSubscriptionsChangedListener(
                context.mainExecutor,
                subscriptionsChangedListener
            )
            verifySimIntegrity()
        } catch (e: SecurityException) {
            Log.w("SimChangeMonitor", "READ_PHONE_STATE permission not granted for SIM monitoring: ${e.message}")
        }
    }

    fun stopMonitoring() {
        try {
            subscriptionManager?.removeOnSubscriptionsChangedListener(subscriptionsChangedListener)
        } catch (e: Exception) {
            Log.w("SimChangeMonitor", "Error removing listener: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun verifySimIntegrity() {
        val simState = telephonyManager?.simState ?: TelephonyManager.SIM_STATE_UNKNOWN
        if (simState == TelephonyManager.SIM_STATE_ABSENT) {
            triggerSimAlert("Physical SIM removed or absent. High-risk state detected.")
            return
        }

        try {
            val activeSubscriptionInfoList: List<SubscriptionInfo>? =
                subscriptionManager?.activeSubscriptionInfoList

            if (activeSubscriptionInfoList.isNullOrEmpty()) {
                triggerSimAlert("No active SIM subscriptions found. Cellular service interrupted.")
                return
            }

            val currentHash = computeSimFingerprint(activeSubscriptionInfoList)
            val storedHash = tokenStorage.simFingerprintHash

            if (storedHash == null) {
                // First initialization: store current SIM fingerprint
                tokenStorage.simFingerprintHash = currentHash
                Log.i("SimChangeMonitor", "Enrolled baseline SIM fingerprint: $currentHash")
            } else if (storedHash != currentHash) {
                triggerSimAlert("SIM card change detected. Subscription metadata differs from baseline.")
            }
        } catch (e: SecurityException) {
            Log.w("SimChangeMonitor", "Missing READ_PHONE_STATE permission during SIM check")
        }
    }

    private fun triggerSimAlert(reason: String) {
        Log.e("SimChangeMonitor", "SECURITY ALERT: $reason")
        // Invalidate session
        tokenStorage.bearerToken = null
        onSimAlert(reason)
    }

    private fun computeSimFingerprint(subscriptions: List<SubscriptionInfo>): String {
        val rawData = subscriptions.sortedBy { it.simSlotIndex }.joinToString(separator = "|") { info ->
            val mcc = info.mccString ?: ""
            val mnc = info.mncString ?: ""
            val carrierId = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                info.carrierId
            } else {
                -1
            }
            "${carrierId}:$mcc:$mnc:${info.simSlotIndex}:${info.subscriptionId}"
        }

        return sha256(rawData)
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
