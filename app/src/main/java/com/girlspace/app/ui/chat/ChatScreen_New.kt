// ChatScreen – WhatsApp-Style Skeleton (UI only)
// File: ChatScreen_New.kt
// Version: v0.2.1 – Layout only, no ViewModel wiring yet.

package com.girlspace.app.ui.chat
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * NEW WhatsApp-style layout skeleton.
 *
 * ⚠ This does NOT replace your existing ChatScreen yet.
 *    It’s a separate Composable we can wire to your ViewModel later.
 */
@Composable
fun ChatScreenNew(
    onBack: () -> Unit
) {
    // Temporary local state just for previewing the UI
    var selectedCount by remember { mutableStateOf(0) }
    var input by remember { mutableStateOf("") }

    val fakeMessages = remember {
        // Simple placeholder data
        (1..25).map { idx -> "This is message #$idx" }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // ✅ derivedStateOf must be remembered
    val isAtBottom by remember(listState, fakeMessages) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisible == fakeMessages.lastIndex
        }
    }

    Scaffold(
        topBar = {
            if (selectedCount > 0) {
                ChatSelectionBar(
                    selectionCount = selectedCount,
                    onBack = { selectedCount = 0 },
                    onReply = { /* TODO: hook to reply */ },
                    onForward = { /* TODO: hook to forward */ },
                    onShare = { /* TODO: hook to share */ },
                    onStar = { /* TODO: star */ },
                    onPin = { /* TODO: pin */ },
                    onInfo = { /* TODO: info */ },
                    onDelete = { /* TODO: delete */ },
                    onReport = { /* TODO: report */ }
                )
            } else {
                ChatTopBar(
                    title = "User name",
                    subtitle = "online",
                    isOnline = true,
                    onBack = onBack,
                    onAddUser = { /* TODO: add participant */ },
                    onMenuClick = { /* TODO: overflow menu */ }
                )
            }
        },
        bottomBar = {
            ChatComposer(
                text = input,
                isSending = false,
                isRecording = false,
                hasAttachments = false,
                onTextChange = { input = it },
                onEmojiClick = { /* TODO: open emoji picker */ },
                onAttachClick = { /* TODO: open attach sheet */ },
                onCameraClick = { /* TODO: open camera */ },
                onMicClick = { /* TODO: start/stop recording */ },
                onSendClick = {
                    // TODO: wire to real send logic
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ───────────────── Messages list (placeholder) ─────────────────
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(fakeMessages) { msg ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ───────────────── Scroll-to-bottom FAB ─────────────────
            ScrollToBottomButton(
                visible = !isAtBottom,
                unreadCount = 0,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 72.dp),
                onClick = {
                    scope.launch {
                        if (fakeMessages.isNotEmpty()) {
                            listState.animateScrollToItem(fakeMessages.lastIndex)
                        }
                    }
                }
            )
        }
    }
}
