package com.niyammitra.payfixationcalculator

import kotlin.math.ceil

/** Financial-upgradation regimes during the 6th CPC period. */
enum class SixthCpcFinancialUpgradation {
    ACP,
    MACP
}

enum class SixthCpcFixationOption {
    FROM_EVENT_DATE,
    FROM_DNI
}

data class SixthCpcEventResult(
    val eventType: String,
    val financialUpgradation: SixthCpcFinancialUpgradation? = null,
    val fixationOption: SixthCpcFixationOption = SixthCpcFixationOption.FROM_EVENT_DATE,
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

private const val ACP_CUTOFF_YEAR = 2008
private const val ACP_CUTOFF_MONTH = java.util.Calendar.AUGUST
private const val ACP_CUTOFF_DAY = 31

fun financialUpgradationForSixthCpcEvent(eventDate: Long): SixthCpcFinancialUpgradation {
    val cutoff = java.util.Calendar.getInstance().apply {
        clear()
        set(ACP_CUTOFF_YEAR, ACP_CUTOFF_MONTH, ACP_CUTOFF_DAY, 23, 59, 59)
        set(java.util.Calendar.MILLISECOND, 999)
    }.timeInMillis
    return if (eventDate <= cutoff) SixthCpcFinancialUpgradation.ACP else SixthCpcFinancialUpgradation.MACP
}

fun calculateSixthCpcPromotionOrMacp(
    payInPayBand: Int,
    currentGradePay: Int,
    targetGradePay: Int,
    eventDate: Long,
    eventType: String,
    fixationOption: SixthCpcFixationOption = SixthCpcFixationOption.FROM_EVENT_DATE,
    financialUpgradation: SixthCpcFinancialUpgradation? = null
): SixthCpcEventResult {
    require(payInPayBand > 0) { "Pay in Pay Band must be positive." }
    require(targetGradePay > currentGradePay) { "The target Grade Pay must be higher than the current Grade Pay." }

    val scheme = financialUpgradation ?: if (eventType == "Financial Upgradation") financialUpgradationForSixthCpcEvent(eventDate) else null
    val annualIncrementDate = nextSixthCpcIncrementDate(eventDate)

    var fixationPayInBand = payInPayBand
    var totalIncrement = calculateSixthCpcIncrement(payInPayBand, currentGradePay)

    if (fixationOption == SixthCpcFixationOption.FROM_DNI) {
        fixationPayInBand += totalIncrement
        val promotionIncrement = calculateSixthCpcIncrement(fixationPayInBand, currentGradePay)
        fixationPayInBand += promotionIncrement
        totalIncrement += promotionIncrement
    } else {
        fixationPayInBand += totalIncrement
    }

    val targetBand = bandForGradePay(targetGradePay)
    val newPayInBand = minOf(maxOf(fixationPayInBand, sixthCpcPayBandMinimum(targetBand)), sixthCpcPayBandMaximum(targetBand))

    val basis = mutableListOf<String>()
    if (scheme == SixthCpcFinancialUpgradation.ACP) {
        basis += "Event date is on or before 31 August 2008: ACP regime applies. The applicable ACP promotional scale/Grade Pay is selected by the user."
        basis += "ACP financial upgradation is regulated under the applicable ACP instructions and FR 22(1)(a)(1); it is not treated as a post-01 September 2008 MACP event."
    } else if (scheme == SixthCpcFinancialUpgradation.MACP) {
        basis += "Event date is on or after 01 September 2008: Modified ACP (MACP) regime applies."
        basis += "MACP financial upgradation is to the immediate next higher Grade Pay in the prescribed hierarchy and is personal financial upgradation."
    } else {
        basis += "Regular promotion fixation is governed by the applicable promotion/fixation provisions; the promoted post Grade Pay is selected by the user."
    }
    basis += "For 6th CPC fixation, the increment is 3% of Pay in Pay Band plus existing Grade Pay, rounded up to the next multiple of Rs.10, and added to Pay in Pay Band."
    basis += if (fixationOption == SixthCpcFixationOption.FROM_DNI) {
        "From-DNI option: the normal annual increment on the 1 July DNI is applied in the lower grade before the promotion/financial-upgradation fixation."
    } else {
        "From-event-date option: the promotion/financial-upgradation fixation is applied from the event date."
    }
    basis += "During the 6th CPC period the normal annual increment date is 1 July; there is no separate 1 January DNI in this workflow."

    return SixthCpcEventResult(
        eventType = eventType,
        financialUpgradation = scheme,
        fixationOption = fixationOption,
        eventDate = eventDate,
        oldPayInPayBand = payInPayBand,
        oldGradePay = currentGradePay,
        increment = totalIncrement,
        newPayInPayBand = newPayInBand,
        newGradePay = targetGradePay,
        newPayBand = targetBand.title,
        revisedBasicPay = newPayInBand + targetGradePay,
        nextIncrementDate = if (fixationOption == SixthCpcFixationOption.FROM_DNI) addSixthYears(annualIncrementDate, 1) else nextSixthCpcIncrementDate(eventDate),
        ruleBasis = basis
    )
}

private fun calculateSixthCpcIncrement(payInPayBand: Int, gradePay: Int): Int {
    val base = payInPayBand + gradePay
    return (ceil((base * 0.03) / 10.0) * 10.0).toInt()
}

fun bandForGradePay(gradePay: Int): SixthCpcPayBand {
    return SixthToSeventhCpcData.payBands.firstOrNull { gradePay in it.gradePays }
        ?: throw IllegalArgumentException("No 6th CPC Pay Band is mapped to Grade Pay Rs.$gradePay")
}

fun sixthCpcPayBandMinimum(payBand: SixthCpcPayBand): Int = when (payBand.title.substringBefore(":")) {
    "PB-1" -> 5200
    "PB-2" -> 9300
    "PB-3" -> 15600
    "PB-4" -> 37400
    else -> throw IllegalArgumentException("Unknown 6th CPC Pay Band: ${payBand.title}")
}

fun sixthCpcPayBandMaximum(payBand: SixthCpcPayBand): Int = when (payBand.title.substringBefore(":")) {
    "PB-1" -> 20200
    "PB-2" -> 34800
    "PB-3" -> 39100
    "PB-4" -> 67000
    else -> throw IllegalArgumentException("Unknown 6th CPC Pay Band: ${payBand.title}")
}

fun sixthCpcGradePayHierarchy(): List<Int> = listOf(1800, 1900, 2000, 2400, 2800, 4200, 4600, 4800, 5400, 6600, 7600, 8700, 8900, 10000, 12000)

fun nextSixthCpcMacpGradePay(currentGradePay: Int): Int? = sixthCpcGradePayHierarchy().firstOrNull { it > currentGradePay }

private fun nextSixthCpcIncrementDate(eventDate: Long): Long {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = eventDate }
    val year = calendar.get(java.util.Calendar.YEAR)
    val july = java.util.Calendar.getInstance().apply {
        clear()
        set(year, java.util.Calendar.JULY, 1, 0, 0, 0)
    }
    return if (eventDate <= july.timeInMillis) july.timeInMillis else addSixthYears(july.timeInMillis, 1)
}

private fun addSixthYears(date: Long, years: Int): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = date
    add(java.util.Calendar.YEAR, years)
}.timeInMillis
