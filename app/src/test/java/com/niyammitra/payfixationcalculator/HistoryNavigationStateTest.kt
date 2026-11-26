package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryNavigationStateTest {
    @Test
    fun cooldownPreventsRepeatedEligibleRequestsFromPresentingAnotherAd() {
        val cooldown = HistoryAdCooldown()
        var presentations = 0
        val firstShownAt = 10_000L

        assertTrue(cooldown.isEligible(firstShownAt))
        presentations++
        cooldown.recordShown(firstShownAt)

        for (now in listOf(10_001L, 60_000L, 129_999L)) {
            if (cooldown.isEligible(now)) {
                presentations++
                cooldown.recordShown(now)
            }
        }

        assertEquals(1, presentations)
    }

    @Test
    fun cooldownAllowsAnotherEligibleRequestWhenTwoMinutesHaveElapsed() {
        val cooldown = HistoryAdCooldown()
        cooldown.recordShown(10_000L)

        assertFalse(cooldown.isEligible(129_999L))
        assertTrue(cooldown.isEligible(130_000L))
    }

    @Test
    fun repeatedNavigationDuringPresentationCoalescesToLatestDestination() {
        val state = HistoryNavigationState()
        val calls = mutableListOf<String>()

        assertTrue(state.begin { calls += "enter" })
        assertFalse(state.begin { calls += "back" })
        state.request { calls += "enter-again" }

        assertTrue(state.isPresenting)
        assertTrue(calls.isEmpty())
        state.finishPresentation()
        state.finishPresentation()

        assertEquals(listOf("enter-again"), calls)
    }

    @Test
    fun dismissalAndShowFailureCanBothFinishExactlyOnce() {
        listOf("dismiss", "show-failure").forEach { result ->
            val state = HistoryNavigationState()
            var continued = 0
            assertTrue(state.begin { continued++ })

            state.finishPresentation()
            state.finishPresentation()

            assertEquals(result, 1, continued)
        }
    }

    @Test
    fun lifecycleInterruptionFinishesAndReleasesPendingNavigation() {
        val state = HistoryNavigationState()
        var continued = 0
        state.begin { continued++ }

        state.finishPresentation()

        assertFalse(state.isPresenting)
        assertEquals(1, continued)
    }

    @Test
    fun boundedFallbackReleasesNavigationButDoesNotPermitADuplicateAd() {
        val state = HistoryNavigationState()
        var continued = 0
        state.begin { continued++ }

        state.releaseNavigationFallback()
        state.releaseNavigationFallback()

        assertEquals(1, continued)
        assertTrue(state.isPresenting)
        assertTrue(state.navigationReleased)
        state.finishPresentation()
        assertFalse(state.isPresenting)
        assertEquals(1, continued)
    }
}
