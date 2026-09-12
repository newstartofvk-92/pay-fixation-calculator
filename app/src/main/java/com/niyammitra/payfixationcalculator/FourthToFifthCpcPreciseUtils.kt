package com.niyammitra.payfixationcalculator

import kotlin.math.roundToInt

/** Exact Rule 7 calculation using the stage structure encoded in each 5th CPC revised scale. */
fun calculateFourthToFifthCpcPrecise(existingBasicPay: Int, scale: FourthCpcScale): FourthToFifthResult {
    require(existingBasicPay > 0) { "Existing basic pay must be positive." }
    require(existingBasicPay in fourthCpcInputRange(scale)) { "Existing basic pay is outside the selected 4th CPC scale." }

    val da = (existingBasicPay * 1.48).roundToInt()
    val firstIr = 100
    val secondIr = maxOf(100, (existingBasicPay * 0.10).roundToInt())
    val existingEmoluments = existingBasicPay + da + firstIr + secondIr
    val fitmentWeightage = (existingBasicPay * 0.40).roundToInt()
    val fitmentTotal = existingEmoluments + fitmentWeightage
    val revisedBasic = if (scale.fixed) {
        scale.revisedMinimum
    } else {
        exactNextStage(fitmentTotal, scale)
    }

    return FourthToFifthResult(
        scale = scale,
        existingBasicPay = existingBasicPay,
        dearnessAllowance = da,
        firstInterimRelief = firstIr,
        secondInterimRelief = secondIr,
        existingEmoluments = existingEmoluments,
        fitmentWeightage = fitmentWeightage,
        fitmentTotal = fitmentTotal,
        revisedBasicPay = revisedBasic,
        conversionDate = "01 January 1996",
        nextIncrementNote = "Under Rule 8, the next increment is generally due on the date on which it would have accrued in the existing scale, subject to the rule's provisos.",
        ruleBasis = listOf(
            "Rule 7: 40% of existing basic pay is added to existing emoluments.",
            "Existing emoluments include basic pay, DA at the 1510 CPI index, and the first and second interim relief instalments.",
            "DA at 01.01.1996: 148% of basic pay.",
            "First interim relief: Rs.100 per month; second interim relief: 10% of basic pay subject to a minimum of Rs.100.",
            "The resulting amount is fixed at the next stage in the corresponding 5th CPC revised scale, subject to the minimum/maximum provisions.",
            "The Rules also contain bunching and one-increment-for-every-three-existing-increments safeguards; those case-specific adjustments are not inferred without the necessary service-history data."
        )
    )
}

private fun exactNextStage(value: Int, scale: FourthCpcScale): Int {
    val stages = revisedStages(scale.revisedScale)
    if (stages.isEmpty()) return scale.revisedMinimum
    if (value <= stages.first()) return stages.first()
    return stages.firstOrNull { it >= value } ?: stages.last()
}

/** Parses a standard CPC notation such as 3050-75-3950-80-4590 into every stage. */
private fun revisedStages(notation: String): List<Int> {
    val numbers = Regex("\\d+").findAll(notation).map { it.value.toInt() }.toList()
    if (numbers.isEmpty()) return emptyList()
    if (numbers.size == 1) return numbers

    val result = mutableListOf<Int>()
    var current = numbers[0]
    result += current
    var index = 1
    while (index + 1 < numbers.size) {
        val increment = numbers[index]
        val end = numbers[index + 1]
        if (increment <= 0 || end < current) break
        while (current + increment <= end) {
            current += increment
            result += current
        }
        if (current < end) {
            current = end
            result += current
        }
        index += 2
    }
    return result.distinct()
}

private fun fourthCpcInputRange(scale: FourthCpcScale): IntRange = when (scale.grade) {
    "S-1" -> 750..940
    "S-2" -> 775..1025
    "S-2A" -> 775..1150
    "S-3" -> 800..1150
    "S-4" -> 825..1200
    "S-5" -> 950..1500
    "S-6" -> 975..1660
    "S-7" -> 1200..2040
    "S-8" -> 1350..2300
    "S-9" -> 1400..2660
    "S-10" -> 1640..2900
    "S-11" -> 2000..2120
    "S-12" -> 2000..3500
    "S-13" -> 2375..3750
    "S-14" -> 2500..4000
    "S-15", "NEW SCALE" -> 2200..4000
    "S-16" -> 2630..2630
    "S-17" -> 2630..2780
    "S-18" -> 3150..3350
    "S-19" -> 3000..5000
    "S-20" -> 3200..4700
    "S-21" -> 3700..5000
    "S-22" -> 3950..5000
    "S-23" -> 3700..5700
    "S-24" -> 4100..5700
    "S-25" -> 4800..5700
    "S-26" -> 5100..6300
    "S-27" -> 5100..6700
    "S-28" -> 4500..7300
    "S-29" -> 5900..7300
    "S-30" -> 7300..7600
    "S-31" -> 7300..8000
    "S-32" -> 7600..8000
    "S-33" -> 8000..8000
    "S-34" -> 9000..9000
    else -> 1..0
}
