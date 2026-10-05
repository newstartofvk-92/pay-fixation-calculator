package com.niyammitra.payfixationcalculator

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class CpcHistoryArchitectureTest {
    private fun completeRecord(id: String = "journey-a"): CpcHistoryRecord {
        val eventResult = SixthCpcEventResult(
            eventType = "Promotion", fixationOption = SixthCpcFixationOption.FROM_DNI, eventDate = 1_200_000L,
            oldPayInPayBand = 10_000, oldGradePay = 2_400, increment = 500,
            newPayInPayBand = 10_500, newGradePay = 4_200, newPayBand = "PB-2",
            revisedBasicPay = 14_700, nextIncrementDate = 1_300_000L,
            ruleBasis = listOf("From-DNI option: normal annual increment on 1 July precedes event fixation.")
        )
        val nested = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L,
            CpcFixationBasis.DNI, listOf(CpcIncrementSnapshot(1, 36_500, 1_700_000L)),
            SeventhCpcPromotionSnapshot("5", 36_500, 1_800_000L, "6", 1_900_000L))
        val snapshot = assignJourneyApplicationSequence(CompleteJourneySnapshot(
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
        ))
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
        assertEquals(SixthCpcFixationOption.FROM_DNI, snapshot.sixth!!.eventChains.single().result!!.fixationOption)
        assertEquals("6", snapshot.seventh!!.promotions.single().subsequentPromotion!!.promotedLevel)
        val sixthEventRow = PayJourneyReportBuilder.build(restored)!!.sections
            .single { it.heading == "6th CPC Pay Journey" }.rows
            .single { it.description == "Promotion" }
        assertTrue(sixthEventRow.remarks!!.contains("From-DNI option"))
    }

    @Test fun scaleUpgradeHistoryRemainsSeparateFromPromotionFixationOption() {
        val original = completeRecord()
        val snapshot = (original.payload as CompleteJourneyPayload).snapshot
        val scaleUpgrade = SixthCpcScaleUpgradeResult(
            eventDate = 1_250_000L,
            oldPayInPayBand = 10_000,
            oldGradePay = 2_400,
            oldPayBand = "PB-1",
            newPayInPayBand = 12_090,
            newGradePay = 4_200,
            newPayBand = "PB-2",
            revisedBasicPay = 16_290,
            nextIncrementDate = 1_300_000L,
            historicalRoute = HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC,
            sourcePreRevisedBasicPay = 12_400,
            fixationIncrement = 410
        )
        val scaleChain = SixthCpcEventChain(
            kind = SixthCpcEventKind.PAY_SCALE_UPGRADATION,
            scaleUpgrade = scaleUpgrade,
            sequence = 25
        )
        val updated = original.copy(payload = CompleteJourneyPayload(
            snapshot.copy(sixth = snapshot.sixth!!.copy(eventChains = listOf(scaleChain)))
        ))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(updated))!!
        val restoredChain = (restored.payload as CompleteJourneyPayload).snapshot.sixth!!.eventChains.single()
        assertEquals(SixthCpcEventKind.PAY_SCALE_UPGRADATION, restoredChain.kind)
        assertEquals(scaleUpgrade, restoredChain.scaleUpgrade)
        assertNull(restoredChain.result)
    }

    @Test fun newJourneySequenceIntegrityIsOriginal() {
        val original = completeRecord()
        val snapshot = (original.payload as CompleteJourneyPayload).snapshot
        val newlySaved = original.copy(payload = CompleteJourneyPayload(assignJourneyApplicationSequence(snapshot)))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(newlySaved))!!
        assertEquals(CpcSequenceIntegrity.ORIGINAL, (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
        assertEquals(1, (JSONObject(CpcHistoryStore.encode(newlySaved)).getJSONObject("payload").getInt("sharedApplicationSequenceVersion")))
    }

    @Test fun legacyJourneyWithoutSequenceIsInferredAndReportDisclosesUncertainty() {
        val encoded = JSONObject(CpcHistoryStore.encode(completeRecord()))
        val payload = encoded.getJSONObject("payload")
        removeSequenceMetadata(payload)
        payload.remove("sequenceIntegrity")
        payload.remove("sharedApplicationSequenceVersion")

        val restored = CpcHistoryStore.decode(encoded.toString())!!
        val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.INFERRED, snapshot.sequenceIntegrity)
        assertTrue(PayJourneyReportBuilder.build(restored)!!.chronologyNote.orEmpty().contains("original application order could not be determined"))
        val resavedSnapshot = assignJourneyApplicationSequence(snapshot)
        assertEquals(CpcSequenceIntegrity.INFERRED, resavedSnapshot.sequenceIntegrity)
    }

    @Test fun legacyPerListSequencesWithoutSharedMarkerRemainInferred() {
        val encoded = JSONObject(CpcHistoryStore.encode(completeRecord()))
        val payload = encoded.getJSONObject("payload")
        payload.remove("sharedApplicationSequenceVersion")
        assertTrue(payload.getJSONObject("fourth").getJSONArray("increments").getJSONObject(0).has("sequence"))

        val restored = CpcHistoryStore.decode(encoded.toString())!!
        val snapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.INFERRED, snapshot.sequenceIntegrity)
        assertTrue(PayJourneyReportBuilder.build(restored)!!.chronologyNote.orEmpty().contains("original application order could not be determined"))

        payload.remove("sequenceIntegrity")
        val withoutIntegrity = CpcHistoryStore.decode(encoded.toString())!!
        assertEquals(CpcSequenceIntegrity.INFERRED, (withoutIntegrity.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
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
        val sequenced = assignJourneyApplicationSequence(snapshot.copy(fourth = snapshot.fourth!!.copy(
            increments = listOf(CpcIncrementSnapshot(1, 920, 200_000L, 1)),
            events = listOf(FourthCpcEventSnapshot(1, 200_000L, CpcJourneyEventKind.PROMOTION, "scale-4b", 940, 2))
        )))
        val record = source.copy(payload = CompleteJourneyPayload(sequenced))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val rows = PayJourneyReportBuilder.build(restored)!!.sections.first().rows.filter { it.dateMillis == 200_000L }
        assertEquals(listOf(CpcJourneyEventKind.ANNUAL_INCREMENT, CpcJourneyEventKind.PROMOTION), rows.map { it.kind })
        assertEquals(listOf(2, 3), rows.map { it.sequence })
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

    @Test fun fourthOriginContinuationKeepsEarlierStagesAndAppendsEffectiveSixthPosition() {
        val fourth = completeRecord().let { (it.payload as CompleteJourneyPayload).snapshot.fourth!! }
        val oldSixth = SixthCpcJourneySnapshot(
            startingDateMillis = 1_000L, payBandId = "PB-2", gradePay = 4_200, startingPayInPayBand = 10_500,
            increments = listOf(SixthCpcIncrementSnapshot(1, 10_800, 4_200, 2_000L, 1)), sequence = 1
        )
        val fifth = FifthCpcJourneySnapshot(600L, "scale-5", 3_200, 900L, sixthContinuation = oldSixth)
        val source = CompleteJourneySnapshot(CpcHistoryStage.FOURTH, 100L, fourth = fourth, fifth = fifth)
        val laterSeventh = SeventhCpcJourneySnapshot("6", 35_400, 5_000L, startingDniMillis = 6_000L)
        val laterSixth = SixthCpcJourneySnapshot(
            startingDateMillis = 4_000L, payBandId = "PB-2", gradePay = 4_200, startingPayInPayBand = 11_000,
            increments = listOf(SixthCpcIncrementSnapshot(1, 11_300, 4_200, 4_500L, 1)),
            seventhContinuation = laterSeventh
        )

        val continued = appendSixthContinuation(source, laterSixth)
        val resultSixth = continued.fifth!!.sixthContinuation!!
        assertEquals(fourth, continued.fourth)
        assertEquals(fifth.copy(sixthContinuation = resultSixth), continued.fifth)
        assertEquals(listOf(2_000L, 4_500L), resultSixth.increments.map { it.dateMillis })
        assertEquals(listOf(1, 2), resultSixth.increments.map { it.sequence })
        assertEquals(4_000L, laterSixth.startingDateMillis)
        assertEquals(laterSeventh, resultSixth.seventhContinuation)
    }

    @Test fun fifthOriginContinuationKeepsIndependentSeventhPromotionEntries() {
        val promotions = listOf(
            SeventhCpcPromotionSnapshot("4", 35_400, 1_500L, "5", 1_600L, sequence = 1),
            SeventhCpcPromotionSnapshot("5", 36_500, 1_700L, "6", 1_800L, eventKind = CpcJourneyEventKind.MACP, sequence = 2),
            SeventhCpcPromotionSnapshot("6", 38_000, 1_900L, "7", 2_000L, sequence = 3)
        )
        val oldSeventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400L, promotions = promotions)
        val oldSixth = SixthCpcJourneySnapshot(1_000L, "PB-2", 4_200, 10_500, seventhContinuation = oldSeventh)
        val fifth = FifthCpcJourneySnapshot(500L, "scale-5", 3_200, null, sixthContinuation = oldSixth)
        val source = CompleteJourneySnapshot(CpcHistoryStage.FIFTH, 500L, fifth = fifth)
        val currentSixth = SixthCpcJourneySnapshot(
            4_000L, "PB-2", 4_200, 11_000,
            seventhContinuation = SeventhCpcJourneySnapshot("6", 39_000, 5_000L, startingDniMillis = 6_000L)
        )

        val continued = appendSixthContinuation(source, currentSixth)
        val resultPromotions = continued.fifth!!.sixthContinuation!!.seventhContinuation!!.promotions
        assertEquals(3, resultPromotions.size)
        assertEquals(promotions.map { it.eventKind }, resultPromotions.map { it.eventKind })
        assertEquals(promotions.map { it.promotedLevel }, resultPromotions.map { it.promotedLevel })
        assertEquals(listOf(1, 2, 3), resultPromotions.map { it.sequence })
        val record = CpcHistoryRecord(
            "multi-promotion", workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = 7_000L, startingCpc = CpcHistoryStage.FIFTH, currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(assignJourneyApplicationSequence(continued))
        )
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val savedPromotions = (restored.payload as CompleteJourneyPayload).snapshot.fifth!!.sixthContinuation!!.seventhContinuation!!.promotions
        assertEquals(3, savedPromotions.size)
        assertEquals(promotions.map { it.eventKind }, savedPromotions.map { it.eventKind })
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

    @Test fun editingRestoredSeventhPromotionPreservesIndependentPromotionEntries() {
        val first = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L,
            eventKind = CpcJourneyEventKind.PROMOTION, sequence = 2)
        val second = first.copy(currentLevel = "5", currentPay = 36_500, promotedLevel = "6",
            promotionDateMillis = 1_700_000L, eventKind = CpcJourneyEventKind.MACP, sequence = 7)
        val editedSecond = second.copy(currentPay = 38_000, resultingPay = 39_000)
        val updated = replaceSeventhPromotion(listOf(first, second), 1, editedSecond, 8)
        assertEquals(2, updated.size)
        assertEquals(first, updated.first())
        assertEquals(editedSecond, updated.last())
        val added = replaceSeventhPromotion(updated, null, first.copy(sequence = 0, eventKind = CpcJourneyEventKind.MACP), 8)
        assertEquals(3, added.size)
        assertEquals(CpcJourneyEventKind.MACP, added.last().eventKind)
        assertEquals(8, added.last().sequence)
    }

    @Test fun legacyPromotionsWithDuplicateSequenceRemainIndividuallyEditableAndResavable() {
        val legacy = listOf(
            SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L, resultingPay = 36_500),
            SeventhCpcPromotionSnapshot("5", 36_500, 1_700_000L, "6", 1_800_000L, resultingPay = 38_000),
            SeventhCpcPromotionSnapshot("6", 38_000, 1_900_000L, "7", 2_000_000L, resultingPay = 40_000)
        )
        var edited = legacy
        edited = replaceSeventhPromotion(edited, 0, legacy[0].copy(currentPay = 35_900), 20)
        assertEquals(listOf(35_900, 36_500, 38_000), edited.map { it.currentPay })
        edited = replaceSeventhPromotion(edited, 1, legacy[1].copy(currentPay = 37_000), 21)
        assertEquals(listOf(35_900, 37_000, 38_000), edited.map { it.currentPay })
        edited = replaceSeventhPromotion(edited, 2, legacy[2].copy(currentPay = 39_000), 22)
        assertEquals(listOf(35_900, 37_000, 39_000), edited.map { it.currentPay })
        assertEquals(listOf(0, 0, 0), edited.map { it.sequence })

        val record = completeRecord().copy(payload = CompleteJourneyPayload(
            CompleteJourneySnapshot(CpcHistoryStage.SEVENTH, 1_400_000L,
                seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L, promotions = edited))
        ), startingCpc = CpcHistoryStage.SEVENTH, currentStage = CpcHistoryStage.SEVENTH)
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val savedPromotions = (restored.payload as CompleteJourneyPayload).snapshot.seventh!!.promotions
        assertEquals(3, savedPromotions.size)
        assertEquals(listOf(35_900, 37_000, 39_000), savedPromotions.map { it.currentPay })
        assertEquals(CpcSequenceIntegrity.INFERRED, (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
    }

    @Test fun reportUsesSequenceInsteadOfPromotionListOrderForFinalPay() {
        val older = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L,
            resultingPay = 36_500, sequence = 10)
        val newer = SeventhCpcPromotionSnapshot("5", 36_500, 1_700_000L, "6", 1_800_000L,
            resultingPay = 38_000, sequence = 12)
        val record = completeRecord().copy(payload = CompleteJourneyPayload(
            CompleteJourneySnapshot(CpcHistoryStage.SEVENTH, 1_400_000L,
                seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L, promotions = listOf(newer, older)))
        ), startingCpc = CpcHistoryStage.SEVENTH, currentStage = CpcHistoryStage.SEVENTH)
        val report = PayJourneyReportBuilder.build(record)!!
        assertEquals(38_000, report.finalPosition!!.basicPay)
        assertEquals("6", report.finalPosition!!.level)
    }

    @Test fun inferredReportChoosesFinalPromotionByEffectiveDate() {
        val earlier = SeventhCpcPromotionSnapshot("4", 35_400, 1_500L, "5", 2_000L,
            resultingPay = 40_000, sequence = 20)
        val later = SeventhCpcPromotionSnapshot("5", 40_000, 2_500L, "6", 3_000L,
            resultingPay = 41_000, sequence = 10)
        val report = reportForSeventh(CpcSequenceIntegrity.INFERRED, listOf(earlier, later))
        assertEquals(41_000, report.finalPosition!!.basicPay)
        assertEquals("6", report.finalPosition!!.level)
        assertNotNull(report.chronologyNote)
    }

    @Test fun inferredReportChoosesNestedPostIncrementByDateForPayAndDni() {
        val earlierDate = 1_700_000_000_000L
        val laterDate = 1_730_000_000_000L
        val promotion = SeventhCpcPromotionSnapshot("4", 35_400, 1_500L, "5", 1_600L,
            postIncrements = listOf(
                CpcIncrementSnapshot(1, 40_000, earlierDate, sequence = 20),
                CpcIncrementSnapshot(2, 42_000, laterDate, sequence = 10)
            ), resultingPay = 36_500, resultingDniMillis = 1_700L, sequence = 1)
        val report = reportForSeventh(CpcSequenceIntegrity.INFERRED, listOf(promotion))
        assertEquals(42_000, report.finalPosition!!.basicPay)
        assertEquals(addYearForTest(laterDate), report.finalDniMillis)
        assertTrue(report.chronologyNote.orEmpty().contains("original application order could not be determined"))
    }

    @Test fun originalReportChoosesFinalPromotionBySharedSequence() {
        val earlier = SeventhCpcPromotionSnapshot("4", 35_400, 1_500L, "5", 2_000L,
            resultingPay = 40_000, sequence = 20)
        val later = SeventhCpcPromotionSnapshot("5", 40_000, 2_500L, "6", 3_000L,
            resultingPay = 41_000, sequence = 10)
        val report = reportForSeventh(CpcSequenceIntegrity.ORIGINAL, listOf(earlier, later))
        assertEquals(40_000, report.finalPosition!!.basicPay)
        assertEquals("5", report.finalPosition!!.level)
        assertNull(report.chronologyNote)
    }

    @Test fun originalReportChoosesNestedPostIncrementBySharedSequenceForPayAndDni() {
        val earlierDate = 1_700_000_000_000L
        val laterDate = 1_730_000_000_000L
        val promotion = SeventhCpcPromotionSnapshot("4", 35_400, 1_500L, "5", 1_600L,
            postIncrements = listOf(
                CpcIncrementSnapshot(1, 40_000, earlierDate, sequence = 20),
                CpcIncrementSnapshot(2, 42_000, laterDate, sequence = 10)
            ), resultingPay = 36_500, sequence = 1)
        val report = reportForSeventh(CpcSequenceIntegrity.ORIGINAL, listOf(promotion))
        assertEquals(40_000, report.finalPosition!!.basicPay)
        assertEquals(addYearForTest(earlierDate), report.finalDniMillis)
    }

    @Test fun validOriginalJourneyRestoreAndResavePreservesAllSharedSequences() {
        val original = completeRecord()
        val before = (original.payload as CompleteJourneyPayload).snapshot
        assertTrue(hasValidSharedApplicationSequence(before))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(original))!!
        val restoredSnapshot = (restored.payload as CompleteJourneyPayload).snapshot
        assertEquals(CpcSequenceIntegrity.ORIGINAL, restoredSnapshot.sequenceIntegrity)
        assertEquals(before, assignJourneyApplicationSequence(restoredSnapshot))
        val resaved = restored.copy(payload = CompleteJourneyPayload(assignJourneyApplicationSequence(restoredSnapshot)))
        val roundTripped = CpcHistoryStore.decode(CpcHistoryStore.encode(resaved))!!
        assertEquals(before, (roundTripped.payload as CompleteJourneyPayload).snapshot)
    }

    private fun reportForSeventh(
        integrity: CpcSequenceIntegrity,
        promotions: List<SeventhCpcPromotionSnapshot>
    ): PayJourneyReport {
        val record = completeRecord().copy(
            startingCpc = CpcHistoryStage.SEVENTH,
            currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(CompleteJourneySnapshot(
                startingCpc = CpcHistoryStage.SEVENTH,
                startingDateMillis = 1_400L,
                seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400L, promotions = promotions),
                sequenceIntegrity = integrity
            ))
        )
        return PayJourneyReportBuilder.build(record)!!
    }

    private fun addYearForTest(date: Long): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = date
        add(java.util.Calendar.YEAR, 1)
    }.timeInMillis

    @Test fun inferredJourneyUsesStoredDatesWhenPromotionSequencesAreDuplicated() {
        val newer = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 2_000_000L,
            resultingPay = 37_000, sequence = 0)
        val older = SeventhCpcPromotionSnapshot("5", 37_000, 1_800_000L, "6", 1_900_000L,
            resultingPay = 38_000, sequence = 0)
        val position = currentSeventhPosition(
            SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L),
            emptyList(), listOf(newer, older), CpcSequenceIntegrity.INFERRED
        )
        assertEquals("5", position.level)
        assertEquals(37_000, position.pay)
    }

    @Test fun restoredEditedPromotionPostIncrementSequencesRemainStableAndNewSequenceFollowsThem() {
        val saved = listOf(
            CpcIncrementSnapshot(1, 40_000, 2_000_000L, 10),
            CpcIncrementSnapshot(2, 42_000, 2_100_000L, 11),
            CpcIncrementSnapshot(3, 44_000, 2_200_000L, 12)
        )
        val restoredSteps = restorePromotionPostSteps(saved)
        val editedSteps = restoredSteps.toMutableList().also { it[1] = it[1].copy(pay = 43_000) }
        val resaved = savePromotionPostSteps(editedSteps)
        assertEquals(listOf(10, 11, 12), resaved.map { it.sequence })
        assertEquals(43_000, resaved[1].pay)

        val added = editedSteps + SeventhCpcIncrementStep(45_000, 2_300_000L,
            nextPromotionPostSequence(editedSteps, saved.maxOf { it.sequence }))
        val savedWithNewEntry = savePromotionPostSteps(added)
        assertEquals(listOf(10, 11, 12, 13), savedWithNewEntry.map { it.sequence })

        val promotion = SeventhCpcPromotionSnapshot("4", 35_400, 1_500_000L, "5", 1_600_000L,
            postIncrements = savedWithNewEntry, resultingPay = 36_500, sequence = 9)
        val journey = CompleteJourneySnapshot(CpcHistoryStage.SEVENTH, 1_400_000L,
            seventh = SeventhCpcJourneySnapshot("4", 35_400, 1_400_000L, promotions = listOf(promotion), sequence = 8))
        val savedJourney = assignJourneyApplicationSequence(journey)
        assertEquals(listOf(10, 11, 12, 13), savedJourney.seventh!!.promotions.single().postIncrements.map { it.sequence })
        val record = CpcHistoryRecord("post-sequence", workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = 1L, startingCpc = CpcHistoryStage.SEVENTH, currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(savedJourney))
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val restoredSequences = (restored.payload as CompleteJourneyPayload).snapshot.seventh!!.promotions.single().postIncrements.map { it.sequence }
        assertEquals(listOf(10, 11, 12, 13), restoredSequences)
        assertEquals(CpcSequenceIntegrity.ORIGINAL, (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity)
    }

    @Test fun standaloneConversionNameAndDesignationRestoreFromMetadataOrSafeLegacyTitle() {
        assertEquals("Asha Rao" to "Accounts Officer", parseCpcConversionTitle("Asha Rao — Accounts Officer — FOURTH TO FIFTH"))
        assertNull(parseCpcConversionTitle("Asha — Rao — Accounts Officer — FOURTH TO FIFTH"))
        val record = CpcHistoryRecord("named-conversion", workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
            title = "Asha Rao — Accounts Officer — FOURTH TO FIFTH", savedAtMillis = 1L,
            startingCpc = CpcHistoryStage.FOURTH, currentStage = CpcHistoryStage.FIFTH,
            payload = StandaloneConversionPayload(StandaloneCpcConversionSnapshot.FourthToFifth("S4", 900)),
            officialName = "Asha Rao", designation = "Accounts Officer")
        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        assertEquals("Asha Rao", restored.officialName)
        assertEquals("Accounts Officer", restored.designation)
    }

    @Test fun allCompleteJourneyCpcTransitionsAreExplicitConversionRows() {
        val rows = PayJourneyReportBuilder.build(completeRecord())!!.sections.flatMap { it.rows }
        val conversions = rows.filter { it.kind == CpcJourneyEventKind.CPC_CONVERSION }
        assertEquals(3, conversions.size)
        assertTrue(conversions.any { it.description.contains("4th to 5th") && it.sourcePosition?.cpc == CpcHistoryStage.FOURTH })
        assertTrue(conversions.any { it.description.contains("5th to 6th") && it.sourcePosition?.cpc == CpcHistoryStage.FIFTH })
        assertTrue(conversions.any { it.description.contains("6th to 7th") && it.sourcePosition?.cpc == CpcHistoryStage.SIXTH })
    }

    @Test fun nestedSixthContinuationIsIncludedInCompleteJourneyReport() {
        val record = completeRecord()
        val source = (record.payload as CompleteJourneyPayload).snapshot
        val nested = source.copy(sixth = null, seventh = null,
            fifth = source.fifth!!.copy(sixthContinuation = source.sixth!!.copy(seventhContinuation = null)))
        val report = PayJourneyReportBuilder.build(record.copy(payload = CompleteJourneyPayload(nested)))!!
        val sixth = report.sections.single { it.heading == "6th CPC Pay Journey" }.rows
        assertEquals(1, sixth.count { it.kind == CpcJourneyEventKind.CPC_CONVERSION })
        assertEquals(1, sixth.count { it.kind == CpcJourneyEventKind.PROMOTION })
        assertEquals(15_200, report.finalPosition!!.basicPay)
    }

    @Test fun nestedSixthSeventhContinuationIsIncludedWithoutTopLevelStages() {
        val record = completeRecord()
        val source = (record.payload as CompleteJourneyPayload).snapshot
        val nested = source.copy(sixth = null, seventh = null,
            fifth = source.fifth!!.copy(sixthContinuation = source.sixth!!.copy(seventhContinuation = source.seventh)))
        val report = PayJourneyReportBuilder.build(record.copy(payload = CompleteJourneyPayload(nested)))!!
        assertTrue(report.sections.any { it.heading == "6th CPC Pay Journey" })
        assertTrue(report.sections.any { it.heading == "7th CPC Pay Journey" })
        val conversions = report.sections.flatMap { it.rows }.filter { it.kind == CpcJourneyEventKind.CPC_CONVERSION }
        assertEquals(3, conversions.size)
        assertTrue(conversions.any { it.description == "5th to 6th CPC conversion" })
        assertTrue(conversions.any { it.description == "6th to 7th CPC conversion" })
    }

    @Test fun topLevelSixthTakesPrecedenceOverNestedSixthWithoutDuplicateRows() {
        val record = completeRecord()
        val source = (record.payload as CompleteJourneyPayload).snapshot
        val nestedSixth = source.sixth!!.copy(startingPayInPayBand = 12_345)
        val changed = source.copy(fifth = source.fifth!!.copy(sixthContinuation = nestedSixth))
        val report = PayJourneyReportBuilder.build(record.copy(payload = CompleteJourneyPayload(changed)))!!
        assertEquals(1, report.sections.count { it.heading == "6th CPC Pay Journey" })
        val row = report.sections.single { it.heading == "6th CPC Pay Journey" }.rows.single { it.kind == CpcJourneyEventKind.CPC_CONVERSION }
        assertEquals(12_400, row.position.basicPay)
    }

    @Test fun nestedAndTopLevelSeventhUseOneChronologicalStage() {
        val record = completeRecord()
        val source = (record.payload as CompleteJourneyPayload).snapshot
        val duplicate = source.copy(fifth = source.fifth!!.copy(
            sixthContinuation = source.sixth!!.copy(seventhContinuation = source.seventh)))
        val report = PayJourneyReportBuilder.build(record.copy(payload = CompleteJourneyPayload(duplicate)))!!
        assertEquals(1, report.sections.count { it.heading == "7th CPC Pay Journey" })
        val seventhRows = report.sections.single { it.heading == "7th CPC Pay Journey" }.rows
        assertEquals(1, seventhRows.count { it.kind == CpcJourneyEventKind.CPC_CONVERSION })
        assertEquals(seventhRows.sortedWith(compareBy<PayJourneyReportRow> { it.dateMillis ?: Long.MIN_VALUE }.thenBy { it.sequence }), seventhRows)
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

    @Test fun fourthOriginRestoreKeepsTopLevelSixthAndSeventhContinuationReachable() {
        val original = completeRecord()
        val savedSnapshot = (original.payload as CompleteJourneyPayload).snapshot.copy(
            seventh = null,
            fifth = (original.payload as CompleteJourneyPayload).snapshot.fifth!!.copy(
                sixthContinuation = null
            )
        )
        val savedRecord = original.copy(payload = CompleteJourneyPayload(savedSnapshot))

        val decoded = CpcHistoryStore.decode(CpcHistoryStore.encode(savedRecord))!!
        val decodedSnapshot = (decoded.payload as CompleteJourneyPayload).snapshot
        val restored = normalizeJourneyForCpcRestore(decodedSnapshot)

        assertEquals(CpcHistoryRestoreDestination.FOURTH_JOURNEY, cpcHistoryRestoreDestination(decoded))
        assertNotNull(decodedSnapshot.sixth)
        assertNull(restored.sixth)
        assertEquals(decodedSnapshot.sixth, restored.fifth!!.sixthContinuation)
        assertNotNull(restored.fourth)
        assertNotNull(restored.fifth)
        assertEquals(11_000, restored.fifth!!.sixthContinuation!!.eventChains.single().increments.single().payInPayBand)
        assertEquals("6", restored.fifth!!.sixthContinuation!!.seventhContinuation!!.promotions.single().subsequentPromotion!!.promotedLevel)
        assertEquals(CpcSequenceIntegrity.ORIGINAL, restored.sequenceIntegrity)
    }
}
