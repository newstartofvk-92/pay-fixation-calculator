package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
    var hasSixthCpcEvent by remember { mutableStateOf(false) }
    var gradePayText by remember(conversion.gradePay) { mutableStateOf(conversion.gradePay.toString()) }

    val editedGradePay = gradePayText.toIntOrNull()?.takeIf { it > 0 } ?: conversion.gradePay
    val latest = incrementSteps.lastOrNull()
    val latestPayInBand = latest?.let { it.pay - editedGradePay } ?: conversion.payInPayBand
    val currentSixthBasicPay = latestPayInBand + editedGradePay
    val reaches2015 = latest?.date == inlineSixthJuly2015Date()
    val continuityPayBand = eventLatestPayBand ?: conversion.scale.payBand
    val continuityGradePay = eventLatestGradePay ?: editedGradePay
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
                Text("Pay in ${conversion.scale.payBand}: ${formatInlineCurrency(conversion.payInPayBand)}", color = ContinuationSecondary, fontSize = 13.sp)

                OutlinedTextField(
                    value = gradePayText,
                    onValueChange = { value -> gradePayText = value.filter(Char::isDigit) },
                    label = { Text("Grade Pay") },
                    supportingText = { Text("Edit the Grade Pay if the applicable Grade Pay is different from the mapped value.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(Modifier.fillMaxWidth(), color = ContinuationBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("6th CPC Revised Basic Pay", color = ContinuationSecondary, fontSize = 13.sp)
                        Text(formatInlineCurrency(currentSixthBasicPay), color = ContinuationBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Pay fixed as on 01 January 2006", color = ContinuationSecondary, fontSize = 12.sp)
                        Text("Pay in Pay Band ${formatInlineCurrency(latestPayInBand)} + Grade Pay ${formatInlineCurrency(editedGradePay)}", color = ContinuationSecondary, fontSize = 12.sp)
                    }
                }
            }
        }

        if (incrementSteps.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("6th CPC Increment Progression", color = ContinuationBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    incrementSteps.forEachIndexed { index, step ->
                        val payInBand = step.pay - editedGradePay
                        Surface(Modifier.fillMaxWidth(), color = ContinuationBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Increment ${index + 1}", color = ContinuationSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Date: ${formatInlineDate(step.date)}", color = ContinuationText, fontSize = 13.sp)
                                    Text("Pay in Pay Band: ${formatInlineCurrency(payInBand)} + GP ${formatInlineCurrency(editedGradePay)} = ${formatInlineCurrency(step.pay)}", color = ContinuationBlue, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                TextButton(onClick = { incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) } }) { Text("Delete") }
                            }
                        }
                    }
                }
            }
        }

        if (!hasSixthCpcEvent) {
            Button(
                onClick = {
                    val currentPayInBand = latest?.let { it.pay - editedGradePay } ?: conversion.payInPayBand
                    val nextPay = calculateSixthCpcNextIncrement(currentPayInBand, editedGradePay, conversion.scale.payBandMaximum) ?: return@Button
                    val nextDate = latest?.let { addInlineSixthYear(it.date) } ?: inlineSixthFirstIncrementDate()
                    if (nextDate <= inlineSixthJuly2015Date()) {
                        incrementSteps = incrementSteps + InlineSixthCpcIncrementStep(nextPay, nextDate)
                        if (nextDate == inlineSixthJuly2015Date()) automaticSeventhPayInBand = nextPay - editedGradePay
                    }
                },
                enabled = !reaches2015 && gradePayText.toIntOrNull()?.let { it > 0 } == true,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ContinuationBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (reaches2015) "01 July 2015 Reached — 7th CPC Starts Automatically"
                    else "Next Increment",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SixthCpcEventsSection(
            calculation = conversion,
            startingPayInPayBand = latestPayInBand,
            startingGradePay = editedGradePay,
            startingPayBand = conversion.scale.payBand,
            onContinueToSeventh = null,
            onLatestStateChange = { band, gp, payInBand, date ->
                eventLatestPayBand = band
                eventLatestGradePay = gp
                eventLatestPayInBand = payInBand
                eventLatestDate = date
                if (date >= inlineSixthJuly2015Date()) automaticSeventhPayInBand = payInBand
            },
            onEventsStateChange = { hasSixthCpcEvent = it }
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
private fun formatInlineCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value)
