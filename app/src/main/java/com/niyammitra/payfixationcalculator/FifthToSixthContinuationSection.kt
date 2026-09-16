package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val ContinuationBlue = Color(0xFF1769AA)
private val ContinuationText = Color(0xFF172B4D)
private val ContinuationSecondary = Color(0xFF5B6B7A)

data class InlineSixthCpcIncrementStep(val pay: Int, val date: Long)

@Composable
fun FifthToSixthContinuationSection(conversion: FifthToSixthResult) {
    var incrementSteps by remember(conversion.revisedBasicPay, conversion.scale.title) { mutableStateOf<List<InlineSixthCpcIncrementStep>>(emptyList()) }
    var eventLatestPayBand by remember { mutableStateOf<String?>(null) }
    var eventLatestGradePay by remember { mutableStateOf<Int?>(null) }
    var eventLatestPayInBand by remember { mutableStateOf<Int?>(null) }
    var eventLatestDate by remember { mutableStateOf<Long?>(null) }
    var automaticSeventhPayInBand by remember { mutableStateOf<Int?>(null) }

    val latest = incrementSteps.lastOrNull()
    val latestPayInBand = latest?.let { it.pay - conversion.gradePay } ?: conversion.payInBand
    val reaches2015 = latest?.date == inlineSixthJuly2015Date()
    val continuityPayBand = eventLatestPayBand ?: conversion.scale.payBand
    val continuityGradePay = eventLatestGradePay ?: conversion.gradePay
    val continuityPayInBand = eventLatestPayInBand ?: automaticSeventhPayInBand ?: latestPayInBand
    val reaches2016 = automaticSeventhPayInBand != null || eventLatestDate?.let { it >= inlineSixthJuly2015Date() } == true || reaches2015

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6th CPC Conversion", color = ContinuationBlue, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("The next increment has crossed 31 December 2005. The pay has been carried into the 6th CPC on the same screen.", color = ContinuationSecondary, fontSize = 13.sp)
                Text("5th CPC Pay: ${formatInlineCurrency(conversion.existingBasicPay)}", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("5th CPC Scale: ${conversion.scale.title}", color = ContinuationSecondary, fontSize = 13.sp)
                HorizontalDivider()
                Text("6th CPC Fitment", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("${formatInlineCurrency(conversion.existingBasicPay)} × 1.86 = ${String.format(Locale.US, "%.2f", conversion.multipliedPay)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Rounded pay: ${formatInlineCurrency(conversion.roundedPay)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Pay in ${conversion.scale.payBand}: ${formatInlineCurrency(conversion.payInBand)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Grade Pay: ${formatInlineCurrency(conversion.gradePay)}", color = ContinuationSecondary, fontSize = 13.sp)
                Surface(Modifier.fillMaxWidth(), color = ContinuationBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("6th CPC Revised Basic Pay", color = ContinuationSecondary, fontSize = 13.sp)
                        Text(formatInlineCurrency(conversion.revisedBasicPay), color = ContinuationBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Pay fixed as on 01 January 2006", color = ContinuationSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (incrementSteps.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("6th CPC Increment Progression", color = ContinuationBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    incrementSteps.forEachIndexed { index, step ->
                        val payInBand = step.pay - conversion.gradePay
                        Surface(Modifier.fillMaxWidth(), color = ContinuationBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Increment ${index + 1}", color = ContinuationSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Date: ${formatInlineDate(step.date)}", color = ContinuationText, fontSize = 13.sp)
                                    Text("Pay in Pay Band: ${formatInlineCurrency(payInBand)}", color = ContinuationBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                TextButton(onClick = { incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val currentPayInBand = latest?.let { it.pay - conversion.gradePay } ?: conversion.payInBand
                val nextPay = calculateSixthCpcNextIncrement(currentPayInBand, conversion.gradePay, conversion.scale.payBandMaximum) ?: return@Button
                val nextDate = latest?.let { addInlineSixthYear(it.date) } ?: inlineSixthFirstIncrementDate()
                if (nextDate <= inlineSixthJuly2015Date()) {
                    incrementSteps = incrementSteps + InlineSixthCpcIncrementStep(nextPay, nextDate)
                    if (nextDate == inlineSixthJuly2015Date()) automaticSeventhPayInBand = nextPay - conversion.gradePay
                }
            },
            enabled = !reaches2015,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = ContinuationBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (reaches2015) "01 July 2015 Reached — 7th CPC Starts Automatically" else "Next Increment", fontWeight = FontWeight.Bold) }

        SixthCpcEventsSection(
            calculation = conversion,
            startingPayInBand = latestPayInBand,
            startingGradePay = conversion.gradePay,
            startingPayBand = conversion.scale.payBand,
            onContinueToSeventh = null,
            onLatestStateChange = { band, gp, payInBand, date ->
                eventLatestPayBand = band
                eventLatestGradePay = gp
                eventLatestPayInBand = payInBand
                eventLatestDate = date
                if (date >= inlineSixthJuly2015Date()) automaticSeventhPayInBand = payInBand
            }
        )

        if (reaches2016) {
            SeventhCpcContinuitySection(
                payBand = continuityPayBand,
                gradePay = continuityGradePay,
                payInPayBand = continuityPayInBand
            )
        }
    }
}

private fun inlineSixthFirstIncrementDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun inlineSixthJuly2015Date(): Long = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun addInlineSixthYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun formatInlineDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatInlineCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
