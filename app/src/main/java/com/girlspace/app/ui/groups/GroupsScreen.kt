package com.girlspace.app.ui.groups

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.girlspace.app.data.groups.GroupsScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    navController: NavHostController,
    viewModel: GroupsViewModel = viewModel(),
    onUpgrade: () -> Unit,
    scope: GroupsScope = GroupsScope.PUBLIC
) {

    val groups by viewModel.groups.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val creationBlocked by viewModel.creationBlocked.collectAsState()

    LaunchedEffect(scope) {
        viewModel.setScope(scope)
    }

    // ✅ Root-cause fix: use a stable backStackEntry + its own savedStateHandle
    val backStackEntry by navController.currentBackStackEntryAsState()
    val handle = backStackEntry?.savedStateHandle

    val pickedFlow = remember(handle) {
        handle?.getStateFlow("picked_member_ids", emptyList<String>())
    }
    val pickedMemberIds by (pickedFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })

    LaunchedEffect(pickedMemberIds) {
        if (pickedMemberIds.isEmpty()) return@LaunchedEffect
        val h = handle ?: return@LaunchedEffect

        val groupId = h.get<String>("pending_group_id")
        if (groupId.isNullOrBlank()) {
            // stale result: clear to prevent loops/blank states
            h.remove<List<String>>("picked_member_ids")
            return@LaunchedEffect
        }

        viewModel.addMembersToGroup(groupId, pickedMemberIds.toSet())

        android.widget.Toast.makeText(
            navController.context,
            "Members added",
            android.widget.Toast.LENGTH_SHORT
        ).show()

        // ✅ one-shot cleanup
        h.remove<List<String>>("picked_member_ids")
        h.remove<String>("pending_group_id")
    }

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (scope == GroupsScope.INNER_CIRCLE)
                            "Inner Circle Communities"
                        else
                            "Groups & Communities"
                    )
                }
            )
        },
        floatingActionButton = {
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = androidx.compose.ui.Modifier
                    .padding(16.dp)
                    .size(56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(28.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Create group",
                    tint = Color.White
                )
            }
        }
    ) { padding ->

        Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            when {
                isLoading && groups.isEmpty() -> {
                    Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                groups.isEmpty() -> {
                    EmptyGroupsState(onCreateClick = { showCreateDialog = true })
                }

                else -> {
                    LazyColumn(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(groups) { group ->
                            GroupCard(
                                group = group,
                                onJoin = { viewModel.joinGroup(group) },
                                onLeave = { viewModel.leaveGroup(group) },
                                onOpenChat = {
                                    val encoded = Uri.encode(group.name.ifBlank { "Group" })
                                    navController.navigate("group_chat/${group.id}/$encoded")
                                },
                                onAddMembers = {
                                    // ✅ store pending groupId on this screen (receiver)
                                    handle?.set("pending_group_id", group.id)

                                    val scopeStr =
                                        if (scope == GroupsScope.INNER_CIRCLE) "inner" else "public"

                                    navController.navigate("add_members/${group.id}/$scopeStr")
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // --- Create Group Dialog ---
    if (showCreateDialog) {
        CreateGroupDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, desc ->
                viewModel.createGroup(name, desc)
                showCreateDialog = false
            }
        )
    }

    // --- Upgrade Dialog ---
    if (creationBlocked) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCreationBlocked() },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCreationBlocked()
                    onUpgrade()
                }) {
                    Text("Upgrade")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearCreationBlocked() }) {
                    Text("Cancel")
                }
            },
            title = { Text("Upgrade required") },
            text = {
                Text(
                    "Your current plan doesn’t allow creating more groups.\n\n" +
                            "Free: cannot create groups\n" +
                            "Basic: 1 group\n" +
                            "Premium+: unlimited groups."
                )
            }
        )
    }

    // --- Error Dialog ---
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Groups") },
            text = { Text(errorMessage ?: "") }
        )
    }
}

@Composable
private fun EmptyGroupsState(onCreateClick: () -> Unit) {
    Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = androidx.compose.ui.Modifier.size(64.dp)
        )
        Spacer(androidx.compose.ui.Modifier.height(12.dp))
        Text("No groups yet", fontWeight = FontWeight.SemiBold)
        Spacer(androidx.compose.ui.Modifier.height(6.dp))
        Text(
            "Join or create a community for your interests.",
            textAlign = TextAlign.Center
        )
        Spacer(androidx.compose.ui.Modifier.height(24.dp))
        Button(onClick = onCreateClick) {
            Text("Create your first group")
        }
    }
}

@Composable
private fun GroupCard(
    group: GroupItem,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    onOpenChat: () -> Unit,
    onAddMembers: () -> Unit
) {
    Card(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .clickable { onOpenChat() },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(androidx.compose.ui.Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null)
                Spacer(androidx.compose.ui.Modifier.width(12.dp))
                Column(androidx.compose.ui.Modifier.weight(1f)) {
                    Text(
                        group.name.ifBlank { "Untitled group" },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("${group.memberCount} members")
                }

                if (group.isOwner) {
                    IconButton(onClick = onAddMembers) {
                        Icon(Icons.Default.Add, contentDescription = "Add members")
                    }
                }
            }

            Spacer(androidx.compose.ui.Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.End,
                modifier = androidx.compose.ui.Modifier.fillMaxWidth()
            ) {
                if (group.isMember) {
                    TextButton(onClick = onLeave) { Text("Leave") }
                    Spacer(androidx.compose.ui.Modifier.width(8.dp))
                    Button(onClick = onOpenChat) { Text("Open chat") }
                } else {
                    Button(onClick = onJoin) { Text("Join") }
                }
            }
        }
    }
}

@Composable
private fun CreateGroupDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create new group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group name") }
                )
                Spacer(androidx.compose.ui.Modifier.height(8.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), desc.trim()) },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
