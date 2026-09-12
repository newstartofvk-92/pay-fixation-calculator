package com.niyammitra.payfixationcalculator

/**
 * Top-level pay-fixation workflows supported by the application.
 *
 * This model deliberately contains navigation/domain choices only. The actual
 * fixation rules remain in their respective calculation engines.
 */
enum class FixationType(
    val title: String,
    val description: String
) {
    FOURTH_TO_FIFTH(
        title = "4th CPC → 5th CPC",
        description = "Revision of pay from the 4th CPC structure to the 5th CPC structure."
    ),
    FIFTH_TO_SIXTH(
        title = "5th CPC → 6th CPC",
        description = "Revision of pay from the 5th CPC structure to the 6th CPC structure."
    ),
    SIXTH_TO_SEVENTH(
        title = "6th CPC → 7th CPC",
        description = "Revision of pay from the 6th CPC structure to the 7th CPC structure."
    ),
    SEVENTH_CPC(
        title = "7th CPC Pay Fixation",
        description = "Pay fixation for Promotion / MACP within the 7th CPC Pay Matrix."
    )
}

/** Types of fixation currently available within the 7th CPC workflow. */
enum class SeventhCpcFixationType(
    val title: String,
    val description: String
) {
    PROMOTION(
        title = "Promotion",
        description = "Fix pay on promotion to a higher Pay Level."
    ),
    MACP(
        title = "MACP",
        description = "Fix pay on financial upgradation under MACP."
    )
}
