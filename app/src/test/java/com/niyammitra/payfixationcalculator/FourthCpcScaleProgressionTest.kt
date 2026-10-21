package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FourthCpcScaleProgressionTest {
    private val scales = FourthToFifthCpcData.scales
    private val s12Second = scales.single { it.grade == "S-12 (II)" }
    private val s14 = scales.single { it.grade == "S-14" }

    @Test
    fun everyFullyDocumentedScaleHasOrderedStagesAndValidMinimumMaximumBehavior() {
        val documentedScales = scales.filter { it.progression == FourthCpcScaleProgression.KNOWN_STAGES }
        assertTrue(documentedScales.isNotEmpty())
        assertEquals(scales.size, documentedScales.size + scales.count { it.progression == FourthCpcScaleProgression.RANGE_ONLY } + scales.count { it.progression == FourthCpcScaleProgression.PARTIALLY_KNOWN })

        documentedScales.forEach { scale ->
            val stages = scale.existingStages
            assertFalse("${scale.grade} should have stages", stages.isEmpty())
            assertEquals("${scale.grade} stages should be unique and ordered", stages.distinct().sorted(), stages)
            assertEquals("${scale.grade} should use the strict notation parser", parsePayScaleStages(scale.existingScale), stages)
            assertTrue(isKnownFourthCpcStage(stages.first(), scale))
            assertTrue(isKnownFourthCpcStage(stages.last(), scale))
            assertEquals(
                if (stages.size == 1) FourthCpcNextIncrementResolution.MaximumReached
                else FourthCpcNextIncrementResolution.Available(stages[1]),
                resolveFourthCpcNextIncrement(stages.first(), scale)
            )
            assertEquals(FourthCpcNextIncrementResolution.MaximumReached, resolveFourthCpcNextIncrement(stages.last(), scale))
        }
    }

    @Test
    fun parserRejectsRangeOnlyAndIncompleteTerminalNotationInsteadOfDroppingNumbers() {
        assertThrows(IllegalArgumentException::class.java) { parsePayScaleStages("2500-4000") }
        assertThrows(IllegalArgumentException::class.java) { parsePayScaleStages("2000-60-2300-75-3200-3500") }
    }

    @Test
    fun multiSegmentSchedulePreservesEachSegmentAndTerminalStage() {
        assertEquals(listOf(2000, 2060, 2120, 2180, 2240, 2300, 2375, 2450, 2525, 2600, 2675, 2750, 2825, 2900, 2975, 3050, 3125, 3200),
            parsePayScaleStages("2000-60-2300-75-3200"))
        assertEquals(3200, s12Second.existingStages.last())
        assertFalse(3500 in s12Second.existingStages)
    }

    @Test
    fun s12SecondReportsUnknownProgressionAfterDocumented3200Stage() {
        assertTrue(isKnownFourthCpcStage(3200, s12Second))
        assertFalse(isKnownFourthCpcStage(3500, s12Second))
        val result = resolveFourthCpcNextIncrement(3200, s12Second)
        assertTrue(result is FourthCpcNextIncrementResolution.ProgressionUnknown)
        assertTrue((result as FourthCpcNextIncrementResolution.ProgressionUnknown).message.contains("through ₹3200"))
        assertTrue(resolveFourthCpcNextIncrement(3300, s12Second) is FourthCpcNextIncrementResolution.ProgressionUnknown)
        assertTrue(resolveFourthCpcNextIncrement(2500, s12Second) is FourthCpcNextIncrementResolution.InvalidPosition)
        assertThrows(IllegalArgumentException::class.java) { calculateFourthToFifthCpc(3500, s12Second) }
        assertNotNull(calculateFourthToFifthCpc(3200, s12Second))
    }

    @Test
    fun s14KeepsItsDocumentedRangeButDoesNotInventStagesOrAllowConversion() {
        assertEquals(2500..4000, s14.declaredPayRange)
        assertTrue(s14.existingStages.isEmpty())
        assertTrue(resolveFourthCpcNextIncrement(3000, s14) is FourthCpcNextIncrementResolution.ProgressionUnknown)
        assertThrows(IllegalArgumentException::class.java) { calculateFourthToFifthCpc(2500, s14) }
        assertThrows(IllegalArgumentException::class.java) { calculateFourthToFifthCpcPrecise(2500, s14) }
    }

    @Test
    fun replayStopsAtUnknownIncrementWithoutAdvancingDateOrApplyingLaterEvent() {
        val target = scales.single { it.grade == "S-13 (I)" }
        val increments = listOf(
            FourthToFifthIncrementStep(pay = 777, date = 100L, sequence = 1),
            FourthToFifthIncrementStep(pay = 888, date = 200L, sequence = 2)
        )
        val laterEvent = FourthCpcEventSnapshot(1, 300L, CpcJourneyEventKind.PROMOTION, target.existingScale, 9999, 3)

        val result = recalculateFourthCpcTimeline(3125, s12Second, increments, listOf(laterEvent))

        assertEquals(3200, result.pay)
        assertEquals(s12Second, result.scale)
        assertEquals(100L, result.lastEffectiveDate)
        assertEquals(3200, result.increments[0].pay)
        assertEquals(888, result.increments[1].pay)
        assertEquals(9999, result.events.single().resultingPay)
        assertEquals(200L, result.failure?.dateMillis)
        assertEquals("increment", result.failure?.action)
        assertTrue(result.failure?.message.orEmpty().contains("unresolved"))
    }

    @Test
    fun eventPlacementRejectsUnknownSourceProgressionAndRangeOnlyTarget() {
        val s1 = scales.single { it.grade == "S-1" }
        val knownTarget = scales.single { it.grade == "S-13 (I)" }
        assertTrue(calculateFourthCpcEventPosition(3200, s12Second, knownTarget) is FourthCpcEventPositionResolution.Unresolved)
        assertTrue(calculateFourthCpcEventPosition(750, s1, s14) is FourthCpcEventPositionResolution.Unresolved)
        val known = calculateFourthCpcEventPosition(750, s1, knownTarget) as FourthCpcEventPositionResolution.Available
        assertEquals(knownTarget, known.position.scale)
        assertEquals(2375, known.position.pay)
    }

    @Test
    fun replayStopsAtRangeOnlyEventWithoutAdvancingItsDateOrLaterIncrement() {
        val s1 = scales.single { it.grade == "S-1" }
        val event = FourthCpcEventSnapshot(1, 100L, CpcJourneyEventKind.PROMOTION, s14.existingScale, 9999, 2)
        val increments = listOf(
            FourthToFifthIncrementStep(pay = 777, date = 50L, sequence = 1),
            FourthToFifthIncrementStep(pay = 888, date = 200L, sequence = 3)
        )

        val result = recalculateFourthCpcTimeline(750, s1, increments, listOf(event))

        assertEquals(762, result.pay)
        assertEquals(s1, result.scale)
        assertEquals(50L, result.lastEffectiveDate)
        assertEquals(762, result.increments[0].pay)
        assertEquals(888, result.increments[1].pay)
        assertEquals(9999, result.events.single().resultingPay)
        assertEquals(100L, result.failure?.dateMillis)
        assertEquals("event", result.failure?.action)
        assertTrue(result.failure?.message.orEmpty().contains("no verified"))
    }

    @Test
    fun oldStandaloneS14HistoryIsClearlyReportedAsSavedAndUnvalidated() {
        val record = CpcHistoryRecord(
            uniqueId = "old-s14",
            workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            savedAtMillis = 100L,
            startingCpc = CpcHistoryStage.FOURTH,
            currentStage = CpcHistoryStage.FIFTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FourthToFifth(s14.existingScale, 3000))
        )

        val report = PayJourneyReportBuilder.build(record)

        assertNotNull(report)
        assertTrue(report!!.validationNote.orEmpty().contains("without recalculating the conversion"))
        assertEquals(CpcHistoryStage.FOURTH, report.finalPosition?.cpc)
        assertTrue(report.sections.single().rows.single().description.contains("conversion not recalculated"))
    }

    @Test
    fun completeJourneyReportFlagsStoredValuesForScalesWithUnresolvedProgression() {
        val snapshot = CompleteJourneySnapshot(
            startingCpc = CpcHistoryStage.FOURTH,
            startingDateMillis = 50L,
            fourth = FourthCpcJourneySnapshot(s14.existingScale, s14.grade, 3000, 50L, null)
        )
        val record = CpcHistoryRecord(
            uniqueId = "old-complete-s14",
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = 100L,
            startingCpc = CpcHistoryStage.FOURTH,
            currentStage = CpcHistoryStage.FOURTH,
            payload = CompleteJourneyPayload(snapshot)
        )

        val report = PayJourneyReportBuilder.build(record)

        assertTrue(report?.validationNote.orEmpty().contains("not validated against a complete increment schedule"))
        val encoded = CpcHistoryCodec.encodeAll(listOf(record))
        assertFalse(encoded.contains("progression"))
        assertEquals(s14.existingScale, (CpcHistoryCodec.decodeAll(encoded).single().payload as CompleteJourneyPayload).snapshot.fourth?.scaleId)
    }
}
