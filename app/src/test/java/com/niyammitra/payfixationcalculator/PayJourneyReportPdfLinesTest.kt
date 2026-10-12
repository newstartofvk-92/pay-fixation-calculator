package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayJourneyReportPdfLinesTest {
    @Test
    fun reportLinesRetainEveryTimelineRowAndFinalPositionAfterLongJourney() {
        val start = CpcPayPosition(CpcHistoryStage.SEVENTH, 35_400, level = "6")
        val rows = buildList {
            add(PayJourneyReportRow(100L, 0, CpcHistoryStage.SEVENTH,
                CpcJourneyEventKind.STARTING_POSITION, "7th CPC starting position", start))
            repeat(125) { index ->
                val pay = 35_400 + (index + 1) * 1_100
                add(PayJourneyReportRow(index + 1L, index + 1, CpcHistoryStage.SEVENTH,
                    CpcJourneyEventKind.ANNUAL_INCREMENT, "Annual increment ${index + 1}",
                    CpcPayPosition(CpcHistoryStage.SEVENTH, pay, level = "6"),
                    dniMillis = (index + 1L) * 1_000))
            }
        }
        val final = rows.last().position
        val finalDni = 999_999L
        val report = PayJourneyReport(
            "Long journey", 1L, CpcHistoryStage.SEVENTH, 100L,
            listOf(PayJourneyReportSection("7th CPC Pay Journey", rows)), final, finalDni
        )

        val lines = payJourneyReportPdfLines(report)

        assertEquals(125, lines.count { it.contains("Annual increment ") })
        assertTrue(lines.any { it.contains("Annual increment 125") })
        assertTrue(lines.last().contains("Final DNI / next increment date"))
        assertTrue(lines[lines.lastIndex - 1].contains("Final Pay Position"))
        assertTrue(lines[lines.lastIndex - 1].contains("Basic Pay"))
    }

    @Test
    fun eventLinesIncludeBothDatesFixationPayBeforeAfterAndReplayWarning() {
        val before = CpcPayPosition(CpcHistoryStage.SIXTH, 13_500, payBandId = "PB-2", gradePay = 4_200, payInPayBand = 9_300)
        val after = CpcPayPosition(CpcHistoryStage.SIXTH, 14_310, payBandId = "PB-2", gradePay = 4_600, payInPayBand = 9_710)
        val report = PayJourneyReport(
            "History", 1L, CpcHistoryStage.SIXTH, 10L,
            listOf(PayJourneyReportSection("6th CPC Pay Journey", listOf(
                PayJourneyReportRow(10L, 0, CpcHistoryStage.SIXTH, CpcJourneyEventKind.STARTING_POSITION,
                    "Starting position", before),
                PayJourneyReportRow(20L, 1, CpcHistoryStage.SIXTH, CpcJourneyEventKind.PROMOTION,
                    "Promotion", after, 30L, CpcFixationBasis.DNI, "From DNI",
                    eventDateMillis = 20L, implementationDateMillis = 21L)
            ))),
            after, 40L,
            replayWarnings = listOf("Saved 6th CPC values are not replay-validated.")
        )

        val lines = payJourneyReportPdfLines(report)
        val promotion = lines.indexOfFirst { it.contains("Promotion") }

        assertTrue(lines.contains("Saved 6th CPC values are not replay-validated."))
        assertTrue(lines[promotion].contains("Event date"))
        assertTrue(lines[promotion].contains("Implementation date"))
        assertTrue(lines[promotion + 1].startsWith("Pay before:"))
        assertTrue(lines[promotion + 2].startsWith("DNI / increment date:"))
        assertTrue(lines[promotion + 3].contains("Fixation basis: DNI"))
        assertTrue(lines.last().startsWith("Final DNI / next increment date:"))
    }

    @Test
    fun conversionLineIncludesTheSourcePayPosition() {
        val source = CpcPayPosition(CpcHistoryStage.SIXTH, 13_500, payBandId = "PB-2", gradePay = 4_200, payInPayBand = 9_300)
        val converted = CpcPayPosition(CpcHistoryStage.SEVENTH, 35_400, level = "6")
        val report = PayJourneyReport(
            "Complete journey", 1L, CpcHistoryStage.FOURTH, 10L,
            listOf(PayJourneyReportSection("7th CPC Pay Journey", listOf(
                PayJourneyReportRow(20L, 0, CpcHistoryStage.SEVENTH, CpcJourneyEventKind.CPC_CONVERSION,
                    "6th to 7th CPC conversion", converted, sourcePosition = source)
            ))),
            converted, 30L
        )

        val lines = payJourneyReportPdfLines(report)

        assertTrue(lines.any { it.startsWith("Converted from: 6th CPC") && it.contains("Grade Pay") && it.contains("4,200") })
    }
}
