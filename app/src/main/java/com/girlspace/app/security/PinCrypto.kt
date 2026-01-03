// File: app/src/main/java/com/girlspace/app/security/PinCrypto.kt
package com.girlspace.app.security

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import java.util.Base64

/**
 * PinCrypto
 * - Stores only (salt + hash), never the raw PIN.
 * - Uses PBKDF2 for basic protection at rest.
 */
object PinCrypto {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun newSaltBase64(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hashPinBase64(pin4: String, saltBase64: String): String {
        require(pin4.length == 4 && pin4.all { it.isDigit() }) { "PIN must be 4 digits" }

        val salt = Base64.getDecoder().decode(saltBase64)
        val spec = PBEKeySpec(pin4.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return Base64.getEncoder().encodeToString(hash)
    }

    fun verify(pin4: String, saltBase64: String, expectedHashBase64: String): Boolean {
        val computed = hashPinBase64(pin4, saltBase64)
        return constantTimeEquals(computed, expectedHashBase64)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].code xor b[i].code)
        return result == 0
    }
}
