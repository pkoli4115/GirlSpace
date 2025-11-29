package com.girlspace.app.ui.billing

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
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
    val uriHandler = LocalUriHandler.current

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

    fun openManageOnPlay(productId: String) {
        val pkg = context.packageName
        val url =
            "https://play.google.com/store/account/subscriptions?sku=$productId&package=$pkg"
        uriHandler.openUri(url)
    }

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

            Text(
                text = when {
                    plan == "premium" || plan == "basic" ->
                        "Thank you for supporting the platform. You’ll see fewer limits and more features."
                    else ->
                        "Upgrade to unlock more images per post, better media limits and a lighter ad experience."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ----- Basic plan pill -----
            PlanPill(
                title = "Basic",
                subtitle = "For regular members",
                price = uiState.basicPrice ?: "₹199 / month*",
                highlights = listOf(
                    "Up to 5 images per post",
                    "Create & join communities",
                    "Reduced ads in feed"
                ),
                badgeLabel = when (plan) {
                    "basic" -> "Current"
                    "premium" -> null
                    else -> "Starter"
                },
                isCurrent = plan == "basic",
                primaryLabel = when (plan) {
                    "basic" -> "Manage on Google Play"
                    "premium" -> null                // 🔒 no button when already Premium
                    else -> "Upgrade to Basic"
                },
                onPrimaryClick = when (plan) {
                    "basic" -> { { openManageOnPlay(BASIC_MONTHLY_ID) } }
                    "premium" -> { {} }              // no-op, button hidden anyway
                    else -> { { billingManager.launchBasicPurchase(activity) } }
                }
            )

            // ----- Premium+ plan pill -----
            PlanPill(
                title = "Premium+",
                subtitle = "For creators & power users",
                price = uiState.premiumPrice ?: "₹299 / month*",
                highlights = listOf(
                    "Up to 10 images per post",
                    "Priority in feed ranking",
                    "No ads in feed & stories"
                ),
                badgeLabel = when (plan) {
                    "premium" -> "Current"
                    else -> "Popular"
                },
                isCurrent = plan == "premium",
                primaryLabel = when (plan) {
                    "premium" -> "Manage on Google Play"
                    else -> "Go Premium+"
                },
                onPrimaryClick = when (plan) {
                    "premium" -> { { openManageOnPlay(PREMIUM_MONTHLY_ID) } }
                    else -> { { billingManager.launchPremiumPurchase(activity) } }
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "You can cancel or change plans anytime from Google Play. " +
                        "Access continues until the end of the billing period. No refunds.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Text(
                text = "*Play Store will show the exact price for your region.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }

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
private fun PlanPill(
    title: String,
    subtitle: String,
    price: String,
    highlights: List<String>,
    badgeLabel: String?,
    isCurrent: Boolean,
    primaryLabel: String?,
    onPrimaryClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = if (isCurrent) 4.dp else 1.dp,
        color = if (isCurrent) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (badgeLabel != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Text(
                text = highlights.joinToString(separator = "  •  "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (primaryLabel != null) {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    onClick = onPrimaryClick,
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = primaryLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
