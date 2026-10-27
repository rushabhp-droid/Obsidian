package com.obsidian.mobile.sim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.security.MessageDigest

class SimFingerprintTest {

    private fun computeSimFingerprint(
        carrierId: Int,
        mcc: String,
        mnc: String,
        slotIndex: Int,
        subId: Int
    ): String {
        val rawData = "$carrierId:$mcc:$mnc:$slotIndex:$subId"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(rawData.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testSimFingerprintConsistency() {
        val hash1 = computeSimFingerprint(1839, "310", "410", 0, 1)
        val hash2 = computeSimFingerprint(1839, "310", "410", 0, 1)
        assertEquals(hash1, hash2)
    }

    @Test
    fun testSimFingerprintDetectsSimChange() {
        val originalHash = computeSimFingerprint(1839, "310", "410", 0, 1)
        // Attacker swaps SIM card with different carrier/IMSI
        val swappedHash = computeSimFingerprint(2020, "310", "260", 0, 2)
        assertNotEquals(originalHash, swappedHash)
    }

    @Test
    fun testSimFingerprintDetectsSlotChange() {
        val hashSlot0 = computeSimFingerprint(1839, "310", "410", 0, 1)
        val hashSlot1 = computeSimFingerprint(1839, "310", "410", 1, 1)
        assertNotEquals(hashSlot0, hashSlot1)
    }
}
