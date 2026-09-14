package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val ContinuityBlue = Color(0xFF1769AA)
private val ContinuityTextPrimary = Color(0xFF172B4D)
private val ContinuityTextSecondary = Color(0xFF5B6B7A)

/**
 * Automatic 7th CPC continuation for the 5th CPC -> 6th CPC workflow.
 * It starts from the latest 6th CPC pay reached on the preceding screen and
 * performs the 7th CPC conversion as on 01 January 2016 without asking the
 * user to re-enter the 6th CPC pay.
 */
@Composable
fun SeventhCpcContinuitySection(
    payBand: String,
    gradePay: Int,
    payInPayBand: Int
) {
    val seventhBand = remember(payBand, gradePay) {
        SixthToSeventhCpcData.payBands.firstOrNull { band ->
            band.title.substringBefore(":").trim() == payBand && gradePay in band.gradePays
        }
    }

    if (seventhBand == null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
            Text(
                "7th CPC conversion could not be matched automatically to the 6th CPC Pay Band / Grade Pay. Verify the applicable pay structure before proceeding.",
                Modifier.padding(16.dp), color = ContinuityTextPrimary, fontSize = 12.sp
            )
        }
        return
    }

    val calculation = remember(payBand, gradePay, payInPayBand) {
        calculateSixthToSeventhCpc(payInPayBand, gradePay, seventhBand)
    }
    var incrementSteps by remember(calculation.revisedBasicPay) { mutableStateOf<List<SeventhCpcIncrementStep>>(emptyList()) }

    val latest7thPay = incrementSteps.lastOrNull()?.pay ?: calculation.revisedBasicPay
    val knownDni = incrementSteps.lastOrNull()?.let { addSeventhYears(it.date, 1) } ?: julyFirst2016ForContinuity()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("7th CPC — Automatic Continuation", color = ContinuityTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "The 6th CPC pay reached above is carried forward automatically. The 7th CPC conversion is now applied as on 01 January 2016; no re-entry of pay is required.",
            color = ContinuityTextSecondary, fontSize = 12.sp
        )

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("6th CPC Pay Carried Forward", color = ContinuityBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                ContinuityRow("Pay in Pay Band", payInPayBand)
                ContinuityRow("Grade Pay", gradePay)
                ContinuityRow("6th CPC Basic Pay", payInPayBand + gradePay)
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("7th CPC Conversion — 01 January 2016", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                ContinuityRow("Existing Pay (PB + GP)", calculation.existingPay)
                Text("${formatContinuityCurrency(calculation.existingPay)} × ${calculation.fitmentFactor} = ${String.format(Locale.US, "%.2f", calculation.multipliedPay)}", color = ContinuityTextSecondary, fontSize = 13.sp)
                ContinuityRow("Rounded to nearest rupee", calculation.roundedPay)
                Text("Applicable 7th CPC Level: Level ${calculation.level}", color = ContinuityBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Surface(Modifier.fillMaxWidth(), color = ContinuityBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("7th CPC Revised Basic Pay", color = ContinuityTextSecondary, fontSize = 13.sp)
                        Text(formatContinuityCurrency(calculation.revisedBasicPay), color = ContinuityBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text("Pay on 01 January 2016", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("DNI: 01 July 2016", color = ContinuityTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (incrementSteps.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("7th CPC Increment Progression", color = ContinuityBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    incrementSteps.forEachIndexed { index, step ->
                        Surface(Modifier.fillMaxWidth(), color = ContinuityBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("Increment ${index + 1}", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Date: ${formatContinuityDate(step.date)}", color = ContinuityTextSecondary, fontSize = 12.sp)
                                    Text("Pay thereon: ${formatContinuityCurrency(step.pay)}", color = ContinuityBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                TextButton(onClick = { incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) } }) { Text("Delete", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                val nextDate = incrementSteps.lastOrNull()?.let { addSeventhYears(it.date, 1) } ?: julyFirst2016ForContinuity()
                getSixthToSeventhNextCell(calculation.level, latest7thPay)?.let { nextPay ->
                    incrementSteps = incrementSteps + SeventhCpcIncrementStep(nextPay, nextDate)
                }
            }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ContinuityBlue), shape = RoundedCornerShape(12.dp)) {
                Text("Next Increment", fontWeight = FontWeight.Bold)
            }
            Button(onClick = { incrementSteps = incrementSteps.dropLast(1) }, enabled = incrementSteps.isNotEmpty(), Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = ContinuityBlue), shape = RoundedCornerShape(12.dp)) {
                Text("Undo Last", fontWeight = FontWeight.Bold)
            }
        }

        PromotionMacpFromConversion(calculation.level, latest7thPay, knownDni)
    }
}

@Composable
private fun ContinuityRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ContinuityTextSecondary, fontSize = 13.sp)
        Text(formatContinuityCurrency(value), color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatContinuityCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
private fun formatContinuityDate(value: Long): String = java.text.SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(java.util.Date(value))
private fun julyFirst2016ForContinuity(): Long = java.util.Calendar.getInstance().apply { clear(); set(2016, java.util.Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun addSeventhYears(date: Long, years: Int): Long = java.util.Calendar.getInstance().apply { timeInMillis = date; add(java.util.Calendar.YEAR, years) }.timeInMillis
