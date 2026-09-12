package com.niyammitra.payfixationcalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SixthToSeventhCpcUtilsTest {

    @Test
    fun rule7_official_pb1_gp2400_example() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-1") }
        val result = calculateSixthToSeventhCpc(10160, 2400, band)

        assertNotNull(result)
        assertEquals(12560, result!!.existingPay)
        assertEquals(32279, result.roundedPay)
        assertEquals("4", result.level)
        assertEquals(32300, result.revisedBasicPay)
    }

    @Test
    fun pb2_gp5400_maps_to_level9() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-2") }
        val result = calculateSixthToSeventhCpc(15600, 5400, band)

        assertNotNull(result)
        assertEquals("9", result!!.level)
        assertEquals(53970, result.roundedPay)
        assertEquals(54700, result.revisedBasicPay)
    }

    @Test
    fun pb3_gp5400_maps_to_level10() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-3") }
        val result = calculateSixthToSeventhCpc(15600, 5400, band)

        assertNotNull(result)
        assertEquals("10", result!!.level)
        assertEquals(53970, result.roundedPay)
        assertEquals(56100, result.revisedBasicPay)
    }

    @Test
    fun pb4_gp8700_uses_amended_level13_minimum() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-4") }
        val result = calculateSixthToSeventhCpc(37400, 8700, band)

        assertNotNull(result)
        assertEquals("13", result!!.level)
        assertEquals(118477, result.roundedPay)
        assertEquals(123100, result.revisedBasicPay)
    }

    @Test
    fun next_increment_for_pay_fixed_on_1_january_2016_is_1_july_2016() {
        val band = SixthToSeventhCpcData.payBands.first { it.title.startsWith("PB-1") }
        val result = calculateSixthToSeventhCpc(10160, 2400, band)

        assertNotNull(result)
        assertEquals("01 July 2016", result!!.nextIncrementDate)
        assertEquals(33300, result.nextIncrementPay)
    }
}
