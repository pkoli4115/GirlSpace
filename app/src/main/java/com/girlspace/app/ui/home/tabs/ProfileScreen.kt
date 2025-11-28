package com.girlspace.app.ui.profile

import android.app.Activity
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.facebook.login.LoginManager
import com.girlspace.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var provider by remember { mutableStateOf("") }
    var isPremium by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf("free") }

    // Load from Firestore
    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    Log.e("GirlSpace", "Profile listen failed", e)
                    return@addSnapshotListener
                }
                val doc = snap ?: return@addSnapshotListener

                name = (doc.getString("name") ?: user.displayName ?: "")
                    .ifBlank { "GirlSpace user" }
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
            }
    }

    val planLabel = when (plan) {
        "basic" -> "Basic (paid)"
        "premium" -> "Premium+ (all access)"
        else -> "Free (ads)"
    }

    // Google sign-out client
    val webClientId = remember { context.getString(R.string.default_web_client_id) }
    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
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

            // Avatar
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1666C5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "G",
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
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
                    } catch (_: Exception) {}
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
        }
    }
}
