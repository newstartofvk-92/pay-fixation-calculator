package com.niyammitra.payfixationcalculator

import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

internal const val BANNER_AD_UNIT_ID = "ca-app-pub-1512519890788753/1856516842"
// Official Google AdMob sample test banner ad unit ID
internal const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

/**
 * Standard anchored adaptive banner advertisement for free users.
 * Automatically removed when [BillingManager.isPremium] is true in release mode.
 * In debug mode, displays Google's official test ad so developers can preview and test layout.
 */
@Composable
fun PayFixationBannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val isDebug = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (BillingManager.isPremium && !isDebug) return

    AndroidView(
        modifier = modifier.fillMaxWidth().navigationBarsPadding().wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = if (isDebug) TEST_BANNER_AD_UNIT_ID else BANNER_AD_UNIT_ID
                val displayMetrics = ctx.resources.displayMetrics
                val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, adWidth))
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d("PayFixationBannerAd", "Banner ad loaded successfully into layout")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.e("PayFixationBannerAd", "Banner ad failed to load: ${error.message} (code: ${error.code})")
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}
