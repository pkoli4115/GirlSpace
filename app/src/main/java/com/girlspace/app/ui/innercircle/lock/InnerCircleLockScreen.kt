package com.girlspace.app.ui.innercircle.lock
import androidx.fragment.app.FragmentActivity
import com.girlspace.app.security.InnerCircleBiometric
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.girlspace.app.security.InnerCircleLockState
import com.girlspace.app.security.InnerCircleLockStore
import com.girlspace.app.security.InnerCircleSession
import kotlinx.coroutines.launch
import android.widget.Toast
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleLockScreen(
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    onForgotPin: () -> Unit,
)
{
    val context = LocalContext.current
    val fragActivity = context as? FragmentActivity
    val biometricAvailable = remember(fragActivity) {
        fragActivity != null && InnerCircleBiometric.isAvailable(fragActivity)
    }
    val store = remember { InnerCircleLockStore(context) }
    val scope = rememberCoroutineScope()

    val state by store.state.collectAsStateWithLifecycle(
        initialValue = InnerCircleLockState(enabled = false, hasPin = false)
    )



    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }

    val needsSetup = state.enabled && !state.hasPin
    fun isValid4Digits(s: String) = s.length == 4 && s.all { it.isDigit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inner Circle Lock") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (needsSetup) "Set a 4-digit PIN for Inner Circle"
                else "Enter your 4-digit PIN",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = pin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        pin = it
                        error = null
                    }
                },
                label = { Text("PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            if (needsSetup) {
                OutlinedTextField(
                    value = pin2,
                    onValueChange = {
                        if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                            pin2 = it
                            error = null
                        }
                    },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
            }

            if (error != null) {
                Text(
                    text = error!!,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(6.dp))
            if (!needsSetup && biometricAvailable) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        fragActivity?.let { act ->
                            InnerCircleBiometric.prompt(
                                activity = act,
                                onSuccess = {
                                    Toast.makeText(context, "Unlocked ✅", Toast.LENGTH_SHORT).show()
                                    InnerCircleSession.unlock()
                                    onUnlocked()
                                },

                                onFailureMessage = { msg ->
                                    // Keep it light; user can still enter PIN
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                ) {
                    Text("Unlock with Biometrics")
                }
            }
            if (!needsSetup) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onForgotPin() }
                ) {
                    Text("Forgot PIN / Reset")
                }
            }


            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !isBusy && (
                        if (needsSetup) isValid4Digits(pin) && isValid4Digits(pin2)
                        else isValid4Digits(pin)
                        ),
                onClick = {
                    scope.launch {
                        isBusy = true
                        error = null
                        try {
                            if (needsSetup) {
                                if (pin != pin2) {
                                    error = "PINs do not match"
                                    return@launch
                                }
                                store.setPin(pin)
                                Toast.makeText(context, "PIN updated ✅", Toast.LENGTH_SHORT).show()
                                InnerCircleSession.unlock()
                                onUnlocked()

                            } else {
                                val ok = store.verifyPin(pin)
                                if (ok) {
                                    InnerCircleSession.unlock()
                                    onUnlocked()
                                } else {
                                    error = "Wrong PIN"
                                }
                            }
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Something went wrong"
                        } finally {
                            isBusy = false
                        }
                    }
                }
            ) {
                Text(if (isBusy) "Please wait..." else if (needsSetup) "Set PIN & Continue" else "Unlock")
            }

            Text(
                text = "This lock applies only to Inner Circle.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}
