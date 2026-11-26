package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsentGateStateTest {
    @Test
    fun consentRequiredBlocksAdsUntilTheRequiredFormCompletes() {
        val pending = ConsentGateState().onInfoUpdated(
            formRequired = true,
            canRequestAds = true,
            privacyOptionsRequired = true
        )

        assertFalse(pending.canInitializeAds())
        assertTrue(pending.privacyOptionsRequired)

        val completed = pending.onFormCompleted(
            failed = false,
            canRequestAds = true,
            privacyOptionsRequired = true
        )
        assertTrue(completed.canInitializeAds())
    }

    @Test
    fun consentNotRequiredAllowsAdsWhenUmpReportsEligibility() {
        val state = ConsentGateState().onInfoUpdated(
            formRequired = false,
            canRequestAds = true,
            privacyOptionsRequired = false
        )

        assertTrue(state.canInitializeAds())
        assertFalse(state.privacyOptionsRequired)
    }

    @Test
    fun failedConsentFormFailsClosed() {
        val state = ConsentGateState()
            .onInfoUpdated(formRequired = true, canRequestAds = false, privacyOptionsRequired = false)
            .onFormCompleted(failed = true, canRequestAds = true, privacyOptionsRequired = false)

        assertFalse(state.canInitializeAds())
    }

    @Test
    fun failedRefreshUsesOnlyUmpCachedEligibilityAndPremiumAlwaysSuppressesAds() {
        val cachedAllowed = ConsentGateState().onInfoUpdateFailed(
            cachedCanRequestAds = true,
            privacyOptionsRequired = false
        )
        val noCachedConsent = ConsentGateState().onInfoUpdateFailed(
            cachedCanRequestAds = false,
            privacyOptionsRequired = false
        )

        assertTrue(cachedAllowed.canRequestAds(isPremium = false))
        assertFalse(cachedAllowed.canRequestAds(isPremium = true))
        assertFalse(noCachedConsent.canInitializeAds())

        val requiredFormStillOpen = ConsentGateState().onInfoUpdateFailed(
            cachedCanRequestAds = true,
            privacyOptionsRequired = true,
            consentFormStillRequired = true
        )
        assertFalse(requiredFormStillOpen.canInitializeAds())
    }
}
