package com.girlspace.app.ui.innercircle.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.girlspace.app.security.InnerCircleLockState
import com.girlspace.app.security.InnerCircleLockStore
import com.girlspace.app.security.InnerCircleSession
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleSettingsScreen(
    onBack: () -> Unit,
    onChangePin: () -> Unit,
    onResetPin: () -> Unit
) {
    val context = LocalContext.current
    val store = remember { InnerCircleLockStore(context) }
    val scope = rememberCoroutineScope()

    val state by store.state.collectAsStateWithLifecycle(
        initialValue = InnerCircleLockState(enabled = false, hasPin = false)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inner Circle Security") },
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

            ListItem(
                headlineContent = { Text("Lock Inner Circle") },
                supportingContent = {
                    Text(
                        if (state.enabled) "PIN required to enter Inner Circle"
                        else "Inner Circle opens without PIN"
                    )
                },
                trailingContent = {
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { enable ->
                            scope.launch {
                                store.setEnabled(enable)
                                InnerCircleSession.lock()

                                // 🔐 FORCE PIN SETUP immediately
                                if (enable && !state.hasPin) {
                                    onChangePin() // routes to inner_lock
                                }
                            }
                        }
                    )

                }
            )

            Divider()

            ListItem(
                headlineContent = { Text("Change PIN") },
                supportingContent = { Text("Use this if you remember your PIN") },
                modifier = Modifier.clickable {
                    // Allow opening even if no PIN (it will show setup if enabled + no pin)
                    onChangePin()
                }
            )

            Divider()

            ListItem(
                headlineContent = { Text("Forgot PIN / Reset") },
                supportingContent = { Text("Verify your login provider to reset") },
                leadingContent = { Icon(Icons.Default.Refresh, contentDescription = null) },
                modifier = Modifier.clickable { onResetPin() }
            )

            Divider()

            Text("Applies only to Inner Circle. Public chats and rest of the app are untouched.")
        }
    }
}
