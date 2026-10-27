package com.obsidian.mobile.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {

    @Test
    fun verifiesOnlyTheRightPin() {
        val stored = PinHasher.hash("482915")
        assertTrue(stored.startsWith("pbkdf2$"))
        assertTrue(PinHasher.verify("482915", stored))
        assertFalse(PinHasher.verify("482916", stored))
        assertFalse(PinHasher.verify("482915", "not a hash"))
    }

    @Test
    fun saltsEveryHash() {
        assertNotEquals(PinHasher.hash("482915"), PinHasher.hash("482915"))
    }

    @Test
    fun rejectsObviousPins() {
        assertTrue(PinHasher.isTooSimple("000000"))
        assertTrue(PinHasher.isTooSimple("123456"))
        assertTrue(PinHasher.isTooSimple("987654"))
        assertFalse(PinHasher.isTooSimple("482915"))
        assertFalse(PinHasher.isValidPin("12345"))
        assertFalse(PinHasher.isValidPin("12345a"))
        assertTrue(PinHasher.isValidPin("482915"))
    }

    @Test
    fun lockoutGrowsAfterFiveWrongPins() {
        assertEquals(0L, PinHasher.lockoutMillis(4))
        assertEquals(30_000L, PinHasher.lockoutMillis(5))
        assertEquals(60_000L, PinHasher.lockoutMillis(6))
        assertEquals(3_600_000L, PinHasher.lockoutMillis(40))
    }
}
