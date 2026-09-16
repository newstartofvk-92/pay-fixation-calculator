package com.niyammitra.payfixationcalculator

import kotlin.math.ceil

/**
 * Historical 6th CPC promotion/upgradation fixation for cases covered by Rule 5
 * of the CCS (Revised Pay) Rules, 2008 and Clarification 2 dated 13.09.2008.
 *
 * This engine deliberately works from the employee's already-fixed 6th CPC
 * Pay-in-Pay-Band + Grade Pay position. It does NOT apply the 1.86 fitment
 * factor again to an already revised 6th CPC basic pay.
 */
object HistoricalSixthCpcFixationUtils {

    data class Input(
        val eventDate: Long,
        val payInPayBandBeforeEvent: Int,
        val gradePayBeforeEvent: Int,
        val promotionalGradePay: Int,
        val option: InterimFixationOption
    )

    data class Output(
        val eventDate: Long,
        val option: InterimFixationOption,
        val payInPayBandOnEvent: Int,
        val gradePayOnEvent: Int,
        val basicPayOnEvent: Int,
        val refixationDate: Long?,
        val payInPayBandAfterRefixation: Int?,
        val basicPayAfterRefixation: Int?,
        val annualIncrement: Int?,
        val promotionIncrement: Int?,
        val ruleBasis: List<String>
    )

    fun calculate(input: Input): Output {
        require(input.promotionalGradePay > input.gradePayBeforeEvent) {
            "Promotional Grade Pay must be higher than the existing Grade Pay."
        }

        val oldBasic = input.payInPayBandBeforeEvent + input.gradePayBeforeEvent

        return when (input.option) {
            InterimFixationOption.FROM_PROMOTION_DATE -> {
                val promotionIncrement = revisedPayIncrement(oldBasic)
                val newPayInBand = input.payInPayBandBeforeEvent + promotionIncrement

                Output(
                    eventDate = input.eventDate,
                    option = input.option,
                    payInPayBandOnEvent = newPayInBand,
                    gradePayOnEvent = input.promotionalGradePay,
                    basicPayOnEvent = newPayInBand + input.promotionalGradePay,
                    refixationDate = null,
                    payInPayBandAfterRefixation = null,
                    basicPayAfterRefixation = null,
                    annualIncrement = null,
                    promotionIncrement = promotionIncrement,
                    ruleBasis = listOf(
                        "Rule 5: the employee may switch to the revised pay structure from the date of promotion/upgradation.",
                        "Clarification 2 dated 13.09.2008: fixation from promotion date is made by granting one increment in the revised structure and the higher Grade Pay."
                    )
                )
            }

            InterimFixationOption.FROM_NEXT_INCREMENT -> {
                // On the promotion date, pay in the pay band remains unchanged;
                // only the higher Grade Pay is granted. Re-fixation takes place
                // on the next 1 July with two increments, both calculated from
                // the basic pay immediately before promotion.
                val annualIncrement = revisedPayIncrement(oldBasic)
                val promotionIncrement = revisedPayIncrement(oldBasic + annualIncrement)
                val refixationDate = nextJulyOnOrAfterEvent(input.eventDate)
                val refixedPayInBand = input.payInPayBandBeforeEvent + annualIncrement + promotionIncrement

                Output(
                    eventDate = input.eventDate,
                    option = input.option,
                    payInPayBandOnEvent = input.payInPayBandBeforeEvent,
                    gradePayOnEvent = input.promotionalGradePay,
                    basicPayOnEvent = input.payInPayBandBeforeEvent + input.promotionalGradePay,
                    refixationDate = refixationDate,
                    payInPayBandAfterRefixation = refixedPayInBand,
                    basicPayAfterRefixation = refixedPayInBand + input.promotionalGradePay,
                    annualIncrement = annualIncrement,
                    promotionIncrement = promotionIncrement,
                    ruleBasis = listOf(
                        "Rule 5 proviso: the employee may continue in the existing scale until the next increment and then switch to the revised pay structure.",
                        "Clarification 2 dated 13.09.2008: on promotion, Pay in Pay Band remains unchanged until the chosen fixation date and the higher Grade Pay is granted.",
                        "On the next 1 July, two increments are granted: one annual increment and one promotion increment, calculated successively from the basic pay immediately before promotion."
                    )
                )
            }
        }
    }

    /** 3% of basic pay, rounded up to the next multiple of Rs.10. */
    private fun revisedPayIncrement(basicPay: Int): Int =
        (ceil((basicPay * 0.03) / 10.0) * 10.0).toInt()

    private fun nextJulyOnOrAfterEvent(eventDate: Long): Long {
        return java.util.Calendar.getInstance().apply {
            timeInMillis = eventDate
            val year = get(java.util.Calendar.YEAR)
            set(year, java.util.Calendar.JULY, 1, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= eventDate) add(java.util.Calendar.YEAR, 1)
        }.timeInMillis
    }
}
