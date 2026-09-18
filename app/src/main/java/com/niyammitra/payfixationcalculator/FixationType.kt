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
        title = "Fixation Starting from 4th CPC",
        description = "4th CPC pay to 5th CPC fixation, with subsequent 5th CPC events."
    ),
    FIFTH_TO_SIXTH(
        title = "Fixation Starting from 5th CPC",
        description = "5th CPC pay to 6th CPC fixation, with subsequent 6th CPC events."
    ),
    SIXTH_TO_SEVENTH(
        title = "Fixation Starting from 6th CPC",
        description = "6th CPC pay to 7th CPC fixation, with subsequent 7th CPC events."
    ),
    SEVENTH_CPC(
        title = "Fixation under 7th CPC",
        description = "Promotion / MACP pay fixation within the 7th CPC Pay Matrix."
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
