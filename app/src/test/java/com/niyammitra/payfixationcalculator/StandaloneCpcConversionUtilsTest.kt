package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class StandaloneCpcConversionUtilsTest {
    @Test
    fun fourth_to_fifth_valid_stage_converts_and_invalid_stage_is_rejected() {
        val scale = FourthToFifthCpcData.scales.first { it.grade == "S-1" }
        val result = calculateFourthToFifthCpc(existingBasicPay = 750, scale = scale)

        assertEquals(2550, result.revisedBasicPay)
        assertThrows(IllegalArgumentException::class.java) {
            calculateFourthToFifthCpc(existingBasicPay = 751, scale = scale)
        }
    }

    @Test
    fun fifth_to_sixth_positive_pay_converts_and_zero_is_rejected() {
        val scale = FifthToSixthCpcData.scales.first()
        val result = calculateFifthToSixthCpc(existingBasicPay = 1000, scale = scale)

        assertEquals(1860.0, result.multipliedPay, 0.0)
        assertEquals(4440, result.payInPayBand)
        assertEquals(5740, result.revisedBasicPay)
        assertThrows(IllegalArgumentException::class.java) {
            calculateFifthToSixthCpc(existingBasicPay = 0, scale = scale)
        }
    }

    @Test
    fun sixth_to_seventh_mapped_position_converts_and_invalid_inputs_return_null() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-1") }
        val result = calculateSixthToSeventhCpc(payInPayBand = 10160, gradePay = 2400, payBand = band)

        assertEquals(32300, result?.revisedBasicPay)
        assertNull(calculateSixthToSeventhCpc(payInPayBand = -1, gradePay = 2400, payBand = band))
        assertNull(calculateSixthToSeventhCpc(payInPayBand = 10160, gradePay = 9999, payBand = band))
    }
}
