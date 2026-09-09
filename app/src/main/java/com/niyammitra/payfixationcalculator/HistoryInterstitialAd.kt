package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Manages the History-screen interstitial advertisement.
 *
 * Premium/ad-free users are excluded from all interstitial loading and display.
 * Free users get a two-minute cooldown when entering History, while returning
 * from History is treated as a separate explicit ad opportunity.
 */
object HistoryInterstitialAd {
    // Google's test interstitial ID. Replace with the production AdMob ID before release.
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Prevents the History-entry ad from appearing too frequently for free users.
    private const val COOLDOWN_MILLIS = 2 * 60 * 1000L

    private var interstitialAd: InterstitialAd? = null
    private var lastShownAt = 0L
    private var isLoading = false

    /** Preloads an interstitial so it is ready when a permitted History action occurs. */
    fun load(activity: Activity) {
        // Never load an ad for a user who has purchased lifetime ad removal.
        if (BillingManager.isPremium || interstitialAd != null || isLoading) return

        isLoading = true
        InterstitialAd.load(
            activity,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    interstitialAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    /**
     * Handles entering History. The normal two-minute cooldown is applied here.
     * If no ad is ready, navigation continues immediately and another ad is loaded.
     */
    fun showIfDue(activity: Activity, onContinue: () -> Unit) {
        // Purchased ad-free access always bypasses the interstitial.
        if (BillingManager.isPremium) {
            onContinue()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val cooldownActive = now - lastShownAt < COOLDOWN_MILLIS
        show(activity, cooldownActive, onContinue)
    }

    /**
     * Handles leaving History. This is intentionally not subject to the normal
     * entry cooldown because the app currently treats Back as another explicit
     * ad opportunity, as requested for the app's free version.
     */
    fun showOnHistoryBack(activity: Activity, onContinue: () -> Unit) {
        if (BillingManager.isPremium) {
            onContinue()
            return
        }

        show(activity, false, onContinue)
    }

    /** Displays the currently loaded ad, or continues immediately when unavailable. */
    private fun show(activity: Activity, cooldownActive: Boolean, onContinue: () -> Unit) {
        // Re-check entitlement immediately before showing an ad so a purchase
        // made since the previous check is respected.
        if (BillingManager.isPremium) {
            onContinue()
            return
        }

        val ad = interstitialAd

        if (cooldownActive || ad == null) {
            onContinue()
            load(activity)
            return
        }

        // Interstitials are single-use. Remove the reference before displaying it.
        interstitialAd = null
        lastShownAt = SystemClock.elapsedRealtime()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                // Preload the next ad and then continue the user's navigation.
                load(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                // Navigation must not get stuck if an ad fails to display.
                load(activity)
                onContinue()
            }
        }

        ad.show(activity)
    }
}
