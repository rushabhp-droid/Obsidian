package com.obsidian.mobile.firewall

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/**
 * The Firewall tab's request list: the last [CAPACITY] lookups and connections,
 * in memory only (never sent anywhere, gone when the app process ends).
 * OUTGOING is an app asking for a name or connecting to an address;
 * INCOMING is the answer that came back (or the refusal we sent instead).
 */
object FirewallLog {

    const val CAPACITY = 500

    enum class Direction { OUTGOING, INCOMING }

    data class Entry(
        val id: Long,
        val at: Long,
        val direction: Direction,
        val host: String,
        val app: String?,
        val blocked: Boolean,
        val reason: FirewallPolicy.Reason,
        /** Resolved addresses for an answer, "port 443" for a connection, and so on. */
        val detail: String?,
    )

    private val ids = AtomicLong()
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(direction: Direction, host: String, app: String?, blocked: Boolean, reason: FirewallPolicy.Reason, detail: String? = null) {
        val entry = Entry(ids.incrementAndGet(), System.currentTimeMillis(), direction, host, app, blocked, reason, detail)
        _entries.update { (listOf(entry) + it).take(CAPACITY) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
