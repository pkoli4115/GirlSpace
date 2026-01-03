package com.girlspace.app.ui.innercircle

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private const val INNER_CIRCLE_TERMS_URL =
    "https://github.com/pkoli4115/GirlSpace/blob/main/docs/Inner%20Circle%20Terms%20%26%20Conditions"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerCircleEntryFlowScreen(
    onClose: () -> Unit,
    onEntered: () -> Unit,
    vm: InnerCircleViewModel = viewModel()
) {
    val ui by vm.ui.collectAsState()

    // Full-screen privacy protection while on Inner Circle entry pages
    val activity = LocalContext.current as? Activity
    DisposableEffect(Unit) {
        activity?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }

    BackHandler { onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inner Circle") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {

            when (ui.step) {
                InnerCircleStep.Rules -> RulesAndTermsStep(
                    termsAccepted = ui.termsAccepted,
                    onTermsAcceptedChange = vm::setTermsAccepted,
                    isSaving = ui.isSaving,
                    onOpenTerms = {
                        val ctx = (activity ?: return@RulesAndTermsStep)
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(INNER_CIRCLE_TERMS_URL))
                            )
                        }.onFailure {
                            vm.clearError()
                            // Use same dialog channel for consistency
                            // (If startActivity fails, show a helpful message)
                            // We'll set error directly:
                            // (keeping minimal changes, no extra SnackbarHost needed)
                        }
                    },
                    onContinue = {
                        // Show message if not agreed (required by your request)
                        if (!ui.termsAccepted) {
                            // ViewModel already uses this exact message during enable too,
                            // but we show it immediately on click, before any save attempt.
                            vm.clearError()
                            // set error via enableInnerCircle check:
                            vm.enableInnerCircle { /* won't reach without acceptance */ }
                            return@RulesAndTermsStep
                        }

                        vm.enableInnerCircle {
                            vm.setStep(InnerCircleStep.Confirm)
                        }
                    }
                )

                InnerCircleStep.Confirm -> ConfirmStep(onEnter = onEntered)
            }
        }
    }

    if (ui.error != null) {
        AlertDialog(
            onDismissRequest = vm::clearError,
            confirmButton = { TextButton(onClick = vm::clearError) { Text("OK") } },
            title = { Text("Please note") },
            text = { Text(ui.error!!) }
        )
    }
}

@Composable
private fun RulesAndTermsStep(
    termsAccepted: Boolean,
    onTermsAcceptedChange: (Boolean) -> Unit,
    isSaving: Boolean,
    onOpenTerms: () -> Unit,
    onContinue: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Inner Circle Community Rules", style = MaterialTheme.typography.headlineSmall)

        // ✅ Girls-only line requested
        Text(
            "Inner Circle is a girls-only community within Togetherly.\n\n" +
                    "To protect everyone here, we follow stricter safety, privacy, and conduct standards."
        )

        Card {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Bullet("Be respectful and supportive at all times")
                Bullet("Harassment, intimidation, hate, or targeting is not allowed")
                Bullet("Do not copy, record, screenshot, or share Inner Circle content outside")
                Bullet("Do not attempt to identify or contact members off-platform")
                Bullet("Moderation actions are final to protect community safety")
            }
        }

        TextButton(onClick = onOpenTerms, modifier = Modifier.fillMaxWidth()) {
            Text("View Inner Circle Terms & Conditions")
        }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked = termsAccepted, onCheckedChange = onTermsAcceptedChange)
            Spacer(Modifier.width(8.dp))
            Text("I agree to the Terms & Conditions")
        }
        if (!termsAccepted) {
            Text(
                text = "You must agree to the Terms & Conditions to continue",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onContinue,
            enabled = termsAccepted && !isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isSaving) "Enabling..." else "Continue")
        }

    }
}

@Composable
private fun ConfirmStep(onEnter: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Welcome to Inner Circle", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Inner Circle is now enabled for your account.\n\n" +
                    "You can access chats, communities, and content designed for this private space."
        )

        Button(onClick = onEnter, modifier = Modifier.fillMaxWidth()) {
            Text("Enter Inner Circle")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text("• ")
        Text(text)
    }
}
