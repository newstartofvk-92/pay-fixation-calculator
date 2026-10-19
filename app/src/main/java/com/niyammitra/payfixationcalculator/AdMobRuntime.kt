package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

private const val ADS_LOG_TAG = "PayFixationAds"

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
    private var lastEligibilityLog: String? = null
    private var diagnosticsEnabled = false

    internal fun configureDebugLogging(context: Context) {
        diagnosticsEnabled =
            context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    val canShowAds: Boolean
        get() {
            val billing = BillingManager.entitlementState
            val eligible = shouldRequestAds(
                AdEligibility(
                    billing = billing,
                    consentAllowsAds = consentAllowsAds,
                    adsSdkReady = sdkReady
                )
            )
            val state = "consentAllowed=$consentAllowsAds sdkReady=$sdkReady " +
                "entitlement=${billing.entitlement} purchaseFlow=${billing.purchaseFlow} eligible=$eligible"
            if (diagnosticsEnabled && state != lastEligibilityLog) {
                lastEligibilityLog = state
                Log.d(ADS_LOG_TAG, "Ad eligibility changed: $state")
            }
            return eligible
        }

    internal fun debugLog(message: String) {
        if (diagnosticsEnabled) Log.d(ADS_LOG_TAG, message)
    }

    /** Refresh UMP consent once per app launch, show any required form, then initialize ads. */
    fun initialize(activity: Activity) {
        configureDebugLogging(activity.applicationContext)
        if (consentUpdateInProgress || consentCheckFinished) {
            debugLog("UMP consent update skipped: already in progress or completed")
            return
        }
        consentUpdateInProgress = true
        debugLog("UMP consent information update started")

        val info = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        val parameters = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                debugLog("UMP consent information update succeeded")
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError == null) {
                        debugLog("UMP required consent form handling completed")
                    } else {
                        debugLog(
                            "UMP required consent form handling failed: " +
                                "code=${formError.errorCode}, message=${formError.message}"
                        )
                    }
                    consentUpdateInProgress = false
                    consentCheckFinished = true
                    updateConsentState(info, activity)
                }
            },
            { error ->
                debugLog("UMP consent information update failed: code=${error.errorCode}, message=${error.message}")
                // UMP recommends checking canRequestAds even when the network update fails;
                // it may still hold a valid choice from the previous session.
                consentUpdateInProgress = false
                consentCheckFinished = true
                updateConsentState(info, activity)
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        debugLog("UMP privacy options form opened")
        val info = consentInformation ?: UserMessagingPlatform.getConsentInformation(activity)
        consentInformation = info
        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            debugLog("UMP privacy options form handling completed")
            updateConsentState(info, activity)
        }
    }

    private fun updateConsentState(info: ConsentInformation, activity: Activity) {
        privacyOptionsRequired = info.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        consentAllowsAds = info.canRequestAds()
        debugLog(
            "UMP state: canRequestAds=$consentAllowsAds " +
                "privacyOptionsRequired=$privacyOptionsRequired"
        )
        if (consentAllowsAds) initializeAdsSdk(activity)
    }

    private fun initializeAdsSdk(activity: Activity) {
        if (sdkInitializationStarted) {
            debugLog("Mobile Ads SDK initialization already started")
            return
        }
        sdkInitializationStarted = true
        debugLog("Mobile Ads SDK initialization started")
        MobileAds.initialize(activity.applicationContext) {
            sdkReady = true
            debugLog("Mobile Ads SDK initialization callback completed; sdkReady=true")
        }
    }
}
