package com.niyammitra.payfixationcalculator

import kotlin.math.max

/**
 * Fixation options used for an event occurring while the employee is drawing
 * pay under the 5th CPC revised scale.
 */
enum class FifthCpcFixationOption {
    FROM_EVENT_DATE,
    FROM_DNI
}

data class FifthCpcEventResult(
    val eventDate: Long,
    val fixationOption: FifthCpcFixationOption,
    val oldPay: Int,
    val firstIncrementedPay: Int,
    val secondIncrementedPay: Int? = null,
    val newPay: Int,
    val targetScale: FifthCpcScale,
    val nextIncrementDate: Long,
    val ruleBasis: List<String>
)

fun calculateFifthCpcEvent(
    eventDate: Long,
    currentPay: Int,
    currentScale: FifthCpcScale,
    targetScale: FifthCpcScale,
    fixationOption: FifthCpcFixationOption
): FifthCpcEventResult {
    require(currentPay > 0) { "Current 5th CPC basic pay must be positive." }
    require(eventDate >= fifthCpcConversionDate() && eventDate <= fifthCpcEndDate()) {
        "The 5th CPC event date must fall between 01 January 1996 and 31 December 2005."
    }

    val firstIncrementedPay = calculateFifthCpcNextStage(currentPay, currentScale)
        ?: currentPay

    val fixationBase = if (fixationOption == FifthCpcFixationOption.FROM_DNI) {
        val dniPay = calculateFifthCpcNextStage(firstIncrementedPay, currentScale)
            ?: firstIncrementedPay
        dniPay
    } else {
        firstIncrementedPay
    }

    val newPay = findEqualOrNextHigherFifthCpcStage(fixationBase, targetScale)
    val nextIncrementDate = if (fixationOption == FifthCpcFixationOption.FROM_DNI) {
        addFifthCpcYear(nextJulyOnOrAfterFifthCpc(eventDate))
    } else {
        nextJulyOnOrAfterFifthCpc(eventDate)
    }

    val ruleBasis = buildList {
        add("The event is processed under the 5th CPC revised-pay structure using the applicable Fundamental Rule pay-fixation method.")
        add("One increment in the lower/current 5th CPC scale is taken for promotion / financial upgradation fixation.")
        if (fixationOption == FifthCpcFixationOption.FROM_DNI) {
            add("From-DNI option: the intervening annual increment is first allowed in the current scale, followed by the event-related increment before placement in the higher scale.")
        }
        add("The resulting pay is placed at the stage equal to or next higher than the fixation amount in the selected higher 5th CPC scale.")
        add("The 5th CPC annual increment cycle is based on 01 July, subject to the applicable rule and service-history provisos.")
    }

    return FifthCpcEventResult(
        eventDate = eventDate,
        fixationOption = fixationOption,
        oldPay = currentPay,
        firstIncrementedPay = firstIncrementedPay,
        secondIncrementedPay = if (fixationOption == FifthCpcFixationOption.FROM_DNI) fixationBase else null,
        newPay = newPay,
        targetScale = targetScale,
        nextIncrementDate = nextIncrementDate,
        ruleBasis = ruleBasis
    )
}

fun calculateFifthCpcNextStage(currentPay: Int, scale: FifthCpcScale): Int? {
    val stages = fifthCpcStages(scale.title)
    return stages.firstOrNull { it > currentPay }
}

fun findEqualOrNextHigherFifthCpcStage(currentPay: Int, scale: FifthCpcScale): Int {
    val stages = fifthCpcStages(scale.title)
    if (stages.isEmpty()) return max(scale.payBandMinimum, currentPay)
    return stages.firstOrNull { it >= currentPay } ?: stages.last()
}

private fun fifthCpcStages(notation: String): List<Int> {
    val numbers = Regex("\\d+").findAll(notation.substringBefore(" (PB-"))
        .map { it.value.toInt() }
        .toList()
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

private fun nextJulyOnOrAfterFifthCpc(date: Long): Long {
    val source = java.util.Calendar.getInstance().apply { timeInMillis = date }
    val year = source.get(java.util.Calendar.YEAR)
    val july = java.util.Calendar.getInstance().apply {
        clear()
        set(year, java.util.Calendar.JULY, 1, 0, 0, 0)
    }
    return if (date <= july.timeInMillis) july.timeInMillis else java.util.Calendar.getInstance().apply {
        clear()
        set(year + 1, java.util.Calendar.JULY, 1, 0, 0, 0)
    }.timeInMillis
}

private fun addFifthCpcYear(date: Long): Long = java.util.Calendar.getInstance().apply {
    timeInMillis = date
    add(java.util.Calendar.YEAR, 1)
}.timeInMillis

private fun fifthCpcConversionDate(): Long = java.util.Calendar.getInstance().apply {
    clear()
    set(1996, java.util.Calendar.JANUARY, 1, 0, 0, 0)
}.timeInMillis

private fun fifthCpcEndDate(): Long = java.util.Calendar.getInstance().apply {
    clear()
    set(2005, java.util.Calendar.DECEMBER, 31, 0, 0, 0)
}.timeInMillis
