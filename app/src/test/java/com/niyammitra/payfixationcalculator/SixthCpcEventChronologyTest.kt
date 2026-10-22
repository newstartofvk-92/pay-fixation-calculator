package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SixthCpcEventChronologyTest {
    @Test
    fun fromDniPromotionBeforeCutoffDisablesUnavailableIncrementButMakesRefixedPositionReady() {
        val eventDate = date(2015, Calendar.FEBRUARY, 3)
        val cutoff = date(2015, Calendar.JULY, 1)
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 17_450,
            currentGradePay = 4_600,
            targetGradePay = 4_800,
            eventDate = eventDate,
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_DNI
        )
        val chain = SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = result, sequence = 1)

        val nextIncrement = planNextSixthCpcEventIncrement(chain, cutoff)

        assertEquals(eventDate, result.eventDate)
        assertEquals(cutoff, result.fixationEffectiveDate)
        assertEquals(date(2016, Calendar.JULY, 1), result.nextIncrementDate)
        assertEquals(18_810, result.newPayInPayBand)
        assertEquals(4_800, result.newGradePay)
        assertEquals(23_610, result.revisedBasicPay)
        assertNull(nextIncrement.increment)
        assertFalse(nextIncrement.unavailableReason.isNullOrBlank())
        assertTrue(nextIncrement.unavailableReason!!.contains("01 July 2016"))
        assertTrue(nextIncrement.unavailableReason!!.contains("01 July 2015"))
        assertFalse(nextIncrement.increment != null) // The production button uses this exact enabled condition.
        assertTrue(isSixthCpcJourneyReady(result.fixationEffectiveDate, result.nextIncrementDate, cutoff))
    }

    @Test
    fun exactFromDniPromotionUsesReplayedPositionForSummaryReadinessAndConversion() {
        val start = date(2014, Calendar.JULY, 1)
        val eventDate = date(2015, Calendar.FEBRUARY, 3)
        val cutoff = date(2015, Calendar.JULY, 1)
        val promotion = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 13_500,
            currentGradePay = 4_600,
            targetGradePay = 4_800,
            eventDate = eventDate,
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_DNI
        )
        val chain = SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = promotion, sequence = 1)
        val position = resolveSixthCpcJourneyPosition(
            startingPayInPayBand = 13_500,
            startingGradePay = 4_600,
            startingPayBand = "PB-2",
            startingPositionDate = start,
            eventChains = listOf(chain)
        )

        // The event date remains distinct from the effective date of From-DNI pay.
        assertEquals(eventDate, promotion.eventDate)
        assertEquals(cutoff, promotion.fixationEffectiveDate)
        assertEquals(date(2016, Calendar.JULY, 1), promotion.nextIncrementDate)
        assertEquals(1_110, promotion.increment)
        assertEquals(14_610, promotion.newPayInPayBand)
        assertEquals(4_800, promotion.newGradePay)
        assertEquals(19_410, promotion.revisedBasicPay)

        // This is the same resolver used for the on-screen current-position summary,
        // readiness, and conversion inputs; it must no longer report the base position.
        assertEquals("PB-2: ₹9,300–34,800", position.payBand)
        assertEquals(14_610, position.payInPayBand)
        assertEquals(4_800, position.gradePay)
        assertEquals(cutoff, position.date)
        assertEquals(19_410, position.payInPayBand!! + position.gradePay!!)
        assertTrue(isSixthCpcJourneyReady(position.date, promotion.nextIncrementDate, cutoff))

        val band = SixthToSeventhCpcData.payBands.single { it.title.startsWith("PB-2:") }
        val conversion = calculateSixthToSeventhCpc(position.payInPayBand!!, position.gradePay!!, band)!!
        assertEquals(14_610, conversion.payInPayBand)
        assertEquals(4_800, conversion.gradePay)
        assertEquals(19_410, conversion.existingPay)
        assertEquals(14_610 + 4_800, conversion.existingPay)

        val nextIncrement = planNextSixthCpcEventIncrement(chain, cutoff)
        assertNull(nextIncrement.increment)
        assertTrue(nextIncrement.unavailableReason!!.contains("01 July 2016"))
        // The July 2015 annual increment is already included in the From-DNI fixation;
        // a separate event-chain increment would duplicate it.
    }

    @Test
    fun fromEventDateKeepsEventPayAndDoesNotUseDniRefixationPosition() {
        val start = date(2014, Calendar.JULY, 1)
        val eventDate = date(2015, Calendar.FEBRUARY, 3)
        val eventDateResult = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 13_500,
            currentGradePay = 4_600,
            targetGradePay = 4_800,
            eventDate = eventDate,
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
        )
        val position = resolveSixthCpcJourneyPosition(
            startingPayInPayBand = 13_500,
            startingGradePay = 4_600,
            startingPayBand = "PB-2",
            startingPositionDate = start,
            eventChains = listOf(SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = eventDateResult, sequence = 1))
        )

        assertEquals(eventDate, eventDateResult.eventDate)
        assertEquals(eventDate, eventDateResult.fixationEffectiveDate)
        assertEquals(550, eventDateResult.increment)
        assertEquals(14_050, eventDateResult.newPayInPayBand)
        assertEquals(4_800, eventDateResult.newGradePay)
        assertEquals(18_850, eventDateResult.revisedBasicPay)
        assertEquals(eventDate, position.date)
        assertEquals(14_050, position.payInPayBand)
        assertEquals(4_800, position.gradePay)
        assertEquals(18_850, position.payInPayBand!! + position.gradePay!!)
        assertTrue(isSixthCpcJourneyReady(position.date, eventDateResult.nextIncrementDate, date(2015, Calendar.JULY, 1)))
    }

    @Test
    fun startingPositionWithIncrementDueAtCutoffIsNotReadyUntilThatIncrementIsRecorded() {
        val cutoff = date(2015, Calendar.JULY, 1)
        val startingDate = date(2014, Calendar.JULY, 1)

        assertFalse(isSixthCpcJourneyReady(startingDate, date(2015, Calendar.JULY, 1), cutoff))
        assertTrue(isSixthCpcJourneyReady(cutoff, date(2016, Calendar.JULY, 1), cutoff))
    }

    @Test
    fun acceptedChainAddEditAndDeleteRecomputeTheResolvedCurrentPosition() {
        val start = date(2014, Calendar.JULY, 1)
        val firstEvent = calculateSixthCpcPromotionOrMacp(
            13_500, 4_600, 4_800, date(2015, Calendar.FEBRUARY, 3), "Promotion", SixthCpcFixationOption.FROM_DNI
        )
        val secondEvent = calculateSixthCpcPromotionOrMacp(
            14_610, 4_800, 5_400, date(2015, Calendar.AUGUST, 1), "Promotion", SixthCpcFixationOption.FROM_EVENT_DATE
        )
        val initial = resolveSixthCpcJourneyPosition(13_500, 4_600, "PB-2", start, emptyList())
        assertEquals(13_500, initial.payInPayBand)

        val added = listOf(SixthCpcEventChain(SixthCpcEventKind.PROMOTION, result = firstEvent, sequence = 1))
        val afterAdd = resolveSixthCpcJourneyPosition(13_500, 4_600, "PB-2", start, added)
        assertEquals(firstEvent.newPayInPayBand, afterAdd.payInPayBand)
        assertEquals(firstEvent.fixationEffectiveDate, afterAdd.date)

        // Editing an accepted chain replaces its result; the resolver follows the updated state.
        val edited = added.map { it.copy(result = secondEvent) }
        val afterEdit = resolveSixthCpcJourneyPosition(13_500, 4_600, "PB-2", start, edited)
        assertEquals(secondEvent.newPayInPayBand, afterEdit.payInPayBand)
        assertEquals(secondEvent.fixationEffectiveDate, afterEdit.date)

        val afterDelete = resolveSixthCpcJourneyPosition(13_500, 4_600, "PB-2", start, emptyList())
        assertEquals(13_500, afterDelete.payInPayBand)
        assertEquals(4_600, afterDelete.gradePay)
        assertEquals(start, afterDelete.date)
    }

    private fun date(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis
}
