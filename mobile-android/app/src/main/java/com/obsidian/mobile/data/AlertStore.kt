package com.obsidian.mobile.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * The bell on the Home tab: what the app warned about (scam links in messages,
 * links stopped by browser defense, firewall changes). Kept in a private file
 * on the phone, newest first, at most [CAPACITY]. Never holds message text.
 */
class AlertStore(private val file: File) {

    @Serializable
    data class Alert(
        val id: String,
        val at: Long,
        val title: String,
        val body: String,
        val severity: Severity,
        val read: Boolean = false,
    )

    enum class Severity { DANGER, WARNING, INFO }

    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(Alert.serializer())
    private val _alerts = MutableStateFlow(load())
    val alerts: StateFlow<List<Alert>> = _alerts.asStateFlow()

    @Synchronized
    fun add(title: String, body: String, severity: Severity) {
        save((listOf(Alert(UUID.randomUUID().toString(), System.currentTimeMillis(), title, body, severity)) + _alerts.value)
            .take(CAPACITY))
    }

    @Synchronized
    fun markAllRead() = save(_alerts.value.map { it.copy(read = true) })

    @Synchronized
    fun clear() = save(emptyList())

    private fun save(alerts: List<Alert>) {
        _alerts.value = alerts
        runCatching { file.writeText(json.encodeToString(serializer, alerts)) }
    }

    private fun load(): List<Alert> =
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())

    companion object {
        const val CAPACITY = 100
    }
}
