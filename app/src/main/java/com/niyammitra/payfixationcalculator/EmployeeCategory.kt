package com.niyammitra.payfixationcalculator

/**
 * Identifies which pay matrix must be used for the employee's fixation.
 *
 * The fixation procedure is common to both categories; only the underlying
 * pay-matrix cells are different.
 */
enum class EmployeeCategory(val displayName: String) {
    ORDINARY("Ordinary / General Employee"),
    FACULTY("Faculty of AIIMS / PGIMER / JIPMER")
}
