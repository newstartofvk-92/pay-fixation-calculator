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

    private fun calendarDate(year: Int, month: Int, day: Int): Long = Calendar.getInstance().apply {
        clear()
        set(year, month, day, 12, 0, 0)
    }.timeInMillis
}
