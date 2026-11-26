package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.lang.ref.WeakReference

/**
 * Manages History interstitials. Navigation is coalesced while a single ad is
 * visible; a timeout releases navigation without opening a second ad.
 */
object HistoryInterstitialAd {
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1512519890788753/3097446327"
    private const val NAVIGATION_FALLBACK_MS = 12_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val navigation = HistoryNavigationState()
    private val cooldown = HistoryAdCooldown()
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var presentationActivity = WeakReference<Activity>(null)
    private var presentationGeneration = 0L
    private var fallback: Runnable? = null

    /** Preloads only after UMP permits ads and premium entitlement is false. */
    fun load(activity: Activity) {
        if (BillingManager.isPremium || !AdMobRuntime.canShowAds || interstitialAd != null || isLoading) return
        if (activity.isFinishing || activity.isDestroyed) return

        isLoading = true
        InterstitialAd.load(
            activity.applicationContext,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isLoading = false
                    if (BillingManager.isPremium || !AdMobRuntime.canShowAds) {
                        interstitialAd = null
                    } else {
                        interstitialAd = ad
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    interstitialAd = null
                }
            }
        )
    }

    /** Drops a cached ad immediately if consent or premium eligibility changes. */
    fun onAdEligibilityChanged(eligible: Boolean) {
        if (!eligible) interstitialAd = null
    }

    fun handleHistoryEntry(activity: Activity, onContinue: () -> Unit) {
        if (navigation.isPresenting) {
            navigation.request(onContinue)
            return
        }
        if (BillingManager.isPremium || !AdMobRuntime.canShowAds) {
            onContinue()
            return
        }
        if (activity.isFinishing || activity.isDestroyed) {
            onContinue()
            return
        }
        if (!cooldown.isEligible(SystemClock.elapsedRealtime())) {
            onContinue()
            return
        }

        val ad = interstitialAd
        if (ad == null) {
            onContinue()
            load(activity)
            return
        }

        // Re-check immediately before display; entitlement or consent may have changed.
        if (BillingManager.isPremium || !AdMobRuntime.canShowAds) {
            interstitialAd = null
            onContinue()
            return
        }

        interstitialAd = null
        if (!navigation.begin(onContinue)) return
        presentationActivity = WeakReference(activity)
        val generation = ++presentationGeneration
        fallback = Runnable {
            if (generation == presentationGeneration) navigation.releaseNavigationFallback()
        }.also { mainHandler.postDelayed(it, NAVIGATION_FALLBACK_MS) }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            private fun finish() = finishPresentation(generation, activity)

            override fun onAdDismissedFullScreenContent() = finish()

            override fun onAdFailedToShowFullScreenContent(adError: AdError) = finish()
        }
        cooldown.recordShown(SystemClock.elapsedRealtime())
        try {
            ad.show(activity)
        } catch (_: RuntimeException) {
            finishPresentation(generation, activity)
        }
    }

    /** Called by the owning Activity so callbacks cannot retain its destroyed instance. */
    fun onActivityInterrupted(activity: Activity) {
        if (presentationActivity.get() !== activity) return
        presentationGeneration++
        cancelFallback()
        presentationActivity.clear()
        navigation.finishPresentation()
    }

    private fun finishPresentation(generation: Long, activity: Activity) {
        if (generation != presentationGeneration || !navigation.isPresenting) return
        presentationGeneration++
        cancelFallback()
        presentationActivity.clear()
        navigation.finishPresentation()
        if (!activity.isFinishing && !activity.isDestroyed && AdMobRuntime.canShowAds) load(activity)
    }

    private fun cancelFallback() {
        fallback?.let(mainHandler::removeCallbacks)
        fallback = null
    }
}
