// File: app/src/main/java/com/girlspace/app/security/InnerCircleLockStore.kt
package com.girlspace.app.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val IC_DATASTORE_NAME = "inner_circle_lock_prefs"
private val Context.icDataStore by preferencesDataStore(name = IC_DATASTORE_NAME)

/**
 * InnerCircleLockStore (UID-scoped)
 * - Stores Inner Circle lock settings per Firebase user (uid).
 * - Prevents PIN sharing across different accounts/providers on the same device.
 *
 * Depends on your existing:
 * - InnerCircleLockState (must include enabled, hasPin, pinSalt, pinHash OR at least enabled/hasPin + optional salt/hash)
 * - PinCrypto (your existing PinCrypto.kt object)
 */
class InnerCircleLockStore(private val appContext: Context) {

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    // UID-scoped keys (critical fix)
    private fun enabledKey(uid: String) = booleanPreferencesKey("ic_enabled_$uid")
    private fun pinSaltKey(uid: String) = stringPreferencesKey("ic_pin_salt_$uid")
    private fun pinHashKey(uid: String) = stringPreferencesKey("ic_pin_hash_$uid")

    /**
     * Emits current user's Inner Circle lock state.
     * If not logged in -> returns default (disabled).
     */
    val state: Flow<InnerCircleLockState> = appContext.icDataStore.data.map { prefs ->
        val uid = currentUid()
        if (uid.isNullOrBlank()) {
            // Not signed in -> treat as disabled
            return@map InnerCircleLockState(
                enabled = false,
                hasPin = false,
                pinSalt = null,
                pinHash = null
            )
        }

        val enabled = prefs[enabledKey(uid)] ?: false
        val salt = prefs[pinSaltKey(uid)]
        val hash = prefs[pinHashKey(uid)]
        val hasPin = !salt.isNullOrBlank() && !hash.isNullOrBlank()

        InnerCircleLockState(
            enabled = enabled,
            hasPin = hasPin,
            pinSalt = salt,
            pinHash = hash
        )
    }

    /**
     * Enable/disable lock for current uid.
     * If not logged in, this is a no-op.
     */
    suspend fun setEnabled(enabled: Boolean) {
        val uid = currentUid() ?: return
        appContext.icDataStore.edit { prefs ->
            prefs[enabledKey(uid)] = enabled
        }
    }

    /**
     * Option B API: store already-generated salt/hash (Base64 strings).
     * This is what your ViewModel calls: store.setPinMaterial(pinSalt, pinHash)
     */
    suspend fun setPinMaterial(pinSalt: String, pinHash: String) {
        val uid = currentUid() ?: return
        appContext.icDataStore.edit { prefs ->
            prefs[pinSaltKey(uid)] = pinSalt
            prefs[pinHashKey(uid)] = pinHash
        }
    }

    /**
     * Convenience: set/change PIN from raw 4-digit PIN.
     * Uses your existing PinCrypto object (salt+hash Base64).
     */
    suspend fun setPin(pin4: String) {
        require(pin4.length == 4 && pin4.all { it.isDigit() }) { "PIN must be 4 digits" }
        val uid = currentUid() ?: return

        val saltB64 = PinCrypto.newSaltBase64()
        val hashB64 = PinCrypto.hashPinBase64(pin4, saltB64)

        appContext.icDataStore.edit { prefs ->
            prefs[pinSaltKey(uid)] = saltB64
            prefs[pinHashKey(uid)] = hashB64
        }
    }

    /**
     * Verify the provided 4-digit PIN for current uid.
     */
    suspend fun verifyPin(pin4: String): Boolean {
        val uid = currentUid() ?: return false
        if (pin4.length != 4 || !pin4.all { it.isDigit() }) return false

        val prefs = appContext.icDataStore.data.first()
        val saltB64 = prefs[pinSaltKey(uid)] ?: return false
        val hashB64 = prefs[pinHashKey(uid)] ?: return false

        return PinCrypto.verify(pin4, saltB64, hashB64)
    }

    /**
     * Clear only the PIN for current uid (keeps enabled flag as-is).
     */
    suspend fun clearPin() {
        val uid = currentUid() ?: return
        appContext.icDataStore.edit { prefs ->
            prefs.remove(pinSaltKey(uid))
            prefs.remove(pinHashKey(uid))
        }
    }

    /**
     * Reset everything for current uid (disable + clear PIN).
     */
    suspend fun resetAllForCurrentUser() {
        val uid = currentUid() ?: return
        appContext.icDataStore.edit { prefs ->
            prefs[enabledKey(uid)] = false
            prefs.remove(pinSaltKey(uid))
            prefs.remove(pinHashKey(uid))
        }
    }

    /**
     * OPTIONAL (recommended one-time migration helper):
     * If you previously stored device-wide keys (not uid-scoped),
     * this clears them so old PINs can’t accidentally affect behavior.
     *
     * Safe to call once after app update (e.g., during login success).
     */
    suspend fun clearLegacyDeviceWideKeys() {
        val legacyEnabled = booleanPreferencesKey("enabled")
        val legacySalt = stringPreferencesKey("pin_salt_b64")
        val legacyHash = stringPreferencesKey("pin_hash_b64")

        appContext.icDataStore.edit { prefs ->
            prefs.remove(legacyEnabled)
            prefs.remove(legacySalt)
            prefs.remove(legacyHash)
        }
    }
}
