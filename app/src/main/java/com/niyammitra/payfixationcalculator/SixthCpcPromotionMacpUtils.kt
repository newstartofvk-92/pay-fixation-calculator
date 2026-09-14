package com.niyammitra.payfixationcalculator

import kotlin.math.ceil

/**
 * Fixation of pay on promotion / MACP within the 6th CPC revised pay structure.
 *
 * Promotion: Rule 13 applies one increment in the existing Pay Band, calculated
 * as 3% of (Pay in Pay Band + existing Grade Pay), rounded to the next Rs.10,
 * followed by the Grade Pay attached to the promotion post.
 *
 * MACP: the financial upgradation is to the immediate next higher Grade Pay in
 * the MACP hierarchy. The same fixation mechanism is used for the pay fixation.
 */
data class SixthCpcEventResult(
    val eventType: String,
    val eventDate: Long,
    val oldPayInPayBand: Int,
    val oldGradePay: Int,
    val increment: Int,
    val newPayInPayBand: Int,
    val newGradePay: Int,
    val newPayBand: String,
    val revisedBasicPay: Int,
    val nextIncrementDate: Long,
    val ruleBasis: List<String>
)

fun calculateSixthCpcPromotionOrMacp(
    payInPayBand: Int,
    currentGradePay: Int,
    targetGradePay: Int,
    eventDate: Long,
    eventType: String
): SixthCpcEventResult {
    require(payInPayBand > 0) { "Pay in Pay Band must be positive." }
    require(targetGradePay > currentGradePay) { "The target Grade Pay must be higher than the current Grade Pay." }

    val incrementBase = payInPayBand + currentGradePay
    val increment = (ceil((incrementBase * 0.03) / 10.0) * 10.0).toInt()
    var newPayInBand = payInPayBand + increment

    val targetBand = bandForGradePay(targetGradePay)
    newPayInBand = maxOf(newPayInBand, targetBand.payBandMinimum)
    newPayInBand = minOf(newPayInBand, targetBand.payBandMaximum)

    return SixthCpcEventResult(
        eventType = eventType,
        eventDate = eventDate,
        oldPayInPayBand = payInPayBand,
        oldGradePay = currentGradePay,
        increment = increment,
        newPayInPayBand = newPayInBand,
        newGradePay = targetGradePay,
        newPayBand = targetBand.title,
        revisedBasicPay = newPayInBand + targetGradePay,
        nextIncrementDate = nextSixthCpcIncrementDate(eventDate),
        ruleBasis = listOf(
            "CCS (Revised Pay) Rules, 2008, Rule 13: on promotion from one Grade Pay to another, one increment is calculated at 3% of Pay in Pay Band plus existing Grade Pay.",
            "The increment is rounded off to the next multiple of Rs.10 and added to the existing Pay in Pay Band.",
            "The Grade Pay corresponding to the promotion post is then granted in addition to the revised Pay in Pay Band.",
            "For MACP, the financial upgradation is to the immediate next higher Grade Pay in the hierarchy; it is personal financial upgradation and not functional promotion.",
            "The next annual increment in the revised structure is governed by Rule 10 and the applicable provisos."
        )
    )
}

fun bandForGradePay(gradePay: Int): SixthCpcPayBand {
    return SixthToSeventhCpcData.payBands.firstOrNull { gradePay in it.gradePays }
        ?: throw IllegalArgumentException("No 6th CPC Pay Band is mapped to Grade Pay Rs.$gradePay")
}

fun sixthCpcGradePayHierarchy(): List<Int> = listOf(1800, 1900, 2000, 2400, 2800, 4200, 4600, 4800, 5400, 6600, 7600, 8700, 8900, 10000, 12000)

fun nextSixthCpcMacpGradePay(currentGradePay: Int): Int? = sixthCpcGradePayHierarchy().firstOrNull { it > currentGradePay }

private fun nextSixthCpcIncrementDate(eventDate: Long): Long {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = eventDate }
    val year = calendar.get(java.util.Calendar.YEAR)
    val july = java.util.Calendar.getInstance().apply {
        set(year, java.util.Calendar.JULY, 1, 0, 0, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    return if (eventDate <= july.timeInMillis) july.timeInMillis else {
        july.add(java.util.Calendar.YEAR, 1)
        july.timeInMillis
    }
}
