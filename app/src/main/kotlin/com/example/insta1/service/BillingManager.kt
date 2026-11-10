package com.example.insta1.service

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class BillingManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID_PRO = "storycut_pro_lifetime"
        const val PRODUCT_ID_PRO_LEGACY_199 = "storycut_pro_199"
        const val PRODUCT_ID_PRO_LEGACY_50 = "storycut_pro_50c"
        private const val PREFS_NAME = "storycut_secure_prefs"
        private const val KEY_IS_PRO = "is_storycut_pro_unlocked"
        private const val KEY_PRO_SIG = "storycut_pro_sig"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun computeSignature(isPro: Boolean): String {
        return try {
            val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "storycut_id"
            val raw = "storycut_pro_auth_${deviceId}_$isPro"
            val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readVerifiedPro(): Boolean {
        val storedPro = prefs.getBoolean(KEY_IS_PRO, false)
        if (!storedPro) return false
        val storedSig = prefs.getString(KEY_PRO_SIG, "") ?: ""
        val expectedSig = computeSignature(true)
        return storedSig.isNotEmpty() && storedSig == expectedSig
    }

    private val _isProUnlocked = MutableStateFlow(readVerifiedPro())
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private var billingClient: BillingClient? = null

    init {
        initBillingClient()
    }

    private fun initBillingClient() {
        try {
            val pendingParams = PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build()

            billingClient = BillingClient.newBuilder(context)
                .setListener(this)
                .enablePendingPurchases(pendingParams)
                .build()

            startConnection()
        } catch (_: Throwable) {
            // Gracefully ignore billing initialization failures
        }
    }

    fun startConnection() {
        try {
            billingClient?.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    try {
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            queryPurchases()
                            queryProductDetails()
                        }
                    } catch (_: Throwable) {}
                }

                override fun onBillingServiceDisconnected() {
                    // Reconnect will be triggered on user action
                }
            })
        } catch (_: Throwable) {}
    }

    fun queryProductDetails() {
        try {
            val productList = listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID_PRO)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )

            val params = QueryProductDetailsParams.newBuilder()
                .setProductList(productList)
                .build()

            billingClient?.queryProductDetailsAsync(params) { billingResult, queryProductDetailsResult ->
                try {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val details = queryProductDetailsResult.firstOrNull()
                        _productDetails.value = details
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun queryPurchases() {
        try {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
                try {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        val validPurchases = purchases.filter { purchase ->
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                                    (purchase.products.contains(PRODUCT_ID_PRO) ||
                                     purchase.products.contains(PRODUCT_ID_PRO_LEGACY_199) ||
                                     purchase.products.contains(PRODUCT_ID_PRO_LEGACY_50))
                        }
                        if (validPurchases.isNotEmpty()) {
                            validPurchases.forEach { handlePurchase(it) }
                        } else {
                            // If purchase was refunded or revoked by Google Play, revoke PRO
                            setProUnlocked(false)
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    fun launchPurchaseFlow(activity: Activity) {
        try {
            val details = _productDetails.value
            if (details != null) {
                val productDetailsParamsList = listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build()
                )

                val flowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build()

                billingClient?.launchBillingFlow(activity, flowParams)
            } else {
                // If details are not ready yet, trigger reload
                queryProductDetails()
            }
        } catch (_: Throwable) {}
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        try {
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                setProUnlocked(true)
                // Acknowledge purchase so Google Play doesn't auto-refund after 3 days
                if (!purchase.isAcknowledged) {
                    val ackParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    billingClient?.acknowledgePurchase(ackParams) { _ -> }
                }
            }
        } catch (_: Throwable) {}
    }

    private fun setProUnlocked(unlocked: Boolean) {
        prefs.edit()
            .putBoolean(KEY_IS_PRO, unlocked)
            .putString(KEY_PRO_SIG, computeSignature(unlocked))
            .apply()
        _isProUnlocked.value = unlocked
    }
}
