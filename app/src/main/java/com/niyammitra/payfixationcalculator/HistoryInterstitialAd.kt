package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

object HistoryInterstitialAd {
    private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    private const val COOLDOWN_MILLIS = 2 * 60 * 1000L

    private var interstitialAd: InterstitialAd? = null
    private var lastShownAt = 0L
    private var isLoading = false

    fun load(activity: Activity) {
        if (interstitialAd != null || isLoading) return

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

    fun showIfDue(activity: Activity, onContinue: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val cooldownActive = now - lastShownAt < COOLDOWN_MILLIS
        show(activity, cooldownActive, onContinue)
    }

    fun showOnHistoryBack(activity: Activity, onContinue: () -> Unit) {
        // Leaving History is an explicit ad opportunity. Do not apply the
        // normal History-entry cooldown here; if an ad is loaded, show it.
        show(activity, false, onContinue)
    }

    private fun show(activity: Activity, cooldownActive: Boolean, onContinue: () -> Unit) {
        val ad = interstitialAd

        if (cooldownActive || ad == null) {
            onContinue()
            load(activity)
            return
        }

        interstitialAd = null
        lastShownAt = SystemClock.elapsedRealtime()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                load(activity)
                onContinue()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                load(activity)
                onContinue()
            }
        }

        ad.show(activity)
    }
}
