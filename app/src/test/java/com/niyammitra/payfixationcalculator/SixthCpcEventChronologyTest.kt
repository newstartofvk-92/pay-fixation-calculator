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
        assertTrue(isSixthCpcJourneyReady(result.fixationEffectiveDate, cutoff))
    }

    private fun date(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis
}
