package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/**
 * Handles Google Play Billing for the app's lifetime ad-free purchase.
 *
 * The product is non-consumable: one successful purchase grants permanent
 * ad-free access to this app for the user's Google Play account.
 */
object BillingManager : PurchasesUpdatedListener {
    // This ID must exactly match the one-time product configured in Google Play Console.
    private const val PRODUCT_ID = "remove_ads_lifetime"

    // Local cache used to keep the UI responsive while Play Billing is initializing.
    // The actual Play purchase is re-checked when BillingClient connects.
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_PREMIUM = "premium_purchased"

    /** True when the user currently has lifetime ad-free entitlement. */
    var isPremium by mutableStateOf(false)
        private set

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var initialized = false

    /** Initializes BillingClient and restores any existing purchase. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isPremium = prefs.getBoolean(KEY_PREMIUM, false)

        billingClient = BillingClient.newBuilder(appContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        startConnection(appContext)
    }

    /** Connects to Google Play Billing before querying products or purchases. */
    private fun startConnection(context: Context) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    queryExistingPurchase(context)
                }
            }

            override fun onBillingServiceDisconnected() {
                // BillingClient handles reconnection automatically.
            }
        })
    }

    /** Retrieves the configured lifetime ad-free product and its Play Store price. */
    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()

        billingClient?.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = result.productDetailsList.firstOrNull()
            }
        }
    }

    /**
     * Checks Google Play for an already-owned non-consumable purchase.
     * This is what allows ad-free access to survive reinstall/login changes
     * on the same Google Play account.
     */
    private fun queryExistingPurchase(context: Context) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) return@queryPurchasesAsync

            val owned = purchases.any { purchase ->
                purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            setPremium(context, owned)

            // Any purchased but not-yet-acknowledged item must be processed.
            purchases
                .filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { processPurchase(context, it) }
        }
    }

    /** Opens the Google Play purchase flow for the lifetime ad-free product. */
    fun launchPurchase(activity: Activity): BillingResult? {
        val details = productDetails ?: return null
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
            ?: return null

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        return billingClient?.launchBillingFlow(activity, flowParams)
    }

    /** Re-checks the user's Google Play ownership when Restore Purchase is pressed. */
    fun restorePurchases(context: Context) {
        queryExistingPurchase(context.applicationContext)
    }

    /** Lets the UI know whether the Play Store product has been loaded. */
    fun isProductAvailable(): Boolean = productDetails != null

    /** Returns the localized Play Store price, with ₹49 as the UI fallback. */
    fun getPrice(): String {
        return productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            ?: "₹49"
    }

    /** Receives the result of a purchase flow started from the app. */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        if (billingResult.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return

        purchases
            .filter { it.products.contains(PRODUCT_ID) }
            .forEach { purchase ->
                if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                    processPurchase(null, purchase)
                }
            }
    }

    /**
     * Grants the ad-free entitlement only for a completed purchase and
     * acknowledges the purchase when required by Google Play.
     */
    private fun processPurchase(context: Context?, purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID) || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val grantEntitlement = {
            if (context != null) {
                setPremium(context, true)
            } else {
                isPremium = true
            }
        }

        if (purchase.isAcknowledged) {
            grantEntitlement()
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient?.acknowledgePurchase(params) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                grantEntitlement()
            }
        }
    }

    /** Updates the reactive UI state and persists the current entitlement locally. */
    private fun setPremium(context: Context, value: Boolean) {
        isPremium = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREMIUM, value)
            .apply()
    }
}
