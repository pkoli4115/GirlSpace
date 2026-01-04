package com.girlspace.app.ui.groups

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.girlspace.app.data.groups.GroupsScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    navController: NavHostController,
    groupId: String,
    scope: GroupsScope,
    viewModel: GroupsViewModel = hiltViewModel()
) {
    // ✅ Ensure VM listens to correct collection for this screen
    LaunchedEffect(scope) {
        viewModel.setScope(scope)
    }

    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showArchiveConfirm by remember { mutableStateOf(false) }

    val currentUid = FirebaseAuth.getInstance().currentUser?.uid

    // ✅ Don’t return early; show loading state until group appears
    val group = remember(groups, groupId) { groups.firstOrNull { it.id == groupId } }

    var memberIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var namesByUid by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(groupId, scope) {
        // small delay helps if listener restarts after setScope()
        delay(50)
        val ids = viewModel.getGroupMemberIds(groupId).toList()
        memberIds = ids
        namesByUid = viewModel.getUserNames(ids)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Group info") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->

        if (group == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "Group not found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        val isOwner = group.createdBy == currentUid
        val isAdmin = group.isOwner || isOwner // your current rule

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // -------- Header --------
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Group, contentDescription = null, tint = Color.White)
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.name.ifBlank { "Untitled group" },
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${group.memberCount} members",
                        style = MaterialTheme.typography.bodySmall
                    )

                    if (scope == GroupsScope.INNER_CIRCLE) {
                        Text(
                            text = "Inner Circle",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Divider()

            // -------- Members --------
            Text(
                "Members",
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.SemiBold
            )

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                items(memberIds, key = { it }) { uid ->
                    val isOwnerRow = uid == group.createdBy
                    val displayName = namesByUid[uid] ?: "User"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Person, contentDescription = null)
                        Spacer(Modifier.width(12.dp))

                        Text(
                            text = if (uid == currentUid) "You ($displayName)" else displayName,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (isOwnerRow) {
                            Text(
                                "Owner",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Divider()

            // -------- Actions --------
            Column(modifier = Modifier.padding(16.dp)) {

                if (isAdmin) {
                    Button(
                        onClick = {
                            val scopeStr = if (scope == GroupsScope.INNER_CIRCLE) "inner" else "public"
                            navController.navigate("add_members/${group.id}/$scopeStr")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add / Remove members") }

                    Spacer(Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = { showArchiveConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Archive group")
                    }

                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = {
                        val encodedName = Uri.encode(group.name.ifBlank { "Group" })
                        navController.navigate("group_chat/${group.id}/$encodedName")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Open chat") }
            }
        }
    }

    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            title = { Text("Archive group?") },
            text = { Text("This group will be hidden from all members.") },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveConfirm = false
                    viewModel.archiveGroup(groupId)
                    navController.popBackStack()
                }) { Text("Archive") }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
