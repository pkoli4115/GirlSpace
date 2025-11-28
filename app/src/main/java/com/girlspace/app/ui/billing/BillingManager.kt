package com.girlspace.app.ui.billing
import kotlinx.coroutines.SupervisorJob
import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Central Billing manager for GirlSpace.
 *
 * Handles:
 *  - Connecting to Play Billing
 *  - Querying ProductDetails for Basic & Premium+ subscriptions
 *  - Launching purchase flows
 *  - Acknowledging purchases
 *  - Updating Firestore user document (plan / isPremium)
 */
class BillingManager(
    private val appContext: Context
) : PurchasesUpdatedListener {

    companion object {
        // ⚠️ Make sure these MATCH your Play Console product IDs exactly.
        const val BASIC_MONTHLY_ID = "com.qtilabs.girlspace.sub.basic.monthly"
        const val PREMIUM_MONTHLY_ID = "com.qtilabs.girlspace.sub.premium.monthly"

        private const val TAG = "BillingManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState

    private val billingClient: BillingClient by lazy {
        BillingClient.newBuilder(appContext)
            .enablePendingPurchases()
            .setListener(this)
            .build()
    }

    private val productDetailsMap: MutableMap<String, ProductDetails> = mutableMapOf()

    /* -------------------------------------------------------------
     *  Public API
     * ------------------------------------------------------------ */

    fun startConnection() {
        if (billingClient.isReady) return

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Billing setup finished, querying products...")
                    _uiState.value = _uiState.value.copy(isReady = true)
                    queryProductDetails()
                    restorePurchases()
                } else {
                    val msg = "Billing setup failed: ${billingResult.debugMessage}"
                    Log.e(TAG, msg)
                    setError(msg)
                }
            }

            override fun onBillingServiceDisconnected() {
                Log.w(TAG, "Billing service disconnected")
                _uiState.value = _uiState.value.copy(isReady = false)
            }
        })
    }

    fun endConnection() {
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    fun launchBasicPurchase(activity: Activity) {
        launchPurchase(activity, BASIC_MONTHLY_ID)
    }

    fun launchPremiumPurchase(activity: Activity) {
        launchPurchase(activity, PREMIUM_MONTHLY_ID)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /* -------------------------------------------------------------
     *  PurchasesUpdatedListener
     * ------------------------------------------------------------ */

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null) {
                    purchases.forEach { handlePurchase(it) }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                Log.d(TAG, "User cancelled purchase flow")
            }

            else -> {
                val msg = "Purchase failed: ${billingResult.debugMessage}"
                Log.e(TAG, msg)
                setError(msg)
            }
        }
    }

    /* -------------------------------------------------------------
     *  Internal helpers
     * ------------------------------------------------------------ */

    private fun queryProductDetails() {
        if (!billingClient.isReady) return

        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BASIC_MONTHLY_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build(),
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PREMIUM_MONTHLY_ID)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { result, productDetailsList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                val msg = "queryProductDetailsAsync failed: ${result.debugMessage}"
                Log.e(TAG, msg)
                setError(msg)
                return@queryProductDetailsAsync
            }

            Log.d(TAG, "ProductDetails loaded: ${productDetailsList.size}")
            productDetailsMap.clear()
            productDetailsList.forEach { pd ->
                productDetailsMap[pd.productId] = pd
            }

            // Update UI prices using first pricing phase
            val basicPd = productDetailsMap[BASIC_MONTHLY_ID]
            val premiumPd = productDetailsMap[PREMIUM_MONTHLY_ID]

            val basicPrice = basicPd
                ?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            val premiumPrice = premiumPd
                ?.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice

            _uiState.value = _uiState.value.copy(
                basicPrice = basicPrice,
                premiumPrice = premiumPrice
            )
        }
    }

    private fun launchPurchase(activity: Activity, productId: String) {
        if (!billingClient.isReady) {
            setError("Billing not ready yet, please try again.")
            return
        }

        val productDetails = productDetailsMap[productId]
        if (productDetails == null) {
            setError("Product unavailable. Please reopen the Premium screen.")
            queryProductDetails()
            return
        }

        val offerToken = productDetails
            .subscriptionOfferDetails
            ?.firstOrNull()
            ?.offerToken

        if (offerToken.isNullOrEmpty()) {
            setError("No subscription offer configured for this product.")
            return
        }

        val productDetailsParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()

        val result = billingClient.launchBillingFlow(activity, flowParams)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            val msg = "launchBillingFlow failed: ${result.debugMessage}"
            Log.e(TAG, msg)
            setError(msg)
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // Determine which product was bought
        val productId = purchase.products.firstOrNull() ?: return

        // Acknowledge if needed
        if (!purchase.isAcknowledged) {
            val acknowledgeParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgeParams) { result ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    Log.d(TAG, "Purchase acknowledged for $productId")
                    scope.launch { updateUserPlan(productId) }
                } else {
                    val msg = "Acknowledge failed: ${result.debugMessage}"
                    Log.e(TAG, msg)
                    setError(msg)
                }
            }
        } else {
            // Already acknowledged → just update Firestore
            scope.launch { updateUserPlan(productId) }
        }
    }

    private fun restorePurchases() {
        if (!billingClient.isReady) return

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        billingClient.queryPurchasesAsync(params) { result, purchasesList ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                val msg = "queryPurchasesAsync failed: ${result.debugMessage}"
                Log.e(TAG, msg)
                setError(msg)
                return@queryPurchasesAsync
            }

            purchasesList.forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    val productId = purchase.products.firstOrNull() ?: return@forEach
                    scope.launch { updateUserPlan(productId) }
                }
            }
        }
    }

    private suspend fun updateUserPlan(productId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val planKey = when (productId) {
            BASIC_MONTHLY_ID -> "basic"
            PREMIUM_MONTHLY_ID -> "premium"
            else -> return
        }

        val isPremium = planKey != "free"

        Log.d(TAG, "Updating Firestore for uid=$uid → plan=$planKey, isPremium=$isPremium")

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .set(
                mapOf(
                    "plan" to planKey,
                    "isPremium" to isPremium,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update user plan in Firestore", e)
            }
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(errorMessage = message)
    }
}

/**
 * Simple UI state for PremiumScreen.
 */
data class BillingUiState(
    val isReady: Boolean = false,
    val basicPrice: String? = null,
    val premiumPrice: String? = null,
    val errorMessage: String? = null
)
