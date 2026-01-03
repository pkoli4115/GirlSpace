package com.girlspace.app.ui.innercircle.settings

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.girlspace.app.security.InnerCircleLockState
import com.girlspace.app.security.InnerCircleLockStore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleChangePinScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { InnerCircleLockStore(context) }
    val scope = rememberCoroutineScope()

    val state by store.state.collectAsStateWithLifecycle(
        initialValue = InnerCircleLockState(enabled = false, hasPin = false)
    )

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun only4Digits(s: String) = s.length == 4 && s.all { it.isDigit() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Change PIN") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            if (!state.hasPin) {
                Text(
                    text = "No PIN is set yet. Please set a PIN first from Inner Circle Lock screen.",
                    color = MaterialTheme.colorScheme.error
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Back") }
                return@Column
            }

            OutlinedTextField(
                value = currentPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        currentPin = it
                        error = null
                    }
                },
                label = { Text("Current PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            OutlinedTextField(
                value = newPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        newPin = it
                        error = null
                    }
                },
                label = { Text("New PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            OutlinedTextField(
                value = confirmPin,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        confirmPin = it
                        error = null
                    }
                },
                label = { Text("Confirm New PIN") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )

            if (error != null) {
                Text(text = error!!, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(6.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy &&
                        only4Digits(currentPin) &&
                        only4Digits(newPin) &&
                        only4Digits(confirmPin),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        try {
                            if (newPin != confirmPin) {
                                error = "New PINs do not match"
                                return@launch
                            }
                            val ok = store.verifyPin(currentPin)
                            if (!ok) {
                                error = "Current PIN is wrong"
                                return@launch
                            }
                            store.setPin(newPin)
                            onDone()
                        } catch (e: Exception) {
                            error = e.localizedMessage ?: "Something went wrong"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) {
                Text(if (busy) "Please wait..." else "Update PIN")
            }
        }
    }
}
