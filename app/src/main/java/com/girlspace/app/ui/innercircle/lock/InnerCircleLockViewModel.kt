package com.girlspace.app.ui.innercircle.lock

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.girlspace.app.security.InnerCircleLockStoreProvider
import com.girlspace.app.security.PinCrypto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class InnerCircleLockUiState(
    val enabled: Boolean = false,
    val hasPin: Boolean = false,
    val entered: String = "",
    val error: String? = null,
    val isWorking: Boolean = false,
    val mode: LockMode = LockMode.VERIFY
)

enum class LockMode {
    VERIFY,          // Enter existing PIN to unlock
    SETUP_NEW_PIN    // First-time set PIN
}

class InnerCircleLockViewModel(
    private val appContext: Context
) : ViewModel() {

    private val store = InnerCircleLockStoreProvider.get(appContext)

    private val _ui = MutableStateFlow(InnerCircleLockUiState())
    val ui: StateFlow<InnerCircleLockUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            // continuously reflect store state into UI state
            store.state.collect { s ->
                val hasPin = !s.pinSalt.isNullOrBlank() && !s.pinHash.isNullOrBlank()
                val mode = when {
                    s.enabled && !hasPin -> LockMode.SETUP_NEW_PIN
                    else -> LockMode.VERIFY
                }
                _ui.value = _ui.value.copy(
                    enabled = s.enabled,
                    hasPin = hasPin,
                    mode = mode
                )
            }
        }
    }

    fun onDigit(d: Char) {
        if (!d.isDigit()) return
        val current = _ui.value.entered
        if (current.length >= 4) return
        _ui.value = _ui.value.copy(entered = current + d, error = null)
    }

    fun onBackspace() {
        val current = _ui.value.entered
        if (current.isEmpty()) return
        _ui.value = _ui.value.copy(entered = current.dropLast(1), error = null)
    }

    fun onClear() {
        _ui.value = _ui.value.copy(entered = "", error = null)
    }

    /**
     * Verify or set PIN. Calls onSuccess() when unlocked or PIN set.
     */
    fun submit(onSuccess: () -> Unit) {
        val entered = _ui.value.entered
        if (entered.length != 4) {
            _ui.value = _ui.value.copy(error = "Enter 4-digit PIN")
            return
        }

        viewModelScope.launch {
            _ui.value = _ui.value.copy(isWorking = true, error = null)

            try {
                val s = store.state.first()
                val hasPin = !s.pinSalt.isNullOrBlank() && !s.pinHash.isNullOrBlank()

                val mode = when {
                    s.enabled && !hasPin -> LockMode.SETUP_NEW_PIN
                    else -> LockMode.VERIFY
                }

                when (mode) {
                    LockMode.SETUP_NEW_PIN -> {
                        val salt = PinCrypto.newSaltBase64()
                        val hash = PinCrypto.hashPinBase64(entered, salt)
                        store.setPinMaterial(pinSalt = salt, pinHash = hash)
                        if (!s.enabled) store.setEnabled(true)

                        _ui.value = _ui.value.copy(isWorking = false, entered = "")
                        onSuccess()
                    }

                    LockMode.VERIFY -> {
                        val salt = s.pinSalt
                        val hash = s.pinHash

                        val ok = !salt.isNullOrBlank() &&
                                !hash.isNullOrBlank() &&
                                PinCrypto.verify(entered, salt, hash)

                        if (!ok) {
                            _ui.value = _ui.value.copy(
                                isWorking = false,
                                entered = "",
                                error = "Wrong PIN"
                            )
                        } else {
                            _ui.value = _ui.value.copy(isWorking = false, entered = "")
                            onSuccess()
                        }
                    }
                }
            } catch (t: Throwable) {
                _ui.value = _ui.value.copy(
                    isWorking = false,
                    entered = "",
                    error = "Something went wrong"
                )
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return InnerCircleLockViewModel(context.applicationContext) as T
        }
    }
}
