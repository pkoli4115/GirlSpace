package com.girlspace.app.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
@Composable
fun AppScaffold(
    title: String = "Togetherly",
    showTopBar: Boolean = true,
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            if (showTopBar) {
                AppTopBar(
                    title = title,
                    showBack = showBack,
                    onBack = onBack
                )
            }
        }
    ) { padding ->
        Surface(
            modifier = androidx.compose.ui.Modifier.padding(padding)
        ) {
            content()
        }
    }
}
