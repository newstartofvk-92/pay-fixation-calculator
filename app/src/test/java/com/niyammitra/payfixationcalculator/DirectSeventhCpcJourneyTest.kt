package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Calendar

class DirectSeventhCpcJourneyTest {
    private fun date(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply { clear(); set(year, month, day) }.timeInMillis

    @Test
    fun directStartingPositionSelectionsUseOnlyExistingValidMatrixCellsAndDniOptions() {
        val matrix = PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY)
        val levelSixCells = validStartingSeventhPayCells("6")
        val startDate = date(2016, Calendar.JANUARY, 1)
        val dniOptions = validStartingSeventhDniOptions(startDate)

        assertTrue(matrix.levels.contains("6"))
        assertTrue(levelSixCells.contains(35_400))
        assertFalse(levelSixCells.contains(35_500))
        assertEquals(35_400, validInitialStartingSeventhBasicPay("6", 35_400))
        assertNull(validInitialStartingSeventhBasicPay("6", 35_500))
        assertTrue(validStartingSeventhPayCells("not-a-level").isEmpty())
        assertEquals(getPayFixationDniOptions(startDate).filter { it >= startDate }.distinct().sorted(), dniOptions)
        assertTrue(dniOptions.all { it >= startDate })
    }

    @Test
    fun directStartingPositionRetainsSuppliedDateLevelBasicPayAndDni() {
        val startDate = date(2022, Calendar.MARCH, 1)
        val dni = date(2022, Calendar.JULY, 1)
        val position = PayFixationStartingPosition(
            scaleOrLevel = "Level 6",
            basicPay = 35_400,
            payBand = null,
            gradePay = null,
            payInPayBand = null,
            dniMillis = dni,
            dniText = "01/07/2022"
        )

        val snapshot = seventhSnapshotFromStartingPosition(startDate, position)!!

        assertEquals("6", snapshot.startingLevel)
        assertEquals(35_400, snapshot.startingBasicPay)
        assertEquals(startDate, snapshot.conversionDateMillis)
        assertEquals(dni, snapshot.startingDniMillis)
        assertTrue(snapshot.increments.isEmpty())
        assertTrue(snapshot.promotions.isEmpty())
    }

    @Test
    fun existingIncrementAndPromotionPositionLogicContinuesFromDirectBase() {
        val startDate = date(2022, Calendar.MARCH, 1)
        val dni = date(2022, Calendar.JULY, 1)
        val base = SeventhCpcJourneySnapshot("6", 35_400, startDate, startingDniMillis = dni)
        val nextPay = getSixthToSeventhNextCell(base.startingLevel, base.startingBasicPay)
        assertNotNull(nextPay)

        val increment = SeventhCpcIncrementStep(nextPay!!, dni, sequence = 2)
        val afterIncrement = currentSeventhPosition(
            base, listOf(increment), emptyList(), CpcSequenceIntegrity.ORIGINAL
        )
        assertEquals(nextPay, afterIncrement.pay)
        assertEquals(addSeventhHistoryYears(dni, 1), afterIncrement.dni)

        val promotionDate = date(2023, Calendar.OCTOBER, 1)
        val promotion = SeventhCpcPromotionSnapshot(
            currentLevel = "6", currentPay = nextPay, knownDniMillis = afterIncrement.dni,
            promotedLevel = "7", promotionDateMillis = promotionDate,
            resultingPay = 44_900, resultingDniMillis = date(2024, Calendar.JULY, 1),
            eventKind = CpcJourneyEventKind.MACP, sequence = 3
        )
        val afterPromotion = currentSeventhPosition(
            base, listOf(increment), listOf(promotion), CpcSequenceIntegrity.ORIGINAL
        )
        assertEquals("7", afterPromotion.level)
        assertEquals(44_900, afterPromotion.pay)
        assertEquals(promotion.resultingDniMillis, afterPromotion.dni)
    }

    @Test
    fun sixthToSeventhConversionStillInitializesTheOriginal2016Position() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-2") }
        val conversion = calculateSixthToSeventhCpc(13_500, 4_200, band)!!

