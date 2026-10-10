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
