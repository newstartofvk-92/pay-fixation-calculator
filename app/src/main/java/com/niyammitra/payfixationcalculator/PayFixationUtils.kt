package com.niyammitra.payfixationcalculator

import java.util.Calendar

/**
 * Utility functions used by the Pay Fixation calculator.
 *
 * The calculation behavior is intentionally kept aligned with the existing
 * NiyamMitra Pay Fixation implementation. Keep changes to this file limited
 * to the calculation rules that are deliberately approved for the app.
 */

/**
 * Builds the two possible DNI dates offered to the user after promotion:
 * the next 1 July and the next 1 January, ordered chronologically.
 */
private fun calculateDniOptions(
    promotionDate: Long
): List<Long> {
    val nextJuly =
        Calendar.getInstance().apply {
            timeInMillis = promotionDate
            set(Calendar.MONTH, Calendar.JULY)
            set(Calendar.DAY_OF_MONTH, 1)
            if (timeInMillis <= promotionDate) {
                add(Calendar.YEAR, 1)
            }
        }

    val nextJan =
        Calendar.getInstance().apply {
            timeInMillis = promotionDate
            set(Calendar.MONTH, Calendar.JANUARY)
            set(Calendar.DAY_OF_MONTH, 1)
            if (timeInMillis <= promotionDate) {
                add(Calendar.YEAR, 1)
            }
        }

    return listOf(
        nextJuly.timeInMillis,
        nextJan.timeInMillis
    ).sorted()
}

/**
 * Calculates the next DNI after a fixation date using the existing
 * six-month/January-July rule used by the calculator.
 */
private fun calculateNextDni(
    fixationDate: Long
): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = fixationDate

    // Step 1: Count six months from the fixation date.
    cal.add(Calendar.MONTH, 6)

    val month = cal.get(Calendar.MONTH)
    val day = cal.get(Calendar.DAY_OF_MONTH)

    // Step 2: Move the result to the applicable 1 January or 1 July DNI.
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

/** A single illustrated step in the fixation result. */
data class FixationStep(
    val description: String,
    val pay: Int,
    val date: Long?
)

/** Contains the two alternative fixation results shown to the user. */
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

/**
 * Performs the existing NiyamMitra Pay Fixation calculation.
 *
 * IMPORTANT: The fixation procedure is the same for ordinary employees and
 * faculty. The employee category only selects which pay-matrix cells are used.
 * In particular, the 3% fallbacks and currentPay + 1 placement rule below are
 * preserved from the existing calculator.
 */
fun calculatePayFixation(
    currentLevel: String,
    currentPay: Int,
    promotedLevel: String,
    promotionDate: Long?,
    dniDate: Long?,
    employeeCategory: EmployeeCategory = EmployeeCategory.ORDINARY
): FixationResult {
    // Select the appropriate matrix without creating a second fixation algorithm.
    val matrix = PayMatrixSelection.forCategory(employeeCategory)

    val promotedLevelMax =
        matrix
            .getPayStages(promotedLevel)
            .lastOrNull()
            ?: Int.MAX_VALUE

    // OPTION 1: fixation is calculated from the date of promotion.
    // First take one increment in the employee's existing pay level.
    val payWithOneIncrement =
        matrix.getNextIncrement(
            currentLevel,
            currentPay
        ) ?: (currentPay * 1.03).toInt()

    // Place the incremented pay at the equal-or-next-higher cell in the
    // promoted level, while respecting that level's maximum available cell.
    val option1Pay =
        minOf(
            matrix.findEqualOrNextHigher(
                promotedLevel,
                payWithOneIncrement
            ) ?: payWithOneIncrement,
            promotedLevelMax
        )

    // Calculate the next DNI from the promotion/fixation date for Option 1.
    val opt1NextDni =
        promotionDate?.let {
            calculateNextDni(it)
        }

    // At the next DNI, move one cell forward in the promoted level.
    val opt1PayAfterNextDni =
        opt1NextDni?.let {
            matrix.getNextIncrement(
                promotedLevel,
                option1Pay
            )
        } ?: option1Pay

    // OPTION 2: fixation is deferred to the selected DNI.
    // Until DNI, place current pay at the next higher cell in the promoted
    // level. The +1 below is intentionally retained from the existing logic.
    val option2PayBeforeDni =
        minOf(
            matrix.findEqualOrNextHigher(
                promotedLevel,
                currentPay + 1
            ) ?: currentPay,
            promotedLevelMax
        )

    // Calculate the normal annual increment in the current/lower level.
    val annualIncrement =
        matrix.getNextIncrement(
            currentLevel,
            currentPay
        ) ?: (currentPay * 1.03).toInt()

    // Calculate the additional promotion increment after the annual increment.
    val promotionIncrement =
        matrix.getNextIncrement(
            currentLevel,
            annualIncrement
        ) ?: (annualIncrement * 1.03).toInt()

    // Place the resulting promotion pay at the equal-or-next-higher cell in
    // the promoted level, again respecting the level maximum.
    val option2PayAfterDni =
        minOf(
            matrix.findEqualOrNextHigher(
                promotedLevel,
                promotionIncrement
            ) ?: promotionIncrement,
            promotedLevelMax
        )

    // Calculate the next DNI after the selected DNI for Option 2.
    val opt2NextDni =
        dniDate?.let {
            calculateNextDni(it)
        }

    // Calculate the promoted-level pay after that future DNI.
    val opt2PayAfterNextDni =
        opt2NextDni?.let {
            matrix.getNextIncrement(
                promotedLevel,
                option2PayAfterDni
            )
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

/** Returns the two DNI choices used by the calculator UI. */
fun getPayFixationDniOptions(promotionDate: Long): List<Long> =
    calculateDniOptions(promotionDate)

/** Returns the next DNI calculated from a fixation date. */
fun getPayFixationNextDni(fixationDate: Long): Long =
    calculateNextDni(fixationDate)
