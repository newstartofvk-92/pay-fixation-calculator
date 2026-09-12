package com.niyammitra.payfixationcalculator

/**
 * 6th CPC -> 7th CPC conversion under Rule 7 of the CCS (RP) Rules, 2016.
 *
 * This module handles the ordinary/general employee case where the existing
 * basic pay is Pay in Pay Band + Grade Pay. Medical officers entitled to
 * NPA/practice allowance require a separate Rule 7 treatment and are not
 * silently routed through this calculation.
 */
data class SixthCpcPayBand(
    val title: String,
    val gradePays: List<Int>
)

data class SixthToSeventhResult(
    val payInPayBand: Int,
    val gradePay: Int,
    val existingPay: Int,
    val fitmentFactor: Double,
    val multipliedPay: Double,
    val roundedPay: Int,
    val level: String,
    val revisedBasicPay: Int,
    val nextIncrementDate: String,
    val nextIncrementPay: Int?,
    val ruleBasis: List<String>
)

object SixthToSeventhCpcData {
    val payBands = listOf(
        SixthCpcPayBand("PB-1: ₹5,200–20,200", listOf(1800, 1900, 2000, 2400, 2800)),
        SixthCpcPayBand("PB-2: ₹9,300–34,800", listOf(4200, 4600, 4800, 5400)),
        SixthCpcPayBand("PB-3: ₹15,600–39,100", listOf(5400, 6600, 7600)),
        SixthCpcPayBand("PB-4: ₹37,400–67,000", listOf(8700, 8900, 10000))
    )

    /**
     * Grade Pay is not sufficient by itself for GP 5400: PB-2 GP 5400 is
     * Level 9, whereas PB-3 GP 5400 is Level 10.
     */
    fun levelFor(payBand: SixthCpcPayBand, gradePay: Int): String? = when (payBand.title.substringBefore(":")) {
        "PB-1" -> mapOf(1800 to "1", 1900 to "2", 2000 to "3", 2400 to "4", 2800 to "5")[gradePay]
        "PB-2" -> mapOf(4200 to "6", 4600 to "7", 4800 to "8", 5400 to "9")[gradePay]
        "PB-3" -> mapOf(5400 to "10", 6600 to "11", 7600 to "12")[gradePay]
        "PB-4" -> mapOf(8700 to "13", 8900 to "13A", 10000 to "14")[gradePay]
        else -> null
    }
}

fun calculateSixthToSeventhCpc(
    payInPayBand: Int,
    gradePay: Int,
    payBand: SixthCpcPayBand
): SixthToSeventhResult? {
    if (payInPayBand < 0) return null

    val level = SixthToSeventhCpcData.levelFor(payBand, gradePay) ?: return null
    val existingPay = payInPayBand + gradePay
    val multiplied = existingPay * 2.57
    val rounded = kotlin.math.round(multiplied).toInt()
    val stages = PayMatrixData.getPayStages(level)
    if (stages.isEmpty()) return null

    // Rule 7(1)(a)(ii): if the calculated amount is below the first cell,
    // pay is fixed at the minimum/first cell of the applicable level.
    val revised = stages.firstOrNull { it >= rounded } ?: stages.last()
    val next = PayMatrixData.getNextIncrement(level, revised)

    return SixthToSeventhResult(
        payInPayBand = payInPayBand,
        gradePay = gradePay,
        existingPay = existingPay,
        fitmentFactor = 2.57,
        multipliedPay = multiplied,
        roundedPay = rounded,
        level = level,
        revisedBasicPay = revised,
        nextIncrementDate = "01 July 2016",
        nextIncrementPay = next,
        ruleBasis = listOf(
            "Existing basic pay = Pay in Pay Band + Grade Pay.",
            "Existing basic pay is multiplied by the uniform fitment factor of 2.57.",
            "The result is rounded to the nearest rupee.",
            "The rounded amount is searched in the applicable 7th CPC Pay Matrix Level; if no equal cell exists, the immediate next higher cell is used.",
            "If the calculated amount is below the first cell of the applicable Level, pay is fixed at that Level's minimum/first cell.",
            "PB-2 Grade Pay ₹5,400 corresponds to Level 9; PB-3 Grade Pay ₹5,400 corresponds to Level 10.",
            "For pay fixed as on 01 January 2016, the next increment is 01 July 2016 under the Rule 10 transitional provision, subject to applicable conditions."
        )
    )
}
