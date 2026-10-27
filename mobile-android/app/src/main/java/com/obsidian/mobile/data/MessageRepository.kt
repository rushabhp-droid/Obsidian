package com.obsidian.mobile.data

import com.obsidian.mobile.intercept.UrlExtractor
import com.obsidian.mobile.network.ApiClient
import com.obsidian.mobile.network.ChannelType
import com.obsidian.mobile.network.ScanLogDto
import com.obsidian.mobile.network.ThreatVerdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class MessageCheckStatus {
    SCAM,
    SUSPICIOUS,
    CHECKING,
    NO_THREATS_FOUND
}

data class MessageCheckItem(
    val id: String = UUID.randomUUID().toString(),
    val sender: String,
    val isSavedContact: Boolean,
    val channel: ChannelType,
    val fullUrl: String,
    val displayDomain: String,
    val timestamp: String,
    val dateGroup: String, // "Today" or "Yesterday"
    val status: MessageCheckStatus,
    val checkedBy: String = "Reported by other users and flagged by security scanners",
    val explanation: String = "We blocked it before it could open. Don't enter any passwords or one-time codes on this site.",
    val isReported: Boolean = false,
    val isTrusted: Boolean = false
)

class MessageRepository {

    private val _messages = MutableStateFlow<List<MessageCheckItem>>(getDefaultSeedMessages())
    val messages: StateFlow<List<MessageCheckItem>> = _messages.asStateFlow()

    fun getMessageById(id: String): MessageCheckItem? {
        return _messages.value.find { it.id == id }
    }

    fun addMessage(item: MessageCheckItem) {
        _messages.value = listOf(item) + _messages.value.filter { it.id != item.id }
    }

