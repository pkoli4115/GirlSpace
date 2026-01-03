package com.girlspace.app.ui.innercircle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class InnerCircleViewModel(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _ui = MutableStateFlow(InnerCircleUiState())
    val ui: StateFlow<InnerCircleUiState> = _ui

    fun setTermsAccepted(accepted: Boolean) {
        _ui.value = _ui.value.copy(termsAccepted = accepted, error = null)
    }

    fun setStep(step: InnerCircleStep) {
        _ui.value = _ui.value.copy(step = step, error = null)
    }

    fun clearError() {
        _ui.value = _ui.value.copy(error = null)
    }

    fun enableInnerCircle(onSuccess: () -> Unit) {
        val uid = auth.currentUser?.uid ?: run {
            _ui.value = _ui.value.copy(error = "Please sign in again and try.")
            return
        }

        val state = _ui.value
        if (!state.termsAccepted) {
            _ui.value = state.copy(error = "You need to agree to the Terms & Conditions to continue.")
            return
        }

        _ui.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val payload = mapOf(
                    "innerCircle" to mapOf(
                        "enabled" to true,
                        "joinedAt" to FieldValue.serverTimestamp(),
                        "termsAccepted" to true
                    )
                )

                firestore.collection("users")
                    .document(uid)
                    .set(payload, com.google.firebase.firestore.SetOptions.merge())
                    .await()

                _ui.value = _ui.value.copy(isSaving = false, enabled = true)
                onSuccess()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(
                    isSaving = false,
                    error = "Couldn’t enable Inner Circle. Please try again."
                )
            }
        }
    }
}
