package com.niyammitra.payfixationcalculator

/**
 * Faculty-specific pay matrix prescribed by the Ministry of Health & Family Welfare
 * order dated 23.08.2018 for AIIMS New Delhi, PGIMER Chandigarh and JIPMER Puducherry,
 * and subsequently extended to faculty of other new AIIMS.
 *
 * Source: Government order V-16020/28/2017-INI-I (Pt.), statement on page 4.
 * Only the pay matrix differs from the ordinary calculator; the fixation procedure
 * remains the same calculation used by PayFixationUtils.kt.
 */
object FacultyPayMatrixData {
    // These columns reproduce the faculty matrix levels shown in the Government order.
    val levels = listOf("12", "13", "13 A-1+", "13-A2+", "14-A", "15", "17")

    // The source table is row-oriented. Null values represent cells that are blank
    // in the Government order and therefore are not available at that index.
    private val rawMatrix: Map<Int, List<Int?>> = mapOf(
        1 to listOf(101500, 123100, 138300, 148200, 168900, 182200, 225000),
        2 to listOf(104500, 126800, 142400, 152600, 174000, 187700, null),
        3 to listOf(107600, 130600, 145700, 157200, 179200, 193300, null),
        4 to listOf(110800, 134500, 151100, 161900, 184600, 199100, null),
        5 to listOf(114100, 138500, 155600, 166800, 190100, 205100, null),
        6 to listOf(117500, 142700, 160300, 171800, 195800, 211300, null),
        7 to listOf(121000, 147000, 165100, 177000, 201700, 217600, null),
        8 to listOf(124600, 151400, 170100, 182300, 207800, 224100, null),
        9 to listOf(128300, 155900, 175200, 187800, 214000, null, null),
        10 to listOf(132100, 160600, 180500, 193400, 220400, null, null),
        11 to listOf(136100, 165400, 185900, 199200, null, null, null),
        12 to listOf(140200, 170400, 191500, 205200, null, null, null),
        13 to listOf(144400, 175500, 197200, 211400, null, null, null),
        14 to listOf(148700, 180800, 203100, null, null, null, null),
        15 to listOf(153200, 185200, 209200, null, null, null, null),
        16 to listOf(157800, 191800, null, null, null, null, null),
        17 to listOf(162500, 197600, null, null, null, null, null),
        18 to listOf(167400, 203500, null, null, null, null, null),
        19 to listOf(null, 209600, null, null, null, null, null),
        20 to listOf(null, 215900, null, null, null, null, null)
    )

    // Convert the source rows into one ordered list of pay cells for each faculty level.
    private val levelStages: Map<String, List<Int>> = levels.mapIndexed { index, level ->
        level to rawMatrix.values.mapNotNull { it.getOrNull(index) }
    }.toMap()

    /** Returns all available cells for the selected faculty pay level. */
    fun getPayStages(level: String): List<Int> = levelStages[level].orEmpty()

    /** Returns the next available pay cell after the supplied current basic pay. */
    fun getNextIncrement(level: String, currentPay: Int): Int? {
        val stages = getPayStages(level)
        val index = stages.indexOf(currentPay)
        return if (index >= 0 && index < stages.size - 1) stages[index + 1] else null
    }

    /** Finds the first faculty-matrix cell equal to or above the supplied pay. */
    fun findEqualOrNextHigher(level: String, pay: Int): Int? =
        getPayStages(level).firstOrNull { it >= pay } ?: getPayStages(level).lastOrNull()

    /** Finds the last faculty-matrix cell equal to or below the supplied pay. */
    fun findEqualOrNextLower(level: String, pay: Int): Int? =
        getPayStages(level).findLast { it <= pay } ?: getPayStages(level).firstOrNull()

    /** Compares faculty levels according to their order in the source matrix. */
    fun isHigherLevel(levelA: String, levelB: String): Boolean =
        levels.indexOf(levelA) > levels.indexOf(levelB)
}
