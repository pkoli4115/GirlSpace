package com.girlspace.app.ui.profile
import com.girlspace.app.utils.AppInfo
import androidx.compose.foundation.layout.width
import android.app.Activity
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage

@Composable
fun ProfileScreen(
    navController: NavHostController,
    onLogout: () -> Unit,
    onUpgrade: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser

    // Onboarding / theme ViewModel for vibe changes
    val onboardingViewModel: OnboardingViewModel = hiltViewModel()
    val currentThemeMode by onboardingViewModel.themeMode.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var isPremium by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf("free") }
    var photoUrl by remember { mutableStateOf<String?>(null) }
    var vibeKey by remember { mutableStateOf<String?>(null) }

    var isUploadingAvatar by remember { mutableStateOf(false) }
    var showVibeDialog by remember { mutableStateOf(false) }

    // Load from Firestore and listen to changes
    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("Togetherly", "Profile listen failed", e)
                    return@addSnapshotListener
                }
                val doc = snap ?: return@addSnapshotListener

                name = (doc.getString("name") ?: user.displayName ?: "")
                    .ifBlank { "Togetherly user" }
                email = (doc.getString("email") ?: user.email ?: "")
                phone = (doc.getString("phone") ?: user.phoneNumber ?: "")
                provider = (doc.getString("provider")
                    ?: user.providerData.firstOrNull()?.providerId ?: "")
                    .ifBlank { "unknown" }

                val planRaw = doc.getString("plan")
                val isPremiumFlag = doc.getBoolean("isPremium") ?: false
                val resolvedPlan = (planRaw ?: if (isPremiumFlag) "premium" else "free")
                    .lowercase()

                plan = resolvedPlan
                isPremium = resolvedPlan == "premium"

                photoUrl = doc.getString("photoUrl") ?: user.photoUrl?.toString()

                // If user doc has explicit vibeKey, prefer that.
                // Otherwise fall back to currentThemeMode.
                val storedVibe = doc.getString("vibeKey")
                vibeKey = storedVibe ?: currentThemeMode
            }
    }

    // --- Vibe UI helpers ---
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

    // Avatar picker launcher
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
                            // Save to Firestore
                            FirebaseFirestore.getInstance()
                                .collection("users")
                                .document(uid)
                                .set(
                                    mapOf("photoUrl" to url),
                                    SetOptions.merge()
                                )
                                .addOnFailureListener { e ->
                                    Log.e("Togetherly", "Failed to save photoUrl", e)
                                }
                            isUploadingAvatar = false
                            Toast.makeText(context, "Profile photo updated", Toast.LENGTH_SHORT)
                                .show()
                        }
                        .addOnFailureListener { e ->
                            Log.e("Togetherly", "Failed to get download URL", e)
                            isUploadingAvatar = false
                            Toast.makeText(
                                context,
                                "Failed to update photo: ${e.localizedMessage}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e("Togetherly", "Avatar upload failed", e)
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

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            // Avatar (tap to change)
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1666C5))
                    .clickable {
                        avatarPickerLauncher.launch("image/*")
                    },
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

            Spacer(modifier = Modifier.height(8.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            // Vibe row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { showVibeDialog = true }
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // color dot
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

            Spacer(modifier = Modifier.height(24.dp))

            // Premium badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (isPremium) Color(0xFF4CAF50).copy(alpha = 0.15f)
                        else Color(0xFF9C27B0).copy(alpha = 0.08f)
                    )
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = when (plan) {
                        "basic" -> "💜 Basic member"
                        "premium" -> "⭐ Premium member"
                        else -> "Free member"
                    },
                    color = if (isPremium) Color(0xFF2E7D32) else Color(0xFF6A1B9A),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Upgrade / manage subscription
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = onUpgrade,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFB000FF),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = if (plan == "premium") "Manage subscription" else "Upgrade to Premium",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Logout
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                onClick = {
                    auth.signOut()
                    try {
                        LoginManager.getInstance().logOut()
                    } catch (_: Exception) {
                    }
                    googleSignInClient.signOut()

                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    onLogout()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE0E0E0),
                    contentColor = MaterialTheme.colorScheme.onBackground
                ),
                shape = RoundedCornerShape(999.dp)
            ) {
                Text("Logout")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Delete account (navigates to dedicated screen)
            TextButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                onClick = {
                    navController.navigate("deleteAccount")
                },
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

            // --- Release / Version info section ---
            Spacer(modifier = Modifier.height(24.dp))

            val versionName = AppInfo.versionName(context)
            val versionCode = AppInfo.versionCode(context)

            androidx.compose.material3.Divider()

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
    }

    // Vibe selection dialog – STILL inside ProfileScreen
    if (showVibeDialog) {
        AlertDialog(
            onDismissRequest = { showVibeDialog = false },
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
                                        currentUserUid = user?.uid,
                                    )
                                    vibeKey = vibe.key
                                    showVibeDialog = false
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
                TextButton(onClick = { showVibeDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}


private data class VibeOption(
    val key: String,
    val label: String,
    val color: Color
)

/**
 * Update vibe both locally (DataStore) and in Firestore.
 */
private fun changeVibe(
    newVibeKey: String,
    onboardingViewModel: OnboardingViewModel,
    currentUserUid: String?
) {
    // 1) Update DataStore → theme changes immediately
    onboardingViewModel.saveThemeMode(newVibeKey)

    // 2) Update Firestore per-user
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
