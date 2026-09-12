package com.niyammitra.payfixationcalculator

/**
 * 6th CPC -> 7th CPC conversion model.
 *
 * The input is the pay drawn in the 6th CPC Pay Band plus Grade Pay.
 * Rule 7 of the CCS (RP) Rules, 2016 applies the uniform 2.57 fitment
 * factor to the existing pay (Pay in Pay Band + Grade Pay), after which
 * the result is placed at the equal or next higher cell in the applicable
 * 7th CPC Pay Matrix Level.
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
        SixthCpcPayBand("PB-2: ₹9,300–34,800", listOf(4200, 4600, 4800)),
        SixthCpcPayBand("PB-3: ₹15,600–39,100", listOf(5400, 6600, 7600)),
        SixthCpcPayBand("PB-4: ₹37,400–67,000", listOf(8700, 8900, 10000))
    )

    /** Mapping of 6th CPC Grade Pay within its Pay Band to the 7th CPC Level. */
    fun levelFor(payBand: SixthCpcPayBand, gradePay: Int): String? = when (payBand.title.substringBefore(":")) {
        "PB-1" -> mapOf(1800 to "1", 1900 to "2", 2000 to "3", 2400 to "4", 2800 to "5")[gradePay]
        "PB-2" -> mapOf(4200 to "6", 4600 to "7", 4800 to "8")[gradePay]
        "PB-3" -> mapOf(5400 to "9", 6600 to "11", 7600 to "12")[gradePay]
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
    val matrix = PayMatrixData
    val revised = matrix.findEqualOrNextHigher(level, rounded) ?: return null
    val next = matrix.getNextIncrement(level, revised)

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
            "Existing pay = Pay in Pay Band + Grade Pay.",
            "Existing pay is multiplied by the uniform fitment factor of 2.57.",
            "The resulting amount is rounded to the nearest rupee.",
            "The rounded amount is placed at the equal or next higher cell in the applicable 7th CPC Pay Matrix Level.",
            "For pay fixed as on 01 January 2016, the next increment accrues on 01 July 2016, subject to the applicable Rule 10 conditions."
        )
    )
}
