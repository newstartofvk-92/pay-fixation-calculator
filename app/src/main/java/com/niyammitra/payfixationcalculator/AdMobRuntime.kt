package com.niyammitra.payfixationcalculator

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Consent-gated, process-wide initialization for Google Mobile Ads. */
object AdMobRuntime {
    var consentAllowsAds by mutableStateOf(false)
        private set

    var sdkReady by mutableStateOf(false)
        private set

    var privacyOptionsRequired by mutableStateOf(false)
        private set

    private var consentInformation: ConsentInformation? = null
    private var consentUpdateInProgress = false
    private var consentCheckFinished = false
    private var sdkInitializationStarted = false

    val canShowAds: Boolean
        get() = shouldRequestAds(
            AdEligibility(
                billing = BillingManager.entitlementState,
                consentAllowsAds = consentAllowsAds,
                adsSdkReady = sdkReady
            )
        )

    /** Refresh UMP consent once per app launch, show any required form, then initialize ads. */
    fun initialize(activity: Activity) {
        if (consentUpdateInProgress || consentCheckFinished) return
        consentUpdateInProgress = true

        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        val parameters = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    consentUpdateInProgress = false
                    consentCheckFinished = true
                    updateConsentState(info, activity)
                }
            },
            {
                // UMP recommends checking canRequestAds even when the network update fails;
                // it may still hold a valid choice from the previous session.
                consentUpdateInProgress = false
                consentCheckFinished = true
                updateConsentState(info, activity)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        val info = consentInformation ?: UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            updateConsentState(info, activity)
        }
    }

    private fun updateConsentState(info: ConsentInformation, activity: Activity) {
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        consentAllowsAds = info.canRequestAds()
        if (consentAllowsAds) initializeAdsSdk(activity)
    }

    private fun initializeAdsSdk(activity: Activity) {
        if (sdkInitializationStarted) return
        sdkInitializationStarted = true
        MobileAds.initialize(activity.applicationContext) {
            sdkReady = true
        }
    }
}
