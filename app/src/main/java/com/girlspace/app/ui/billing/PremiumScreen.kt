package com.girlspace.app.ui.billing
import androidx.compose.material.icons.filled.ArrowBack
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.girlspace.app.ui.billing.BillingManager.Companion.BASIC_MONTHLY_ID
import com.girlspace.app.ui.billing.BillingManager.Companion.PREMIUM_MONTHLY_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val activity = context as Activity

    // --- Billing manager ---
    val billingManager = remember { BillingManager(context.applicationContext) }
    LaunchedEffect(Unit) { billingManager.startConnection() }
    DisposableEffect(Unit) {
        onDispose { billingManager.endConnection() }
    }

    val uiState by billingManager.uiState.collectAsState()

    // --- Current plan from Firestore ---
    val auth = remember { FirebaseAuth.getInstance() }
    val user = auth.currentUser
    var plan by remember { mutableStateOf("free") }
    var isPremium by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .addSnapshotListener { snap, _ ->
                val doc = snap ?: return@addSnapshotListener
                plan = doc.getString("plan") ?: "free"
                isPremium = doc.getBoolean("isPremium") ?: false
            }
    }

    // --- Layout ---
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        TopAppBar(
            title = { Text("GirlSpace Premium") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = when {
                    plan == "premium" -> "You’re on the Premium+ plan"
                    plan == "basic" -> "You’re on the Basic plan"
                    else -> "You’re on the Free (ads) plan"
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            if (isPremium) {
                Text(
                    text = "Thank you for supporting GirlSpace. You’ll see fewer limits and more features.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Upgrade to unlock more images per post, better media limits and an ad-light experience.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ----- Basic plan card -----
            PlanCard(
                title = "Basic",
                subtitle = "For active girls who want more freedom",
                price = uiState.basicPrice ?: "₹79 / month*",
                highlights = listOf(
                    "Up to 5 images per post",
                    "Groups – create & join",
                    "Chats with media (photos, voice)",
                    "Lighter ads in feed"
                ),
                isCurrent = plan == "basic",
                isRecommended = plan == "basic" || plan == "free",
                buttonLabel = if (plan == "basic") "Manage subscription" else "Choose Basic",
                onClick = {
                    billingManager.launchBasicPurchase(activity)
                }
            )

            // ----- Premium plan card -----
            PlanCard(
                title = "Premium+",
                subtitle = "For creators & power users",
                price = uiState.premiumPrice ?: "₹149 / month*",
                highlights = listOf(
                    "Up to 10 images per post",
                    "Priority in feed ranking",
                    "Future: 1:1 + group video calls",
                    "Future: Creator badge & boosts",
                    "No ads in feed & stories"
                ),
                isCurrent = plan == "premium",
                isRecommended = true,
                buttonLabel = if (plan == "premium") "Manage subscription" else "Go Premium+",
                onClick = {
                    billingManager.launchPremiumPurchase(activity)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "*Prices shown are examples. Play Store will show the exact price for your region.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }

    // Error dialog from BillingManager
    if (uiState.errorMessage != null) {
        AlertDialog(
            onDismissRequest = { billingManager.clearError() },
            confirmButton = {
                TextButton(onClick = { billingManager.clearError() }) {
                    Text("OK")
                }
            },
            title = { Text("Billing") },
            text = { Text(uiState.errorMessage ?: "") }
        )
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    highlights: List<String>,
    isCurrent: Boolean,
    isRecommended: Boolean,
    buttonLabel: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                if (isRecommended) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isCurrent) "Current" else "Popular",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = price,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                highlights.forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                onClick = onClick,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = buttonLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
