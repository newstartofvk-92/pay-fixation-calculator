package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SixthCpcJourneyReplayTest {
    private val base = SixthCpcJourneyStartingPosition(date(2006, Calendar.JANUARY, 1), "PB-2", 4200, 9300)

    @Test
    fun deletingMiddleStandaloneIncrementReplaysLaterPay() {
        val original = state(increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1),
            inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2008, Calendar.JULY, 1), 3)
        ))
        val changed = deleteSixthCpcIncrement(original, 1)
        assertTrue(changed.accepted)
        assertEquals(listOf(date(2006, Calendar.JULY, 1), date(2008, Calendar.JULY, 1)), changed.state.increments.map { it.date })
        assertNotEquals(original.increments[2].payInPayBand, changed.state.increments[1].payInPayBand)
    }

    @Test
    fun standaloneIncrementAddAndDeleteInvalidateRestoredSeventhContinuationWhenSixthHasNoEvents() {
        val seventh = SeventhCpcJourneySnapshot(
            startingLevel = "6", startingBasicPay = 35_400, conversionDateMillis = date(2016, Calendar.JANUARY, 1),
            increments = listOf(CpcIncrementSnapshot(1, 36_500, date(2017, Calendar.JULY, 1), 7)),
            promotions = listOf(SeventhCpcPromotionSnapshot(
                currentLevel = "6", currentPay = 35_400, knownDniMillis = date(2016, Calendar.JULY, 1),
                promotedLevel = "7", promotionDateMillis = date(2017, Calendar.APRIL, 1), sequence = 8
            ))
        )
        val original = state()
        assertTrue(original.events.isEmpty())
        val withRestoredContinuation = SixthCpcContinuationMutationState(original, seventh)

        val incremented = original.copy(increments = listOf(inc(date(2006, Calendar.JULY, 1), 1)))
        val afterAdd = withRestoredContinuation.withSixthCpc(incremented)
        assertEquals(incremented, afterAdd.sixthCpc)
        assertNull("Adding a 6th CPC increment must clear saved 7th CPC actions", afterAdd.seventhCpc)

        val afterDelete = SixthCpcContinuationMutationState(incremented, seventh).withSixthCpc(original)
        assertEquals(original, afterDelete.sixthCpc)
        assertNull("Deleting a 6th CPC increment must clear saved 7th CPC actions", afterDelete.seventhCpc)
    }

    @Test
    fun deletingFirstLastAndNestedIncrementUpdatesRemainingCalculatedPay() {
        val standalone = state(increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2), inc(date(2008, Calendar.JULY, 1), 3)
        ))
        assertEquals(2, deleteSixthCpcIncrement(standalone, 0).state.increments.first().sequence)
        assertEquals(2, deleteSixthCpcIncrement(standalone, 2).state.increments.size)

        val chain = event(date(2006, Calendar.APRIL, 1), 1, 4600).copy(
            increments = listOf(
                SixthCpcEventIncrement(0, 0, date(2007, Calendar.JULY, 1), 2),
                SixthCpcEventIncrement(0, 0, date(2008, Calendar.JULY, 1), 3),
                SixthCpcEventIncrement(0, 0, date(2009, Calendar.JULY, 1), 4)
            )
        )
        val withNested = state(events = listOf(chain))
        val deleted = deleteSixthCpcEventIncrement(withNested, chain.localId, 1)
        assertTrue(deleted.accepted)
        assertEquals(listOf(date(2007, Calendar.JULY, 1), date(2009, Calendar.JULY, 1)), deleted.state.events.single().increments.map { it.date })
        assertNotEquals(chain.increments[2].payInPayBand, deleted.state.events.single().increments[1].payInPayBand)
        assertEquals(2, deleteSixthCpcEventIncrement(withNested, chain.localId, 0).state.events.single().increments.size)
        assertEquals(2, deleteSixthCpcEventIncrement(withNested, chain.localId, 2).state.events.single().increments.size)
    }

    @Test
    fun deletingEventKeepsAndRecalculatesLaterReplayableEvents() {
        val increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2009, Calendar.JULY, 1), 4), inc(date(2010, Calendar.JULY, 1), 5), inc(date(2011, Calendar.JULY, 1), 6)
        )
        val first = event(date(2008, Calendar.APRIL, 1), 3, 4600, payInBand = 9710)
        val prefix = replaySixthCpcJourney(state(increments = increments, events = listOf(first))).finalPosition
        val second = event(date(2012, Calendar.APRIL, 1), 7, 4800, prefix.payInPayBand, prefix.gradePay)
        val before = state(increments = increments, events = listOf(first, second))
        val oldLaterPay = second.result!!.oldPayInPayBand
        val changed = deleteSixthCpcEvent(before, first.localId)
        assertTrue(changed.accepted)
        assertEquals(listOf(second.localId), changed.state.events.map { it.localId })
        val independentPosition = replaySixthCpcJourney(state(increments = increments)).finalPosition
        assertEquals(independentPosition.payInPayBand, changed.state.events.single().result!!.oldPayInPayBand)
        assertNotEquals(oldLaterPay, changed.state.events.single().result!!.oldPayInPayBand)
    }

    @Test
    fun eventEditUsesStableIdentityAfterEarlierEventIsDeleted() {
        val increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2009, Calendar.JULY, 1), 4), inc(date(2010, Calendar.JULY, 1), 5)
        )
        val first = event(date(2008, Calendar.APRIL, 1), 3, 4600, 9710)
        val prefix = replaySixthCpcJourney(state(increments = increments, events = listOf(first))).finalPosition
        val second = event(date(2011, Calendar.APRIL, 1), 6, 4800, prefix.payInPayBand, prefix.gradePay)
        val saved = state(increments = increments, events = listOf(first, second))
        val edited = event(date(2011, Calendar.MAY, 1), 999, 5400, prefix.payInPayBand, prefix.gradePay)
        val afterDelete = deleteSixthCpcEvent(saved, first.localId)
        assertTrue(afterDelete.accepted)
        val result = replaceSixthCpcEvent(afterDelete.state, second.localId, edited)
        assertTrue(result.accepted)
        assertEquals(1, result.state.events.count { it.localId == second.localId })
        assertEquals(second.sequence, result.state.events.single().sequence)
        assertEquals(date(2011, Calendar.MAY, 1), result.state.events.single().result!!.eventDate)
        assertEquals(5400, result.state.events.single().result!!.newGradePay)
    }

    @Test
    fun fromDniPromotionHasSeparatePromotionAndDniRefixationPositions() {
        val start = SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930)
        val promotionDate = date(2015, Calendar.FEBRUARY, 4)
        val promotion = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, promotionDate, "Promotion", SixthCpcFixationOption.FROM_DNI),
            sequence = 1
        )
        val replay = replaySixthCpcJourney(SixthCpcJourneyState(start, events = listOf(promotion)))
        val promotionRow = replay.timeline.single { it.kind == SixthCpcTimelineKind.EVENT }
        val refixRow = replay.timeline.single { it.kind == SixthCpcTimelineKind.DNI_REFIXATION }

        assertEquals(date(2014, Calendar.JULY, 1), replay.timeline.first().dateMillis)
        assertEquals(12930, replay.timeline.first().position.payInPayBand)
        assertEquals(17130, replay.timeline.first().position.basicPay)
        assertEquals(promotionDate, promotionRow.dateMillis)
        assertEquals(12930, promotionRow.position.payInPayBand)
        assertEquals(4600, promotionRow.position.gradePay)
        assertEquals(17530, promotionRow.position.basicPay)
        assertEquals(date(2015, Calendar.JULY, 1), promotionRow.dniMillis)
        assertEquals(date(2015, Calendar.JULY, 1), refixRow.dateMillis)
        assertEquals(13980, refixRow.position.payInPayBand)
        assertEquals(4600, refixRow.position.gradePay)
        assertEquals(18580, refixRow.position.basicPay)
        assertEquals(date(2016, Calendar.JULY, 1), refixRow.dniMillis)
        assertEquals(date(2016, Calendar.JULY, 1), replay.events.single().result!!.nextIncrementDate)
        assertEquals(1050, replay.events.single().result!!.increment)
        assertTrue(replay.increments.isEmpty())
        assertEquals(1, replay.timeline.count { it.kind == SixthCpcTimelineKind.DNI_REFIXATION })
    }

    @Test
    fun fromDniPromotionDisplayUsesBothReplayPositionsAndExistingIncrementResult() {
        val starting = date(2014, Calendar.JULY, 1)
        val promotionDate = date(2015, Calendar.FEBRUARY, 4)
        val result = calculateSixthCpcPromotionOrMacp(
            12_930, 4_200, 4_600, promotionDate, "Promotion", SixthCpcFixationOption.FROM_DNI
        )
        val replay = replaySixthCpcJourney(SixthCpcJourneyState(
            SixthCpcJourneyStartingPosition(starting, "PB-2", 4_200, 12_930),
            events = listOf(SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = result, sequence = 1))
        ))
        val promotionRow = replay.timeline.single { it.kind == SixthCpcTimelineKind.EVENT }
        val display = sixthCpcDniPromotionDisplay(promotionRow, replay.events.single().result!!, replay.timeline)!!

        assertEquals(promotionDate, display.promotionDateMillis)
        assertEquals(17_530, display.promotionPosition.basicPay)
        assertEquals(date(2015, Calendar.JULY, 1), display.dniEffectiveDateMillis)
        assertEquals(18_580, display.postDniPosition.basicPay)
        assertEquals(result.increment, display.totalFixationIncrement)
    }

    @Test
    fun twoFromDniPromotionDisplaysUseTheirOwnMatchingRefixationRows() {
        val startDate = date(2014, Calendar.JULY, 1)
        val firstPromotionDate = date(2015, Calendar.FEBRUARY, 4)
        val firstResult = calculateSixthCpcPromotionOrMacp(
            12_930, 4_200, 4_600, firstPromotionDate, "Promotion", SixthCpcFixationOption.FROM_DNI
        )
        val firstEvent = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = firstResult,
            sequence = 1
        )
        val startingPosition = SixthCpcJourneyStartingPosition(startDate, "PB-2", 4_200, 12_930)
        val afterFirstPromotion = replaySixthCpcJourney(
            SixthCpcJourneyState(startingPosition, events = listOf(firstEvent))
        )
        val firstRefixPosition = afterFirstPromotion.finalPosition
        val secondPromotionDate = date(2015, Calendar.AUGUST, 1)
        val secondResult = calculateSixthCpcPromotionOrMacp(
            firstRefixPosition.payInPayBand,
            firstRefixPosition.gradePay,
            4_800,
            secondPromotionDate,
            "Promotion",
            SixthCpcFixationOption.FROM_DNI
        )
        val secondEvent = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = secondResult,
            sequence = 2
        )

        assertNotEquals(firstEvent.localId, secondEvent.localId)

        val replay = replaySixthCpcJourney(
            SixthCpcJourneyState(startingPosition, events = listOf(firstEvent, secondEvent))
        )
        val eventRows = replay.timeline.filter { it.kind == SixthCpcTimelineKind.EVENT }
        assertEquals(2, eventRows.size)
        assertEquals(setOf(firstEvent.localId, secondEvent.localId), eventRows.mapNotNull { it.eventId }.toSet())

        val firstEventRow = eventRows.single { it.eventId == firstEvent.localId }
        val secondEventRow = eventRows.single { it.eventId == secondEvent.localId }
        val firstReplayedResult = replay.events.single { it.localId == firstEvent.localId }.result!!
        val secondReplayedResult = replay.events.single { it.localId == secondEvent.localId }.result!!
        val firstRefixRow = replay.timeline.single {
            it.kind == SixthCpcTimelineKind.DNI_REFIXATION && it.eventId == firstEvent.localId
        }
        val secondRefixRow = replay.timeline.single {
            it.kind == SixthCpcTimelineKind.DNI_REFIXATION && it.eventId == secondEvent.localId
        }
        val firstDisplay = sixthCpcDniPromotionDisplay(firstEventRow, firstReplayedResult, replay.timeline)!!
        val secondDisplay = sixthCpcDniPromotionDisplay(secondEventRow, secondReplayedResult, replay.timeline)!!

        assertEquals(firstPromotionDate, firstDisplay.promotionDateMillis)
        assertEquals(12_930, firstDisplay.promotionPosition.payInPayBand)
        assertEquals(4_600, firstDisplay.promotionPosition.gradePay)
        assertEquals(17_530, firstDisplay.promotionPosition.basicPay)
        assertEquals(date(2015, Calendar.JULY, 1), firstDisplay.dniEffectiveDateMillis)
        assertEquals(firstRefixRow.dateMillis, firstDisplay.dniEffectiveDateMillis)
        assertEquals(13_980, firstDisplay.postDniPosition.payInPayBand)
        assertEquals(4_600, firstDisplay.postDniPosition.gradePay)
        assertEquals(18_580, firstDisplay.postDniPosition.basicPay)
        assertEquals(date(2016, Calendar.JULY, 1), firstDisplay.postDniPosition.nextDniMillis)
        assertEquals(1_050, firstDisplay.totalFixationIncrement)
        assertEquals(firstReplayedResult.increment, firstDisplay.totalFixationIncrement)

        assertEquals(secondPromotionDate, secondDisplay.promotionDateMillis)
        assertEquals(13_980, secondDisplay.promotionPosition.payInPayBand)
        assertEquals(4_800, secondDisplay.promotionPosition.gradePay)
        assertEquals(18_780, secondDisplay.promotionPosition.basicPay)
        assertEquals(date(2016, Calendar.JULY, 1), secondDisplay.dniEffectiveDateMillis)
        assertEquals(secondRefixRow.dateMillis, secondDisplay.dniEffectiveDateMillis)
        assertEquals(15_120, secondDisplay.postDniPosition.payInPayBand)
        assertEquals(4_800, secondDisplay.postDniPosition.gradePay)
        assertEquals(19_920, secondDisplay.postDniPosition.basicPay)
        assertEquals(date(2017, Calendar.JULY, 1), secondDisplay.postDniPosition.nextDniMillis)
        assertEquals(1_140, secondDisplay.totalFixationIncrement)
        assertEquals(secondReplayedResult.increment, secondDisplay.totalFixationIncrement)

        assertNotEquals(firstDisplay.dniEffectiveDateMillis, secondDisplay.dniEffectiveDateMillis)
        assertNotEquals(firstDisplay.postDniPosition.payInPayBand, secondDisplay.postDniPosition.payInPayBand)
        assertNotEquals(firstDisplay.postDniPosition.basicPay, secondDisplay.postDniPosition.basicPay)
        assertNotEquals(firstDisplay.totalFixationIncrement, secondDisplay.totalFixationIncrement)
    }

    @Test
    fun explicitAnnualIncrementCannotDuplicateTheFromDniRefixation() {
        val start = SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930)
        val promotion = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, date(2015, Calendar.FEBRUARY, 4), "Promotion", SixthCpcFixationOption.FROM_DNI),
            sequence = 1
        )
        val duplicateAnnualIncrement = inc(date(2015, Calendar.JULY, 1), 2)
        val result = runCatching {
            replaySixthCpcJourney(SixthCpcJourneyState(start, increments = listOf(duplicateAnnualIncrement), events = listOf(promotion)))
        }
        assertTrue("The event already contains the two DNI fixation increments; a duplicate standalone increment must not replay.", result.isFailure)
    }

    @Test
    fun eventsBeforeAndAfterFromDniRefixUseTheirEffectivePayPositions() {
        val start = SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930)
        val februaryPromotion = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, date(2015, Calendar.FEBRUARY, 4), "Promotion", SixthCpcFixationOption.FROM_DNI),
            sequence = 1
        )
        val preDniEvent = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = calculateSixthCpcPromotionOrMacp(12930, 4600, 4800, date(2015, Calendar.APRIL, 1), "Promotion"),
            sequence = 2
        )
        val promotionOnly = SixthCpcJourneyState(start, events = listOf(februaryPromotion))
        val preDniEntryPosition = sixthCpcPositionBeforeDate(promotionOnly, date(2015, Calendar.APRIL, 1))!!
        assertEquals(12930, preDniEntryPosition.payInPayBand)
        assertEquals(4600, preDniEntryPosition.gradePay)
        val preDniReplay = replaySixthCpcJourney(SixthCpcJourneyState(start, events = listOf(februaryPromotion, preDniEvent)))
        val recalculatedPreDniEvent = preDniReplay.events.first { it.localId == preDniEvent.localId }.result!!
        assertEquals(12930, recalculatedPreDniEvent.oldPayInPayBand)
        assertEquals(4600, recalculatedPreDniEvent.oldGradePay)
        assertEquals(date(2015, Calendar.APRIL, 1), preDniReplay.timeline.first { it.eventId == preDniEvent.localId }.dateMillis)

        val postDniEvent = SixthCpcEventChain(
            kind = SixthCpcEventKind.PROMOTION,
            result = calculateSixthCpcPromotionOrMacp(13980, 4600, 4800, date(2015, Calendar.AUGUST, 1), "Promotion"),
            sequence = 2
        )
        val postDniReplay = replaySixthCpcJourney(SixthCpcJourneyState(start, events = listOf(februaryPromotion, postDniEvent)))
        val recalculatedPostDniEvent = postDniReplay.events.first { it.localId == postDniEvent.localId }.result!!
        val postDniEntryPosition = sixthCpcPositionBeforeDate(promotionOnly, date(2015, Calendar.AUGUST, 1))!!
        assertEquals(13980, postDniEntryPosition.payInPayBand)
        assertEquals(4600, postDniEntryPosition.gradePay)
        assertEquals(13980, recalculatedPostDniEvent.oldPayInPayBand)
        assertEquals(4600, recalculatedPostDniEvent.oldGradePay)
        assertTrue(postDniReplay.timeline.indexOfFirst { it.kind == SixthCpcTimelineKind.DNI_REFIXATION } <
            postDniReplay.timeline.indexOfFirst { it.eventId == postDniEvent.localId })
    }

    @Test
    fun fromDniRefixationMakes2015PositionConvertibleWithoutJuly2016Increment() {
        val start = SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930)
        val promotion = event(date(2015, Calendar.FEBRUARY, 4), 1, 4600, 12930, 4200).copy(
            result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, date(2015, Calendar.FEBRUARY, 4), "Promotion", SixthCpcFixationOption.FROM_DNI)
        )
        val replay = replaySixthCpcJourney(SixthCpcJourneyState(start, events = listOf(promotion)))
        assertEquals(date(2015, Calendar.JULY, 1), replay.finalPosition.dateMillis)
        assertTrue(sixthCpcPositionValidThrough(replay.finalPosition, sixthCpcEndOfPeriodDate()))
        assertEquals(date(2016, Calendar.JULY, 1), replay.finalPosition.nextDniMillis)
        assertTrue(replay.increments.isEmpty())
        val pb2 = bandForGradePay(replay.finalPosition.gradePay)
        val converted = calculateSixthToSeventhCpc(replay.finalPosition.payInPayBand, replay.finalPosition.gradePay, pb2)
        assertEquals(18580, converted!!.existingPay)
    }

    @Test
    fun editingAndDeletingFromDniPromotionRecalculatesOrRemovesGeneratedRefixation() {
        val start = SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930)
        val original = event(date(2015, Calendar.FEBRUARY, 4), 1, 4600, 12930, 4200).copy(
            result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, date(2015, Calendar.FEBRUARY, 4), "Promotion", SixthCpcFixationOption.FROM_DNI)
        )
        val state = SixthCpcJourneyState(start, events = listOf(original))
        val editedResult = calculateSixthCpcPromotionOrMacp(12930, 4200, 4800, date(2015, Calendar.MARCH, 4), "Promotion", SixthCpcFixationOption.FROM_DNI)
        val edited = replaceSixthCpcEvent(state, original.localId, original.copy(result = editedResult))
        assertTrue(edited.accepted)
        assertEquals(date(2015, Calendar.MARCH, 4), edited.replay!!.timeline.single { it.kind == SixthCpcTimelineKind.EVENT }.dateMillis)
        assertEquals(1, edited.replay.timeline.count { it.kind == SixthCpcTimelineKind.DNI_REFIXATION })
        assertEquals(12930 + editedResult.increment, edited.replay.finalPosition.payInPayBand)

        val deleted = deleteSixthCpcEvent(edited.state, original.localId)
        assertTrue(deleted.accepted)
        assertTrue(deleted.replay!!.timeline.none { it.kind == SixthCpcTimelineKind.DNI_REFIXATION })
        assertEquals(12930, deleted.replay.finalPosition.payInPayBand)
        assertEquals(date(2014, Calendar.JULY, 1), deleted.replay.finalPosition.dateMillis)
    }

    @Test
    fun fromEventDatePromotionRetainsExistingFixationAndDniBehavior() {
        val promotionDate = date(2015, Calendar.FEBRUARY, 4)
        val result = calculateSixthCpcPromotionOrMacp(12930, 4200, 4600, promotionDate, "Promotion", SixthCpcFixationOption.FROM_EVENT_DATE)
        val replay = replaySixthCpcJourney(SixthCpcJourneyState(
            SixthCpcJourneyStartingPosition(date(2014, Calendar.JULY, 1), "PB-2", 4200, 12930),
            events = listOf(SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = result, sequence = 1))
        ))
        val eventRow = replay.timeline.single { it.kind == SixthCpcTimelineKind.EVENT }
        assertEquals(13450, eventRow.position.payInPayBand)
        assertEquals(4600, eventRow.position.gradePay)
        assertEquals(18050, eventRow.position.basicPay)
        assertEquals(date(2016, Calendar.JULY, 1), result.nextIncrementDate)
        assertTrue(replay.timeline.none { it.kind == SixthCpcTimelineKind.DNI_REFIXATION })
        assertEquals(promotionDate, replay.finalPosition.dateMillis)
    }

    @Test
    fun editingDeletedEventDoesNotTurnIntoAdd() {
        val old = event(date(2008, Calendar.APRIL, 1), 1, 4600)
        val removed = state(events = listOf(old)).copy(events = emptyList())
        val result = replaceSixthCpcEvent(removed, old.localId, old)
        assertFalse(result.accepted)
        assertTrue(result.state.events.isEmpty())
    }

    @Test
    fun productionAddAndEditMutationKeepsOneUpdatedEvent() {
        val original = state()
        val draft = event(date(2006, Calendar.APRIL, 1), 999, 4600)
        val added = addSixthCpcEvent(original, draft)
        assertTrue(added.accepted)
        assertEquals(1, added.state.events.size)
        assertEquals(1, added.state.events.single().sequence)

        val replacement = draft.copy(result = calculateSixthCpcPromotionOrMacp(
            9300, 4200, 4800, date(2006, Calendar.MAY, 1), "Promotion"
        ))
        val edited = replaceSixthCpcEvent(added.state, draft.localId, replacement)
        assertTrue(edited.accepted)
        assertEquals(1, edited.state.events.size)
        assertEquals(date(2006, Calendar.MAY, 1), edited.state.events.single().result!!.eventDate)
        assertEquals(1, edited.state.events.single().sequence)
    }

    @Test
    fun editingEarlierEventAfterDeletingUnrelatedLaterEventUsesStableIdentity() {
        val increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2009, Calendar.JULY, 1), 4)
        )
        val first = event(date(2008, Calendar.APRIL, 1), 3, 4600)
        val before = replaySixthCpcJourney(state(increments = increments, events = listOf(first))).finalPosition
        val editedTarget = event(date(2010, Calendar.APRIL, 1), 5, 4800, before.payInPayBand, before.gradePay)
        val later = event(date(2011, Calendar.APRIL, 1), 6, 5400, before.payInPayBand, before.gradePay)
        val saved = state(increments = increments, events = listOf(first, editedTarget, later))
        val afterDelete = deleteSixthCpcEvent(saved, later.localId)
        assertTrue(afterDelete.accepted)
        val replacement = event(date(2010, Calendar.MAY, 1), 0, 5400, before.payInPayBand, before.gradePay)
        val edited = replaceSixthCpcEvent(afterDelete.state, editedTarget.localId, replacement)
        assertTrue(edited.accepted)
        assertEquals(listOf(first.localId, editedTarget.localId), edited.state.events.map { it.localId })
        assertEquals(1, edited.state.events.count { it.localId == editedTarget.localId })
        assertEquals(editedTarget.sequence, edited.state.events.last().sequence)
        assertEquals(date(2010, Calendar.MAY, 1), edited.state.events.last().result!!.eventDate)
    }

    @Test
    fun deletingUnreplayableLaterScaleUpgradeRequiresExplicitConfirmation() {
        val increments = listOf(inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2008, Calendar.JULY, 1), 4), inc(date(2009, Calendar.JULY, 1), 5))
        val first = event(date(2008, Calendar.APRIL, 1), 3, 4600, 9710)
        val laterUpgrade = SixthCpcEventChain(
            kind = SixthCpcEventKind.PAY_SCALE_UPGRADATION,
            scaleUpgrade = SixthCpcScaleUpgradeResult(date(2010, Calendar.APRIL, 1), 10000, 4600, "PB-2", 12000, 4600,
                "PB-2", 16600, date(2011, Calendar.JULY, 1)), sequence = 6
        )
        val saved = state(increments = increments, events = listOf(first, laterUpgrade))
        val blocked = deleteSixthCpcEvent(saved, first.localId)
        assertFalse(blocked.accepted)
        assertEquals(2, blocked.state.events.size)
        assertTrue(blocked.error.orEmpty().contains("lacks replayable inputs"))
    }

    @Test
    fun confirmedDeleteUsesDateAndListOrderForInferredJourney() {
        val earlier = event(date(2008, Calendar.APRIL, 1), 20, 4600)
        val later = event(date(2010, Calendar.APRIL, 1), 10, 4800)
        val saved = state(events = listOf(earlier, later)).copy(sequenceIntegrity = CpcSequenceIntegrity.INFERRED)
        val result = confirmDeleteSixthCpcEventAndLater(saved, earlier.localId)
        assertTrue(result.accepted)
        assertTrue(result.state.events.isEmpty())
    }

    @Test
    fun pb3GradePay5400StandaloneIncrementUsesPb3PayBandCeiling() {
        val start = base.copy(payBand = "PB-3", gradePay = 5400, payInPayBand = 35000)
        assertEquals(39100, sixthCpcPayBandForPosition(start.payBand, start.gradePay).payBandMaximum)
        val standalone = state(base = start, increments = listOf(inc(date(2006, Calendar.JULY, 1), 1)))
        val replay = replaySixthCpcJourney(standalone)
        assertEquals("PB-3", replay.finalPosition.payBand.substringBefore(":"))
        assertTrue(replay.finalPosition.payInPayBand > 34800)
        assertEquals(36220, replay.finalPosition.payInPayBand)
    }

    @Test
    fun pb3GradePay5400NestedIncrementUsesPb3PayBandCeiling() {
        val eventDate = date(2006, Calendar.APRIL, 1)
        val incrementDate = date(2006, Calendar.JULY, 1)
        val upgrade = SixthCpcScaleUpgradeResult(
            eventDate, 35000, 5400, "PB-3", 35000, 5400, "PB-3", 40400,
            incrementDate, HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC
        )
        val chain = SixthCpcEventChain(
            kind = SixthCpcEventKind.PAY_SCALE_UPGRADATION,
            scaleUpgrade = upgrade,
            increments = listOf(SixthCpcEventIncrement(0, 0, incrementDate, 2)),
            sequence = 1
        )
        val replay = replaySixthCpcJourney(state(
            base = base.copy(payBand = "PB-3", gradePay = 5400, payInPayBand = 35000),
            events = listOf(chain)
        ))
        val nestedPosition = replay.timeline.last()
        assertEquals(39100, sixthCpcPayBandForPosition(nestedPosition.position.payBand, nestedPosition.position.gradePay).payBandMaximum)
        assertEquals(SixthCpcTimelineKind.EVENT_INCREMENT, nestedPosition.kind)
        assertEquals("PB-3", nestedPosition.position.payBand.substringBefore(":"))
        assertTrue(nestedPosition.position.payInPayBand > 34800)
        assertEquals(37470, nestedPosition.position.payInPayBand)
    }

    @Test
    fun pb2GradePay5400StillUsesPb2PayBandCeiling() {
        val start = base.copy(payBand = "PB-2", gradePay = 5400, payInPayBand = 34800)
        val replay = replaySixthCpcJourney(state(base = start))
        assertEquals("PB-2", replay.finalPosition.payBand.substringBefore(":"))
        assertTrue(runCatching {
            replaySixthCpcJourney(state(base = start, increments = listOf(inc(date(2006, Calendar.JULY, 1), 1))))
        }.isFailure)
    }

    @Test
    fun eventEditorPrefixExcludesLaterNestedIncrementAndMatchesSameDateReplayOrder() {
        val eventA = event(date(2006, Calendar.APRIL, 1), 1, 4600)
        val nested = SixthCpcEventIncrement(0, 0, date(2007, Calendar.JULY, 1), 2)
        val eventAWithIncrement = eventA.copy(increments = listOf(nested))
        val target = event(date(2007, Calendar.JULY, 1), 3, 4800)
        val sameDateState = state(events = listOf(eventAWithIncrement, target))
        val expected = replaySixthCpcJourney(state(events = listOf(eventAWithIncrement))).finalPosition
        assertEquals(expected, sixthCpcPositionBeforeEvent(sameDateState, target.localId))

        val inferredA = eventAWithIncrement.copy(sequence = 20, increments = listOf(nested.copy(sequence = 50)))
        val inferredTarget = target.copy(sequence = 10)
        val inferredState = state(events = listOf(inferredA, inferredTarget))
            .copy(sequenceIntegrity = CpcSequenceIntegrity.INFERRED)
        val inferredExpected = replaySixthCpcJourney(state(events = listOf(inferredA))
            .copy(sequenceIntegrity = CpcSequenceIntegrity.INFERRED)).finalPosition
        assertEquals(inferredExpected, sixthCpcPositionBeforeEvent(inferredState, inferredTarget.localId))

        val laterNested = nested.copy(date = date(2008, Calendar.JULY, 1), sequence = 4)
        val laterState = state(events = listOf(eventA.copy(increments = listOf(laterNested)), target.copy(
            result = calculateSixthCpcPromotionOrMacp(9300, 4200, 4800, date(2007, Calendar.APRIL, 1), "Promotion"), sequence = 3
        )))
        val beforeTarget = sixthCpcPositionBeforeEvent(laterState, laterState.events.last().localId)
        assertEquals(date(2006, Calendar.APRIL, 1), beforeTarget?.dateMillis)
        assertEquals(laterState.events.first().result!!.newPayInPayBand, beforeTarget?.payInPayBand)
    }

    @Test
    fun successiveEventChainIncrementsUseLatestReplayPositionAndDni() {
        val promotion = event(date(2006, Calendar.APRIL, 1), 1, 4600)
        val starting = state(events = listOf(promotion))

        val afterFirst = addNextSixthCpcEventChainIncrement(starting, promotion.localId)
        assertTrue(afterFirst.error ?: "First nested increment should be accepted", afterFirst.accepted)
        val firstReplay = afterFirst.replay!!
        val firstRow = firstReplay.timeline.last()
        assertEquals(SixthCpcTimelineKind.EVENT_INCREMENT, firstRow.kind)
        assertEquals(date(2007, Calendar.JULY, 1), firstRow.dateMillis)
        assertEquals(date(2008, Calendar.JULY, 1), firstRow.dniMillis)

        val afterSecond = addNextSixthCpcEventChainIncrement(afterFirst.state, promotion.localId)
        assertTrue(afterSecond.error ?: "Second nested increment should be accepted", afterSecond.accepted)
        val secondReplay = afterSecond.replay!!
        val secondRow = secondReplay.timeline.last()
        assertEquals(2, afterSecond.state.events.single().increments.size)
        assertEquals(listOf(date(2007, Calendar.JULY, 1), date(2008, Calendar.JULY, 1)),
            afterSecond.state.events.single().increments.map { it.date })
        assertEquals(date(2008, Calendar.JULY, 1), secondRow.dateMillis)
        assertEquals(date(2009, Calendar.JULY, 1), secondRow.dniMillis)
        assertTrue(secondRow.position.basicPay > firstRow.position.basicPay)
        assertEquals(14_740, firstRow.position.basicPay)
        assertEquals(15_190, secondRow.position.basicPay)
        assertEquals(firstRow.position, latestSixthCpcEventChainPosition(firstReplay, promotion.localId))
        assertEquals(secondRow.position, latestSixthCpcEventChainPosition(secondReplay, promotion.localId))
        assertEquals(promotion.result!!.eventDate, afterSecond.state.events.single().result!!.eventDate)
        assertEquals(promotion.result!!.nextIncrementDate, afterSecond.state.events.single().result!!.nextIncrementDate)
    }

    @Test
    fun eventChainIncrementCannotBeAddedAfterAnotherLaterJourneyAction() {
        val first = event(date(2006, Calendar.APRIL, 1), 1, 4600)
        val firstWithIncrement = first.copy(increments = listOf(
            SixthCpcEventIncrement(0, 0, date(2007, Calendar.JULY, 1), 2)
        ))
        val later = event(date(2007, Calendar.AUGUST, 1), 3, 4800)
        val saved = state(events = listOf(firstWithIncrement, later))
        val mutation = addNextSixthCpcEventChainIncrement(saved, first.localId)
        assertFalse(mutation.accepted)
        assertEquals(1, saved.events.first().increments.size)
    }

    @Test
    fun deletionPlanAndReducerRemoveSameStandaloneIncrementWhenDateAndSequenceConflict() {
        val selected = event(date(2008, Calendar.APRIL, 1), 3, 4600)
        val conflictingLaterIncrement = inc(date(2009, Calendar.JULY, 1), 2)
        val saved = state(increments = listOf(conflictingLaterIncrement), events = listOf(selected))
        val plan = planSixthCpcEventAndLaterDeletion(saved, selected.localId)!!
        assertEquals(setOf(0), plan.removedTopIncrementIndices)

        val removed = confirmDeleteSixthCpcEventAndLater(saved, selected.localId)
        assertTrue(removed.accepted)
        assertTrue(removed.state.increments.isEmpty())
        assertTrue(plan.removedEventIds.contains(selected.localId))
    }

    @Test
    fun replayRejectsStandaloneOrNestedIncrementNotOnCurrentDni() {
        val wrongStandalone = state(increments = listOf(inc(date(2006, Calendar.AUGUST, 1), 1)))
        assertTrue(runCatching { replaySixthCpcJourney(wrongStandalone) }.isFailure)

        val chain = event(date(2006, Calendar.APRIL, 1), 1, 4600).copy(
            increments = listOf(SixthCpcEventIncrement(0, 0, date(2007, Calendar.AUGUST, 1), 2))
        )
        assertTrue(runCatching { replaySixthCpcJourney(state(events = listOf(chain))) }.isFailure)
    }

    @Test
    fun editingEventDateAndFixationReplaysLaterEventAndConversionFromNewFinalPosition() {
        val increments = listOf(
            inc(date(2006, Calendar.JULY, 1), 1), inc(date(2007, Calendar.JULY, 1), 2),
            inc(date(2009, Calendar.JULY, 1), 4)
        )
        val first = event(date(2008, Calendar.APRIL, 1), 3, 4600)
        val initial = replaySixthCpcJourney(state(increments = increments, events = listOf(first)))
        val later = event(date(2010, Calendar.APRIL, 1), 5, 5400, initial.finalPosition.payInPayBand, initial.finalPosition.gradePay)
        val saved = state(increments = increments, events = listOf(first, later))
        val changedFirst = first.copy(result = calculateSixthCpcPromotionOrMacp(
            first.result!!.oldPayInPayBand, first.result.oldGradePay, 4800, date(2008, Calendar.MAY, 1),
            "Promotion", SixthCpcFixationOption.FROM_DNI
        ))
        val mutation = replaceSixthCpcEvent(saved, first.localId, changedFirst)
        assertTrue(mutation.error ?: "Event edit failed", mutation.accepted)
        assertEquals(date(2008, Calendar.MAY, 1), mutation.state.events.first().result!!.eventDate)
        assertEquals(SixthCpcFixationOption.FROM_DNI, mutation.state.events.first().result!!.fixationOption)
        assertNotEquals(later.result!!.oldPayInPayBand, mutation.state.events.last().result!!.oldPayInPayBand)
        val converted = calculateSixthToSeventhCpc(
            mutation.replay!!.finalPosition.payInPayBand,
            mutation.replay.finalPosition.gradePay,
            bandForGradePay(mutation.replay.finalPosition.gradePay)
        )
        assertEquals(mutation.replay.finalPosition.basicPay, converted!!.existingPay)
    }

    @Test
    fun changedStartingPositionRecalculatesRestoredJourneyAndRejectsInvalidMapping() {
        val increments = listOf(inc(date(2006, Calendar.JULY, 1), 1))
        val savedEvent = event(date(2007, Calendar.APRIL, 1), 2, 4800)
        val originalState = state(increments = increments, events = listOf(savedEvent))
        val original = replaySixthCpcJourney(originalState)
        val changedStartingPosition = base.copy(gradePay = 4600, payInPayBand = 11000, dateMillis = date(2006, Calendar.FEBRUARY, 1))
        val changedMutation = replaySixthCpcJourneyFromStartingPosition(originalState, changedStartingPosition)
        assertTrue(changedMutation.accepted)
        val changed = changedMutation.replay!!
        assertNotEquals(original.finalPosition.basicPay, changed.finalPosition.basicPay)
        assertEquals(changed.finalPosition.basicPay, changed.timeline.last().position.basicPay)

        val pb3Event = event(date(2007, Calendar.APRIL, 1), 2, 6600)
        val pb3Mutation = replaySixthCpcJourneyFromStartingPosition(
            state(increments = increments, events = listOf(pb3Event)),
            base.copy(payBand = "PB-3", gradePay = 5400, payInPayBand = 16000, dateMillis = date(2006, Calendar.FEBRUARY, 1))
        )
        assertTrue(pb3Mutation.accepted)
        assertEquals(6600, pb3Mutation.replay!!.finalPosition.gradePay)
        assertTrue(pb3Mutation.replay.finalPosition.payBand.startsWith("PB-3"))
        assertTrue(runCatching {
            replaySixthCpcJourney(state(base = base.copy(gradePay = 4600, payBand = "PB-1"), events = listOf(savedEvent)))
        }.isFailure)
    }

    @Test
    fun sequenceAllocatorIncludesNestedIncrementSequences() {
        val chain = event(date(2008, Calendar.APRIL, 1), 2, 4600).copy(
            increments = listOf(SixthCpcEventIncrement(0, 0, date(2009, Calendar.JULY, 1), 8))
        )
        assertEquals(9, nextSixthCpcApplicationSequence(emptyList(), listOf(chain)))
    }

    @Test
    fun invalidBandGradePayOrPayInBandIsRejectedBeforeReplay() {
        assertTrue(runCatching { replaySixthCpcJourney(state(base = base.copy(payBand = "PB-1", gradePay = 4600))) }.isFailure)
        assertTrue(runCatching { replaySixthCpcJourney(state(base = base.copy(payInPayBand = 40000))) }.isFailure)
    }

    private fun event(date: Long, sequence: Int, targetGp: Int, payInBand: Int = 9300, currentGp: Int = 4200): SixthCpcEventChain {
        val result = calculateSixthCpcPromotionOrMacp(payInBand, currentGp, targetGp, date, "Promotion")
        return SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = result, sequence = sequence)
    }

    private fun inc(date: Long, sequence: Int) = SixthCpcHistoricalIncrement(0, 4200, date, sequence)
    private fun state(
        base: SixthCpcJourneyStartingPosition = this.base,
        increments: List<SixthCpcHistoricalIncrement> = emptyList(),
        events: List<SixthCpcEventChain> = emptyList(),
        sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
    ) = SixthCpcJourneyState(base, increments, events, sequenceIntegrity)

    private fun date(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear(); set(year, month, day, 0, 0, 0)
    }.timeInMillis
}
