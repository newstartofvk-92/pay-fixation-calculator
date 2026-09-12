package com.niyammitra.payfixationcalculator

import kotlin.math.ceil

data class FifthCpcScale(val title: String, val payBand: String, val payBandMinimum: Int, val payBandMaximum: Int, val gradePay: Int)
data class FifthToSixthResult(val scale: FifthCpcScale, val existingBasicPay: Int, val multipliedPay: Double, val roundedPay: Int, val payInPayBand: Int, val gradePay: Int, val revisedBasicPay: Int, val conversionDate: String, val nextIncrementDate: String, val ruleBasis: List<String>)

object FifthToSixthCpcData {
    val scales = listOf(
        FifthCpcScale("Rs. 2550-55-2660-60-3200", "PB-1", 4440, 7440, 1300),
        FifthCpcScale("Rs. 2610-60-3150-65-3540", "PB-1", 4440, 7440, 1400),
        FifthCpcScale("Rs. 2610-60-2910-65-3300-70-4000", "PB-1", 4440, 7440, 1600),
        FifthCpcScale("Rs. 2650-65-3300-70-4000", "PB-1", 4440, 7440, 1650),
        FifthCpcScale("Rs. 2750-70-3800-75-4400", "PB-1", 5200, 20200, 1800),
        FifthCpcScale("Rs. 3050-75-3950-80-4590", "PB-1", 5200, 20200, 1900),
        FifthCpcScale("Rs. 3200-85-4900", "PB-1", 5200, 20200, 2000),
        FifthCpcScale("Rs. 4000-100-6000", "PB-1", 5200, 20200, 2400),
        FifthCpcScale("Rs. 4500-125-7000", "PB-1", 5200, 20200, 2800),
        FifthCpcScale("Rs. 5000-150-8000", "PB-2", 9300, 34800, 4200),
        FifthCpcScale("Rs. 5500-175-9000", "PB-2", 9300, 34800, 4200),
        FifthCpcScale("Rs. 6500-200-6900", "PB-2", 9300, 34800, 4200),
        FifthCpcScale("Rs. 6500-200-10500", "PB-2", 9300, 34800, 4200),
        FifthCpcScale("Rs. 7450-225-11500", "PB-2", 9300, 34800, 4600),
        FifthCpcScale("Rs. 7500-250-12000", "PB-2", 9300, 34800, 4800),
        FifthCpcScale("Rs. 8000-275-13500 (PB-2)", "PB-2", 9300, 34800, 5400),
        FifthCpcScale("Rs. 8000-275-13500 (PB-3)", "PB-3", 15600, 39100, 5400),
        FifthCpcScale("Rs. 9000-250-10500", "PB-3", 15600, 39100, 5400),
        FifthCpcScale("Rs. 9000-275-9550", "PB-3", 15600, 39100, 5400),
        FifthCpcScale("Rs. 10325-325-10975", "PB-3", 15600, 39100, 6600),
        FifthCpcScale("Rs. 10000-325-15200", "PB-3", 15600, 39100, 6600),
        FifthCpcScale("Rs. 10650-325-15850", "PB-3", 15600, 39100, 6600),
        FifthCpcScale("Rs. 12000-375-16500", "PB-3", 15600, 39100, 7600),
        FifthCpcScale("Rs. 12750-375-16500", "PB-3", 15600, 39100, 7600),
        FifthCpcScale("Rs. 12000-375-18000", "PB-3", 15600, 39100, 7600),
        FifthCpcScale("Rs. 14300-400-18300", "PB-4", 37400, 67000, 8700),
        FifthCpcScale("Rs. 15100-400-18300", "PB-4", 37400, 67000, 8700),
        FifthCpcScale("Rs. 16400-450-20000", "PB-4", 37400, 67000, 8900),
        FifthCpcScale("Rs. 16400-450-20900", "PB-4", 37400, 67000, 8900),
        FifthCpcScale("Rs. 14300-450-22400", "PB-4", 37400, 67000, 10000),
        FifthCpcScale("Rs. 18400-500-22400", "PB-4", 37400, 67000, 10000),
        FifthCpcScale("Rs. 22400-525-24500", "PB-4", 37400, 67000, 12000)
    )
}

fun calculateFifthToSixthCpc(existingBasicPay: Int, scale: FifthCpcScale): FifthToSixthResult {
    require(existingBasicPay > 0) { "Existing basic pay must be positive." }
    val multiplied = existingBasicPay * 1.86
    val rounded = (ceil(multiplied / 10.0) * 10.0).toInt()
    val payInBand = maxOf(rounded, scale.payBandMinimum)
    return FifthToSixthResult(scale, existingBasicPay, multiplied, rounded, payInBand, scale.gradePay, payInBand + scale.gradePay, "01 January 2006", "01 July 2006", listOf(
        "CCS (Revised Pay) Rules, 2008, Rule 7: existing basic pay as on 01.01.2006 is multiplied by 1.86.",
        "The resultant amount is rounded up to the next multiple of Rs.10.",
        "If the minimum of the revised pay band is higher than the calculated amount, the minimum of the revised pay band is taken.",
        "Grade Pay corresponding to the existing 5th CPC scale is added to the pay in the Pay Band.",
        "Rule 10 provides a uniform annual increment date of 01 July; the standard first increment after fixation on 01.01.2006 is 01.07.2006, subject to the rule's provisos."
    ))
}