        val startingPosition = convertedSeventhStartingSnapshot(conversion)

        assertEquals(conversion.level, startingPosition.startingLevel)
        assertEquals(conversion.revisedBasicPay, startingPosition.startingBasicPay)
        assertEquals(date(2016, Calendar.JANUARY, 1), startingPosition.conversionDateMillis)
        assertEquals(julyFirst2016(), startingPosition.startingDniMillis)
    }

    @Test
    fun directSaveAndRestoreUsesOnlyTopLevelSeventhSnapshotAndPreservesIntegrity() {
        val startDate = date(2020, Calendar.JANUARY, 1)
        val dni = date(2020, Calendar.JULY, 1)
        val incrementDate = date(2020, Calendar.JULY, 1)
        val promotionDate = date(2022, Calendar.JUNE, 1)
        val base = SeventhCpcJourneySnapshot(
            startingLevel = "6", startingBasicPay = 35_400,
            conversionDateMillis = startDate, startingDniMillis = dni, sequence = 1
        )
        val promotion = SeventhCpcPromotionSnapshot(
            currentLevel = "6", currentPay = 36_500, knownDniMillis = date(2021, Calendar.JULY, 1),
            promotedLevel = "7", promotionDateMillis = promotionDate, resultingPay = 44_900,
            resultingDniMillis = date(2023, Calendar.JULY, 1),
            eventKind = CpcJourneyEventKind.PROMOTION, sequence = 3
        )
        val snapshot = directSeventhCompleteJourneySnapshot(
            base,
            listOf(SeventhCpcIncrementStep(36_500, incrementDate, sequence = 2)),
            listOf(promotion),
            CpcSequenceIntegrity.ORIGINAL
        )
        val record = CpcHistoryRecord(
            uniqueId = "direct-seventh",
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = startDate,
            startingCpc = CpcHistoryStage.SEVENTH,
            currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(snapshot)
        )

        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        val restoredSnapshot = (restored.payload as CompleteJourneyPayload).snapshot
        val restoredSeventh = restoredSnapshot.seventh!!

        assertEquals(CpcHistoryStage.SEVENTH, restoredSnapshot.startingCpc)
        assertEquals(startDate, restoredSnapshot.startingDateMillis)
        assertNull(restoredSnapshot.fourth)
        assertNull(restoredSnapshot.fifth)
        assertNull(restoredSnapshot.sixth)
        assertEquals(36_500, restoredSeventh.increments.single().pay)
        assertEquals(2, restoredSeventh.increments.single().sequence)
        assertEquals("7", restoredSeventh.promotions.single().promotedLevel)
        assertEquals(3, restoredSeventh.promotions.single().sequence)
        assertEquals(CpcSequenceIntegrity.ORIGINAL, restoredSnapshot.sequenceIntegrity)

        val report = PayJourneyReportBuilder.build(restored)!!
        val rows = report.sections.single { it.heading == "7th CPC Pay Journey" }.rows
        assertEquals("7th CPC starting position", rows.first().description)
        assertFalse(rows.any { it.description.contains("6th to 7th CPC conversion") })
    }

    @Test
    fun inferredDirectSeventhSnapshotRetainsInferredSequenceIntegrity() {
        val starting = SeventhCpcJourneySnapshot("6", 35_400, 1_000L, startingDniMillis = 2_000L)
        val snapshot = directSeventhCompleteJourneySnapshot(
            starting, emptyList(), emptyList(), CpcSequenceIntegrity.INFERRED
        )
        val record = CpcHistoryRecord(
            uniqueId = "inferred-direct-seventh",
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            savedAtMillis = 3_000L,
            startingCpc = CpcHistoryStage.SEVENTH,
            currentStage = CpcHistoryStage.SEVENTH,
            payload = CompleteJourneyPayload(snapshot)
        )

        val restored = CpcHistoryStore.decode(CpcHistoryStore.encode(record))!!
        assertEquals(
            CpcSequenceIntegrity.INFERRED,
            (restored.payload as CompleteJourneyPayload).snapshot.sequenceIntegrity
        )
    }
}
