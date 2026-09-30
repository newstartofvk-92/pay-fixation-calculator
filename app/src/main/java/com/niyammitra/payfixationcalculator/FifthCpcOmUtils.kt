package com.niyammitra.payfixationcalculator

import java.util.Calendar

/** True when the employee's normal 5th CPC DNI falls from 01 Feb through 30 Jun 2006. */
fun isEligibleForFifthCpcOmAdjustment(normalDniMillis: Long): Boolean {
    val dni = Calendar.getInstance().apply { timeInMillis = normalDniMillis }
    val year = dni.get(Calendar.YEAR)
    val month = dni.get(Calendar.MONTH)
    val day = dni.get(Calendar.DAY_OF_MONTH)
    return year == 2006 && month in Calendar.FEBRUARY..Calendar.JUNE && day in 1..30
}

/** Builds the one-time O.M. adjustment using the selected 5th CPC scale's existing stage logic. */
fun calculateFifthCpcOmAdjustment(
    normalDniMillis: Long,
    existingBasicPay: Int,
    scale: FifthCpcScale
): FifthCpcOmAdjustmentSnapshot? {
    if (!isEligibleForFifthCpcOmAdjustment(normalDniMillis)) return null
    val adjustedPay = calculateNextFifthCpcStage(existingBasicPay, scale.title) ?: return null
    return FifthCpcOmAdjustmentSnapshot(
        normalDniMillis = normalDniMillis,
        incrementDateMillis = fifthCpcOmDate(2006, Calendar.JANUARY, 1),
        scaleTitle = scale.title,
        basicPayBefore = existingBasicPay,
        incrementAmount = adjustedPay - existingBasicPay,
        adjustedBasicPay = adjustedPay,
        nextRevisedIncrementDateMillis = fifthCpcOmDate(2006, Calendar.JULY, 1)
    )
}

private fun fifthCpcOmDate(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance().apply { clear(); set(year, month, day, 0, 0, 0) }.timeInMillis
