package com.niyammitra.payfixationcalculator

import kotlin.math.roundToInt

/** Exact Rule 7 calculation using the stage structure encoded in each 5th CPC revised scale. */
fun calculateFourthToFifthCpcPrecise(existingBasicPay: Int, scale: FourthCpcScale): FourthToFifthResult {
    require(existingBasicPay > 0) { "Existing basic pay must be positive." }
    require(isKnownFourthCpcStage(existingBasicPay, scale)) { fourthCpcPayValidationMessage(existingBasicPay, scale) }

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
        nextIncrementNote = "Under Rule 8, the next increment is generally due on the date the employee would have drawn the increment had the employee continued in the existing scale, subject to the rule's provisos.",
        ruleBasis = listOf(
            "Rule 7: 40% of existing basic pay is added to existing emoluments.",
            "Existing emoluments include basic pay, DA at the 1510 CPI index, and the first and second interim relief instalments.",
            "DA at 01.01.1996: 148% of basic pay.",
            "First interim relief: Rs.100 per month; second interim relief: 10% of basic pay subject to a minimum of Rs.100.",
            "The resulting amount is fixed at the next stage in the corresponding 5th CPC revised scale, subject to the minimum/maximum provisions.",
            "Rule 8: the next increment in the revised scale is granted on the date the employee would have drawn the increment in the existing scale, subject to the rule's provisos.",
            "The Rules also contain bunching and one-increment-for-every-three-existing-increments safeguards; those case-specific adjustments are not inferred without the necessary service-history data."
        )
    )
}

/** Returns the next 5th CPC stage after the current revised basic pay. */
fun calculateFourthToFifthNextIncrement(currentPay: Int, scale: FourthCpcScale): Int? {
    val stages = revisedStages(scale.revisedScale)
    if (stages.isEmpty() || scale.fixed) return null
    return stages.firstOrNull { it > currentPay }
}

private fun exactNextStage(value: Int, scale: FourthCpcScale): Int {
    val stages = revisedStages(scale.revisedScale)
    require(stages.isNotEmpty()) { "The corresponding 5th CPC scale does not contain a usable stage schedule." }
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
