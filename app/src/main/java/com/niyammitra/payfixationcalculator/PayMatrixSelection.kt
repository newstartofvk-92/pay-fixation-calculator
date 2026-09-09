package com.niyammitra.payfixationcalculator

/**
 * Provides the pay-matrix implementation selected by the employee category.
 *
 * The ordinary matrix is the existing PayMatrixData. The faculty matrix is
 * adapted to the same provider contract so the fixation formulas remain shared.
 */
object PayMatrixSelection {
    /** Returns the matrix that belongs to the selected employee category. */
    fun forCategory(category: EmployeeCategory): PayMatrixProvider =
        when (category) {
            EmployeeCategory.ORDINARY -> PayMatrixData
            EmployeeCategory.FACULTY -> FacultyPayMatrixProvider
        }
}

/**
 * Adapts the Government-order faculty matrix to the common calculator contract.
 * This adapter contains no new fixation rules; it only delegates matrix lookups.
 */
private object FacultyPayMatrixProvider : PayMatrixProvider {
    override val levels: List<String>
        get() = FacultyPayMatrixData.levels

    override fun getPayStages(level: String): List<Int> =
        FacultyPayMatrixData.getPayStages(level)

    override fun getNextIncrement(level: String, currentPay: Int): Int? =
        FacultyPayMatrixData.getNextIncrement(level, currentPay)

    override fun findEqualOrNextHigher(level: String, pay: Int): Int? =
        FacultyPayMatrixData.findEqualOrNextHigher(level, pay)

    override fun findEqualOrNextLower(level: String, pay: Int): Int? =
        FacultyPayMatrixData.findEqualOrNextLower(level, pay)

    override fun isHigherLevel(levelA: String, levelB: String): Boolean =
        FacultyPayMatrixData.isHigherLevel(levelA, levelB)
}
