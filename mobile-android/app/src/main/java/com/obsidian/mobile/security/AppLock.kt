package com.obsidian.mobile.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the app's screens are locked. The app starts locked and locks again
 * whenever it goes to the background, except while the user is in a screen we
 * sent them to ourselves (file picker, VPN consent, system settings).
 * Registered on ProcessLifecycleOwner by ObsidianApplication.
 */
object AppLock : DefaultLifecycleObserver {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile private var leavingOnPurpose = false

    fun unlock() {
        _locked.value = false
    }

    fun lock() {
        _locked.value = true
    }

    /** Call right before opening another app's screen for the user; coming back won't ask to unlock. */
    fun expectReturn() {
        leavingOnPurpose = true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (!leavingOnPurpose) lock()
    }

    override fun onStart(owner: LifecycleOwner) {
        leavingOnPurpose = false
    }
}
