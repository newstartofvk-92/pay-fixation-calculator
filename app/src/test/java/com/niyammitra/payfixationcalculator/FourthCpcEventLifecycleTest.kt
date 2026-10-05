package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FourthCpcEventLifecycleTest {
    private val scales = FourthToFifthCpcData.scales
    private val s1 = scales.first { it.grade == "S-1" }
    private val s13 = scales.first { it.grade == "S-13 (I)" }
    private val s14 = scales.first { it.grade == "S-14" }
    private val s15 = scales.first { it.grade == "S-15 (I)" }

    private fun event(order: Int, date: Long, target: FourthCpcScale, sequence: Int = order) =
        FourthCpcEventSnapshot(order, date, CpcJourneyEventKind.PROMOTION, target.existingScale, 0, sequence)

    @Test fun addEventOpensTypesThenSelectedTypeStartsDraft() {
        val options = reduceFourthCpcEventState(FourthCpcEventUiState(), FourthCpcEventAction.OpenEventTypes)
        assertTrue(options.showEventTypes)
        val draft = reduceFourthCpcEventState(options, FourthCpcEventAction.SelectType(FourthCpcEventType.ACP))
        assertFalse(draft.showEventTypes)
        assertEquals(FourthCpcEventType.ACP, draft.draftType)
        assertTrue(draft.acceptedEvents.isEmpty())
    }

    @Test fun cancellingDraftDiscardsItAndPreservesAcceptedEvents() {
        val accepted = event(1, 100L, s14, 1)
        val before = FourthCpcEventUiState(draftType = FourthCpcEventType.PROMOTION, acceptedEvents = listOf(accepted))
        val after = reduceFourthCpcEventState(before, FourthCpcEventAction.CancelDraft)
        assertNull(after.draftType)
        assertFalse(after.showEventTypes)
        assertEquals(listOf(accepted), after.acceptedEvents)
    }

    @Test fun onlyAcceptedDraftEntersAcceptedEventStateOnce() {
        val draft = reduceFourthCpcEventState(FourthCpcEventUiState(), FourthCpcEventAction.SelectType(FourthCpcEventType.PROMOTION))
        val accepted = event(1, 100L, s14, 1)
        val after = reduceFourthCpcEventState(draft, FourthCpcEventAction.Accept(accepted))
        assertNull(after.draftType)
        assertEquals(listOf(accepted), after.acceptedEvents)
        assertEquals(after, reduceFourthCpcEventState(after, FourthCpcEventAction.Accept(accepted)))
    }

    @Test fun deletingFirstMiddleOrLastAcceptedEventRemovesOnlySelectedEventAndKeepsOrder() {
        val events = listOf(event(1, 10L, s14, 1), event(2, 20L, s15, 2), event(3, 30L, s14, 3))
        val expected = listOf(
            listOf(events[1].copy(order = 1), events[2].copy(order = 2)),
            listOf(events[0].copy(order = 1), events[2].copy(order = 2)),
            listOf(events[0].copy(order = 1), events[1].copy(order = 2))
        )
        expected.indices.forEach { index ->
            val result = reduceFourthCpcEventState(FourthCpcEventUiState(acceptedEvents = events), FourthCpcEventAction.Delete(index))
            assertEquals(expected[index], result.acceptedEvents)
        }
    }

    @Test fun deletingEarlierEventRecalculatesLaterIncrementAndEventFromNewUpstreamPay() {
        val firstEvent = event(1, 200L, s13, 2)
        val laterEvent = event(2, 400L, s15, 4)
        val increments = listOf(
            FourthToFifthIncrementStep(0, 100L, 1),
            FourthToFifthIncrementStep(0, 300L, 3)
        )
        val withBothEvents = recalculateFourthCpcTimeline(750, s1, increments, listOf(firstEvent, laterEvent))
        assertEquals(2575, withBothEvents.events.last().resultingPay)

        val afterDelete = reduceFourthCpcEventState(
            FourthCpcEventUiState(acceptedEvents = withBothEvents.events),
            FourthCpcEventAction.Delete(0)
        )
        val replayed = recalculateFourthCpcTimeline(750, s1, withBothEvents.increments, afterDelete.acceptedEvents)
        assertEquals(762, replayed.increments[0].pay)
        assertEquals(774, replayed.increments[1].pay)
        assertEquals(2200, replayed.events.single().resultingPay)
        assertEquals(2200, replayed.pay)
        assertEquals(400L, replayed.lastEffectiveDate)
    }

    @Test fun deletingAcceptedEventInvalidatesSavedFifthCpcContinuation() {
        val prior = FourthCpcContinuationState(
            conversionActivated = true,
            fifthSnapshot = FifthCpcJourneySnapshot(100L, "scale", 1000, null)
        )
        val invalidated = invalidateFourthCpcContinuation(prior)
        assertFalse(invalidated.conversionActivated)
        assertNull(invalidated.fifthSnapshot)
    }
}
