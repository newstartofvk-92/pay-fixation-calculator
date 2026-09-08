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

object BillingManager : PurchasesUpdatedListener {
    private const val PRODUCT_ID = "remove_ads_lifetime"
    private const val PREFS_NAME = "billing_prefs"
    private const val KEY_PREMIUM = "premium_purchased"

    var isPremium by mutableStateOf(false)
        private set

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var initialized = false

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

            purchases
                .filter { it.products.contains(PRODUCT_ID) && it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { processPurchase(context, it) }
        }
    }

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

    fun restorePurchases(context: Context) {
        queryExistingPurchase(context.applicationContext)
    }

    fun isProductAvailable(): Boolean = productDetails != null

    fun getPrice(): String {
        return productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            ?: "₹49"
    }

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

    private fun setPremium(context: Context, value: Boolean) {
        isPremium = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PREMIUM, value)
            .apply()
    }
}
