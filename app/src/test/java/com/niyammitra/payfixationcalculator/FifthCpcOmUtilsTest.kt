package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.util.Calendar

class FifthCpcOmUtilsTest {
    private fun date(month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2006, month, day, 0, 0, 0)
    }.timeInMillis

    private fun omDate(month: Int, day: Int): Long = date(month, day)

    private fun omEligibleJourney(integrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL): CpcHistoryRecord {
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val adjustment = calculateFifthCpcOmAdjustment(date(Calendar.APRIL, 15), 8_300, scale)!!
        val fifth = FifthCpcJourneySnapshot(
            startingDateMillis = date(Calendar.JANUARY, 1),
            scaleId = scale.title,
            startingBasicPay = 8_300,
            startingDniMillis = date(Calendar.APRIL, 15),
            omAdjustment = adjustment
        )
        val snapshot = assignJourneyApplicationSequence(CompleteJourneySnapshot(
            startingCpc = CpcHistoryStage.FIFTH,
            startingDateMillis = date(Calendar.JANUARY, 1),
            fifth = fifth,
            sequenceIntegrity = integrity
        ))
        return CpcHistoryRecord(
            uniqueId = "om-complete",
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = date(Calendar.APRIL, 16),
            startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.FIFTH,
            payload = CompleteJourneyPayload(snapshot)
        )
    }

    @Test fun eligibilityUsesInclusiveFebruaryThroughJuneDates() {
        assertFalse(isEligibleForFifthCpcOmAdjustment(date(Calendar.JANUARY, 31)))
        assertTrue(isEligibleForFifthCpcOmAdjustment(date(Calendar.FEBRUARY, 1)))
        assertTrue(isEligibleForFifthCpcOmAdjustment(date(Calendar.APRIL, 15)))
        assertTrue(isEligibleForFifthCpcOmAdjustment(date(Calendar.JUNE, 30)))
        assertFalse(isEligibleForFifthCpcOmAdjustment(date(Calendar.JULY, 1)))
    }

    @Test fun selectedScaleStageDeterminesOneTimeIncrementAndAdjustedSixthInput() {
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val adjustment = calculateFifthCpcOmAdjustment(date(Calendar.APRIL, 15), 8_300, scale)!!

        assertEquals(8_300, adjustment.basicPayBefore)
        assertEquals(275, adjustment.incrementAmount)
        assertEquals(8_575, adjustment.adjustedBasicPay)
        assertEquals(omDate(Calendar.JANUARY, 1), adjustment.incrementDateMillis)
        assertEquals(omDate(Calendar.JULY, 1), adjustment.nextRevisedIncrementDateMillis)

        val result = calculateFifthToSixthCpc(adjustment.adjustedBasicPay, scale)
        assertEquals(8_575, result.existingBasicPay)
        assertEquals("01 July 2006", result.nextIncrementDate)
    }

    @Test fun nonEligibleDniLeavesFifthCpcPayUnchangedForSixthFixation() {
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        assertNull(calculateFifthCpcOmAdjustment(date(Calendar.JULY, 1), 8_300, scale))
        val result = calculateFifthToSixthCpc(8_300, scale)
        assertEquals(8_300, result.existingBasicPay)
    }

    @Test fun completeJourneyOmDataSurvivesRestoreResaveWithoutChangingSequenceIntegrity() {
        val original = omEligibleJourney()
        val originalSnapshot = (original.payload as CompleteJourneyPayload).snapshot
        val originalFifth = originalSnapshot.fifth!!
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(original))!!
        val restoredSnapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.ORIGINAL, restoredSnapshot.sequenceIntegrity)
        assertEquals(originalFifth.omAdjustment, restoredSnapshot.fifth!!.omAdjustment)
        assertEquals(originalSnapshot.fifth!!.sequence, restoredSnapshot.fifth!!.sequence)

        val resaved = restored.copy(payload = CompleteJourneyPayload(assignJourneyApplicationSequence(restoredSnapshot)))
        val roundTrip = CpcHistoryStore.decode(CpcHistoryStore.encode(resaved))!!
        val roundTripSnapshot = (roundTrip.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.ORIGINAL, roundTripSnapshot.sequenceIntegrity)
        assertEquals(originalFifth.omAdjustment, roundTripSnapshot.fifth!!.omAdjustment)
        assertEquals(originalFifth.sequence, roundTripSnapshot.fifth!!.sequence)
    }

    @Test fun omFieldDoesNotUpgradeAnInferredJourney() {
        val record = omEligibleJourney(CpcSequenceIntegrity.INFERRED)
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.INFERRED, snapshot.sequenceIntegrity)
        assertEquals(
            (record.payload as CompleteJourneyPayload).snapshot.fifth!!.omAdjustment,
            snapshot.fifth!!.omAdjustment
        )
    }

    @Test fun completeJourneyReportShowsTypedOmEventAndUsesAdjustedFifthPay() {
        val record = omEligibleJourney()
        val report = PayJourneyReportBuilder.build(record)!!
        val omRow = report.sections.flatMap { it.rows }.single { it.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT }
        assertEquals(omDate(Calendar.JANUARY, 1), omRow.dateMillis)
        assertEquals(date(Calendar.APRIL, 15), omRow.dniMillis)
        assertEquals(8_300, omRow.sourcePosition!!.basicPay)
        assertEquals(8_575, omRow.position.basicPay)
        assertEquals(8_575, report.finalPosition!!.basicPay)
        assertTrue(omRow.description.contains("O.M. dated 19 March 2012"))
        assertTrue(omRow.remarks!!.contains("next revised-pay increment"))
    }

    @Test fun legacyStandaloneFifthToSixthRecordDisplaysOldResultWithoutOmInference() {
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val old = CpcHistoryRecord(
            uniqueId = "legacy-standalone-5-6",
            workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            savedAtMillis = date(Calendar.MAY, 1),
            startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.SIXTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FifthToSixth(scale.title, 8_300))
        )
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(old))!!
        val restoredSnapshot = (restored.payload as StandaloneConversionPayload).snapshot as StandaloneCpcConversionSnapshot.FifthToSixth
        assertNull(restoredSnapshot.normalDniMillis)
        assertNull(restoredSnapshot.omAdjustment)
        val report = PayJourneyReportBuilder.build(restored)!!
        assertEquals(1, report.sections.single().rows.size)
        assertEquals(8_300, report.sections.single().rows.single().sourcePosition!!.basicPay)
        assertEquals(calculateFifthToSixthCpc(8_300, scale).revisedBasicPay, report.finalPosition!!.basicPay)
    }

    @Test fun standaloneOmAdjustmentIsSavedReportedAndNotAppliedTwice() {
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val adjustment = calculateFifthCpcOmAdjustment(date(Calendar.APRIL, 15), 8_300, scale)!!
        val record = CpcHistoryRecord(
            uniqueId = "standalone-om",
            workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            savedAtMillis = date(Calendar.APRIL, 16),
            startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.SIXTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FifthToSixth(
                scale.title, 8_300, date(Calendar.APRIL, 15), adjustment
            ))
        )
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val restoredSnapshot = (restored.payload as StandaloneConversionPayload).snapshot as StandaloneCpcConversionSnapshot.FifthToSixth
        assertEquals(adjustment, restoredSnapshot.omAdjustment)
        assertEquals(8_300, restoredSnapshot.existingBasicPay)

        val report = PayJourneyReportBuilder.build(restored)!!
        val rows = report.sections.single().rows
        assertEquals(CpcJourneyEventKind.OM_SPECIAL_INCREMENT, rows.first().kind)
        assertEquals(8_300, rows.first().sourcePosition!!.basicPay)
        assertEquals(8_575, rows.first().position.basicPay)
        assertEquals(8_575, rows.last().sourcePosition!!.basicPay)
        assertEquals(calculateFifthToSixthCpc(8_575, scale).revisedBasicPay, report.finalPosition!!.basicPay)
        assertEquals(omDate(Calendar.JULY, 1), report.finalDniMillis)
    }

    @Test fun laterFromDniEventUsesOmAdjustedUpstreamAndFeedsFinalConversion() {
        assertLaterEventUsesOmAdjustedUpstream("From DNI", date(Calendar.DECEMBER, 15), date(Calendar.APRIL, 15))
    }

    @Test fun laterFromEventDateEventUsesOmAdjustedUpstreamAndFeedsFinalConversion() {
        assertLaterEventUsesOmAdjustedUpstream("From Event Date", date(Calendar.FEBRUARY, 15), date(Calendar.APRIL, 15))
    }

    private fun assertLaterEventUsesOmAdjustedUpstream(option: String, eventDate: Long, normalDni: Long) {
        val feederScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val targetScale = FifthToSixthCpcData.scales.first { it.title == "Rs. 7450-225-11500" }
        val adjustment = calculateFifthCpcOmAdjustment(normalDni, 8_300, feederScale)!!
        val event = calculateFifthCpcEventFixation(
            currentPay = 8_300,
            currentScale = feederScale,
            targetScale = targetScale,
            eventType = "Promotion",
            placementMethod = "Next Higher After Increment",
            implementationOption = option,
            eventDate = eventDate,
            currentDni = normalDni,
            omAdjustment = adjustment
        )

        assertEquals(8_575, event.upstreamPay)
        assertEquals(8_825, event.feederIncrementedPay)
        assertEquals(9_025, event.fixedPay)
        assertEquals(adjustment, event.appliedOmAdjustment)
        val finalEventPay = event.fixedPay!!

        // Once the event result includes OM, subsequent events and 6th CPC use that result directly.
        val subsequent = calculateFifthCpcEventFixation(
            currentPay = finalEventPay,
            currentScale = targetScale,
            targetScale = feederScale,
            eventType = "Promotion",
            placementMethod = "Next Higher After Increment",
            implementationOption = option,
            eventDate = eventDate,
            currentDni = normalDni,
            omAdjustment = null
        )
        assertEquals(finalEventPay, subsequent.upstreamPay)
        assertNull(subsequent.appliedOmAdjustment)
        assertEquals(finalEventPay, calculateFifthToSixthCpc(finalEventPay, targetScale).existingBasicPay)
    }

    @Test fun legacyJsonWithOmKeysAbsentDecodesWithoutInferringAdjustmentOrChangingIntegrity() {
        for (integrity in CpcSequenceIntegrity.values()) {
            val record = omEligibleJourney(integrity)
            val json = JSONObject(CpcHistoryStore.encode(record))
            json.getJSONObject("payload").getJSONObject("fifth").remove("omAdjustment")

            val restored = CpcHistoryStore.decode(json.toString())!!
            val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
            assertEquals(integrity, snapshot.sequenceIntegrity)
            assertNull(snapshot.fifth!!.omAdjustment)
            val report = PayJourneyReportBuilder.build(restored)!!
            assertFalse(report.sections.flatMap { it.rows }.any { it.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT })
        }

        val standalone = CpcHistoryRecord(
            uniqueId = "legacy-json-om-absent",
            workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            savedAtMillis = date(Calendar.MAY, 1),
            startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.SIXTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FifthToSixth(
                "Rs. 8000-275-13500 (PB-2)", 8_300
            ))
        )
        val oldStandaloneJson = JSONObject(CpcHistoryStore.encode(standalone))
        oldStandaloneJson.getJSONObject("payload").remove("normalDniMillis")
        oldStandaloneJson.getJSONObject("payload").remove("omAdjustment")
        val restoredStandalone = CpcHistoryStore.decode(oldStandaloneJson.toString())!!
        val fifthToSixth = ((restoredStandalone.payload as StandaloneConversionPayload).snapshot as StandaloneCpcConversionSnapshot.FifthToSixth)
        assertNull(fifthToSixth.normalDniMillis)
        assertNull(fifthToSixth.omAdjustment)
        assertTrue(PayJourneyReportBuilder.build(restoredStandalone) != null)
    }

    @Test fun eventChronologyMarkerAndTypedOmSnapshotSurviveRestoreWithoutSequenceEntry() {
        val normalDni = date(Calendar.APRIL, 15)
        val scale = FifthToSixthCpcData.scales.first { it.title == "Rs. 8000-275-13500 (PB-2)" }
        val adjustment = calculateFifthCpcOmAdjustment(normalDni, 8_300, scale)!!
        val fifth = FifthCpcJourneySnapshot(
            startingDateMillis = date(Calendar.JANUARY, 1),
            scaleId = scale.title,
            startingBasicPay = 8_300,
            startingDniMillis = normalDni,
            events = listOf(FifthCpcEventSnapshot(
                order = 1,
                eventType = CpcJourneyEventKind.PROMOTION,
                eventDateMillis = date(Calendar.DECEMBER, 15),
                implementationDateMillis = normalDni,
                targetScaleId = "Rs. 7450-225-11500",
                resultingPay = 9_025,
                resultingDniMillis = date(Calendar.APRIL, 15),
                fixationBasis = CpcFixationBasis.DNI,
                placementMethod = "Next Higher After Increment",
                implementationOption = "From DNI",
                sequence = 7,
                omAppliedBeforeEvent = true
            )),
            sequence = 3,
            omAdjustment = adjustment
        )
        val snapshot = assignJourneyApplicationSequence(CompleteJourneySnapshot(
            startingCpc = CpcHistoryStage.FIFTH,
            startingDateMillis = date(Calendar.JANUARY, 1),
            fifth = fifth
        ))
        val record = CpcHistoryRecord(
            uniqueId = "om-event-sequence",
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = normalDni,
            startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.FIFTH,
            payload = CompleteJourneyPayload(snapshot)
        )
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val restoredFifth = (restored.payload as CompleteJourneyPayload).snapshot.fifth!!
        assertEquals(adjustment, restoredFifth.omAdjustment)
        assertEquals(true, restoredFifth.events.single().omAppliedBeforeEvent)
        assertEquals(7, restoredFifth.events.single().sequence)
        assertEquals(3, restoredFifth.sequence)
    }
}
