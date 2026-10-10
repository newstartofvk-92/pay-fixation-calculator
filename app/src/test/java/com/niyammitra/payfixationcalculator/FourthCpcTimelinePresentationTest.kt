package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FourthCpcTimelinePresentationTest {
    private val scales = FourthToFifthCpcData.scales
    private val startingScale = scales.first { it.grade == "S-1" }
    private val promotedScale = scales.first { it.grade == "S-13 (I)" }
    private val laterScale = scales.first { it.grade == "S-14" }
    private val startDate = 50L

    private fun increment(date: Long, sequence: Int, pay: Int = 0) =
        FourthToFifthIncrementStep(pay, date, sequence)

    private fun event(order: Int, date: Long, sequence: Int, target: FourthCpcScale = promotedScale) =
        FourthCpcEventSnapshot(order, date, CpcJourneyEventKind.PROMOTION, target.existingScale, 0, sequence)

    private fun replay(increments: List<FourthToFifthIncrementStep>, events: List<FourthCpcEventSnapshot>) =
        recalculateFourthCpcTimeline(750, startingScale, increments, events)

    private fun rows(timeline: FourthCpcTimelineResult) = buildFourthCpcTimelineEntries(
        startingPay = 750,
        startingDateMillis = startDate,
        timeline = timeline,
        sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
    )

    private fun types(timeline: FourthCpcTimelineResult) = rows(timeline).map { it.type }

    @Test
    fun startingPayIncrementThenEventAreShownChronologically() {
        val timeline = replay(listOf(increment(100, 1)), listOf(event(1, 200, 2)))

        assertEquals(
            listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.INCREMENT, FourthCpcTimelineEntryType.EVENT),
            types(timeline)
        )
    }

    @Test
    fun startingPayEventThenIncrementAreShownChronologically() {
        val timeline = replay(listOf(increment(200, 2)), listOf(event(1, 100, 1)))

        assertEquals(
            listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.EVENT, FourthCpcTimelineEntryType.INCREMENT),
            types(timeline)
        )
    }

    @Test
    fun incrementEventIncrementJourneyUsesEffectiveDates() {
        val timeline = replay(
            listOf(increment(100, 1), increment(300, 3)),
            listOf(event(1, 200, 2))
        )

        assertEquals(
            listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.INCREMENT, FourthCpcTimelineEntryType.EVENT, FourthCpcTimelineEntryType.INCREMENT),
            types(timeline)
        )
        assertEquals(listOf(50L, 100L, 200L, 300L), rows(timeline).map { it.effectiveDateMillis })
    }

    @Test
    fun twoEventsAtDifferentDatesAppearInDateOrder() {
        val timeline = replay(emptyList(), listOf(event(1, 300, 2, laterScale), event(2, 200, 1)))

        assertEquals(listOf(50L, 200L, 300L), rows(timeline).map { it.effectiveDateMillis })
        assertEquals(listOf(1, 0), rows(timeline).drop(1).map { it.sourceIndex })
    }

    @Test
    fun sameDateOriginalJourneyUsesSharedSequenceForDisplayOrder() {
        val timeline = replay(
            listOf(increment(200, 2)),
            listOf(event(1, 200, 1))
        )

        assertEquals(
            listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.EVENT, FourthCpcTimelineEntryType.INCREMENT),
            types(timeline)
        )
    }

    @Test
    fun sameDateInferredJourneyUsesLegacyListOrderForDisplay() {
        val timeline = recalculateFourthCpcTimeline(
            startingPay = 750,
            startingScale = startingScale,
            increments = listOf(increment(200, 2)),
            events = listOf(event(1, 200, 1)),
            sequenceIntegrity = CpcSequenceIntegrity.INFERRED
        )
        val entries = buildFourthCpcTimelineEntries(750, startDate, timeline, CpcSequenceIntegrity.INFERRED)

        assertEquals(
            listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.INCREMENT, FourthCpcTimelineEntryType.EVENT),
            entries.map { it.type }
        )
    }

    @Test
    fun deletingEarlierEventRemovesOnlyItsTimelineEntry() {
        val events = listOf(event(1, 100, 1), event(2, 300, 3, laterScale))
        val accepted = reduceFourthCpcEventState(
            FourthCpcEventUiState(acceptedEvents = events),
            FourthCpcEventAction.Delete(0)
        ).acceptedEvents
        val timeline = replay(listOf(increment(200, 2)), accepted)

        assertEquals(listOf(FourthCpcTimelineEntryType.STARTING_POSITION, FourthCpcTimelineEntryType.INCREMENT, FourthCpcTimelineEntryType.EVENT), types(timeline))
        assertEquals(0, rows(timeline).last().sourceIndex)
    }

    @Test
    fun eventAddedAfterSeveralIncrementsAppearsAfterThoseIncrements() {
        val timeline = replay(
            listOf(increment(100, 1), increment(200, 2), increment(400, 4)),
            listOf(event(1, 300, 3))
        )

        assertEquals(
            listOf(
                FourthCpcTimelineEntryType.STARTING_POSITION,
                FourthCpcTimelineEntryType.INCREMENT,
                FourthCpcTimelineEntryType.INCREMENT,
                FourthCpcTimelineEntryType.EVENT,
                FourthCpcTimelineEntryType.INCREMENT
            ),
            types(timeline)
        )
    }

    @Test
    fun restoredSnapshotIncrementsAndEventsAreProjectedWithoutDuplication() {
        val snapshot = FourthCpcJourneySnapshot(
            scaleId = startingScale.existingScale,
            scaleTitle = startingScale.grade,
            startingBasicPay = 750,
            payDateMillis = startDate,
            nextIncrementDateMillis = 100L,
            increments = listOf(CpcIncrementSnapshot(1, 0, 100L, 1), CpcIncrementSnapshot(2, 0, 300L, 3)),
            events = listOf(event(1, 200L, 2))
        )
        val timeline = replay(
            snapshot.increments.map { FourthToFifthIncrementStep(it.pay, it.dateMillis, it.sequence) },
            snapshot.events
        )
        val entries = rows(timeline)

        assertEquals(4, entries.size)
        assertEquals(listOf(50L, 100L, 200L, 300L), entries.map { it.effectiveDateMillis })
        assertEquals(1, entries.count { it.type == FourthCpcTimelineEntryType.EVENT })
        assertTrue(entries.drop(1).all { it.basicPay > 0 })
    }

    @Test
    fun finalChronologicalPositionRemainsTheInputToFifthCpcContinuation() {
        val timeline = replay(
            listOf(increment(100, 1), increment(300, 3)),
            listOf(event(1, 200, 2))
        )
        val finalEntry = rows(timeline).last()
        val conversion = calculateFourthToFifthCpc(timeline.pay, timeline.scale)

        assertEquals(timeline.pay, finalEntry.basicPay)
        assertEquals(timeline.pay, conversion.existingBasicPay)
        assertEquals(timeline.scale, conversion.scale)
    }
}
