package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonetizationStateTest {
    @Test
    fun adsRequirePlayVerifiedFreeEntitlementAndConsentAndReadySdk() {
        val verifiedFree = BillingEntitlementState().ownershipQuerySucceeded(owned = false, pending = false)
        assertFalse(shouldShow(verifiedFree, consent = false, sdkReady = true))
        assertFalse(shouldShow(verifiedFree, consent = true, sdkReady = false))
        assertTrue(shouldShow(verifiedFree, consent = true, sdkReady = true))
        assertFalse(shouldShow(BillingEntitlementState(), consent = true, sdkReady = true))
    }

    @Test
    fun restoredAdFreePurchaseSuppressesAdsWithoutLocalBoolean() {
        val restored = BillingEntitlementState().ownershipQuerySucceeded(owned = true, pending = false)
        assertTrue(restored.isAdFree)
        assertFalse(shouldShow(restored, consent = true, sdkReady = true))
    }

    @Test
    fun purchaseTransitionsHandleLaunchPendingCancelFailureAndAcknowledgement() {
        val free = BillingEntitlementState().ownershipQuerySucceeded(owned = false, pending = false)
        assertFalse(shouldShow(free.purchaseLaunched(), consent = true, sdkReady = true))
        assertFalse(shouldShow(free.purchasePending(), consent = true, sdkReady = true))
        assertTrue(shouldShow(free.purchaseLaunched().purchaseFinishedWithoutOwnership(), consent = true, sdkReady = true))
        assertFalse(shouldShow(free.purchaseAcknowledging(), consent = true, sdkReady = true))
        val acknowledgementRetry = free.purchaseAcknowledging().purchaseAcknowledgementRetryNeeded()
        assertFalse(shouldShow(acknowledgementRetry, consent = true, sdkReady = true))
        assertTrue(acknowledgementRetry.ownershipQuerySucceeded(owned = false, pending = false).let {
            shouldShow(it, consent = true, sdkReady = true)
        })
        assertFalse(shouldShow(free.purchaseAcknowledged(), consent = true, sdkReady = true))
        assertTrue(free.purchaseAcknowledged().isAdFree)
    }

    @Test
    fun adsAreSuppressedDuringJourneysEditingPurchasesAndDocumentWorkflows() {
        val free = BillingEntitlementState().ownershipQuerySucceeded(owned = false, pending = false)
        assertFalse(shouldRequestAds(AdEligibility(free, true, true, criticalWorkflowActive = true)))
        assertFalse(shouldShow(free.purchaseLaunched(), consent = true, sdkReady = true))
    }

    @Test
    fun acknowledgementTrackerDeduplicatesTokensAndAllowsRetryAfterCompletion() {
        val tracker = PurchaseAcknowledgementTracker()
        assertTrue(tracker.tryStart("token-a"))
        assertFalse(tracker.tryStart("token-a"))
        assertTrue(tracker.tryStart("token-b"))

        tracker.finish("token-a") // Same release path is used for success, failure, and exception.
        assertTrue(tracker.tryStart("token-a"))
        tracker.finish("token-a")
        tracker.finish("token-b")
    }

    @Test
    fun staleAcknowledgementFailureCannotUndoConfirmedOwnership() {
        val confirmed = BillingEntitlementState().purchaseAcknowledged()
        val afterStaleFailure = confirmed.purchaseAcknowledgementRetryNeeded()
        assertTrue(afterStaleFailure.isAdFree)
        assertTrue(afterStaleFailure.purchaseFlow == PurchaseFlowState.IDLE)
    }

    @Test
    fun staleOrInFlightNegativeOwnershipQueryCannotClearPurchaseState() {
        val confirmed = BillingEntitlementState().purchaseAcknowledged()
        assertTrue(resolveOwnershipQuery(confirmed, owned = false, pending = false, stale = true).isAdFree)

        val freshNoPurchase = resolveOwnershipQuery(confirmed, owned = false, pending = false, stale = false)
        assertTrue(freshNoPurchase.entitlement == ProductEntitlement.VERIFIED_FREE)

        val acknowledging = BillingEntitlementState().purchaseAcknowledging()
        assertTrue(resolveOwnershipQuery(acknowledging, owned = false, pending = false, stale = false)
            .purchaseFlow == PurchaseFlowState.ACKNOWLEDGING)
    }

    @Test
    fun billingConnectionStateRecoversAndRequestsProductReload() {
        val ready = BillingConnectionState().connectionStarted().connectionReady()
            .productDetailsResult(available = true)
        assertTrue(ready.ready)
        assertTrue(ready.productDetailsAvailable)

        val disconnected = ready.disconnected()
        assertFalse(disconnected.ready)
        assertFalse(disconnected.productDetailsAvailable)
        val reconnected = disconnected.connectionStarted().connectionReady()
        assertTrue(reconnected.ready)
        assertFalse(reconnected.productDetailsAvailable)
        assertTrue(reconnected.generation > ready.generation)
    }

    private fun shouldShow(state: BillingEntitlementState, consent: Boolean, sdkReady: Boolean): Boolean =
        shouldRequestAds(AdEligibility(state, consent, sdkReady))
}
