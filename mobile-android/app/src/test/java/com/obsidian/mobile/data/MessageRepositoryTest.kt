package com.obsidian.mobile.data

import com.obsidian.mobile.network.ChannelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryTest {

    @Test
    fun testAddMessagePrependsToRepository() {
        val repo = MessageRepository()
        val initialCount = repo.messages.value.size

        val newItem = MessageCheckItem(
            id = "new-msg-test",
            sender = "Sender Test",
            isSavedContact = false,
            channel = ChannelType.SMS,
            fullUrl = "phishing-link.biz",
            displayDomain = "phishing-link.biz",
            timestamp = "15:00",
            dateGroup = "Today",
            status = MessageCheckStatus.SCAM
        )

        repo.addMessage(newItem)
        assertEquals(initialCount + 1, repo.messages.value.size)
        assertEquals(newItem.id, repo.messages.value.first().id)
    }

    @Test
    fun testMarkReportedUpdatesStatus() {
        val repo = MessageRepository()
        val testId = "msg-2" // starts as SUSPICIOUS in default seeds

        repo.markReported(testId)
        val item = repo.getMessageById(testId)
        assertEquals(MessageCheckStatus.SCAM, item?.status)
        assertTrue(item?.isReported == true)
        assertFalse(item?.isTrusted == true)
    }

    @Test
    fun testMarkTrustedUpdatesStatus() {
        val repo = MessageRepository()
        val testId = "msg-1" // starts as SCAM in default seeds

        repo.markTrusted(testId)
        val item = repo.getMessageById(testId)
        assertEquals(MessageCheckStatus.NO_THREATS_FOUND, item?.status)
        assertTrue(item?.isTrusted == true)
        assertFalse(item?.isReported == true)
    }

    @Test
    fun testUndoReport() {
        val repo = MessageRepository()
        val testId = "msg-1"

        repo.markReported(testId)
        assertTrue(repo.getMessageById(testId)?.isReported == true)

        repo.undoReport(testId)
        assertFalse(repo.getMessageById(testId)?.isReported == true)
    }
}
