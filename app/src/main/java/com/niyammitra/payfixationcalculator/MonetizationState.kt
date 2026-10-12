package com.niyammitra.payfixationcalculator

/** A Play ownership result, never inferred from local preferences. */
internal enum class ProductEntitlement {
    UNVERIFIED,
    VERIFIED_FREE,
    VERIFIED_AD_FREE
}

internal enum class PurchaseFlowState {
    IDLE,
    LAUNCHING,
    PENDING,
    ACKNOWLEDGING
}

internal data class BillingEntitlementState(
    val entitlement: ProductEntitlement = ProductEntitlement.UNVERIFIED,
    val purchaseFlow: PurchaseFlowState = PurchaseFlowState.IDLE
) {
    val isAdFree: Boolean get() = entitlement == ProductEntitlement.VERIFIED_AD_FREE

    fun ownershipQuerySucceeded(owned: Boolean, pending: Boolean): BillingEntitlementState = copy(
        entitlement = if (owned) ProductEntitlement.VERIFIED_AD_FREE else ProductEntitlement.VERIFIED_FREE,
        purchaseFlow = if (pending) PurchaseFlowState.PENDING else PurchaseFlowState.IDLE
    )

    fun purchaseLaunched(): BillingEntitlementState = copy(purchaseFlow = PurchaseFlowState.LAUNCHING)
    fun purchasePending(): BillingEntitlementState = copy(purchaseFlow = PurchaseFlowState.PENDING)
    fun purchaseAcknowledging(): BillingEntitlementState = copy(purchaseFlow = PurchaseFlowState.ACKNOWLEDGING)
    fun purchaseAcknowledgementRetryNeeded(): BillingEntitlementState = copy(
        purchaseFlow = if (isAdFree) PurchaseFlowState.IDLE else PurchaseFlowState.PENDING
    )
    fun purchaseAcknowledged(): BillingEntitlementState = copy(
        entitlement = ProductEntitlement.VERIFIED_AD_FREE,
        purchaseFlow = PurchaseFlowState.IDLE
    )
    fun purchaseFinishedWithoutOwnership(): BillingEntitlementState = copy(purchaseFlow = PurchaseFlowState.IDLE)
}

/** Thread-safe single-flight guard for acknowledgement requests keyed by Play purchase token. */
internal class PurchaseAcknowledgementTracker {
    private val lock = Any()
    private val inFlightTokens = mutableSetOf<String>()

    fun tryStart(token: String): Boolean = synchronized(lock) { inFlightTokens.add(token) }

    fun finish(token: String) {
        synchronized(lock) { inFlightTokens.remove(token) }
    }
}

internal data class BillingConnectionState(
    val ready: Boolean = false,
    val connecting: Boolean = false,
    val productDetailsAvailable: Boolean = false,
    val generation: Int = 0
) {
    fun connectionStarted(): BillingConnectionState = copy(connecting = true)
    fun connectionReady(): BillingConnectionState = copy(ready = true, connecting = false)
    fun connectionFailed(): BillingConnectionState = copy(ready = false, connecting = false)
    fun disconnected(): BillingConnectionState = copy(
        ready = false,
        connecting = false,
        productDetailsAvailable = false,
        generation = generation + 1
    )
    fun productDetailsResult(available: Boolean): BillingConnectionState = copy(productDetailsAvailable = available)
}

/** Ignores a stale negative query and prevents a query from clearing an active purchase flow. */
internal fun resolveOwnershipQuery(
    current: BillingEntitlementState,
    owned: Boolean,
    pending: Boolean,
    stale: Boolean
): BillingEntitlementState {
    if (!owned && stale) return current
    if (!owned && current.purchaseFlow in setOf(PurchaseFlowState.LAUNCHING, PurchaseFlowState.ACKNOWLEDGING)) {
        return current
    }
    return current.ownershipQuerySucceeded(owned, pending)
}

internal data class AdEligibility(
    val billing: BillingEntitlementState,
    val consentAllowsAds: Boolean,
    val adsSdkReady: Boolean,
    val criticalWorkflowActive: Boolean = false
)

internal fun shouldRequestAds(eligibility: AdEligibility): Boolean =
    eligibility.billing.entitlement == ProductEntitlement.VERIFIED_FREE &&
        eligibility.billing.purchaseFlow == PurchaseFlowState.IDLE &&
        eligibility.consentAllowsAds &&
        eligibility.adsSdkReady &&
        !eligibility.criticalWorkflowActive
