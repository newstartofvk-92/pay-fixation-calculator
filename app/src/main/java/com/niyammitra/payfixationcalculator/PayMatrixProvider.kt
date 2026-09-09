package com.niyammitra.payfixationcalculator

/**
 * Common contract for the pay matrices used by the calculator.
 *
 * The fixation algorithm works against this interface, allowing ordinary and
 * faculty employees to use exactly the same formulas with different pay cells.
 */
interface PayMatrixProvider {
    val levels: List<String>

    /** Returns the available pay cells for a level. */
    fun getPayStages(level: String): List<Int>

    /** Returns the next pay cell after the current basic pay. */
    fun getNextIncrement(level: String, currentPay: Int): Int?

    /** Finds the first pay cell equal to or above the supplied value. */
    fun findEqualOrNextHigher(level: String, pay: Int): Int?

    /** Finds the last pay cell equal to or below the supplied value. */
    fun findEqualOrNextLower(level: String, pay: Int): Int?

    /** Compares two levels according to the matrix's level order. */
    fun isHigherLevel(levelA: String, levelB: String): Boolean
}
