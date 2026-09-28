package com.niyammitra.payfixationcalculator

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class CpcHistoryArchitectureTest {
    private fun completeRecord(id: String = "journey-a"): CpcHistoryRecord {
        val eventResult = SixthCpcEventResult(
            eventType = "Promotion", eventDate = 1_200_000L,
            oldPayInPayBand = 10_000, oldGradePay = 2_400, increment = 500,
            newPayInPayBand = 10_500, newGradePay = 4_200, newPayBand = "PB-2",
            revisedBasicPay = 14_700, nextIncrementDate = 1_300_000L, ruleBasis = listOf("Rule basis")
        )
        val nested = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L,
            CpcFixationBasis.DNI, listOf(CpcIncrementSnapshot(1, 36_500, 1_700_000L)),
            SeventhCpcPromotionSnapshot("5", 36_500, 1_800_000L, "6", 1_900_000L))
        val snapshot = CompleteJourneySnapshot(
            startingCpc = CpcHistoryStage.FOURTH,
            startingDateMillis = 100_000L,
            fourth = FourthCpcJourneySnapshot("scale-4", "Scale IV", 900, 100_000L, null,
                listOf(CpcIncrementSnapshot(1, 920, 200_000L))),
            fifth = FifthCpcJourneySnapshot(300_000L, "scale-5", 3_000, null,
                events = listOf(FifthCpcEventSnapshot(1, CpcJourneyEventKind.PROMOTION, 400_000L, 410_000L,
                    "scale-5b", 3_200, 500_000L, CpcFixationBasis.DNI, "same-cell"))),
            sixth = SixthCpcJourneySnapshot(600_000L, "PB-1", 2_400, 10_000,
                eventChains = listOf(SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = eventResult,
                    increments = listOf(SixthCpcEventIncrement(11_000, 4_200, 1_250_000L)))),
                seventhContinuation = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L,
                    listOf(CpcIncrementSnapshot(1, 36_500, 1_500_000L)), listOf(nested))),
            seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L,
                listOf(CpcIncrementSnapshot(1, 36_500, 1_500_000L)), listOf(nested))
        )
        return CpcHistoryRecord(id, workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = 2_000_000L, startingCpc = CpcHistoryStage.FOURTH,
            currentStage = CpcHistoryStage.SEVENTH, payload = CompleteJourneyPayload(snapshot))
    }

    @Test fun completeJourneyRoundTripsMultipleStagesAndEventChains() {
        val record = completeRecord()
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        assertEquals(record, restored)
        val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(920, snapshot.fourth!!.increments.single().pay)
        assertEquals("scale-5b", snapshot.fifth!!.events.single().targetScaleId)
        assertEquals(11_000, snapshot.sixth!!.eventChains.single().increments.single().payInPayBand)
        assertEquals("6", snapshot.seventh!!.promotions.single().subsequentPromotion!!.promotedLevel)
    }

    @Test fun newJourneySequenceIntegrityIsOriginal() {
        val original = completeRecord()
        val snapshot = (original.payload as CompleteJourneyPayload).snapshot
        val newlySaved = original.copy(payload = CompleteJourneyPayload(assignJourneyApplicationSequence(snapshot)))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(newlySaved))!!
        assertEquals(CpcSequenceIntegrity.ORIGINAL, (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
    }

    @Test fun legacyJourneyWithoutSequenceIsInferredAndReportDisclosesUncertainty() {
        val encoded = JSONObject(CpcHistoryStore.encode(completeRecord()))
        val payload = encoded.getJSONObject("payload")
        removeSequenceMetadata(payload)
        payload.remove("sequenceIntegrity")

        val restored = CpcHistoryStore.decode(encoded.toString())!!
        val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.INFERRED, snapshot.sequenceIntegrity)
        assertTrue(PayJourneyReportBuilder.build(restored)!!.chronologyNote.orEmpty().contains("original application order could not be determined"))
        val resavedSnapshot = assignJourneyApplicationSequence(snapshot)
        assertEquals(CpcSequenceIntegrity.INFERRED, resavedSnapshot.sequenceIntegrity)
    }

    private fun removeSequenceMetadata(value: Any?) {
        when (value) {
            is JSONObject -> {
                value.remove("sequence")
                value.keys().asSequence().toList().forEach { removeSequenceMetadata(value.opt(it)) }
            }
            is JSONArray -> (0 until value.length()).forEach { removeSequenceMetadata(value.opt(it)) }
        }
    }

    @Test fun nullableDatesAndCorruptOrUnknownRecordsAreHandled() {
        val original = completeRecord()
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(original))!!
        assertNull((restored.payload as CompleteJourneyPayload).snapshot.fourth!!.nextIncrementDateMillis)
        assertNull(CpcHistoryStore.decode("{broken"))
        val unknownVersion = CpcHistoryStore.encode(original).replace("\"schemaVersion\":1", "\"schemaVersion\":88")
        assertNull(CpcHistoryStore.decode(unknownVersion))
        assertTrue(CpcHistoryCodec.decodeAll("[{},not-json]").isEmpty())
    }

    @Test fun collectionSupportsMultipleRecordsLookupDeleteAndClear() {
        val first = completeRecord()
        val second = first.copy(uniqueId = "journey-b", savedAtMillis = 3_000_000L)
        val values = CpcHistoryCollection.save(CpcHistoryCollection.save(emptyList(), first), second)
        assertEquals(2, values.size)
        assertEquals(second, CpcHistoryCollection.getById(values, "journey-b"))
        assertEquals(listOf(second), CpcHistoryCollection.delete(values, first.uniqueId))
        assertTrue(CpcHistoryCollection.clear().isEmpty())
        assertEquals(2, CpcHistoryCodec.decodeAll(CpcHistoryCodec.encodeAll(values)).size)
    }

    @Test fun reportRowsSortChronologicallyAndKeepSameDateApplicationOrder() {
        val record = completeRecord()
        val snapshot = (record.payload as CompleteJourneyPayload).snapshot
        val changed = record.copy(payload = CompleteJourneyPayload(snapshot.copy(fourth = snapshot.fourth!!.copy(
            increments = listOf(CpcIncrementSnapshot(2, 940, 200_000L), CpcIncrementSnapshot(1, 920, 200_000L))
        ))))
        val rows = PayJourneyReportBuilder.build(changed)!!.sections.first().rows
        assertEquals(CpcJourneyEventKind.STARTING_POSITION, rows.first().kind)
        assertEquals(listOf(1, 2), rows.filter { it.dateMillis == 200_000L }.map { it.order })
    }

    @Test fun standaloneConversionRecordsRoundTripAndReportUtilityResults() {
        val scale = FourthToFifthCpcData.scales.first()
        val fourthInput = scale.existingStages.first()
        val fourthRecord = CpcHistoryRecord("conversion-4-5", workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            savedAtMillis = 10L, startingCpc = CpcHistoryStage.FOURTH, currentStage = CpcHistoryStage.FIFTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FourthToFifth(scale.existingScale, fourthInput)))
        assertEquals(fourthRecord, CpcHistoryStore.decode(CpcHistoryStore.encode(fourthRecord)))
        assertEquals(calculateFourthToFifthCpc(fourthInput, scale).revisedBasicPay,
            PayJourneyReportBuilder.build(fourthRecord)!!.finalPosition!!.basicPay)
        assertEquals(CpcHistoryStage.FOURTH, PayJourneyReportBuilder.build(fourthRecord)!!.sections.single().rows.single().sourcePosition?.cpc)

        val fifthScale = FifthToSixthCpcData.scales.first()
        val fifthInput = 5000
        val fifthRecord = fourthRecord.copy(uniqueId = "conversion-5-6", startingCpc = CpcHistoryStage.FIFTH,
            currentStage = CpcHistoryStage.SIXTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FifthToSixth(fifthScale.title, fifthInput)))
        assertEquals(calculateFifthToSixthCpc(fifthInput, fifthScale).revisedBasicPay,
            PayJourneyReportBuilder.build(fifthRecord)!!.finalPosition!!.basicPay)
        assertEquals(CpcHistoryStage.FIFTH, PayJourneyReportBuilder.build(fifthRecord)!!.sections.single().rows.single().sourcePosition?.cpc)

        val band = SixthToSeventhCpcData.payBands.first()
        val sixthRecord = fourthRecord.copy(uniqueId = "conversion-6-7", startingCpc = CpcHistoryStage.SIXTH,
            currentStage = CpcHistoryStage.SEVENTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.SixthToSeventh(band.title.substringBefore(":"), 5200, 1800)))
        val expected = calculateSixthToSeventhCpc(5200, 1800, band)!!
        assertEquals(expected.revisedBasicPay, PayJourneyReportBuilder.build(sixthRecord)!!.finalPosition!!.basicPay)
        assertEquals(CpcHistoryStage.SIXTH, PayJourneyReportBuilder.build(sixthRecord)!!.sections.single().rows.single().sourcePosition?.cpc)
    }

    @Test fun sharedSequenceSurvivesJsonRoundTripAndOrdersSameDateIncrementThenEvent() {
        val source = completeRecord()
        val snapshot = (source.payload as CompleteJourneyPayload).snapshot
        val record = source.copy(payload = CompleteJourneyPayload(snapshot.copy(fourth = snapshot.fourth!!.copy(
            increments = listOf(CpcIncrementSnapshot(1, 920, 200_000L, 1)),
            events = listOf(FourthCpcEventSnapshot(1, 200_000L, CpcJourneyEventKind.PROMOTION, "scale-4b", 940, 2))
        ))))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val rows = PayJourneyReportBuilder.build(restored)!!.sections.first().rows.filter { it.dateMillis == 200_000L }
        assertEquals(listOf(CpcJourneyEventKind.ANNUAL_INCREMENT, CpcJourneyEventKind.PROMOTION), rows.map { it.kind })
        assertEquals(listOf(1, 2), rows.map { it.sequence })
        assertEquals(CpcSequenceIntegrity.ORIGINAL, (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
    }

    @Test fun sharedSequenceOrdersSameDateEventThenIncrement() {
        val source = completeRecord()
        val snapshot = (source.payload as CompleteJourneyPayload).snapshot
        val record = source.copy(payload = CompleteJourneyPayload(snapshot.copy(fourth = snapshot.fourth!!.copy(
            increments = listOf(CpcIncrementSnapshot(1, 940, 200_000L, 2)),
            events = listOf(FourthCpcEventSnapshot(1, 200_000L, CpcJourneyEventKind.PROMOTION, "scale-4b", 920, 1))
        ))))
        val rows = PayJourneyReportBuilder.build(record)!!.sections.first().rows.filter { it.dateMillis == 200_000L }
        assertEquals(listOf(CpcJourneyEventKind.PROMOTION, CpcJourneyEventKind.ANNUAL_INCREMENT), rows.map { it.kind })
        assertEquals(listOf(1, 2), rows.map { it.sequence })
    }

    @Test fun fifthEventUsesEffectiveImplementationDateAndRetainsBothDates() {
        val record = completeRecord()
        val snapshot = (record.payload as CompleteJourneyPayload).snapshot
        val eventDate = 400_000L
        val effectiveDate = 410_000L
        val event = snapshot.fifth!!.events.single().copy(eventDateMillis = eventDate, implementationDateMillis = effectiveDate)
        val incrementAtEventDate = CpcIncrementSnapshot(1, 3_100, eventDate)
        val changed = record.copy(payload = CompleteJourneyPayload(snapshot.copy(fifth = snapshot.fifth.copy(
            increments = listOf(incrementAtEventDate), events = listOf(event)
        ))))
        val rows = PayJourneyReportBuilder.build(changed)!!.sections.first { it.heading == "5th CPC Pay Journey" }.rows
        val eventRow = rows.single { it.kind == CpcJourneyEventKind.PROMOTION }
        assertEquals(effectiveDate, eventRow.dateMillis)
        assertEquals(eventDate, eventRow.eventDateMillis)
        assertEquals(effectiveDate, eventRow.implementationDateMillis)
        assertTrue(rows.indexOf(eventRow) > rows.indexOfFirst { it.kind == CpcJourneyEventKind.ANNUAL_INCREMENT })
    }

    @Test fun savingJourneyAssignsOneIncreasingSequenceAcrossCpcStages() {
        val record = completeRecord()
        val source = (record.payload as CompleteJourneyPayload).snapshot
        val realistic = source.copy(seventh = null, fifth = source.fifth!!.copy(sixthContinuation = source.sixth), sixth = null)
        val snapshot = assignJourneyApplicationSequence(realistic)
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record.copy(payload = CompleteJourneyPayload(snapshot))))!!
        val saved = (restored.payload as CompleteJourneyPayload).snapshot
        fun promotionSequences(p: SeventhCpcPromotionSnapshot): List<Int> = listOf(p.sequence) + p.postIncrements.map { it.sequence } + (p.subsequentPromotion?.let(::promotionSequences) ?: emptyList())
        val sequences = buildList {
            saved.fourth?.let { stage -> add(stage.sequence); addAll(stage.increments.map { it.sequence }); addAll(stage.events.map { it.sequence }) }
            saved.fifth?.let { stage ->
                add(stage.sequence); addAll(stage.increments.map { it.sequence }); addAll(stage.events.map { it.sequence })
                stage.sixthContinuation?.let { sixth ->
                    add(sixth.sequence); addAll(sixth.increments.map { it.sequence })
                    sixth.eventChains.forEach { chain -> add(chain.sequence); addAll(chain.increments.map { it.sequence }) }
                    sixth.seventhContinuation?.let { seventh ->
                        add(seventh.sequence); addAll(seventh.increments.map { it.sequence })
                        seventh.promotions.forEach { addAll(promotionSequences(it)) }
                    }
                }
            }
        }
        assertEquals(sequences.size, sequences.distinct().size)
        assertEquals((1..sequences.size).toList(), sequences.sorted())
    }

    @Test fun fourthPayDateResetDropsAcceptedIncrementEventAndLaterStages() {
        val reset = resetFourthJourneyAfterStartingDateEdit()
        assertTrue(reset.increments.isEmpty())
        assertTrue(reset.events.isEmpty())
        assertNull(reset.fifthSnapshot)
    }

    @Test fun seventhPromotionMacpKindAndKnownDniSurviveAndAppearInReport() {
        val date = 1_600_000L
        val promotion = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", date,
            eventKind = CpcJourneyEventKind.PROMOTION, sequence = 2)
        val macp = promotion.copy(eventKind = CpcJourneyEventKind.MACP, sequence = 3)
        val record = completeRecord().copy(payload = CompleteJourneyPayload(CompleteJourneySnapshot(
            CpcHistoryStage.SEVENTH, 1_400_000L,
            seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L, promotions = listOf(promotion, macp), startingDniMillis = 1_500_000L)
        )), startingCpc = CpcHistoryStage.SEVENTH, currentStage = CpcHistoryStage.SEVENTH)
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val seventh = (restored.payload as CompleteJourneyPayload).snapshot.seventh!!
        assertEquals(CpcJourneyEventKind.PROMOTION, seventh.promotions[0].eventKind)
        assertEquals(CpcJourneyEventKind.MACP, seventh.promotions[1].eventKind)
        assertEquals(1_500_000L, seventh.promotions[0].knownDniMillis)
        val rows = PayJourneyReportBuilder.build(restored)!!.sections.single().rows
        assertTrue(rows.any { it.description.contains("PROMOTION") && it.remarks?.contains("Known DNI") == true })
        assertTrue(rows.any { it.kind == CpcJourneyEventKind.MACP })
    }

    @Test fun allCompleteJourneyCpcTransitionsAreExplicitConversionRows() {
        val rows = PayJourneyReportBuilder.build(completeRecord())!!.sections.flatMap { it.rows }
        val conversions = rows.filter { it.kind == CpcJourneyEventKind.CPC_CONVERSION }
        assertEquals(3, conversions.size)
        assertTrue(conversions.any { it.description.contains("4th to 5th") && it.sourcePosition?.cpc == CpcHistoryStage.FOURTH })
        assertTrue(conversions.any { it.description.contains("5th to 6th") && it.sourcePosition?.cpc == CpcHistoryStage.FIFTH })
        assertTrue(conversions.any { it.description.contains("6th to 7th") && it.sourcePosition?.cpc == CpcHistoryStage.SIXTH })
    }

    @Test fun seventhOnlyJourneyUsesSeventhRestoreRoute() {
        val record = completeRecord().copy(startingCpc = CpcHistoryStage.SEVENTH, currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(CompleteJourneySnapshot(CpcHistoryStage.SEVENTH, 1_400_000L,
                seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L,
                    listOf(CpcIncrementSnapshot(1, 36_500, 1_500_000L, 1)), startingDniMillis = 1_500_000L))))
        val fetched = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        assertEquals(CpcHistoryRestoreDestination.SEVENTH_JOURNEY, cpcHistoryRestoreDestination(fetched))
        val restoredSeventh = (fetched.payload as CompleteJourneyPayload).snapshot.seventh!!
        assertEquals("4", restoredSeventh.startingLevel)
        assertEquals(36_500, restoredSeventh.increments.single().pay)
        assertEquals(1_500_000L, restoredSeventh.startingDniMillis)
    }
}
