package com.obsidian.mobile.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * The app PIN, stored only as PBKDF2-HMAC-SHA256 with a random salt:
 * "pbkdf2$<iterations>$<salt b64>$<hash b64>". It never leaves the phone.
 */
object PinHasher {

    const val PIN_LENGTH = 6
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it.isDigit() }

    /** Rejects PINs anyone would try first: one digit repeated, or a straight run like 123456. */
    fun isTooSimple(pin: String): Boolean {
        if (pin.toSet().size == 1) return true
        val steps = pin.zipWithNext { a, b -> b - a }.toSet()
        return steps == setOf(1) || steps == setOf(-1)
    }

    fun hash(pin: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return encode(ITERATIONS, salt, derive(pin, salt, ITERATIONS))
    }

    fun verify(pin: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        val decoder = Base64.getDecoder()
        val salt = decoder.decode(parts[2])
        val expected = decoder.decode(parts[3])
        return MessageDigest.isEqual(expected, derive(pin, salt, iterations))
    }

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(iterations: Int, salt: ByteArray, hash: ByteArray): String {
        val encoder = Base64.getEncoder()
        return "pbkdf2$$iterations$${encoder.encodeToString(salt)}$${encoder.encodeToString(hash)}"
    }

    /** Wait after [failures] wrong PINs in a row: none for the first 4, then 30 s doubling up to 1 h. */
    fun lockoutMillis(failures: Int): Long {
        if (failures < 5) return 0
        val doublings = (failures - 5).coerceAtMost(7)
        return (30_000L shl doublings).coerceAtMost(3_600_000L)
    }
}
