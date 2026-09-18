package com.niyammitra.payfixationcalculator

data class FourthCpcScale(
    val grade: String,
    val existingScale: String,
    val revisedScale: String,
    val revisedMinimum: Int,
    val revisedMaximum: Int,
    val revisedIncrement: Int,
    val fixed: Boolean = false
) {
    /** Every valid stage of the 4th CPC scale, including stepped increment portions. */
    val existingStages: List<Int>
        get() = parsePayScaleStages(existingScale)
}

data class FourthToFifthResult(
    val scale: FourthCpcScale,
    val existingBasicPay: Int,
    val dearnessAllowance: Int,
    val firstInterimRelief: Int,
    val secondInterimRelief: Int,
    val existingEmoluments: Int,
    val fitmentWeightage: Int,
    val fitmentTotal: Int,
    val revisedBasicPay: Int,
    val conversionDate: String,
    val nextIncrementNote: String,
    val ruleBasis: List<String>
)

object FourthToFifthCpcData {
    val scales = listOf(
        FourthCpcScale("S-1", "750-12-870-14-940", "2550-55-2660-60-3200", 2550, 3200, 55),
        FourthCpcScale("S-2", "775-12-871-14-1025", "2610-60-3150-65-3540", 2610, 3540, 60),
        FourthCpcScale("S-2A", "775-12-871-14-955-15-1030-20-1150", "2610-60-2910-65-3300-70-4000", 2610, 4000, 60),
        FourthCpcScale("S-3", "800-15-1010-20-1150", "2650-65-3300-70-4000", 2650, 4000, 65),
        FourthCpcScale("S-4", "825-15-900-20-1200", "2750-70-3800-75-4400", 2750, 4400, 70),
        FourthCpcScale("S-5", "950-20-1150-25-1400 / 950-20-1150-25-1500", "3050-75-3950-80-4590", 3050, 4590, 75),
        FourthCpcScale("S-6", "975-25-1150-30-1540 / 975-25-1150-30-1660", "3200-85-4900", 3200, 4900, 85),
        FourthCpcScale("S-7", "1200-30-1440-30-1800 / 1200-30-1560-40-2040", "4000-100-6000", 4000, 6000, 100),
        FourthCpcScale("S-8", "1350-30-1440-40-1800-50-2200 / 1400-40-1800-50-2300", "4500-125-7000", 4500, 7000, 125),
        FourthCpcScale("S-9", "1400-40-1600-50-2300-60-2600", "5000-150-8000", 5000, 8000, 150),
        FourthCpcScale("S-10", "1640-60-2600-75-2900", "5500-175-9000", 5500, 9000, 175),
        FourthCpcScale("S-11", "2000-60-2120", "6500-200-6900", 6500, 6900, 200),
        FourthCpcScale("S-12", "2000-60-2300-75-3200 / 2000-60-2300-75-3200-3500", "6500-200-10500", 6500, 10500, 200),
        FourthCpcScale("S-13", "2375-75-3200-100-3500 / 2375-75-3200-100-3500-125-3750", "7450-225-11500", 7450, 11500, 225),
        FourthCpcScale("S-14", "2500-4000", "7500-250-12000", 7500, 12000, 250),
        FourthCpcScale("S-15", "2200-75-2800-100-4000 / 2300-100-2800", "8000-275-13500", 8000, 13500, 275),
        FourthCpcScale("NEW SCALE", "2200-75-2800-100-4000", "8000-275-13500 (Group A Entry)", 8000, 13500, 275),
        FourthCpcScale("S-16", "2630 fixed", "9000 fixed", 9000, 9000, 0, true),
        FourthCpcScale("S-17", "2630-75-2780", "9000-275-9550", 9000, 9550, 275),
        FourthCpcScale("S-18", "3150-100-3350", "10325-325-10975", 10325, 10975, 325),
        FourthCpcScale("S-19", "3000-125-3625 / 3000-100-3500-125-4500 / 3000-100-3500-125-5000", "10000-325-15200", 10000, 15200, 325),
        FourthCpcScale("S-20", "3200-100-3700-125-4700", "10650-325-15850", 10650, 15850, 325),
        FourthCpcScale("S-21", "3700-150-4450 / 3700-125-4700-150-5000", "12000-375-16500", 12000, 16500, 375),
        FourthCpcScale("S-22", "3950-125-4700-150-5000", "12750-375-16500", 12750, 16500, 375),
        FourthCpcScale("S-23", "3700-125-4950-150-5700", "12000-375-18000", 12000, 18000, 375),
        FourthCpcScale("S-24", "4100-125-4850-150-5300 / 4500-150-5700", "14300-400-18300", 14300, 18300, 400),
        FourthCpcScale("S-25", "4800-150-5700", "15100-400-18300", 15100, 18300, 400),
        FourthCpcScale("S-26", "5100-150-5700 / 5100-150-6150 / 5100-150-5700-200-6300", "16400-450-20000", 16400, 20000, 450),
        FourthCpcScale("S-27", "5100-150-6300-200-6700", "16400-450-20900", 16400, 20900, 450),
        FourthCpcScale("S-28", "4500-150-5700-200-7300", "14300-450-22400", 14300, 22400, 450),
        FourthCpcScale("S-29", "5900-200-6700 / 5900-200-7300", "18400-500-22400", 18400, 22400, 500),
        FourthCpcScale("S-30", "7300-100-7600", "22400-525-24500", 22400, 24500, 525),
        FourthCpcScale("S-31", "7300-200-7500-250-8000", "22400-600-26000", 22400, 26000, 600),
        FourthCpcScale("S-32", "7600 fixed / 7600-100-8000", "24050-650-26000", 24050, 26000, 650),
        FourthCpcScale("S-33", "8000 fixed", "26000 fixed", 26000, 26000, 0, true),
        FourthCpcScale("S-34", "9000 fixed", "30000 fixed", 30000, 30000, 0, true)
    )
}

