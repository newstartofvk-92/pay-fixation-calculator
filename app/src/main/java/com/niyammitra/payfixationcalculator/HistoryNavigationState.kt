package com.niyammitra.payfixationcalculator

/**
 * Coalesces navigation continuations behind one History interstitial.
 * Callbacks are UI state setters; callers may replace an earlier request with
 * the latest requested History destination while the ad is visible.
 */
internal class HistoryAdCooldown(private val cooldownMillis: Long = 2 * 60 * 1000L) {
    private var lastShownAtElapsedRealtime: Long? = null

    fun isEligible(nowElapsedRealtime: Long): Boolean =
        lastShownAtElapsedRealtime?.let { nowElapsedRealtime - it >= cooldownMillis } ?: true

    fun recordShown(nowElapsedRealtime: Long) {
        lastShownAtElapsedRealtime = nowElapsedRealtime
    }
}

internal class HistoryNavigationState {
    var isPresenting: Boolean = false
        private set
    var navigationReleased: Boolean = false
        private set

    private var pendingContinuation: (() -> Unit)? = null

    fun begin(continuation: () -> Unit): Boolean {
        if (isPresenting) {
            request(continuation)
            return false
        }
        isPresenting = true
        navigationReleased = false
        pendingContinuation = continuation
        return true
    }

    fun request(continuation: () -> Unit) {
        if (!isPresenting || navigationReleased) {
            continuation()
        } else {
            pendingContinuation = continuation
        }
    }

    /** Releases navigation on the timeout but keeps the ad slot locked. */
    fun releaseNavigationFallback() {
        if (!isPresenting || navigationReleased) return
        navigationReleased = true
        drainContinuation()
    }

    /** Ad dismissal/failure or Activity destruction ends the presentation. */
    fun finishPresentation() {
        if (!isPresenting) return
        isPresenting = false
        navigationReleased = false
        drainContinuation()
    }

    private fun drainContinuation() {
        val continuation = pendingContinuation
        pendingContinuation = null
        continuation?.invoke()
    }
}
