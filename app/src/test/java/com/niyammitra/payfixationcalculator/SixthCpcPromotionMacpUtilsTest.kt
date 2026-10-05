package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SixthCpcPromotionMacpUtilsTest {

    @Test
    fun financialUpgradationBeforeSeptember2008IsAcp() {
        val date = calendarDate(2008, Calendar.AUGUST, 31)
        assertEquals(SixthCpcFinancialUpgradation.ACP, financialUpgradationForSixthCpcEvent(date))
    }

    @Test
    fun financialUpgradationFromSeptember2008IsMacp() {
        val date = calendarDate(2008, Calendar.SEPTEMBER, 1)
        assertEquals(SixthCpcFinancialUpgradation.MACP, financialUpgradationForSixthCpcEvent(date))
    }

    @Test
    fun macpAutomaticallyUsesImmediateNextGradePay() {
        assertEquals(4600, nextSixthCpcMacpGradePay(4200))
        assertEquals(4800, nextSixthCpcMacpGradePay(4600))
        assertEquals(4600, targetGradePayForSixthCpcFinancialUpgradation(4200, SixthCpcFinancialUpgradation.MACP, acpGradePay = 12000))
    }

    @Test
    fun acpUsesApplicableUserSelectedGradePay() {
        assertEquals(4600, targetGradePayForSixthCpcFinancialUpgradation(4200, SixthCpcFinancialUpgradation.ACP, acpGradePay = 4600))
    }

    @Test
    fun eventDateFixationUsesOnePromotionIncrement() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9300,
            currentGradePay = 4200,
            targetGradePay = 4600,
            eventDate = calendarDate(2008, Calendar.APRIL, 1),
            eventType = "Financial Upgradation",
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE,
            financialUpgradation = SixthCpcFinancialUpgradation.ACP
        )
        assertEquals(410, result.increment)
        assertEquals(9710, result.newPayInPayBand)
        assertEquals(4600, result.newGradePay)
        assertEquals(14310, result.revisedBasicPay)
    }

    @Test
    fun macpIgnoresUserSuppliedTargetAndUsesImmediateNextGradePay() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9300,
            currentGradePay = 4200,
            targetGradePay = 12000,
            eventDate = calendarDate(2008, Calendar.SEPTEMBER, 1),
            eventType = "Financial Upgradation",
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE,
            financialUpgradation = SixthCpcFinancialUpgradation.MACP
        )
        assertEquals(4600, result.newGradePay)
        assertEquals(14310, result.revisedBasicPay)
    }

    @Test
    fun fromDniFixationAppliesAnnualIncrementBeforePromotionFixation() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9300,
            currentGradePay = 4200,
            targetGradePay = 4600,
            eventDate = calendarDate(2008, Calendar.APRIL, 1),
            eventType = "Financial Upgradation",
            fixationOption = SixthCpcFixationOption.FROM_DNI,
            financialUpgradation = SixthCpcFinancialUpgradation.ACP
        )
        assertEquals(830, result.increment)
        assertEquals(10130, result.newPayInPayBand)
        assertEquals(4600, result.newGradePay)
        assertEquals(14730, result.revisedBasicPay)
    }

    @Test
    fun promotionFromEventDateUsesExistingEventDateFixation() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9_300,
            currentGradePay = 4_200,
            targetGradePay = 4_600,
            eventDate = calendarDate(2008, Calendar.APRIL, 1),
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
        )
        assertEquals(SixthCpcFixationOption.FROM_EVENT_DATE, result.fixationOption)
        assertEquals(410, result.increment)
        assertEquals(9_710, result.newPayInPayBand)
        assertEquals(14_310, result.revisedBasicPay)
        assertEquals(calendarDateAtMidnight(2009, Calendar.JULY, 1), result.nextIncrementDate)
    }

    @Test
    fun fromEventDatePromotionUsesClarification2bIncrementDatesAtExamplesAndBoundaries() {
        val cases = listOf(
            Triple(calendarDate(2012, Calendar.MARCH, 15), 2013, "15 March"),
            Triple(calendarDate(2012, Calendar.SEPTEMBER, 15), 2013, "15 September"),
            Triple(calendarDate(2012, Calendar.JUNE, 30), 2013, "30 June"),
            Triple(calendarDate(2012, Calendar.JULY, 1), 2012, "1 July"),
            Triple(calendarDate(2012, Calendar.JULY, 2), 2013, "2 July"),
            Triple(calendarDate(2012, Calendar.DECEMBER, 31), 2013, "31 December"),
            Triple(calendarDate(2012, Calendar.JANUARY, 1), 2012, "1 January"),
            Triple(calendarDate(2012, Calendar.JANUARY, 2), 2013, "2 January")
        )

        cases.forEach { (eventDate, expectedYear, label) ->
            val result = calculateSixthCpcPromotionOrMacp(
                payInPayBand = 9_300,
                currentGradePay = 4_200,
                targetGradePay = 4_600,
                eventDate = eventDate,
                eventType = "Promotion",
                fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
            )

            assertEquals("Unexpected next increment date for $label", calendarDateAtMidnight(expectedYear, Calendar.JULY, 1), result.nextIncrementDate)
        }
    }

    @Test
    fun januaryToJuneFromEventDatePromotionDoesNotGrantJulyIncrementInPromotionYear() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9_300,
            currentGradePay = 4_200,
            targetGradePay = 4_600,
            eventDate = calendarDate(2012, Calendar.MARCH, 15),
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
        )

        assertEquals(410, result.increment)
        assertEquals(9_710, result.newPayInPayBand)
        assertEquals(14_310, result.revisedBasicPay)
        assertEquals(calendarDateAtMidnight(2013, Calendar.JULY, 1), result.nextIncrementDate)
    }

    @Test
    fun promotionFromDniUsesExistingDniBasedFixation() {
        val result = calculateSixthCpcPromotionOrMacp(
            payInPayBand = 9_300,
            currentGradePay = 4_200,
            targetGradePay = 4_600,
            eventDate = calendarDate(2008, Calendar.APRIL, 1),
            eventType = "Promotion",
            fixationOption = SixthCpcFixationOption.FROM_DNI
        )
        assertEquals(SixthCpcFixationOption.FROM_DNI, result.fixationOption)
        assertEquals(830, result.increment)
        assertEquals(10_130, result.newPayInPayBand)
        assertEquals(14_730, result.revisedBasicPay)
        assertEquals(calendarDateAtMidnight(2009, Calendar.JULY, 1), result.nextIncrementDate)
    }

    private fun calendarDateAtMidnight(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 0, 0, 0)
    }.timeInMillis

    private fun calendarDate(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis
}
