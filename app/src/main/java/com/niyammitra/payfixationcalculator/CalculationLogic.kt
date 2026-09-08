package com.niyammitra.payfixationcalculator

import java.util.Calendar

data class FixationResult(
    val option1: Option1Detail,
    val option2: Option2Detail
)

data class Option1Detail(
    val lowerLevelPay: Int,
    val payWithIncrement: Int,
    val finalFixedPay: Int,
    val nextDni: Long?,
    val payAfterNextDni: Int?
)

data class Option2Detail(
    val payUntilDni: Int,
    val payWithAnnualIncrement: Int,
    val payWithPromotionIncrement: Int,
    val finalFixedPay: Int,
    val nextDni: Long?,
    val payAfterNextDni: Int?
)

fun getPayFixationDniOptions(promotionDate: Long): List<Long> {
    val nextJuly = Calendar.getInstance().apply {
        timeInMillis = promotionDate
        set(Calendar.MONTH, Calendar.JULY)
        set(Calendar.DAY_OF_MONTH, 1)
        if (timeInMillis <= promotionDate) add(Calendar.YEAR, 1)
    }

    val nextJan = Calendar.getInstance().apply {
        timeInMillis = promotionDate
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
        if (timeInMillis <= promotionDate) add(Calendar.YEAR, 1)
    }

    return listOf(nextJuly.timeInMillis, nextJan.timeInMillis).sorted()
}

fun calculatePayFixation(
    currentLevel: String,
    currentPay: Int,
    promotedLevel: String,
    promotionDate: Long?,
    dniDate: Long?
): FixationResult {
    val promotedLevelMax =
        PayMatrixData.getPayStages(promotedLevel).lastOrNull() ?: Int.MAX_VALUE

    // OPTION 1
    val payWithOneIncrement =
        PayMatrixData.getNextIncrement(currentLevel, currentPay)
            ?: (currentPay * 1.03).toInt()

    val option1Pay =
        minOf(
            PayMatrixData.findEqualOrNextHigher(promotedLevel, payWithOneIncrement)
                ?: payWithOneIncrement,
            promotedLevelMax
        )

    val opt1NextDni = promotionDate?.let { calculateNextDni(it) }

    val opt1PayAfterNextDni = opt1NextDni?.let {
        PayMatrixData.getNextIncrement(promotedLevel, option1Pay)
    } ?: option1Pay

    // OPTION 2
    val option2PayBeforeDni =
        minOf(
            PayMatrixData.findEqualOrNextHigher(promotedLevel, currentPay + 1)
                ?: currentPay,
            promotedLevelMax
        )

    val annualIncrement =
        PayMatrixData.getNextIncrement(currentLevel, currentPay)
            ?: (currentPay * 1.03).toInt()

    val promotionIncrement =
        PayMatrixData.getNextIncrement(currentLevel, annualIncrement)
            ?: (annualIncrement * 1.03).toInt()

    val option2PayAfterDni =
        minOf(
            PayMatrixData.findEqualOrNextHigher(promotedLevel, promotionIncrement)
                ?: promotionIncrement,
            promotedLevelMax
        )

    val opt2NextDni = dniDate?.let { calculateNextDni(it) }

    val opt2PayAfterNextDni = opt2NextDni?.let {
        PayMatrixData.getNextIncrement(promotedLevel, option2PayAfterDni)
    } ?: option2PayAfterDni

    return FixationResult(
        option1 = Option1Detail(
            lowerLevelPay = currentPay,
            payWithIncrement = payWithOneIncrement,
            finalFixedPay = option1Pay,
            nextDni = opt1NextDni,
            payAfterNextDni = opt1PayAfterNextDni
        ),
        option2 = Option2Detail(
            payUntilDni = option2PayBeforeDni,
            payWithAnnualIncrement = annualIncrement,
            payWithPromotionIncrement = promotionIncrement,
            finalFixedPay = option2PayAfterDni,
            nextDni = opt2NextDni,
            payAfterNextDni = opt2PayAfterNextDni
        )
    )
}

private fun calculateNextDni(fixationDate: Long): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = fixationDate

    // Step 1: Count six months from fixation
    cal.add(Calendar.MONTH, 6)

    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)

    when {
        month == Calendar.JANUARY && day <= 1 -> {
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        month < Calendar.JULY -> {
            cal.set(Calendar.MONTH, Calendar.JULY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        month == Calendar.JULY && day <= 1 -> {
            cal.set(Calendar.MONTH, Calendar.JULY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        else -> {
            cal.add(Calendar.YEAR, 1)
            cal.set(Calendar.MONTH, Calendar.JANUARY)
            cal.set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    return cal.timeInMillis
}
