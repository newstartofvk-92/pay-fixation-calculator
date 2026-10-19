package com.niyammitra.payfixationcalculator

import android.content.Context
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val eligible = AdMobRuntime.canShowAds
    val billing = BillingManager.entitlementState
    remember(
        eligible,
        AdMobRuntime.consentAllowsAds,
        AdMobRuntime.sdkReady,
        billing.entitlement,
        billing.purchaseFlow
    ) {
        if (!eligible) {
            AdMobRuntime.debugLog(
                "BannerAd returns early: consentAllowed=${AdMobRuntime.consentAllowsAds} " +
                    "sdkReady=${AdMobRuntime.sdkReady} entitlement=${billing.entitlement} " +
                    "purchaseFlow=${billing.purchaseFlow}"
            )
        } else {
            AdMobRuntime.debugLog("BannerAd eligible to create/request an AdView")
        }
    }
    if (!eligible) return

    val unitId = context.getString(R.string.admob_banner_ad_unit_id)
    val adView = remember(context, unitId) {
        AdMobRuntime.debugLog("Banner AdView created")
        AdView(context).apply {
            adUnitId = unitId
            val displayMetrics = context.resources.displayMetrics
            val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
            AdMobRuntime.debugLog("Banner size configured for $adWidth dp width")
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    AdMobRuntime.debugLog("Banner onAdLoaded: AdView size=${width}x$height px")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    val responseInfo = error.responseInfo
                    val adapterResponses = responseInfo?.adapterResponses
                        ?.joinToString { "${it.adapterClassName}:${it.latencyMillis}ms" }
                        ?: "unavailable"
                    AdMobRuntime.debugLog(
                        "Banner onAdFailedToLoad: code=${error.code}, domain=${error.domain}, " +
                            "message=${error.message}, mediationAdapter=${responseInfo?.mediationAdapterClassName}, " +
                            "adapterResponses=$adapterResponses"
                    )
                }
            }
            AdMobRuntime.debugLog("Banner ad request started")
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose {
            AdMobRuntime.debugLog("Banner AdView disposed: size=${adView.width}x${adView.height} px")
            adView.destroy()
        }
    }
    AndroidView(
        modifier = modifier.wrapContentHeight().onSizeChanged { size ->
            AdMobRuntime.debugLog("Banner Compose host measured: ${size.width}x${size.height} px")
        },
        factory = { adView }
    )
}

@Composable
fun AdFreePurchaseDialog(context: Context, onClose: () -> Unit) {
    val purchaseBusy = BillingManager.entitlementState.purchaseFlow != PurchaseFlowState.IDLE
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (BillingManager.isPremium) "Ad-Free Access Active" else "NiyamMitra Ad-Free") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (BillingManager.isPremium) "Google Play confirms lifetime ad-free access for this account." else "Remove all advertisements from Pay Fixation Calculator permanently.")
                if (!BillingManager.isPremium) {
                    Text("Lifetime ad-free access", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("One-time purchase: ${BillingManager.getPrice() ?: "Loading price from Google Play…"}")
                }
                Text("No subscription. Restore the purchase using the same Google Play account.")
                BillingManager.statusMessage?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(
                enabled = !BillingManager.isPremium && BillingManager.isBillingReady && BillingManager.isProductAvailable() && !purchaseBusy,
                onClick = {
                    val activity = context as? android.app.Activity
                    if (activity == null) {
                        Toast.makeText(context, "Google Play purchase is unavailable in this screen.", Toast.LENGTH_SHORT).show()
                    } else {
                        BillingManager.launchPurchase(activity)
                    }
                }
            ) { Text(if (BillingManager.isPremium) "Ad-Free Active" else if (purchaseBusy) "Processing…" else "Buy Ad-Free") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    enabled = BillingManager.isBillingReady && !purchaseBusy,
                    onClick = { BillingManager.restorePurchases(context) }
                ) { Text("Restore") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        }
    )
}
