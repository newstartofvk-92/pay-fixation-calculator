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

    /** The Play-verified purchase state. SharedPreferences is not used as proof of ownership. */
    internal var entitlementState by mutableStateOf(BillingEntitlementState())
        private set

    val isPremium: Boolean get() = entitlementState.isAdFree
    val canShowAds: Boolean get() = shouldRequestAds(
        AdEligibility(
            billing = entitlementState,
            consentAllowsAds = AdMobRuntime.consentAllowsAds,
            adsSdkReady = AdMobRuntime.sdkReady
        )
    )

    /** Latest user-facing billing message shown by the purchase UI. */
    var statusMessage by mutableStateOf<String?>(null)
        private set

    var productPrice by mutableStateOf<String?>(null)
        private set

    private var connectionState by mutableStateOf(BillingConnectionState())
    val isBillingReady: Boolean get() = connectionState.ready

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var initialized = false
    private var productQueryInProgress = false
    private var purchaseQueryInProgress = false
    private var restoreMessageRequested = false
    private var purchaseUpdateGeneration = 0L
    private val acknowledgementTracker = PurchaseAcknowledgementTracker()

    /** Initializes BillingClient and restores any existing purchase. */
    fun initialize(context: Context) {
        AdMobRuntime.configureDebugLogging(context.applicationContext)
        if (initialized) return
        initialized = true

        // Keep only the application context for BillingClient's process lifetime.
        val savedContext = context.applicationContext
        billingClient = BillingClient.newBuilder(savedContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

        startConnection()
    }

    /** Connects to Google Play Billing before querying products or purchases. */
    private fun startConnection() {
        val client = billingClient ?: return
        if (client.isReady) {
            AdMobRuntime.debugLog("Billing connection already ready")
            handleBillingReady()
            return
        }
        if (connectionState.connecting) {
            AdMobRuntime.debugLog("Billing connection attempt already in progress")
            return
        }
        connectionState = connectionState.connectionStarted()
        AdMobRuntime.debugLog("Billing connection started")
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                        AdMobRuntime.debugLog(
                            "Billing connection succeeded: code=${billingResult.responseCode}, " +
                                "message=${billingResult.debugMessage}"
                        )
                        handleBillingReady()
                    } else {
                        connectionState = connectionState.connectionFailed()
                        AdMobRuntime.debugLog(
                            "Billing connection failed: code=${billingResult.responseCode}, " +
                                "message=${billingResult.debugMessage}"
                        )
                        statusMessage = "Google Play Billing is temporarily unavailable. Please try again later."
                    }
                }

                override fun onBillingServiceDisconnected() {
                    // BillingClient handles reconnection automatically. The UI remains usable,
                    // while a later successful connection will restore the billing state.
                    AdMobRuntime.debugLog("Billing service disconnected")
                    connectionState = connectionState.disconnected()
                    productDetails = null
                    productPrice = null
                    statusMessage = "Google Play Billing disconnected. Reconnecting…"
                }
            })
        } catch (exception: RuntimeException) {
            connectionState = connectionState.connectionFailed()
            AdMobRuntime.debugLog("Billing connection threw ${exception.javaClass.simpleName}")
            statusMessage = "Google Play Billing is temporarily unavailable. Please try again later."
        }
    }

    /** Handles both initial setup and automatic reconnection observed on app resume. */
    private fun handleBillingReady() {
        connectionState = connectionState.connectionReady()
        if (!connectionState.productDetailsAvailable) queryProduct()
        queryExistingPurchase(showRestoreMessage = false)
    }

    /** Retrieves the configured lifetime ad-free product and its Play Store price. */
    private fun queryProduct() {
        if (productQueryInProgress) return
        val client = billingClient ?: return
        productQueryInProgress = true
        val connectionGeneration = connectionState.generation
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

        try {
            client.queryProductDetailsAsync(params) { billingResult, result ->
                productQueryInProgress = false
                if (connectionGeneration != connectionState.generation) {
                    if (billingClient?.isReady == true && !connectionState.productDetailsAvailable) queryProduct()
                    return@queryProductDetailsAsync
                }
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    productDetails = result.productDetailsList.firstOrNull()
                    productPrice = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                    connectionState = connectionState.productDetailsResult(isProductAvailable())
                    if (productDetails == null) statusMessage = "The ad-free product is not available from Google Play right now."
                } else {
                    productDetails = null
                    productPrice = null
                    connectionState = connectionState.productDetailsResult(available = false)
                    statusMessage = "Google Play could not load the ad-free product. Please try again later."
                }
            }
        } catch (exception: RuntimeException) {
            productQueryInProgress = false
            connectionState = connectionState.productDetailsResult(available = false)
            statusMessage = "Google Play could not load the ad-free product. Please try again later."
        }
    }

    /**
     * Checks Google Play for an already-owned non-consumable purchase.
     * This restores ad-free access after reinstalling the app on the same
     * Google Play account.
     */
    private fun queryExistingPurchase(showRestoreMessage: Boolean) {
        if (purchaseQueryInProgress) {
            AdMobRuntime.debugLog("Billing ownership query not started: another query is in progress")
            if (showRestoreMessage) {
                restoreMessageRequested = true
                statusMessage = "Checking Google Play for your purchase..."
            }
            return
        }
        purchaseQueryInProgress = true
        val purchaseGenerationAtStart = purchaseUpdateGeneration
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()

        val client = billingClient
        if (client == null) {
            AdMobRuntime.debugLog("Billing ownership query inconclusive: BillingClient is unavailable")
            purchaseQueryInProgress = false
            return
        }
        try {
            client.queryPurchasesAsync(params) { billingResult, purchases ->
                purchaseQueryInProgress = false
                val stale = purchaseGenerationAtStart != purchaseUpdateGeneration
                val shouldShowRestoreMessage = showRestoreMessage || restoreMessageRequested
                restoreMessageRequested = false
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    AdMobRuntime.debugLog(
                        "Billing ownership query inconclusive: code=${billingResult.responseCode}, " +
                            "message=${billingResult.debugMessage}"
                    )
                    statusMessage = "Google Play could not verify your purchase right now. Please try again."
                    return@queryPurchasesAsync
                }

                val ownedPurchases = purchases.filter { purchase ->
                    purchase.products.contains(PRODUCT_ID) &&
                        purchase.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                val owned = ownedPurchases.isNotEmpty()
                val pending = purchases.any {
                    it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PENDING
                }
                entitlementState = resolveOwnershipQuery(entitlementState, owned, pending, stale)
                val ownershipOutcome = when {
                    stale && !owned -> "inconclusive_stale_negative"
                    owned -> "verified_ad_free"
                    pending -> "no_owned_purchase_pending"
                    else -> "verified_free"
                }
                AdMobRuntime.debugLog(
                    "Billing ownership query completed: result=$ownershipOutcome " +
                        "entitlement=${entitlementState.entitlement} purchaseFlow=${entitlementState.purchaseFlow}"
                )

                if (shouldShowRestoreMessage) {
                    if (owned) {
                        statusMessage = "Your lifetime ad-free purchase has been restored."
                    } else if (!stale) {
                        statusMessage = "No lifetime ad-free purchase was found on this Google Play account."
                    }
                }

                // Any purchased but not-yet-acknowledged item must be processed.
                ownedPurchases.forEach(::processPurchase)

                // A pending purchase is deliberately not treated as owned/ad-free.
                if (!owned && purchases.any {
                        it.products.contains(PRODUCT_ID) &&
                            it.purchaseState == Purchase.PurchaseState.PENDING
                    }) {
                    statusMessage = "Purchase is pending. Ad-free access will activate after Google Play confirms payment."
                }
            }
        } catch (exception: RuntimeException) {
            purchaseQueryInProgress = false
            AdMobRuntime.debugLog("Billing ownership query threw ${exception.javaClass.simpleName}")
            restoreMessageRequested = false
            statusMessage = "Google Play could not verify your purchase right now. Please try again."
        }
    }

    /** Opens the Google Play purchase flow for the lifetime ad-free product. */
    fun launchPurchase(activity: Activity): BillingResult? {
        if (isPremium) {
            statusMessage = "Ad-free access is already active on this device."
            return null
        }
        if (!isBillingReady || entitlementState.purchaseFlow != PurchaseFlowState.IDLE) {
            statusMessage = "Google Play Billing is busy or reconnecting. Please try again in a moment."
            return null
        }

        val details = productDetails
        if (details == null) {
            statusMessage = "Google Play purchase details are still loading. Please try again in a moment."
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

        entitlementState = entitlementState.purchaseLaunched()
        val result = try {
            billingClient?.launchBillingFlow(activity, flowParams)
        } catch (exception: RuntimeException) {
            null
        }
        if (result == null || result.responseCode != BillingClient.BillingResponseCode.OK) {
            entitlementState = entitlementState.purchaseFinishedWithoutOwnership()
            statusMessage = "Google Play could not start the purchase. Please try again."
        } else {
            statusMessage = "Complete the purchase in Google Play."
        }
        return result
    }

    /** Re-checks the user's Google Play ownership when Restore Purchase is pressed. */
    fun restorePurchases(context: Context) {
        if (entitlementState.purchaseFlow != PurchaseFlowState.IDLE) {
            statusMessage = "A Google Play purchase is already being processed. Please wait before restoring."
            return
        }
        statusMessage = "Checking Google Play for your purchase..."
        if (billingClient?.isReady == true) {
            queryExistingPurchase(showRestoreMessage = true)
        } else {
            statusMessage = "Google Play Billing is reconnecting. Please try restoring again in a moment."
        }
    }

    /** Requeries Play on resume so a pending purchase that completed while backgrounded is restored. */
    fun refreshPurchases(context: Context) {
        if (entitlementState.purchaseFlow == PurchaseFlowState.LAUNCHING ||
            entitlementState.purchaseFlow == PurchaseFlowState.ACKNOWLEDGING
        ) return
        val client = billingClient
        if (client?.isReady == true) {
            handleBillingReady()
        } else if (initialized) {
            startConnection()
        }
    }

    /** Lets the UI know whether the Play Store product has been loaded. */
    fun isProductAvailable(): Boolean = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken != null

    /** Returns only the localized price fetched from Google Play; never invent a fallback price. */
    fun getPrice(): String? = productPrice

    /** Clears a previous purchase message when the purchase dialog is opened again. */
    fun clearStatusMessage() {
        statusMessage = null
    }

    /** Receives the result of a purchase flow started from the app. */
    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: MutableList<Purchase>?
    ) {
        purchaseUpdateGeneration++
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val matching = purchases.orEmpty().filter { it.products.contains(PRODUCT_ID) }
                if (matching.isEmpty()) {
                    entitlementState = entitlementState.purchaseFinishedWithoutOwnership()
                    statusMessage = "Google Play did not return a completed purchase. No ad-free access was activated."
                }
                matching.forEach { purchase ->
                        when (purchase.purchaseState) {
                            Purchase.PurchaseState.PURCHASED -> processPurchase(purchase)
                            Purchase.PurchaseState.PENDING -> {
                                // Do not grant ad-free access while payment is pending.
                                entitlementState = entitlementState.purchasePending()
                                statusMessage = "Purchase is pending. Ad-free access will activate after Google Play confirms payment."
                            }
                            else -> {
                                // An unspecified/non-completed state does not grant entitlement.
                                entitlementState = entitlementState.purchaseFinishedWithoutOwnership()
                                statusMessage = "The purchase has not completed yet."
                            }
                        }
                    }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // Cancellation is not an error and must never grant entitlement.
                entitlementState = entitlementState.purchaseFinishedWithoutOwnership()
                statusMessage = "Purchase cancelled. No payment was made."
            }

            else -> {
                // Keep the technical BillingResult out of the user-facing message while
                // still giving the user a clear action they can take.
                entitlementState = entitlementState.purchaseFinishedWithoutOwnership()
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
            purchaseUpdateGeneration++
            entitlementState = entitlementState.purchaseAcknowledged()
            statusMessage = "Purchase successful. Lifetime ad-free access is now active."
        }

        if (purchase.isAcknowledged) {
            grantEntitlement()
            return
        }

        if (!acknowledgementTracker.tryStart(purchase.purchaseToken)) return
        val client = billingClient
        if (client == null) {
            acknowledgementTracker.finish(purchase.purchaseToken)
            entitlementState = entitlementState.purchaseAcknowledgementRetryNeeded()
            statusMessage = "Google Play Billing is unavailable. Purchase confirmation will be retried."
            return
        }

        entitlementState = entitlementState.purchaseAcknowledging()
        try {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { result ->
                try {
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        grantEntitlement()
                    } else {
                        // Do not let a late failed callback undo a confirmed entitlement.
                        entitlementState = entitlementState.purchaseAcknowledgementRetryNeeded()
                        statusMessage = "Payment was received, but Google Play is still confirming the purchase. Please wait a moment and try again."
                    }
                } finally {
                    acknowledgementTracker.finish(purchase.purchaseToken)
                }
            }
        } catch (exception: RuntimeException) {
            acknowledgementTracker.finish(purchase.purchaseToken)
            entitlementState = entitlementState.purchaseAcknowledgementRetryNeeded()
            statusMessage = "Google Play Billing is unavailable. Purchase confirmation will be retried."
        }
    }
}
