package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FifthCpcJourneyReplayTest {
    private val startDate = date(1988, Calendar.JANUARY, 1)
    private val dni = date(1988, Calendar.JULY, 1)
    private val feeder = FifthToSixthCpcData.scales.first { parseFifthCpcScaleStages(it.title).size > 2 }
    private val target = FifthToSixthCpcData.scales.first { it.title != feeder.title }
    private val startingPay = parseFifthCpcScaleStages(feeder.title).first()

    @Test
    fun deletingFirstMiddleOrLastIncrementReplaysRemainingPayAndDni() {
        val source = listOf(
            FifthCpcHistoricalIncrementStep(0, date(1988, Calendar.JULY, 1), 1),
            FifthCpcHistoricalIncrementStep(0, date(1989, Calendar.JULY, 1), 2),
            FifthCpcHistoricalIncrementStep(0, date(1990, Calendar.JULY, 1), 3)
        )
        listOf(0, 1, 2).forEach { deleted ->
            val remaining = source.filterIndexed { index, _ -> index != deleted }
            val replay = replay(remaining, emptyList())
            val expectedPay = generateSequence(startingPay) { calculateNextFifthCpcStage(it, feeder.title) }
                .take(remaining.size + 1).last()
            assertEquals(expectedPay, replay.pay)
            assertEquals(addFifthHistoricalYear(replay.increments.lastOrNull()?.date ?: dni), replay.dni)
        }
    }

    @Test
    fun incrementEventIncrementJourneyReplaysInEffectiveDateOrderAndFeedsSixthFixation() {
        val firstIncrement = FifthCpcHistoricalIncrementStep(0, dni, 1)
        val event = event(eventDate = date(1989, Calendar.JANUARY, 15), implementationDate = date(1989, Calendar.JANUARY, 15), sequence = 2)
        val laterIncrement = FifthCpcHistoricalIncrementStep(0, date(1989, Calendar.JULY, 1), 3)
        val replay = replay(listOf(firstIncrement, laterIncrement), listOf(event))

        assertTrue(replay.increments.first().date < replay.events.single().implementationDate)
        assertTrue(replay.events.single().implementationDate < replay.increments.last().date)
        assertEquals(calculateNextFifthCpcStage(replay.events.single().pay, target.title), replay.increments.last().pay)
        assertEquals(addFifthHistoricalYear(replay.increments.last().date), replay.dni)
        assertEquals(replay.pay, calculateFifthToSixthCpc(replay.pay, replay.scale).existingBasicPay)
    }

    @Test
    fun replayUsesSharedSequenceForSameDateIncrementAndEvent() {
        val increment = FifthCpcHistoricalIncrementStep(0, dni, 1)
        val event = event(eventDate = dni, implementationDate = dni, sequence = 2)
        val incrementThenEvent = replay(listOf(increment), listOf(event), CpcSequenceIntegrity.ORIGINAL)
        val eventThenIncrement = replay(
            listOf(increment.copy(sequence = 2)), listOf(event.copy(sequence = 1)), CpcSequenceIntegrity.ORIGINAL
        )
        assertNotEquals(incrementThenEvent.events.single().pay, eventThenIncrement.events.single().pay)
        assertEquals(1, eventThenIncrement.events.single().sequence)
    }

    @Test
    fun eventAcceptedThroughStateUpdaterRetainsFixationInputsAndSequence() {
        val accepted = event(dni, dni, 0).copy(
            type = "Scale Upgradation",
            placementMethod = "Next Higher Without Increment",
            implementationOption = "From DNI"
        )
        val rows = upsertFifthCpcHistoricalEvent(emptyList(), accepted, editingIdentity = null, nextSequence = 7)

        assertEquals(1, rows.size)
        assertEquals("Scale Upgradation", rows.single().type)
        assertEquals(target.title, rows.single().scale)
        assertEquals("Next Higher Without Increment", rows.single().placementMethod)
        assertEquals("From DNI", rows.single().implementationOption)
        assertEquals(7, rows.single().sequence)

        val replay = replay(emptyList(), rows)
        assertEquals("From DNI", replay.events.single().implementationOption)
        assertEquals(dni, replay.events.single().implementationDate)
        val expected = calculateFifthCpcEventFixation(
            startingPay, feeder, target, "Scale Upgradation", "Next Higher Without Increment",
            "From DNI", dni, dni, null
        ).fixedPay
        assertEquals(expected, replay.events.single().pay)
    }

    @Test
    fun fromDniEventCannotBeImplementedBeforeItsEventDate() {
        val eventDate = date(1988, Calendar.AUGUST, 1)
        val earlierDni = date(1988, Calendar.JULY, 1)
        val fromDni = event(eventDate, earlierDni, 1).copy(implementationOption = "From DNI")

        val replay = replay(emptyList(), listOf(fromDni))

        assertEquals(eventDate, replay.events.single().implementationDate)
        assertTrue(replay.events.single().implementationDate >= replay.events.single().eventDate)
        assertEquals(calculateNextFifthCpcDni(eventDate), replay.events.single().dniDate)
    }

    @Test
    fun editingEventThroughStateUpdaterPersistsFieldsWithoutDuplicateAndReplaysFollowingIncrement() {
        val thirdScale = FifthToSixthCpcData.scales.first { it.title != feeder.title && it.title != target.title }
        val old = event(dni, dni, 4)
        val unrelated = event(date(1988, Calendar.MAY, 15), date(1988, Calendar.MAY, 15), 5)
        val edited = old.copy(
            eventDate = date(1988, Calendar.AUGUST, 1),
            implementationDate = date(1988, Calendar.AUGUST, 1),
            scale = thirdScale.title,
            implementationOption = "From Event Date",
            placementMethod = "Next Higher Without Increment"
        )
        val rows = upsertFifthCpcHistoricalEvent(listOf(old, unrelated), edited, editingIdentity = old.eventIdentity, nextSequence = 9)
        assertEquals(2, rows.size)
        assertEquals(4, rows[0].sequence)
        assertEquals(unrelated, rows[1])
        assertEquals(date(1988, Calendar.AUGUST, 1), rows[0].eventDate)
        assertEquals(thirdScale.title, rows[0].scale)
        assertEquals("Next Higher Without Increment", rows[0].placementMethod)

        val laterIncrement = FifthCpcHistoricalIncrementStep(0, date(1989, Calendar.JULY, 1), 6)
        val replay = replay(listOf(laterIncrement), rows)
        assertEquals(thirdScale.title, replay.scale.title)
        assertEquals(calculateNextFifthCpcStage(replay.events.first().pay, thirdScale.title), replay.increments.single().pay)
        assertEquals(calculateEventBasedFifthCpcDni(edited.eventDate), replay.increments.single().date)
    }

    @Test
    fun editingBAfterDeletingEarlierAUpdatesBExactlyOnce() {
        val a = event(date(1988, Calendar.MARCH, 1), date(1988, Calendar.MARCH, 1), 1)
        val b = event(date(1988, Calendar.MAY, 1), date(1988, Calendar.MAY, 1), 2)
        val remaining = deleteFifthCpcHistoricalEvent(listOf(a, b), a.eventIdentity)
        val editedB = b.copy(type = "ACP", pay = b.pay + 100, scale = target.title)

        val rows = upsertFifthCpcHistoricalEvent(remaining, editedB, b.eventIdentity, nextSequence = 3)

        assertEquals(1, rows.size)
        assertEquals(b.eventIdentity, rows.single().eventIdentity)
        assertEquals(2, rows.single().sequence)
        assertEquals("ACP", rows.single().type)
        assertEquals(b.pay + 100, rows.single().pay)
    }

    @Test
    fun savingStaleEditorAfterDeletingItsTargetDoesNotAppend() {
        val a = event(date(1988, Calendar.MARCH, 1), date(1988, Calendar.MARCH, 1), 1)
        val b = event(date(1988, Calendar.MAY, 1), date(1988, Calendar.MAY, 1), 2)
        val remaining = deleteFifthCpcHistoricalEvent(listOf(a, b), b.eventIdentity)

        val rows = upsertFifthCpcHistoricalEvent(remaining, b.copy(pay = b.pay + 100), b.eventIdentity, nextSequence = 3)

        assertEquals(listOf(a), rows)
    }

    @Test
    fun editingBAfterDeletingUnrelatedLaterEventStillUpdatesB() {
        val b = event(date(1988, Calendar.MARCH, 1), date(1988, Calendar.MARCH, 1), 1)
        val later = event(date(1988, Calendar.MAY, 1), date(1988, Calendar.MAY, 1), 2)
        val remaining = deleteFifthCpcHistoricalEvent(listOf(b, later), later.eventIdentity)
        val editedB = b.copy(implementationOption = "From DNI", placementMethod = "Next Higher Without Increment")

        val rows = upsertFifthCpcHistoricalEvent(remaining, editedB, b.eventIdentity, nextSequence = 3)

        assertEquals(1, rows.size)
        assertEquals(b.eventIdentity, rows.single().eventIdentity)
        assertEquals(1, rows.single().sequence)
        assertEquals("From DNI", rows.single().implementationOption)
        assertEquals("Next Higher Without Increment", rows.single().placementMethod)
    }

    @Test
    fun earlierEventRecalculatesLaterFromDniEventAndIncrementDeterministically() {
        val secondScale = FifthToSixthCpcData.scales.first { it.title != feeder.title && it.title != target.title }
        val first = event(date(1988, Calendar.MARCH, 15), date(1988, Calendar.MARCH, 15), 1)
        val laterFromDni = event(date(1988, Calendar.JUNE, 1), dni, 2).copy(
            scale = secondScale.title,
            implementationOption = "From DNI"
        )
        val pendingIncrement = FifthCpcHistoricalIncrementStep(0, dni, 3)
        val replay = replay(listOf(pendingIncrement), listOf(first, laterFromDni))

        val firstDni = calculateEventBasedFifthCpcDni(first.eventDate)
        val secondDni = calculateNextFifthCpcDni(firstDni)
        assertEquals(first.eventDate, replay.events[0].implementationDate)
        assertEquals(firstDni, replay.events[0].dniDate)
        assertEquals("From DNI", replay.events[1].implementationOption)
        assertEquals(firstDni, replay.events[1].implementationDate)
        assertEquals(secondDni, replay.increments.single().date)
        assertEquals(calculateNextFifthCpcStage(replay.events[1].pay, secondScale.title), replay.increments.single().pay)
        assertEquals(addFifthHistoricalYear(secondDni), replay.dni)
    }

    @Test
    fun invalidatingFifthMutationClearsRestoredAndCalculatedSixthContinuation() {
        val sixth = SixthCpcJourneySnapshot(startDate, "PB-1", 1900, 5200)
        val state = FifthCpcContinuationState(sixth, sixth, visible = true)

        assertEquals(FifthCpcContinuationState(null, null, visible = false), invalidateFifthCpcContinuation(state))
    }

    @Test
    fun inferredSameDateFallbackKeepsExistingEventBeforeIncrementOrder() {
        val replay = replay(
            listOf(FifthCpcHistoricalIncrementStep(0, dni, 1)),
            listOf(event(eventDate = dni, implementationDate = dni, sequence = 9)),
            CpcSequenceIntegrity.INFERRED
        )
        assertEquals(date(1989, Calendar.JULY, 1), replay.increments.single().date)
        assertEquals(dni, replay.events.single().implementationDate)
    }

    @Test
    fun deletingAnEarlierEventReplaysLaterEventAndPreservesOtherEvents() {
        val first = event(eventDate = dni, implementationDate = dni, sequence = 1)
        val second = event(eventDate = date(1990, Calendar.JANUARY, 1), implementationDate = date(1990, Calendar.JANUARY, 1), sequence = 2)
        val before = replay(emptyList(), listOf(first, second))
        val afterDelete = replay(emptyList(), listOf(second))
        assertEquals(1, afterDelete.events.size)
        assertEquals(second.eventDate, afterDelete.events.single().eventDate)
        assertNotEquals(before.events.last().pay, afterDelete.events.single().pay)
        assertEquals(target.title, afterDelete.scale.title)
    }

    @Test
    fun deletingFirstMiddleOrLastEventReplaysEveryRetainedEventAndDni() {
        val source = listOf(
            event(eventDate = dni, implementationDate = dni, sequence = 1),
            event(eventDate = date(1989, Calendar.JANUARY, 15), implementationDate = date(1989, Calendar.JANUARY, 15), sequence = 2),
            event(eventDate = date(1990, Calendar.JANUARY, 15), implementationDate = date(1990, Calendar.JANUARY, 15), sequence = 3)
        )
        listOf(0, 1, 2).forEach { deleted ->
            val retained = source.filterIndexed { index, _ -> index != deleted }
            val replay = replay(emptyList(), retained)
            assertEquals(retained.map { it.sequence }, replay.events.map { it.sequence })
            assertEquals(replay.events.last().pay, replay.pay)
            assertEquals(calculateEventBasedFifthCpcDni(replay.events.last().eventDate), replay.dni)
        }
    }

    @Test
    fun editingEventDateAndScaleReplaysTheEditedEventAndFollowingPosition() {
        val original = event(eventDate = dni, implementationDate = dni, sequence = 1)
        val edited = original.copy(
            eventDate = date(1989, Calendar.JANUARY, 15),
            implementationDate = date(1989, Calendar.JANUARY, 15),
            scale = FifthToSixthCpcData.scales.last().title
        )
        val replay = replay(emptyList(), listOf(edited))
        assertEquals(edited.eventDate, replay.events.single().eventDate)
        assertEquals(edited.eventDate, replay.events.single().implementationDate)
        assertEquals(edited.scale, replay.scale.title)
        assertEquals(replay.events.single().pay, replay.pay)
    }

    @Test
    fun replayedRowsAndSixthInputUseTheRecalculatedFinalPosition() {
        val event = event(eventDate = dni, implementationDate = dni, sequence = 1)
        val steps = listOf(FifthCpcHistoricalIncrementStep(0, date(1989, Calendar.JULY, 1), 2))
        val replay = replay(steps, listOf(event))
        val sixthInput = replay.omAdjustment?.adjustedBasicPay ?: replay.pay
        assertEquals(replay.pay, sixthInput)
        assertEquals(replay.increments.single().pay, replay.pay)
        assertNull(replay.omAdjustment)
    }

    @Test
    fun reportUsesTheSameDeterministicInferredTieOrderAsTheScreen() {
        val event = event(eventDate = dni, implementationDate = dni, sequence = 10)
        val increment = FifthCpcHistoricalIncrementStep(0, dni, 1)
        val replay = replay(listOf(increment), listOf(event), CpcSequenceIntegrity.INFERRED)
        val snapshot = FifthCpcJourneySnapshot(
            startDate, feeder.title, startingPay, dni,
            increments = listOf(CpcIncrementSnapshot(1, replay.increments.single().pay, dni, 1)),
            events = replay.events.mapIndexed { index, item ->
                FifthCpcEventSnapshot(index + 1, CpcJourneyEventKind.PROMOTION, item.eventDate, item.implementationDate,
                    item.scale, item.pay, item.dniDate, CpcFixationBasis.EVENT_DATE, item.placementMethod,
                    item.implementationOption, item.sequence, item.omAppliedBeforeEvent)
            }
        )
        val record = CpcHistoryRecord(
            uniqueId = "fifth-order", workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = startDate, startingCpc = CpcHistoryStage.FIFTH, currentStage = CpcHistoryStage.FIFTH,
            title = "5th CPC", payload = CompleteJourneyPayload(CompleteJourneySnapshot(
                CpcHistoryStage.FIFTH, startDate, fifth = snapshot, sequenceIntegrity = CpcSequenceIntegrity.INFERRED
            ))
        )
        val report = PayJourneyReportBuilder.build(record)!!
        val rows = report.sections.single().rows.filter { it.dateMillis == dni }
        assertEquals(CpcJourneyEventKind.PROMOTION, rows.first().kind)
        assertEquals(CpcJourneyEventKind.ANNUAL_INCREMENT, rows.last().kind)
        assertEquals(snapshot.increments.single().pay, report.finalPosition?.basicPay)
        assertEquals(addFifthHistoricalYear(dni), report.finalDniMillis)
    }

    @Test
    fun reportAttributesEachIncrementAndFinalPositionToTheScaleActiveAtThatDate() {
        val firstIncrement = FifthCpcHistoricalIncrementStep(0, dni, 1)
        val promotion = event(date(1989, Calendar.JANUARY, 15), date(1989, Calendar.JANUARY, 15), 2)
        val laterIncrement = FifthCpcHistoricalIncrementStep(0, date(1989, Calendar.JULY, 1), 3)
        val replay = replay(listOf(firstIncrement, laterIncrement), listOf(promotion))
        val snapshot = FifthCpcJourneySnapshot(
            startDate, feeder.title, startingPay, dni,
            increments = replay.increments.mapIndexed { index, step ->
                CpcIncrementSnapshot(index + 1, step.pay, step.date, step.sequence)
            },
            events = replay.events.mapIndexed { index, item ->
                FifthCpcEventSnapshot(index + 1, CpcJourneyEventKind.PROMOTION, item.eventDate,
                    item.implementationDate, item.scale, item.pay, item.dniDate,
                    CpcFixationBasis.EVENT_DATE, item.placementMethod, item.implementationOption,
                    item.sequence, item.omAppliedBeforeEvent)
            }
        )
        val record = CpcHistoryRecord(
            uniqueId = "fifth-scale-attribution", workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = startDate, startingCpc = CpcHistoryStage.FIFTH, currentStage = CpcHistoryStage.FIFTH,
            title = "5th CPC", payload = CompleteJourneyPayload(CompleteJourneySnapshot(
                CpcHistoryStage.FIFTH, startDate, fifth = snapshot, sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
            ))
        )

        val report = PayJourneyReportBuilder.build(record)!!
        val rows = report.sections.single().rows
        val starting = rows.first { it.kind == CpcJourneyEventKind.STARTING_POSITION }
        val increments = rows.filter { it.kind == CpcJourneyEventKind.ANNUAL_INCREMENT }

        assertEquals(feeder.title, starting.position.scaleId)
        assertEquals(feeder.title, increments.first().position.scaleId)
        assertEquals(target.title, increments.last().position.scaleId)
        assertEquals(target.title, report.finalPosition?.scaleId)
        assertEquals(replay.pay, report.finalPosition?.basicPay)
    }

    @Test
    fun promotionFromDniCoincidingWithDniAppliesTwoIncrementBenefitAndAdvancesToNextYear() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val promotedScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }
        val startDate = date(2002, Calendar.FEBRUARY, 1)
        val dniDate = date(2003, Calendar.FEBRUARY, 1)
        val promotionDate = date(2003, Calendar.FEBRUARY, 1)
        val startingPay = 6_050

        // 1. Direct fixation calculation verification
        val fixation = calculateFifthCpcEventFixation(
            currentPay = startingPay,
            currentScale = feederScale,
            targetScale = promotedScale,
            eventType = "Promotion",
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            eventDate = promotionDate,
            currentDni = dniDate,
            omAdjustment = null
        )

        assertEquals(6_050, fixation.upstreamPay)
        assertEquals(6_200, fixation.feederIncrementedPay)
        assertEquals(6_350, fixation.secondFeederIncrementedPay)
        assertEquals(6_350, fixation.placementBasePay)
        assertEquals(6_375, fixation.fixedPay)

        // 2. Journey replay verification
        val event = FifthCpcHistoricalEventStep(
            type = "Promotion",
            scale = promotedScale.title,
            pay = startingPay,
            eventDate = promotionDate,
            implementationDate = promotionDate,
            dniDate = date(2004, Calendar.FEBRUARY, 1),
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            sequence = 1
        )
        val replay = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = emptyList(),
            events = listOf(event),
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )

        assertEquals(6_375, replay.pay)
        assertEquals(promotedScale.title, replay.scale.title)
        assertEquals(promotionDate, replay.date)
        assertEquals(date(2004, Calendar.FEBRUARY, 1), replay.dni)
        assertEquals(promotionDate, replay.events.single().implementationDate)
        assertEquals(6_375, replay.events.single().pay)
        assertEquals(date(2004, Calendar.FEBRUARY, 1), replay.events.single().dniDate)

        // 3. Subsequent annual increment on 01.02.2004
        val subsequentIncrement = FifthCpcHistoricalIncrementStep(
            pay = 0,
            date = date(2004, Calendar.FEBRUARY, 1),
            sequence = 2
        )
        val replayWithIncrement = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = listOf(subsequentIncrement),
            events = listOf(event),
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )

        assertEquals(6_550, replayWithIncrement.pay)
        assertEquals(promotedScale.title, replayWithIncrement.scale.title)
        assertEquals(date(2004, Calendar.FEBRUARY, 1), replayWithIncrement.date)
        assertEquals(date(2005, Calendar.FEBRUARY, 1), replayWithIncrement.dni)
        assertEquals(6_550, replayWithIncrement.increments.single().pay)
        assertEquals(date(2004, Calendar.FEBRUARY, 1), replayWithIncrement.increments.single().date)
    }

    @Test
    fun promotionFromDniPrecedingDniAppliesTwoIncrementBenefitOnDni() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val promotedScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }
        val startDate = date(2002, Calendar.FEBRUARY, 1)
        val dniDate = date(2003, Calendar.FEBRUARY, 1)
        val promotionDate = date(2002, Calendar.AUGUST, 15)
        val startingPay = 6_050

        val fixation = calculateFifthCpcEventFixation(
            currentPay = startingPay,
            currentScale = feederScale,
            targetScale = promotedScale,
            eventType = "Promotion",
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            eventDate = promotionDate,
            currentDni = dniDate,
            omAdjustment = null
        )

        assertEquals(6_200, fixation.feederIncrementedPay)
        assertEquals(6_350, fixation.secondFeederIncrementedPay)
        assertEquals(6_375, fixation.fixedPay)

        val event = FifthCpcHistoricalEventStep(
            type = "Promotion",
            scale = promotedScale.title,
            pay = startingPay,
            eventDate = promotionDate,
            implementationDate = dniDate,
            dniDate = date(2004, Calendar.FEBRUARY, 1),
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            sequence = 1
        )
        val replay = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = emptyList(),
            events = listOf(event),
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )

        assertEquals(6_375, replay.pay)
        assertEquals(dniDate, replay.date)
        assertEquals(date(2004, Calendar.FEBRUARY, 1), replay.dni)
    }

    @Test
    fun acpFromDniAppliesTwoIncrementBenefitUnderFr221a1() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val targetScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }
        val fixation = calculateFifthCpcEventFixation(
            currentPay = 6_050,
            currentScale = feederScale,
            targetScale = targetScale,
            eventType = "ACP",
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            eventDate = date(2003, Calendar.FEBRUARY, 1),
            currentDni = date(2003, Calendar.FEBRUARY, 1),
            omAdjustment = null
        )

        assertEquals(6_200, fixation.feederIncrementedPay)
        assertEquals(6_350, fixation.secondFeederIncrementedPay)
        assertEquals(6_375, fixation.fixedPay)
    }

    @Test
    fun scaleUpgradationFromDniDoesNotApplyTwoIncrementBenefit() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val targetScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }

        val withoutIncrement = calculateFifthCpcEventFixation(
            currentPay = 6_050,
            currentScale = feederScale,
            targetScale = targetScale,
            eventType = "Scale Upgradation",
            placementMethod = "Next Higher Without Increment",
            implementationOption = "From DNI",
            eventDate = date(2003, Calendar.FEBRUARY, 1),
            currentDni = date(2003, Calendar.FEBRUARY, 1),
            omAdjustment = null
        )
        assertNull(withoutIncrement.secondFeederIncrementedPay)
        assertEquals(6_050, withoutIncrement.placementBasePay)
        assertEquals(6_200, withoutIncrement.fixedPay)

        val afterIncrement = calculateFifthCpcEventFixation(
            currentPay = 6_050,
            currentScale = feederScale,
            targetScale = targetScale,
            eventType = "Scale Upgradation",
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            eventDate = date(2003, Calendar.FEBRUARY, 1),
            currentDni = date(2003, Calendar.FEBRUARY, 1),
            omAdjustment = null
        )
        assertNull(afterIncrement.secondFeederIncrementedPay)
        assertEquals(6_200, afterIncrement.placementBasePay)
        assertEquals(6_200, afterIncrement.fixedPay)
    }

    @Test
    fun promotionFromEventDatePreservesSingleIncrementFixation() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val promotedScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }
        val fixation = calculateFifthCpcEventFixation(
            currentPay = 6_050,
            currentScale = feederScale,
            targetScale = promotedScale,
            eventType = "Promotion",
            placementMethod = "Next Higher After Increment",
            implementationOption = "From Event Date",
            eventDate = date(2003, Calendar.FEBRUARY, 1),
            currentDni = date(2003, Calendar.FEBRUARY, 1),
            omAdjustment = null
        )

        assertEquals(6_200, fixation.feederIncrementedPay)
        assertNull(fixation.secondFeederIncrementedPay)
        assertEquals(6_200, fixation.placementBasePay)
        assertEquals(6_200, fixation.fixedPay)
    }

    @Test
    fun editingAndDeletingFromDniPromotionReplaysCorrectly() {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 5000-150-8000" }
        val firstTarget = FifthToSixthCpcData.scales.first { it.title == "Rs. 5500-175-9000" }
        val secondTarget = FifthToSixthCpcData.scales.first { it.title == "Rs. 6500-200-10500" }
        val startDate = date(2002, Calendar.FEBRUARY, 1)
        val dniDate = date(2003, Calendar.FEBRUARY, 1)
        val startingPay = 6_050

        val originalEvent = FifthCpcHistoricalEventStep(
            type = "Promotion",
            scale = firstTarget.title,
            pay = 6_375,
            eventDate = dniDate,
            implementationDate = dniDate,
            dniDate = date(2004, Calendar.FEBRUARY, 1),
            placementMethod = "Next Higher After Increment",
            implementationOption = "From DNI",
            sequence = 1
        )
        val replayOriginal = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = emptyList(),
            events = listOf(originalEvent),
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )
        assertEquals(6_375, replayOriginal.pay)

        // Edit to higher scale Rs. 6500-200-10500
        val editedEvent = originalEvent.copy(scale = secondTarget.title)
        val rows = upsertFifthCpcHistoricalEvent(listOf(originalEvent), editedEvent, originalEvent.eventIdentity, 2)
        val replayEdited = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = emptyList(),
            events = rows,
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )
        // In Rs. 6500-200-10500, minimum is 6500, which is >= 6350, so fixed pay is 6500
        assertEquals(6_500, replayEdited.pay)
        assertEquals(secondTarget.title, replayEdited.scale.title)

        // Delete event and verify replay reverts to feeder scale
        val remaining = deleteFifthCpcHistoricalEvent(rows, originalEvent.eventIdentity)
        val replayDeleted = replayFifthCpcJourney(
            initialPay = startingPay,
            initialScale = feederScale,
            initialDate = startDate,
            initialDni = dniDate,
            increments = emptyList(),
            events = remaining,
            sequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
        )
        assertEquals(6_050, replayDeleted.pay)
        assertEquals(feederScale.title, replayDeleted.scale.title)
        assertEquals(dniDate, replayDeleted.dni)
    }

    private fun replay(
        increments: List<FifthCpcHistoricalIncrementStep>,
        events: List<FifthCpcHistoricalEventStep>,
        integrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
    ) = replayFifthCpcJourney(startingPay, feeder, startDate, dni, increments, events, integrity)

    private fun event(eventDate: Long, implementationDate: Long, sequence: Int) = FifthCpcHistoricalEventStep(
        type = "Promotion", scale = target.title, pay = startingPay, eventDate = eventDate,
        implementationDate = implementationDate, dniDate = date(1989, Calendar.JULY, 1),
        placementMethod = "Next Higher After Increment", implementationOption = "From Event Date", sequence = sequence
    )

    private fun date(year: Int, month: Int, day: Int) = Calendar.getInstance().apply {
        clear(); set(year, month, day, 0, 0, 0)
    }.timeInMillis
}
