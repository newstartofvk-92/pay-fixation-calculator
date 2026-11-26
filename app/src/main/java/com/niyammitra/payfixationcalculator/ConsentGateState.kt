package com.niyammitra.payfixationcalculator

/**
 * Pure consent decision state kept separate from UMP callbacks so eligibility
 * can be regression-tested without Google Play services.
 */
internal data class ConsentGateState(
    val isResolved: Boolean = false,
    val consentAllowsAds: Boolean = false,
    val privacyOptionsRequired: Boolean = false
) {
    fun onInfoUpdated(
        formRequired: Boolean,
        canRequestAds: Boolean,
        privacyOptionsRequired: Boolean
    ): ConsentGateState = copy(
        isResolved = !formRequired,
        consentAllowsAds = !formRequired && canRequestAds,
        privacyOptionsRequired = privacyOptionsRequired
    )

    fun onFormCompleted(
        failed: Boolean,
        canRequestAds: Boolean,
        privacyOptionsRequired: Boolean
    ): ConsentGateState = copy(
        isResolved = true,
        consentAllowsAds = !failed && canRequestAds,
        privacyOptionsRequired = privacyOptionsRequired
    )

    /** A failed refresh may use UMP's locally cached consent decision. */
    fun onInfoUpdateFailed(
        cachedCanRequestAds: Boolean,
        privacyOptionsRequired: Boolean,
        consentFormStillRequired: Boolean = false
    ): ConsentGateState {
        val allowed = cachedCanRequestAds && !consentFormStillRequired
        return copy(
            isResolved = allowed,
            consentAllowsAds = allowed,
            privacyOptionsRequired = privacyOptionsRequired
        )
    }

    fun canInitializeAds(): Boolean = isResolved && consentAllowsAds

    fun canRequestAds(isPremium: Boolean): Boolean =
        canInitializeAds() && !isPremium
}
