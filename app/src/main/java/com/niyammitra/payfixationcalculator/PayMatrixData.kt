package com.niyammitra.payfixationcalculator

/**
 * Pay Matrix data used by the ordinary/general employee Pay Fixation calculator.
 *
 * The raw matrix values are intentionally preserved. The columns correspond
 * to the pay levels listed below, while each row represents a successive cell
 * in the matrix. Do not edit the numeric matrix as part of UI changes.
 */
object PayMatrixData : PayMatrixProvider {
    override val levels = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "13A", "14", "15", "16", "17", "18")

    private val rawMatrix: Map<Int, List<Int?>> = mapOf(
        1 to listOf(18000,19900,21700,25500,29200,35400,44900,47600,53100,56100,67700,78800,123100,131100,144200,182200,205400,225000,250000),
        2 to listOf(18500,20500,22400,26300,30100,36500,46200,49000,54700,57800,69700,81200,126800,135000,148500,187700,211600,null,null),
        3 to listOf(19100,21100,23100,27100,31000,37600,47600,50500,56300,59500,71800,83600,130600,139100,153000,193300,217900,null,null),
        4 to listOf(19700,21700,23800,27900,31900,38700,49000,52000,58000,61300,74000,86100,134500,143300,157600,199100,224400,null,null),
        5 to listOf(20300,22400,24500,28700,32900,39900,50500,53600,59700,63100,76200,88700,138500,147600,162300,205100,null,null,null),
        6 to listOf(20900,23100,25200,29600,33900,41100,52000,55200,61500,65000,78500,91400,142700,152000,167200,211300,null,null,null),
        7 to listOf(21500,23800,26000,30500,34900,42300,53600,56900,63300,67000,80900,94100,147000,156600,172200,217600,null,null,null),
        8 to listOf(22100,24500,26800,31400,35900,43600,55200,58600,65200,69000,83300,96900,151400,161300,177400,224100,null,null,null),
        9 to listOf(22800,25200,27600,32300,37000,44900,56900,60400,67200,71100,85800,99800,155900,166100,182700,null,null,null),
        10 to listOf(23500,26000,28400,33300,38100,46200,58600,62200,69200,73200,88400,102800,160600,171100,188200,null,null,null),
        11 to listOf(24200,26800,29300,34300,39200,47600,60400,64100,71300,75400,91100,105900,165400,176200,193800,null,null,null),
        12 to listOf(24900,27600,30200,35300,40400,49000,62200,66000,73400,77700,93800,109100,170400,181500,199600,null,null,null),
        13 to listOf(25600,28400,31100,36400,41600,50500,64100,68000,75600,80000,96600,112400,175500,186900,205600,null,null,null),
        14 to listOf(26400,29300,32000,37500,42800,52000,66000,70000,77900,82400,99500,115800,180800,192500,211800,null,null,null),
        15 to listOf(27200,30200,33000,38600,44100,53600,68000,72100,80200,84900,102500,119300,186200,198300,218200,null,null,null),
        16 to listOf(28000,31100,34000,39800,45400,55200,70000,74300,82600,87400,105600,122900,191800,204200,null,null,null),
        17 to listOf(28800,32000,35000,41000,46800,56900,72100,76500,85100,90000,108800,126600,197600,210300,null,null,null),
        18 to listOf(29700,33000,36100,42200,48200,58600,74300,78800,87700,92700,112100,130400,203500,216600,null,null,null),
        19 to listOf(30600,34000,37200,43500,49600,60400,76500,81200,90300,95500,115500,134300,209600,null,null,null,null,null,null),
        20 to listOf(31500,35000,38300,44800,51100,62200,78800,83600,93000,98400,119000,138300,215900,null,null,null,null,null,null),
        21 to listOf(32400,36100,39400,46100,52600,64100,81200,86100,95800,101400,122600,142400,null,null,null,null,null,null,null),
        22 to listOf(33400,37200,40600,47500,54200,66000,83600,88700,98700,104400,126300,146700,null,null,null,null,null,null,null),
        23 to listOf(34400,38300,41800,48900,55800,68000,86100,91400,101700,107500,130100,151100,null,null,null,null,null,null,null),
        24 to listOf(35400,39400,43100,50400,57500,70000,88700,94100,104800,110700,134000,155600,null,null,null,null,null,null,null),
        25 to listOf(36500,40600,44400,51900,59200,72100,91400,96900,107900,114000,138000,160300,null,null,null,null,null,null,null),
        26 to listOf(37600,41800,45700,53500,61000,74300,94100,99800,111100,117400,142100,165100,null,null,null,null,null,null,null),
        27 to listOf(38700,43100,47100,55100,62800,76500,96900,102800,114400,120900,146400,170100,null,null,null,null,null,null,null),
        28 to listOf(39900,44400,48500,56800,64700,78800,99800,105900,117800,124500,150800,175200,null,null,null,null,null,null,null),
        29 to listOf(41100,45700,50000,58500,66600,81200,102800,109100,121300,128200,155300,180500,null,null,null,null,null,null,null),
        30 to listOf(42300,47100,51500,60300,68600,83600,105900,112400,124900,132000,160000,185900,null,null,null,null,null,null,null),
        31 to listOf(43600,48500,53000,62100,70700,86100,109100,115800,128600,136000,164800,191500,null,null,null,null,null,null,null),
        32 to listOf(44900,50000,54600,64000,72800,88700,112400,119300,132500,140100,169700,197200,null,null,null,null,null,null,null),
        33 to listOf(46200,51500,56200,65900,75000,91400,115800,122900,136500,144300,174800,203100,null,null,null,null,null,null,null),
        34 to listOf(47600,53000,57900,67900,77300,94100,119300,126600,140600,148600,180000,209200,null,null,null,null,null,null,null),
        35 to listOf(49000,54600,59600,69900,79600,96900,122900,130400,144800,153100,185400,null,null,null,null,null,null,null),
        36 to listOf(50500,56200,61400,72000,82000,99800,126600,134300,149100,157700,191000,null,null,null,null,null,null,null),
        37 to listOf(52000,57900,63200,74200,84500,102800,130400,138300,153600,162400,196700,null,null,null,null,null,null,null),
        38 to listOf(53600,59600,65100,76400,87000,105900,134300,142400,158200,167300,202600,null,null,null,null,null,null,null),
        39 to listOf(55200,61400,67100,78700,89600,109100,138300,146700,162900,172300,208700,null,null,null,null,null,null,null),
        40 to listOf(56900,63200,69100,81100,92300,112400,142400,151100,167800,177500,null,null,null,null,null,null,null),
        41 to listOf(58500,64700,71600,83500,95000,null,null,null,null,null,null,null,null,null,null,null,null,null,null)
    )

    private val levelStages: Map<String, List<Int>> = levels.mapIndexed { index, level ->
        level to rawMatrix.values.mapNotNull { it.getOrNull(index) }
    }.toMap()

    override fun getPayStages(level: String): List<Int> = levelStages[level].orEmpty()

    override fun getNextIncrement(level: String, currentPay: Int): Int? {
        val stages = getPayStages(level)
        val index = stages.indexOf(currentPay)
        return if (index >= 0 && index < stages.size - 1) stages[index + 1] else null
    }

    override fun findEqualOrNextHigher(level: String, pay: Int): Int? =
        getPayStages(level).firstOrNull { it >= pay } ?: getPayStages(level).lastOrNull()

    override fun findEqualOrNextLower(level: String, pay: Int): Int? =
        getPayStages(level).lastOrNull { it <= pay }

    override fun isHigherLevel(fromLevel: String, toLevel: String): Boolean =
        (levels.indexOf(toLevel) > levels.indexOf(fromLevel))
}
