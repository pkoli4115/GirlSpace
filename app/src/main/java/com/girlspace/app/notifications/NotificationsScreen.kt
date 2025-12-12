package com.girlspace.app.ui.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import kotlinx.coroutines.tasks.await

data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val deepLink: String?,
    val read: Boolean,
    val createdAtMillis: Long
)

private fun Any?.toMillisSafe(): Long {
    return when (this) {
        is Long -> this
        is Int -> this.toLong()
        is Double -> this.toLong()
        is Float -> this.toLong()
        is Timestamp -> this.toDate().time
        is Date -> this.time
        else -> 0L
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val firestore = FirebaseFirestore.getInstance()

    var notifications by remember { mutableStateOf<List<AppNotification>>(emptyList()) }

    // ✅ Cache resolved titles by notificationId (prevents "Someone" flicker on scroll)
    val titleCache = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(uid) {
        firestore.collection("users")
            .document(uid)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.map { d ->
                    val createdAny = d.get("createdAt")
                    AppNotification(
                        id = d.id,
                        title = d.getString("title") ?: "Notification",
                        body = d.getString("body") ?: "",
                        deepLink = d.getString("deepLink"),
                        read = d.getBoolean("read") ?: false,
                        createdAtMillis = createdAny.toMillisSafe()
                    )
                } ?: emptyList()

                notifications = list.sortedByDescending { it.createdAtMillis }
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            notifications
                                .filter { !it.read }
                                .forEach { n ->
                                    firestore.collection("users")
                                        .document(uid)
                                        .collection("notifications")
                                        .document(n.id)
                                        .update("read", true)
                                }
                        }
                    ) {
                        Text("Mark all read")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            items(notifications, key = { it.id }) { n ->
                val cachedTitle = titleCache[n.id]
                var resolvedTitle by remember(n.id) {
                    mutableStateOf(cachedTitle ?: n.title)
                }

                // ✅ Resolve sender/group name for chat notifications once, then cache forever
                LaunchedEffect(n.id) {
                    // Already cached → done
                    titleCache[n.id]?.let {
                        resolvedTitle = it
                        return@LaunchedEffect
                    }

                    val link = n.deepLink.orEmpty()
                    if (!link.startsWith("togetherly://chat/")) {
                        // Non-chat or missing deepLink → cache original title so it never flickers
                        titleCache[n.id] = resolvedTitle.ifBlank { "Notification" }
                        return@LaunchedEffect
                    }

                    val threadId = link.substringAfterLast("/")

                    try {
                        val threadDoc = firestore.collection("chatThreads")
                            .document(threadId)
                            .get()
                            .await()

                        if (!threadDoc.exists()) {
                            titleCache[n.id] = resolvedTitle.ifBlank { "Chat" }
                            return@LaunchedEffect
                        }

                        val participants =
                            (threadDoc.get("participants") as? List<*>)?.filterIsInstance<String>().orEmpty()

                        if (participants.size > 2) {
                            // Try to show a real group name if your thread has it
                            val groupName =
                                threadDoc.getString("title")
                                    ?: threadDoc.getString("name")
                                    ?: threadDoc.getString("groupName")

                            val finalTitle = groupName?.takeIf { it.isNotBlank() } ?: "Group chat"
                            titleCache[n.id] = finalTitle
                            resolvedTitle = finalTitle
                        } else {
                            val otherUid = participants.firstOrNull { it != uid }
                            if (otherUid.isNullOrBlank()) {
                                titleCache[n.id] = resolvedTitle.ifBlank { "Chat" }
                                return@LaunchedEffect
                            }

                            val userDoc = firestore.collection("users")
                                .document(otherUid)
                                .get()
                                .await()

                            val name =
                                userDoc.getString("displayName")
                                    ?: userDoc.getString("name")
                                    ?: userDoc.getString("fullName")
                                    ?: userDoc.getString("username")

                            val finalTitle = name?.takeIf { it.isNotBlank() } ?: resolvedTitle.ifBlank { "Chat" }
                            titleCache[n.id] = finalTitle
                            resolvedTitle = finalTitle
                        }
                    } catch (_: Exception) {
                        // Cache fallback to avoid flicker even on failures
                        titleCache[n.id] = resolvedTitle.ifBlank { "Chat" }
                    }
                }

                ListItem(
                    headlineContent = {
                        Text(
                            text = resolvedTitle,
                            style = if (!n.read)
                                MaterialTheme.typography.titleSmall
                            else
                                MaterialTheme.typography.bodyMedium
                        )
                    },
                    supportingContent = {
                        if (n.body.isNotBlank()) Text(n.body)
                    },
                    modifier = Modifier.clickable {
                        firestore.collection("users")
                            .document(uid)
                            .collection("notifications")
                            .document(n.id)
                            .update("read", true)

                        n.deepLink?.let(onNavigate)
                    }
                )
                Divider()
            }
        }
    }
}
