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
    // Google Play is still queried whenever BillingClient connects.
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_PREMIUM = "premium_purchased"

    /** True when the user currently has lifetime ad-free entitlement. */
    var isPremium by mutableStateOf(false)
        private set

    /** True after BillingClient has connected successfully and product queries can run. */
    var isBillingReady by mutableStateOf(false)
        private set

    /** Latest user-facing billing message shown by the purchase UI. */
    var statusMessage by mutableStateOf<String?>(null)
        private set

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var appContext: Context? = null
    private var initialized = false

    /** Initializes BillingClient and restores any existing purchase. */
    fun initialize(context: Context) {
        if (initialized) return
        initialized = true

        // Keep only the application context so purchase callbacks can persist entitlement
        // even when Google Play calls us without an Activity/Context argument.
        appContext = context.applicationContext
        val savedContext = appContext ?: return
        val prefs = savedContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isPremium = prefs.getBoolean(KEY_PREMIUM, false)

        billingClient = BillingClient.newBuilder(savedContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        startConnection(savedContext)
    }

    /** Connects to Google Play Billing before querying products or purchases. */
    private fun startConnection(context: Context) {
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    isBillingReady = true
                    queryProduct()
                    queryExistingPurchase(context, showRestoreMessage = false)
                } else {
                    isBillingReady = false
                    statusMessage = "Google Play Billing is temporarily unavailable. Please try again later."
                }
            }

            override fun onBillingServiceDisconnected() {
                // BillingClient handles reconnection automatically. The UI remains usable,
                // while a later successful connection will restore the billing state.
                isBillingReady = false
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
            } else {
                productDetails = null
            }
        }
    }

    /**
     * Checks Google Play for an already-owned non-consumable purchase.
     * This restores ad-free access after reinstalling the app on the same
     * Google Play account.
     */
    private fun queryExistingPurchase(context: Context, showRestoreMessage: Boolean) {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        billingClient?.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                if (showRestoreMessage) {
                    statusMessage = "Google Play could not verify your purchase right now. Please try again."
                }
                return@queryPurchasesAsync
            }

            val owned = purchases.any { purchase ->
                purchase.products.contains(PRODUCT_ID) &&
                    purchase.purchaseState == Purchase.PurchaseState.PURCHASED
            }

            setPremium(context, owned)

            if (showRestoreMessage) {
                statusMessage = if (owned) {
                    "Your lifetime ad-free purchase has been restored."
                } else {
                    "No lifetime ad-free purchase was found on this Google Play account."
                }
            }

            // Any purchased but not-yet-acknowledged item must be processed.
            purchases
                .filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { processPurchase(it) }

            // A pending purchase is deliberately not treated as owned/ad-free.
            if (!owned && purchases.any {
                    it.products.contains(PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PENDING
                }) {
                statusMessage = "Purchase is pending. Ad-free access will activate after Google Play confirms payment."
            }
        }
    }

    /** Opens the Google Play purchase flow for the lifetime ad-free product. */
    fun launchPurchase(activity: Activity): BillingResult? {
        if (isPremium) {
            statusMessage = "Ad-free access is already active on this device."
            return null
        }

        val details = productDetails
        if (details == null) {
            statusMessage = "Google Play purchase is still loading. Please try again in a moment."
            return null
        }

        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        if (offerToken == null) {
            statusMessage = "The ad-free product is not currently available. Please try again later."
            return null
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()

        val result = billingClient?.launchBillingFlow(activity, flowParams)
        if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
            statusMessage = "Google Play could not start the purchase. Please try again."
        }
        return result
    }

    /** Re-checks the user's Google Play ownership when Restore Purchase is pressed. */
    fun restorePurchases(context: Context) {
        statusMessage = "Checking Google Play for your purchase..."
        queryExistingPurchase(context.applicationContext, showRestoreMessage = true)
    }

    /** Lets the UI know whether the Play Store product has been loaded. */
    fun isProductAvailable(): Boolean = productDetails != null

    /** Returns the localized Play Store price, with ₹49 as the UI fallback. */
    fun getPrice(): String {
        return productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            ?: "₹49"
    }

    /** Clears a previous purchase message when the purchase dialog is opened again. */
    fun clearStatusMessage() {
        statusMessage = null
    }

    /** Receives the result of a purchase flow started from the app. */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases.orEmpty()
                    .filter { it.products.contains(PRODUCT_ID) }
                    .forEach { purchase ->
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> processPurchase(purchase)
                            Purchase.PurchaseState.PENDING -> {
                                // Do not grant ad-free access while payment is pending.
                                statusMessage = "Purchase is pending. Ad-free access will activate after Google Play confirms payment."
                            }
                            else -> {
                                // An unspecified/non-completed state does not grant entitlement.
                                statusMessage = "The purchase has not completed yet."
                            }
                        }
                    }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Cancellation is not an error and must never grant entitlement.
                statusMessage = "Purchase cancelled. No payment was made."
            }

            else -> {
                // Keep the technical BillingResult out of the user-facing message while
                // still giving the user a clear action they can take.
                statusMessage = "The purchase could not be completed. Please try again."
            }
        }
    }

    /**
     * Grants the ad-free entitlement only for a completed purchase and
     * acknowledges the purchase when required by Google Play.
     */
    private fun processPurchase(purchase: Purchase) {
        if (!purchase.products.contains(PRODUCT_ID) || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        val grantEntitlement = {
            // Persist the entitlement using the application context so the successful
            // purchase is not lost when the purchase callback has no Activity context.
            appContext?.let { setPremium(it, true) } ?: run { isPremium = true }
            statusMessage = "Purchase successful. Lifetime ad-free access is now active."
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
            } else {
                // Do not claim success until Google Play has acknowledged the purchase.
                statusMessage = "Payment was received, but Google Play is still confirming the purchase. Please wait a moment and try again."
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
