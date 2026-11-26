package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference

/** Consent-gated, process-wide initialization for Google Mobile Ads. */
object AdMobRuntime {
    private const val CONSENT_CALLBACK_TIMEOUT_MS = 15_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private var consentState by mutableStateOf(ConsentGateState())
    private var activeActivity = WeakReference<Activity>(null)
    private var consentInformation: ConsentInformation? = null
    private var consentUpdateInProgress = false
    private var consentCheckFinished = false
    private var sdkInitializationStarted = false
    private var sdkReady by mutableStateOf(false)
    private var generation = 0L
    private var timeout: Runnable? = null

    val canShowAds: Boolean
        get() = sdkReady && consentState.canRequestAds(BillingManager.isPremium)

    val privacyOptionsRequired: Boolean
        get() = consentState.privacyOptionsRequired

    /** Refreshes UMP consent, shows any required form, then initializes Mobile Ads. */
    fun initialize(activity: Activity) {
        activeActivity = WeakReference(activity)
        if (activity.isFinishing || activity.isDestroyed) return

        if (consentUpdateInProgress) return
        if (consentCheckFinished) {
            maybeInitializeAds(activity)
            return
        }

        consentUpdateInProgress = true
        val requestGeneration = ++generation
        val info = try {
            UserMessagingPlatform.getConsentInformation(activity)
        } catch (_: RuntimeException) {
            finishUpdateFailure(requestGeneration, null)
            return
        }
        consentInformation = info
        val parameters = ConsentRequestParameters.Builder().build()
        scheduleTimeout(requestGeneration, info)

        try {
            info.requestConsentInfoUpdate(
                activity,
                parameters,
                {
                    if (requestGeneration != generation) return@requestConsentInfoUpdate
                    cancelTimeout()
                    val currentActivity = usableActivity()
                    if (currentActivity == null) {
                        consentUpdateInProgress = false
                        return@requestConsentInfoUpdate
                    }

                    val privacyRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    val formRequired = info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                    if (!formRequired) {
                        consentState = consentState.onInfoUpdated(
                            formRequired = false,
                            canRequestAds = info.canRequestAds(),
                            privacyOptionsRequired = privacyRequired
                        )
                        finishUpdateSuccess(requestGeneration, currentActivity)
                        return@requestConsentInfoUpdate
                    }

                    consentState = consentState.onInfoUpdated(
                        formRequired = true,
                        canRequestAds = false,
                        privacyOptionsRequired = privacyRequired
                    )
                    try {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(currentActivity) { formError ->
                            if (requestGeneration != generation) return@loadAndShowConsentFormIfRequired
                            cancelTimeout()
                            val latestActivity = usableActivity()
                            val failed = formError != null || latestActivity == null
                            consentState = consentState.onFormCompleted(
                                failed = failed,
                                canRequestAds = info.canRequestAds(),
                                privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                            )
                            finishUpdateSuccess(requestGeneration, latestActivity)
                        }
                    } catch (_: RuntimeException) {
                        consentState = consentState.onFormCompleted(
                            failed = true,
                            canRequestAds = false,
                            privacyOptionsRequired = privacyRequired
                        )
                        finishUpdateSuccess(requestGeneration, null)
                    }
                },
                {
                    if (requestGeneration != generation) return@requestConsentInfoUpdate
                    cancelTimeout()
                    val privacyRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    consentState = consentState.onInfoUpdateFailed(
                        cachedCanRequestAds = info.canRequestAds(),
                        privacyOptionsRequired = privacyRequired,
                        consentFormStillRequired = info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                    )
                    finishUpdateSuccess(requestGeneration, usableActivity())
                }
            )
        } catch (_: RuntimeException) {
            finishUpdateFailure(requestGeneration, info)
        }
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!privacyOptionsRequired || activity.isFinishing || activity.isDestroyed) return
        val info = consentInformation ?: runCatching {
            UserMessagingPlatform.getConsentInformation(activity)
        }.getOrNull() ?: return
        val privacyGeneration = ++generation
        consentUpdateInProgress = true
        scheduleTimeout(privacyGeneration, info, allowCachedConsent = false)

        try {
            UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
                if (privacyGeneration != generation) return@showPrivacyOptionsForm
                cancelTimeout()
                consentUpdateInProgress = false
                val latestActivity = usableActivity()
                consentState = consentState.onFormCompleted(
                    failed = formError != null || latestActivity == null,
                    canRequestAds = info.canRequestAds(),
                    privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                )
                consentCheckFinished = true
                applyEligibility(latestActivity)
            }
        } catch (_: RuntimeException) {
            cancelTimeout()
            consentUpdateInProgress = false
            consentState = consentState.onFormCompleted(
                failed = true,
                canRequestAds = false,
                privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
            )
            consentCheckFinished = true
            applyEligibility(usableActivity())
        }
    }

    fun onActivityDestroyed(activity: Activity) {
        if (activeActivity.get() !== activity) return
        activeActivity.clear()
        if (consentUpdateInProgress) {
            generation++
            consentUpdateInProgress = false
            consentCheckFinished = false
            cancelTimeout()
        }
    }

    private fun finishUpdateSuccess(requestGeneration: Long, activity: Activity?) {
        if (requestGeneration != generation) return
        consentUpdateInProgress = false
        consentCheckFinished = consentState.isResolved
        applyEligibility(activity)
    }

    private fun finishUpdateFailure(requestGeneration: Long, info: ConsentInformation?) {
        if (requestGeneration != generation) return
        cancelTimeout()
        consentUpdateInProgress = false
        val privacyRequired = info?.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        consentState = consentState.onInfoUpdateFailed(
            cachedCanRequestAds = info?.canRequestAds() == true,
            privacyOptionsRequired = privacyRequired,
            consentFormStillRequired = info?.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
        )
        consentCheckFinished = consentState.isResolved
        applyEligibility(usableActivity())
    }

    private fun applyEligibility(activity: Activity?) {
        val eligible = consentState.canInitializeAds()
        HistoryInterstitialAd.onAdEligibilityChanged(sdkReady && eligible && !BillingManager.isPremium)
        if (eligible && activity != null) maybeInitializeAds(activity)
    }

    private fun maybeInitializeAds(activity: Activity) {
        if (!consentState.canInitializeAds() || sdkInitializationStarted) return
        sdkInitializationStarted = true
        MobileAds.initialize(activity.applicationContext) {
            sdkReady = true
            HistoryInterstitialAd.onAdEligibilityChanged(canShowAds)
            if (canShowAds) usableActivity()?.let(HistoryInterstitialAd::load)
        }
    }

    private fun usableActivity(): Activity? =
        activeActivity.get()?.takeUnless { it.isFinishing || it.isDestroyed }

    private fun scheduleTimeout(
        requestGeneration: Long,
        info: ConsentInformation,
        allowCachedConsent: Boolean = true
    ) {
        cancelTimeout()
        timeout = Runnable {
            if (requestGeneration != generation) return@Runnable
            consentState = if (allowCachedConsent) {
                consentState.onInfoUpdateFailed(
                    cachedCanRequestAds = info.canRequestAds(),
                    privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED,
                    consentFormStillRequired = info.consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                )
            } else {
                consentState.onFormCompleted(
                    failed = true,
                    canRequestAds = false,
                    privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                )
            }
            consentUpdateInProgress = false
            consentCheckFinished = consentState.isResolved
            generation++
            applyEligibility(usableActivity())
        }.also { mainHandler.postDelayed(it, CONSENT_CALLBACK_TIMEOUT_MS) }
    }

    private fun cancelTimeout() {
        timeout?.let(mainHandler::removeCallbacks)
        timeout = null
    }
}