/** Standard Rule 7 replacement-scale calculation. */
fun calculateFourthToFifthCpc(existingBasicPay: Int, scale: FourthCpcScale): FourthToFifthResult {
    require(existingBasicPay in scale.existingStages) { "Basic pay must be a valid stage of the selected 4th CPC scale." }
    val da = (existingBasicPay * 1.48).toInt()
    val firstIr = 100
    val secondIr = maxOf(100, (existingBasicPay * 0.10).toInt())
    val existingEmoluments = existingBasicPay + da + firstIr + secondIr
    val fitmentWeightage = (existingBasicPay * 0.40).toInt()
    val fitmentTotal = existingEmoluments + fitmentWeightage
    val revisedBasic = if (scale.fixed) scale.revisedMinimum else parsePayScaleStages(scale.revisedScale).firstOrNull { it >= fitmentTotal } ?: scale.revisedMaximum
    return FourthToFifthResult(
        scale, existingBasicPay, da, firstIr, secondIr, existingEmoluments, fitmentWeightage, fitmentTotal,
        revisedBasic, "01 January 1996",
        "The next increment date is determined from the 4th CPC service/pay history and is carried into the revised scale under the applicable Rule 8 provisions.",
        listOf(
            "Rule 7 standard fitment: existing emoluments plus 40% of existing basic pay.",
            "The employee's basic pay must correspond to an actual stage of the selected existing scale.",
            "The revised pay is selected from the stages of the corresponding revised scale.",
            "Bunching and the one-increment-for-every-three-existing-increments safeguard require the employee's prior service history and are handled separately."
        )
    )
}

/** Parse one or more CPC scale notations into all valid pay stages. */
fun parsePayScaleStages(notation: String): List<Int> {
    val alternatives = notation.split("/")
    return alternatives.flatMap { part ->
        val numbers = Regex("\\d+").findAll(part).map { it.value.toInt() }.toList()
        if (numbers.size == 1) numbers
        else if (numbers.size >= 3) {
            val result = mutableListOf<Int>()
            var current = numbers[0]
            result += current
            var i = 1
            while (i + 1 < numbers.size) {
                val increment = numbers[i]
                val end = numbers[i + 1]
                if (increment <= 0 || end < current) break
                while (current + increment <= end) {
                    current += increment
                    result += current
                }
                if (current < end) {
                    current = end
                    result += current
                }
                i += 2
            }
            result
        } else emptyList()
    }.distinct().sorted()
}

fun calculateFourthCpcNextIncrement(currentPay: Int, scale: FourthCpcScale): Int? {
    return scale.existingStages.firstOrNull { it > currentPay }
}
