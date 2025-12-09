package com.girlspace.app.ui.profile
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.lazy.items
import androidx.navigation.NavController
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import com.girlspace.app.ui.chat.ChatViewModel
import androidx.compose.material3.CircularProgressIndicator
import android.app.Activity
import androidx.compose.foundation.layout.RowScope
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.facebook.login.LoginManager
import com.girlspace.app.R
import com.girlspace.app.ui.onboarding.OnboardingViewModel
import com.girlspace.app.utils.AppInfo
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await


@Composable
fun ProfileScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    onUpgrade: () -> Unit,
    profileUserId: String? = null   // profile being viewed (null = self)
) {
    val context = LocalContext.current
    val activity = context as Activity

    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser

    // ViewModels
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val currentThemeMode by onboardingViewModel.themeMode.collectAsState()

    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileState by profileViewModel.uiState.collectAsState()

    // 🔹 Chat VM used for "Message" button → start or open 1:1 thread
    val chatViewModel: ChatViewModel = viewModel()
    val lastStartedThread by chatViewModel.lastStartedThread.collectAsState()

    // Local state derived from Firestore + FirebaseAuth (for SELF profile only)
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var isPremium by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf("free") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var vibeKey by remember { mutableStateOf<String?>(null) }
    var savedCount by remember { mutableStateOf(0) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var showVibeDialog by remember { mutableStateOf(false) }

    // Simple per-user media (posts / reels / photos) for the content tabs
    var userPosts by remember { mutableStateOf<List<ProfileMediaItem>>(emptyList()) }
    var userReels by remember { mutableStateOf<List<ProfileMediaItem>>(emptyList()) }
    var userPhotos by remember { mutableStateOf<List<ProfileMediaItem>>(emptyList()) }

    // Decide which user we are actually viewing
    val profileUser = profileState.userProfile
    val selfUid = user?.uid
    val profileOwnerId = profileUser?.id ?: profileUserId ?: selfUid
    val isSelf = profileOwnerId != null && profileOwnerId == selfUid

    // ----------------------------------------------------------
    // Load profile details + saved posts (SELF only)
    // ----------------------------------------------------------
    LaunchedEffect(profileUserId, user?.uid) {
        val currentUserId = user?.uid
        val targetUserId = profileUserId ?: currentUserId ?: return@LaunchedEffect

        // Ask ViewModel to load data for this user
        profileViewModel.onAction(ProfileAction.LoadProfile(targetUserId))

        if (currentUserId != null && targetUserId == currentUserId) {
            // SELF-only Firestore listeners (vibe, plan, saved posts)
            val usersRef = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)

            usersRef.addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("GirlSpace", "Profile listen failed", e)
                    return@addSnapshotListener
                }
                val doc = snap ?: return@addSnapshotListener

                name = (doc.getString("name") ?: user?.displayName ?: "")
                    .ifBlank { "Togetherly user" }

                email = doc.getString("email") ?: user?.email.orEmpty()
                phone = doc.getString("phone") ?: user?.phoneNumber.orEmpty()

                provider = (doc.getString("provider")
                    ?: user?.providerData?.firstOrNull()?.providerId ?: "")
                    .ifBlank { "unknown" }

                val planRaw = doc.getString("plan")
                val isPremiumFlag = doc.getBoolean("isPremium") ?: false
                val resolvedPlan =
                    (planRaw ?: if (isPremiumFlag) "premium" else "free").lowercase()

                plan = resolvedPlan
                isPremium = resolvedPlan == "premium"

                photoUrl = doc.getString("photoUrl") ?: user?.photoUrl?.toString()

                val storedVibe = doc.getString("vibeKey")
                vibeKey = storedVibe ?: currentThemeMode
            }

            // Saved posts count listener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .collection("savedPosts")
                .addSnapshotListener { savedSnap, savedErr ->
                    if (savedErr != null) {
                        Log.e("GirlSpace", "Saved posts listen failed", savedErr)
                        return@addSnapshotListener
                    }
                    savedCount = savedSnap?.size() ?: 0
                }
        } else {
            // 🔹 Viewing SOMEONE ELSE → clear self-only fields so they don't flash
            name = ""
            email = ""
            phone = ""
            provider = ""
            plan = "free"
            isPremium = false
            photoUrl = null
            vibeKey = null
            savedCount = 0
        }
    }

    // Load real Posts / Reels / Photos for the profile owner
    LaunchedEffect(profileOwnerId) {
        val ownerId = profileOwnerId ?: return@LaunchedEffect

        try {
            val firestore = FirebaseFirestore.getInstance()

            // Helper to try a simple equality query on a given field
            suspend fun queryByField(field: String): List<com.google.firebase.firestore.DocumentSnapshot> {
                return firestore.collection("posts")
                    .whereEqualTo(field, ownerId)
                    .get()
                    .await()
                    .documents
            }

            // 1) Try the "ideal" field first
            var docs = queryByField("authorId")

            // 2) Fallbacks for common schemas (userId / uid)
            if (docs.isEmpty()) {
                docs = queryByField("userId")
            }
            if (docs.isEmpty()) {
                docs = queryByField("uid")
            }

            // 3) Ultimate fallback – small scan + client-side filter
            if (docs.isEmpty()) {
                val snap = firestore.collection("posts")
                    .limit(200)
                    .get()
                    .await()

                docs = snap.documents.filter { doc ->
                    val candidates = listOf("authorId", "userId", "uid", "ownerId", "creatorId")
                    candidates.any { key ->
                        val v = doc.getString(key)
                        v == ownerId
                    }
                }
            }

            val posts = mutableListOf<ProfileMediaItem>()
            val reels = mutableListOf<ProfileMediaItem>()
            val photos = mutableListOf<ProfileMediaItem>()

            for (doc in docs) {
                val type = (doc.getString("type") ?: "post").lowercase()
                val caption = doc.getString("caption") ?: ""
                val mediaUrls = doc.get("mediaUrls") as? List<*>
                val firstMediaUrl = mediaUrls?.firstOrNull() as? String
                val thumb = firstMediaUrl ?: doc.getString("thumbnailUrl")

                val item = ProfileMediaItem(
                    id = doc.id,
                    caption = caption,
                    thumbnailUrl = thumb,
                    type = type
                )

                when (type) {
                    "reel", "reels", "video" -> reels.add(item)
                    "photo", "image", "picture" -> photos.add(item)
                    else -> posts.add(item)
                }
            }

            userPosts = posts
            userReels = reels
            userPhotos = photos
        } catch (e: Exception) {
            Log.e("GirlSpace", "Failed to load user media", e)
        }
    }

    // --- Vibe options used by row & dialog (SELF only) ---
    val vibeOptions = remember {
        listOf(
            VibeOption("serenity", "Serenity", Color(0xFF1877F2)),
            VibeOption("radiance", "Radiance", Color(0xFFF77737)),
            VibeOption("wisdom", "Wisdom", Color(0xFF0A66C2)),
            VibeOption("pulse", "Pulse", Color(0xFF111827)),
            VibeOption("harmony", "Harmony", Color(0xFF25D366)),
            VibeOption("ignite", "Ignite", Color(0xFFFF0000))
        )
    }

    val currentVibeLabel = vibeOptions
        .firstOrNull { it.key == vibeKey }
        ?.label ?: "Not set"

    val currentVibeColor = vibeOptions
        .firstOrNull { it.key == vibeKey }
        ?.color ?: MaterialTheme.colorScheme.primary

    // Google sign-out client
    val webClientId = remember { context.getString(R.string.default_web_client_id) }
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    // Avatar picker launcher (SELF only)
    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && user != null) {
            isUploadingAvatar = true
            val uid = user.uid
            val storageRef = FirebaseStorage.getInstance()
                .reference
                .child("users/$uid/avatar.jpg")

            storageRef.putFile(uri)
                .addOnSuccessListener {
                    storageRef.downloadUrl
                        .addOnSuccessListener { downloadUri ->
                            val url = downloadUri.toString()
                            photoUrl = url
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .set(
                                    mapOf("photoUrl" to url),
                                    SetOptions.merge()
                                )
                                .addOnFailureListener { e ->
                                    Log.e("GirlSpace", "Failed to save photoUrl", e)
                                }
                            isUploadingAvatar = false
                            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .addOnFailureListener { e ->
                            Log.e("GirlSpace", "Failed to get download URL", e)
                            isUploadingAvatar = false
                            Toast.makeText(
                                context,
                                "Failed to update photo: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("GirlSpace", "Avatar upload failed", e)
                    isUploadingAvatar = false
                    Toast.makeText(
                        context,
                        "Upload failed: ${e.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    val planLabel = when (plan) {
        "basic" -> "Basic (paid)"
        "premium" -> "Premium+ (all access)"
        else -> "Free (ads)"
    }

    val scrollState = rememberScrollState()

    val headerName = when {
        isSelf && name.isNotBlank() -> name
        !isSelf && profileUser != null -> profileUser.displayName
        else -> name.ifBlank { "Togetherly user" }
    }

    val headerPhotoUrl = when {
        isSelf && !photoUrl.isNullOrBlank() -> photoUrl
        !isSelf && profileUser?.avatarUrl != null -> profileUser.avatarUrl
        else -> photoUrl
    }

    val headerEmail = if (isSelf) email else ""
    val headerPhone = if (isSelf) phone else ""

    // ----------------------------------------------------------
    // UI: Loading state vs full profile
    // ----------------------------------------------------------
    if (!isSelf && profileOwnerId != null && profileUser == null) {
        // 🔹 Viewing someone else, but their profile hasn't loaded yet
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading profile...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        // 🔹 Self-profile OR other user's profile (data available)
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {

                // 1) Core Identity (Avatar + Name + Email + Phone)
                ProfileHeaderSection(
                    name = headerName,
                    email = headerEmail,
                    phone = headerPhone,
                    photoUrl = headerPhotoUrl,
                    isUploadingAvatar = isUploadingAvatar,
                    onAvatarClick = {
                        if (isSelf) {
                            avatarPickerLauncher.launch("image/*")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 2) Social metrics row (Followers / Following / Friends)
                SocialMetricsRow(
                    followers = profileState.profileStats.followersCount,
                    following = profileState.profileStats.followingCount,
                    friends = profileState.profileStats.friendsCount,
                    mutualPreview = profileState.profileStats.mutualConnectionsPreview,
                    onFollowersClick = {
                        profileOwnerId?.let { uid ->
                            navController.navigate("friends?userId=$uid&tab=followers")
                        }
                    },
                    onFollowingClick = {
                        profileOwnerId?.let { uid ->
                            navController.navigate("friends?userId=$uid&tab=following")
                        }
                    },
                    onFriendsClick = {
                        profileOwnerId?.let { uid ->
                            navController.navigate("friends?userId=$uid&tab=friends")
                        }
                    }
                )

                // 2b) Other-user actions (Follow + Message)
                if (!isSelf && profileState.userProfile != null && profileOwnerId != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OtherProfileActionsRow(
                        relationshipStatus = profileState.relationshipStatus,
                        onFollowClick = { profileViewModel.onAction(ProfileAction.OnFollowClicked) },
                        onMessageClick = {
                            val me = selfUid
                            if (me == null) {
                                Toast
                                    .makeText(
                                        context,
                                        "Please log in to message",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            } else {
                                // 🔹 Ask ChatViewModel to start or get the real 1:1 thread
                                chatViewModel.startChatWithUser(profileOwnerId)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3) Content tabs (Posts / Reels / Photos)
                // 3) Content tabs (Posts / Reels / Photos + counts)
                val postsCount = userPosts.size
                val reelsCount = userReels.size
                val photosCount = userPhotos.size

                ProfileTabsRow(
                    selectedTab = profileState.selectedTab,
                    postsCount = postsCount,
                    reelsCount = reelsCount,
                    photosCount = photosCount,
                    onTabSelected = { tab ->
                        profileViewModel.onAction(ProfileAction.OnTabSelected(tab))
                    }
                )


                Spacer(modifier = Modifier.height(20.dp))

                // 3b) Content section – real media for this user
                ProfileContentSection(
                    selectedTab = profileState.selectedTab,
                    posts = userPosts,
                    reels = userReels,
                    photos = userPhotos
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ---------------- SELF-ONLY SECTION ----------------
                if (isSelf) {

                    // 4) Login provider + Plan
                    ProfileMetaSection(
                        provider = provider,
                        planLabel = planLabel
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 5) Vibe selection row
                    VibeRow(
                        currentVibeLabel = currentVibeLabel,
                        currentVibeColor = currentVibeColor,
                        onClick = { showVibeDialog = true }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 6) Saved posts preview row
                    SavedPostsRow(
                        savedCount = savedCount,
                        onClick = { navController.navigate("savedPosts") }
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // 7) Plan badge (free / basic / premium)
                    PlanBadge(
                        isPremium = isPremium,
                        plan = plan
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Spacer(modifier = Modifier.height(16.dp))

                    Spacer(modifier = Modifier.height(12.dp))

                    // 10) Delete account → dedicated screen
                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onClick = { navController.navigate("deleteAccount") },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Delete my account",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 11) App version / release info
                    VersionInfoSection()
                }
            }
        }
    }

    // 🔹 Once ChatViewModel has started/loaded a thread, jump into ChatScreen
    LaunchedEffect(lastStartedThread) {
        val thread = lastStartedThread
        if (thread != null) {
            navController.navigate("chat/${thread.id}")
            chatViewModel.consumeLastStartedThread()
        }
    }

    // Vibe selection dialog (SELF only)
    if (showVibeDialog && isSelf) {
        VibeDialog(
            vibeOptions = vibeOptions,
            onboardingViewModel = onboardingViewModel,
            currentUserUid = user?.uid,
            onDismiss = { showVibeDialog = false },
            onVibeSelected = { selectedKey ->
                vibeKey = selectedKey
                showVibeDialog = false
            }
        )
    }
}

// -----------------------------
// Subsections (Composable parts)
// -----------------------------


@Composable
private fun ProfileHeaderSection(
    name: String,
    email: String,
    phone: String,
    photoUrl: String?,
    isUploadingAvatar: Boolean,
    onAvatarClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(Color(0xFF1666C5))
                .clickable { onAvatarClick() },
            contentAlignment = Alignment.Center
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isUploadingAvatar) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Uploading photo...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        if (email.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        if (phone.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "📱 $phone",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SocialMetricsRow(
    followers: Int,
    following: Int,
    friends: Int,
    mutualPreview: List<String>,
    onFollowersClick: () -> Unit,
    onFollowingClick: () -> Unit,
    onFriendsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricItem(
                label = "Followers",
                value = followers,
                onClick = onFollowersClick
            )
            MetricItem(
                label = "Following",
                value = following,
                onClick = onFollowingClick
            )
            MetricItem(
                label = "Friends",
                value = friends,
                onClick = onFriendsClick
            )
        }

        if (mutualPreview.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Mutual: " + mutualPreview.joinToString(", "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun OtherProfileActionsRow(
    relationshipStatus: RelationshipStatus,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Follow / Following / Friends button
        val followLabel: String
        val followBg: Color
        val followTextColor: Color

        when (relationshipStatus) {
            RelationshipStatus.FOLLOWING -> {
                followLabel = "Following"
                followBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                followTextColor = MaterialTheme.colorScheme.primary
            }
            RelationshipStatus.MUTUALS -> {
                followLabel = "Friends"
                followBg = Color(0xFF4CAF50).copy(alpha = 0.15f)
                followTextColor = Color(0xFF2E7D32)
            }
            RelationshipStatus.BLOCKED -> {
                followLabel = "Blocked"
                followBg = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                followTextColor = MaterialTheme.colorScheme.error
            }
            RelationshipStatus.BLOCKED_BY_OTHER -> {
                followLabel = "Blocked you"
                followBg = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                followTextColor = MaterialTheme.colorScheme.error
            }
            else -> {
                followLabel = "Follow"
                followBg = MaterialTheme.colorScheme.primary
                followTextColor = Color.White
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(followBg)
                .clickable(
                    enabled = relationshipStatus != RelationshipStatus.BLOCKED_BY_OTHER
                ) { onFollowClick() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = followLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = followTextColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Message button – now actually opens chat
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surface)
                .clickable { onMessageClick() }
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Message",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun MetricItem(
    label: String,
    value: Int,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ProfileTabsRow(
    selectedTab: ProfileTab,
    postsCount: Int,
    reelsCount: Int,
    photosCount: Int,
    onTabSelected: (ProfileTab) -> Unit
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val postsLabel = if (postsCount > 0) "Posts ($postsCount)" else "Posts"
        val reelsLabel = if (reelsCount > 0) "Reels ($reelsCount)" else "Reels"
        val photosLabel = if (photosCount > 0) "Photos ($photosCount)" else "Photos"

        ProfileTabChip(
            tab = ProfileTab.POSTS,
            label = postsLabel,
            selected = selectedTab == ProfileTab.POSTS,
            onClick = { onTabSelected(ProfileTab.POSTS) }
        )
        ProfileTabChip(
            tab = ProfileTab.REELS,
            label = reelsLabel,
            selected = selectedTab == ProfileTab.REELS,
            onClick = { onTabSelected(ProfileTab.REELS) }
        )
        ProfileTabChip(
            tab = ProfileTab.PHOTOS,
            label = photosLabel,
            selected = selectedTab == ProfileTab.PHOTOS,
            onClick = { onTabSelected(ProfileTab.PHOTOS) }
        )
    }

}

@Composable
private fun RowScope.ProfileTabChip(
    tab: ProfileTab,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val content = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content
        )
    }
}
@Composable
private fun ProfileContentSection(
    selectedTab: ProfileTab,
    posts: List<ProfileMediaItem>,
    reels: List<ProfileMediaItem>,
    photos: List<ProfileMediaItem>
) {
    when (selectedTab) {
        ProfileTab.POSTS -> {
            // For Posts: no inline list to avoid messy stacking.
            // Only show a message when there are zero posts.
            if (posts.isEmpty()) {
                Text(
                    text = "No posts yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // There are posts, but we don't show a preview list here anymore.
                // The count is visible in the "Posts (N)" tab label already.
            }
        }

        ProfileTab.REELS -> {
            val items = reels
            if (items.isEmpty()) {
                Text(
                    text = "No reels yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.4f
                                    )
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.thumbnailUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = "Media thumbnail",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                if (item.caption.isNotBlank()) {
                                    Text(
                                        text = item.caption,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                } else {
                                    Text(
                                        text = "Reel",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        ProfileTab.PHOTOS -> {
            val items = photos
            if (items.isEmpty()) {
                Text(
                    text = "No photos yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(
                                        alpha = 0.4f
                                    )
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!item.thumbnailUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                ) {
                                    AsyncImage(
                                        model = item.thumbnailUrl,
                                        contentDescription = "Media thumbnail",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                if (item.caption.isNotBlank()) {
                                    Text(
                                        text = item.caption,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2
                                    )
                                } else {
                                    Text(
                                        text = "Photo",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun ProfileMetaSection(
    provider: String,
    planLabel: String
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Logged in using: ${provider.ifBlank { "unknown" }}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Current plan: $planLabel",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun VibeRow(
    currentVibeLabel: String,
    currentVibeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(currentVibeColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Vibe",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentVibeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            text = "Change",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SavedPostsRow(
    savedCount: Int,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Saved posts",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (savedCount == 0) "No saved posts yet" else "$savedCount saved",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Text(
            text = "View",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlanBadge(
    isPremium: Boolean,
    plan: String
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                when {
                    plan == "basic" -> Color(0xFF9C27B0).copy(alpha = 0.08f)
                    isPremium -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        val text = when (plan) {
            "basic" -> "💜 Basic member"
            "premium" -> "⭐ Premium member"
            else -> "Free member"
        }
        val color = when {
            plan == "basic" -> Color(0xFF6A1B9A)
            isPremium -> Color(0xFF2E7D32)
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun VersionInfoSection() {
    val context = LocalContext.current
    val versionName = AppInfo.versionName(context)
    val versionCode = AppInfo.versionCode(context)

    Divider()

    Spacer(modifier = Modifier.height(8.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Togetherly",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "Version $versionName (Build $versionCode)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Text(
            text = "Release: Production",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "© 2025 QTI Labs Pvt. Ltd.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )

    }

   }

// -----------------------------
// Vibe dialog + helpers
// -----------------------------

private data class VibeOption(
    val key: String,
    val label: String,
    val color: Color
)

// Simple media item used by profile tabs
private data class ProfileMediaItem(
    val id: String,
    val caption: String,
    val thumbnailUrl: String?,
    val type: String
)

@Composable
private fun VibeDialog(
    vibeOptions: List<VibeOption>,
    onboardingViewModel: OnboardingViewModel,
    currentUserUid: String?,
    onDismiss: () -> Unit,
    onVibeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Choose your vibe",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vibeOptions.forEach { vibe ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                changeVibe(
                                    newVibeKey = vibe.key,
                                    onboardingViewModel = onboardingViewModel,
                                    currentUserUid = currentUserUid
                                )
                                onVibeSelected(vibe.key)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(vibe.color)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = vibe.label,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserPostsScreen(
    userId: String,
    navController: NavController
) {
    var isLoading by remember { mutableStateOf(true) }
    var posts by remember { mutableStateOf<List<ProfileMediaItem>>(emptyList()) }

    LaunchedEffect(userId) {
        try {
            val firestore = FirebaseFirestore.getInstance()

            suspend fun queryByField(field: String): List<com.google.firebase.firestore.DocumentSnapshot> {
                return firestore.collection("posts")
                    .whereEqualTo(field, userId)
                    .get()
                    .await()
                    .documents
            }

            var docs = queryByField("authorId")
            if (docs.isEmpty()) docs = queryByField("userId")
            if (docs.isEmpty()) docs = queryByField("uid")

            if (docs.isEmpty()) {
                val snap = firestore.collection("posts")
                    .limit(200)
                    .get()
                    .await()

                docs = snap.documents.filter { doc ->
                    val candidates = listOf("authorId", "userId", "uid", "ownerId", "creatorId")
                    candidates.any { key ->
                        val v = doc.getString(key)
                        v == userId
                    }
                }
            }

            posts = docs.map { doc ->
                val type = (doc.getString("type") ?: "post").lowercase()
                val mediaUrls = doc.get("mediaUrls") as? List<*>
                val firstMediaUrl = mediaUrls?.firstOrNull() as? String
                val thumb = firstMediaUrl ?: doc.getString("thumbnailUrl")

                ProfileMediaItem(
                    id = doc.id,
                    // we don’t rely on caption here; keep it simple
                    caption = doc.getString("caption") ?: "",
                    thumbnailUrl = thumb,
                    type = type
                )
            }
        } catch (e: Exception) {
            Log.e("GirlSpace", "Failed to load user posts list", e)
            posts = emptyList()
        } finally {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text("Posts") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                posts.isEmpty() -> {
                    Text(
                        text = "No posts yet",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(posts) { item: ProfileMediaItem ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(16.dp),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = if (item.caption.isNotBlank()) item.caption else "Post",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Update vibe both locally (DataStore via OnboardingViewModel) and in Firestore.
 */
private fun changeVibe(
    newVibeKey: String,
    onboardingViewModel: OnboardingViewModel,
    currentUserUid: String?
) {
    onboardingViewModel.saveThemeMode(newVibeKey)

    if (currentUserUid != null) {
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(currentUserUid)
            .set(
                mapOf(
                    "vibeKey" to newVibeKey,
                    "hasVibe" to true
                ),
                SetOptions.merge()
            )
    }
}