    suspend fun syncScans(apiClient: ApiClient) {
        withContext(Dispatchers.IO) {
            try {
                val backendScans = apiClient.service.getScans()
                if (backendScans.isNotEmpty()) {
                    val mappedScans = backendScans.map { mapScanLogToItem(it) }

                    withContext(Dispatchers.Main) {
                        // Merge: backend scans + local unique items
                        val backendIds = mappedScans.map { it.id }.toSet()
                        val localOnly = _messages.value.filter { it.id !in backendIds }
                        _messages.value = mappedScans + localOnly
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun markReported(id: String) {
        _messages.value = _messages.value.map { item ->
            if (item.id == id) {
                item.copy(isReported = true, isTrusted = false, status = MessageCheckStatus.SCAM)
            } else {
                item
            }
        }
    }

    fun undoReport(id: String) {
        _messages.value = _messages.value.map { item ->
            if (item.id == id) {
                item.copy(isReported = false)
            } else {
                item
            }
        }
    }

    fun markTrusted(id: String) {
        _messages.value = _messages.value.map { item ->
            if (item.id == id) {
                item.copy(isTrusted = true, isReported = false, status = MessageCheckStatus.NO_THREATS_FOUND)
            } else {
                item
            }
        }
    }

    private fun mapScanLogToItem(scan: ScanLogDto): MessageCheckItem {
        val url = scan.indicatorChecked ?: ""
        val domain = if (url.isNotBlank()) UrlExtractor.extractDomain(url) else (scan.sender ?: "Unknown")

        val status = when (scan.verdict) {
            ThreatVerdict.MALICIOUS -> MessageCheckStatus.SCAM
            ThreatVerdict.SUSPICIOUS -> MessageCheckStatus.SUSPICIOUS
            ThreatVerdict.PENDING -> MessageCheckStatus.CHECKING
            ThreatVerdict.CLEAN, ThreatVerdict.BYPASSED -> MessageCheckStatus.NO_THREATS_FOUND
        }

        val formattedTime = scan.createdAt?.let {
            try {
                val inst = Instant.parse(it)
                DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(inst)
            } catch (e: Exception) {
                "12:00"
            }
        } ?: SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

        val isReported = status == MessageCheckStatus.SCAM
        val isTrusted = scan.verdict == ThreatVerdict.BYPASSED

        return MessageCheckItem(
            id = scan.id,
            sender = scan.sender ?: "Unknown",
            isSavedContact = false,
            channel = scan.channel,
            fullUrl = url,
            displayDomain = domain,
            timestamp = formattedTime,
            dateGroup = "Today",
            status = status,
            checkedBy = if (scan.vtPositives > 0) "Flagged by ${scan.vtPositives} security scanners on VirusTotal v3" else "Verified by Obsidian threat intelligence",
            explanation = if (status == MessageCheckStatus.SCAM) "We blocked it before it could open. Don't enter any passwords or one-time codes on this site."
            else if (status == MessageCheckStatus.SUSPICIOUS) "This link has suspicious indicators. Proceed with caution."
            else "No threats found across security intelligence engines.",
            isReported = isReported,
            isTrusted = isTrusted
        )
    }

    companion object {
        val instance by lazy { MessageRepository() }

        private fun getDefaultSeedMessages(): List<MessageCheckItem> = listOf(
            MessageCheckItem(
                id = "msg-1",
                sender = "Rahul",
                isSavedContact = true,
                channel = ChannelType.WHATSAPP,
                fullUrl = "secure-login-verify.co/kyc-update",
                displayDomain = "secure-login-verify.co",
                timestamp = "14:32",
                dateGroup = "Today",
                status = MessageCheckStatus.SCAM,
                checkedBy = "Reported by other users and flagged by security scanners",
                explanation = "We blocked it before it could open. Don't enter any passwords or one-time codes on this site."
            ),
            MessageCheckItem(
                id = "msg-2",
                sender = "+91 98200 11234",
                isSavedContact = false,
                channel = ChannelType.SMS,
                fullUrl = "bit.ly/3xQa9",
                displayDomain = "bit.ly/3xQa9",
                timestamp = "12:05",
                dateGroup = "Today",
                status = MessageCheckStatus.SUSPICIOUS,
                checkedBy = "URL shortener hiding destination. Flagged by heuristic scanner.",
                explanation = "This link uses a shortener often associated with phishing campaigns. Proceed with caution."
            ),
            MessageCheckItem(
                id = "msg-3",
                sender = "Delivery Updates",
                isSavedContact = false,
                channel = ChannelType.SMS,
                fullUrl = "track-parcel-info.net/item?id=892",
                displayDomain = "track-parcel-info.net",
                timestamp = "11:48",
                dateGroup = "Today",
                status = MessageCheckStatus.CHECKING,
                checkedBy = "Awaiting VirusTotal v3 and sandbox response",
                explanation = "Analyzing indicators in cloud sandbox. Not yet verified as safe."
            ),
            MessageCheckItem(
                id = "msg-4",
                sender = "Mom",
                isSavedContact = true,
                channel = ChannelType.WHATSAPP,
                fullUrl = "https://youtube.com/watch?v=dQw4w9WgXcQ",
                displayDomain = "youtube.com",
                timestamp = "Yesterday, 21:10",
                dateGroup = "Yesterday",
                status = MessageCheckStatus.NO_THREATS_FOUND,
                checkedBy = "Verified official domain and clean threat intelligence database",
                explanation = "No malicious signatures or phishing reports detected."
            ),
            MessageCheckItem(
                id = "msg-5",
                sender = "Office Group",
                isSavedContact = true,
                channel = ChannelType.WHATSAPP,
                fullUrl = "https://docs.google.com/spreadsheets/d/123",
                displayDomain = "docs.google.com",
                timestamp = "Yesterday, 17:42",
                dateGroup = "Yesterday",
                status = MessageCheckStatus.NO_THREATS_FOUND,
                checkedBy = "Google Workspace domain verified",
                explanation = "Official verified domain."
            ),
            MessageCheckItem(
                id = "msg-6",
                sender = "HDFC-BK",
                isSavedContact = false,
                channel = ChannelType.SMS,
                fullUrl = "https://hdfcbank.com/netbanking",
                displayDomain = "hdfcbank.com",
                timestamp = "Yesterday, 09:15",
                dateGroup = "Yesterday",
                status = MessageCheckStatus.NO_THREATS_FOUND,
                checkedBy = "Verified banking portal domain",
                explanation = "Known clean banking gateway."
            )
        )
    }
}
