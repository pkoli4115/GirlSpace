package com.girlspace.app.ui.profile

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

data class DeleteAccountUiState(
    val isDeleting: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

class DeleteAccountViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _uiState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = _uiState

    fun reset() {
        _uiState.value = DeleteAccountUiState()
    }

    fun requestDelete() {
        val user = auth.currentUser ?: run {
            _uiState.value = DeleteAccountUiState(
                isDeleting = false,
                isSuccess = false,
                errorMessage = "No active user session."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = DeleteAccountUiState(isDeleting = true)

            try {
                val uid = user.uid
                val email = user.email ?: ""

                // 1) Mark Firestore doc as deleted
                firestore.collection("users")
                    .document(uid)
                    .set(
                        mapOf(
                            "deleted" to true,
                            "deletedAt" to FieldValue.serverTimestamp(),
                            "deleteRequested" to true,
                            "deleteRequestedAt" to FieldValue.serverTimestamp(),
                            "deleteAfterBillingPeriod" to true,
                            "authDeleted" to false
                        ),
                        SetOptions.merge()
                    )
                    .await()

                // 2) Create GDPR deletion request
                firestore.collection("gdpr_delete_requests")
                    .document(uid)
                    .set(
                        mapOf(
                            "uid" to uid,
                            "email" to email,
                            "requestedAt" to FieldValue.serverTimestamp(),
                            "status" to "pending",
                            "deleteAfterBillingPeriod" to true
                        ),
                        SetOptions.merge()
                    )
                    .await()

                // 3) Delete Firebase Auth user using await()
                try {
                    user.delete().await()
                } catch (e: Exception) {
                    val msg = e.message ?: ""
                    if (msg.contains("recent", true)) {
                        _uiState.value = DeleteAccountUiState(
                            isDeleting = false,
                            isSuccess = false,
                            errorMessage = "Please logout and login again before deletion (Google security)."
                        )
                        return@launch
                    }

                    _uiState.value = DeleteAccountUiState(
                        isDeleting = false,
                        isSuccess = false,
                        errorMessage = "Failed to delete account: ${e.message}"
                    )
                    return@launch
                }

                // Success
                _uiState.value = DeleteAccountUiState(
                    isDeleting = false,
                    isSuccess = true
                )

            } catch (e: Exception) {
                _uiState.value = DeleteAccountUiState(
                    isDeleting = false,
                    isSuccess = false,
                    errorMessage = e.message ?: "Something went wrong."
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: DeleteAccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var confirmChecked by remember { mutableStateOf(false) }
    var billingChecked by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onAccountDeleted()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Delete account") },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            viewModel.reset()
                            onBack()
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    "Are you sure you want to delete your account?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))

                Text("This will:", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("• Disable your profile immediately")
                Text("• Schedule your messages & media for deletion")
                Text("• Keep minimal logs for legal compliance")

                Spacer(Modifier.height(16.dp))

                Text(
                    "Subscriptions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deleting the account does NOT cancel Google Play subscriptions. "
                            + "Please manage billing from Play Store → Payments & subscriptions.",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = confirmChecked,
                        onCheckedChange = { confirmChecked = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("I understand my account will be deleted permanently.")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = billingChecked,
                        onCheckedChange = { billingChecked = it }
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("I will manage/cancel my subscription in Google Play.")
                }

                if (uiState.errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        uiState.errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                if (uiState.isDeleting) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Deleting your account…")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.requestDelete() },
                    enabled = confirmChecked && billingChecked && !uiState.isDeleting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete my account")
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Google may ask you to sign in again for security.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
