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
import kotlin.math.ceil

private val ContinuationBlue = Color(0xFF1769AA)
private val ContinuationText = Color(0xFF172B4D)
private val ContinuationSecondary = Color(0xFF5B6B7A)
private val InterimAmber = Color(0xFF9A6700)

data class InlineSixthCpcIncrementStep(val pay: Int, val date: Long)

enum class InterimFixationOption { FROM_PROMOTION_DATE, FROM_NEXT_INCREMENT }

data class InterimSixthCpcFixationResult(
    val option: InterimFixationOption,
    val eventDate: Long,
    val oldPayInBand: Int,
    val oldGradePay: Int,
    val newGradePay: Int,
    val payInBandOnEvent: Int,
    val revisedBasicPayOnEvent: Int,
    val refixationDate: Long?,
    val payInBandOnRefixation: Int?,
    val revisedBasicPayOnRefixation: Int?,
    val ruleBasis: List<String>
)

@Composable
fun FifthToSixthContinuationSection(conversion: FifthToSixthResult) {
    var incrementSteps by remember(conversion.revisedBasicPay, conversion.scale.title) { mutableStateOf<List<InlineSixthCpcIncrementStep>>(emptyList()) }
    var eventLatestPayBand by remember { mutableStateOf<String?>(null) }
    var eventLatestGradePay by remember { mutableStateOf<Int?>(null) }
    var eventLatestPayInBand by remember { mutableStateOf<Int?>(null) }
    var eventLatestDate by remember { mutableStateOf<Long?>(null) }
    var automaticSeventhPayInBand by remember { mutableStateOf<Int?>(null) }
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
                OutlinedTextField(value = gradePayText, onValueChange = { value -> gradePayText = value.filter(Char::isDigit) }, label = { Text("Grade Pay") }, supportingText = { Text("Edit the Grade Pay if the applicable Grade Pay is different from the mapped value.") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
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

        InterimSixthCpcPeriodSection(startingPayInBand = latestPayInBand, startingGradePay = editedGradePay, startingPayBand = conversion.scale.payBand)

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

        Button(onClick = {
            val currentPayInBand = latest?.let { it.pay - editedGradePay } ?: conversion.payInPayBand
            val nextPay = calculateSixthCpcNextIncrement(currentPayInBand, editedGradePay, conversion.scale.payBandMaximum) ?: return@Button
            val nextDate = latest?.let { addInlineSixthYear(it.date) } ?: inlineSixthFirstIncrementDate()
            if (nextDate <= inlineSixthJuly2015Date()) {
                incrementSteps = incrementSteps + InlineSixthCpcIncrementStep(nextPay, nextDate)
                if (nextDate == inlineSixthJuly2015Date()) automaticSeventhPayInBand = nextPay - editedGradePay
            }
        }, enabled = !reaches2015 && gradePayText.toIntOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ContinuationBlue), shape = RoundedCornerShape(12.dp)) {
            Text(if (reaches2015) "01 July 2015 Reached — 7th CPC Starts Automatically" else "Next Increment", fontWeight = FontWeight.Bold)
        }

        SixthCpcEventsSection(calculation = conversion, startingPayInBand = latestPayInBand, startingGradePay = editedGradePay, startingPayBand = conversion.scale.payBand, onContinueToSeventh = null, onLatestStateChange = { band, gp, payInBand, date ->
            eventLatestPayBand = band
            eventLatestGradePay = gp
            eventLatestPayInBand = payInBand
            eventLatestDate = date
            if (date >= inlineSixthJuly2015Date()) automaticSeventhPayInBand = payInBand
        })

        if (reaches2016) SeventhCpcContinuitySection(payBand = continuityPayBand, gradePay = continuityGradePay, payInPayBand = continuityPayInBand)
    }
}

@Composable
private fun InterimSixthCpcPeriodSection(startingPayInBand: Int, startingGradePay: Int, startingPayBand: String) {
    var eventDateText by remember { mutableStateOf("") }
    var higherGradePayText by remember { mutableStateOf("") }
    var option by remember { mutableStateOf(InterimFixationOption.FROM_PROMOTION_DATE) }
    var result by remember { mutableStateOf<InterimSixthCpcFixationResult?>(null) }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("6th CPC Interim Period — 01.01.2006 to 29.08.2008", color = InterimAmber, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text("For promotion / financial upgradation before notification of the CCS (Revised Pay) Rules, 2008, Rule 5 and Clarification 2 dated 13.09.2008 provide specific fixation options.", color = ContinuationSecondary, fontSize = 12.sp)
            OutlinedTextField(value = eventDateText, onValueChange = { eventDateText = it }, label = { Text("Promotion / Upgradation Date (dd/MM/yyyy)") }, placeholder = { Text("e.g. 15/09/2006") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = higherGradePayText, onValueChange = { higherGradePayText = it.filter(Char::isDigit) }, label = { Text("Higher / Promotional Grade Pay") }, placeholder = { Text("e.g. 4600") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Fixation option", color = ContinuationText, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = option == InterimFixationOption.FROM_PROMOTION_DATE, onClick = { option = InterimFixationOption.FROM_PROMOTION_DATE }, label = { Text("From promotion date") })
                FilterChip(selected = option == InterimFixationOption.FROM_NEXT_INCREMENT, onClick = { option = InterimFixationOption.FROM_NEXT_INCREMENT }, label = { Text("From next increment") })
            }
            Text("Current 6th CPC position: ${formatInlineCurrency(startingPayInBand)} + GP ${formatInlineCurrency(startingGradePay)} in $startingPayBand", color = ContinuationSecondary, fontSize = 12.sp)
            Button(onClick = {
                val date = parseInlineDate(eventDateText)
                val higherGp = higherGradePayText.toIntOrNull()
                result = if (date != null && higherGp != null && higherGp > startingGradePay && date in interimSixthStartDate()..interimSixthNotificationDate()) calculateInterimSixthFixation(date, startingPayInBand, startingGradePay, higherGp, option) else null
            }, enabled = eventDateText.isNotBlank() && higherGradePayText.toIntOrNull()?.let { it > startingGradePay } == true, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = InterimAmber), shape = RoundedCornerShape(12.dp)) {
                Text("Calculate Interim Period Fixation", fontWeight = FontWeight.Bold)
            }
            result?.let { r ->
                HorizontalDivider()
                Text("Fixation Result", color = ContinuationText, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("Event date: ${formatInlineDate(r.eventDate)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Option: ${if (r.option == InterimFixationOption.FROM_PROMOTION_DATE) "From promotion / upgradation date" else "From next increment"}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Pay in Pay Band on event date: ${formatInlineCurrency(r.payInBandOnEvent)}", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("Grade Pay: ${formatInlineCurrency(r.newGradePay)}", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("Revised Basic Pay: ${formatInlineCurrency(r.revisedBasicPayOnEvent)}", color = ContinuationBlue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                r.refixationDate?.let { date ->
                    Text("Re-fixation date: ${formatInlineDate(date)}", color = ContinuationSecondary, fontSize = 13.sp)
                    Text("Pay in Pay Band after re-fixation: ${formatInlineCurrency(r.payInBandOnRefixation ?: 0)}", color = ContinuationText, fontWeight = FontWeight.Bold)
                    Text("Revised Basic Pay after re-fixation: ${formatInlineCurrency(r.revisedBasicPayOnRefixation ?: 0)}", color = ContinuationBlue, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("Rule basis", color = ContinuationText, fontWeight = FontWeight.Bold)
                r.ruleBasis.forEach { Text("• $it", color = ContinuationSecondary, fontSize = 11.sp) }
            }
        }
    }
}

private fun calculateInterimSixthFixation(eventDate: Long, oldPayInBand: Int, oldGradePay: Int, newGradePay: Int, option: InterimFixationOption): InterimSixthCpcFixationResult {
    val oldBasic = oldPayInBand + oldGradePay
    val oneIncrement = sixthInterimIncrement(oldBasic)
    return if (option == InterimFixationOption.FROM_PROMOTION_DATE) {
        val newPayInBand = oldPayInBand + oneIncrement
        InterimSixthCpcFixationResult(option, eventDate, oldPayInBand, oldGradePay, newGradePay, newPayInBand, newPayInBand + newGradePay, null, null, null, listOf(
            "CCS (Revised Pay) Rules, 2008, Rule 5: an employee placed in a higher pay scale between 01.01.2006 and 29.08.2008 may elect to switch to the revised structure from the promotion/upgradation date.",
            "Clarification 2 dated 13.09.2008: where fixation is from the promotion date, one increment is allowed in the revised structure and the higher Grade Pay is added."
        ))
    } else {
        val secondIncrement = sixthInterimIncrement(oldBasic + oneIncrement)
        val reFixDate = nextInterimJulyAfter(eventDate)
        val newPayInBand = oldPayInBand + oneIncrement + secondIncrement
        InterimSixthCpcFixationResult(option, eventDate, oldPayInBand, oldGradePay, newGradePay, oldPayInBand, oldPayInBand + newGradePay, reFixDate, newPayInBand, newPayInBand + newGradePay, listOf(
            "Rule 5 proviso: the Government servant may continue in the existing scale until the next increment and then switch to the revised pay structure.",
            "Clarification 2 dated 13.09.2008: when fixation is from the next increment, Pay in Pay Band remains unchanged on promotion and the higher Grade Pay is granted.",
            "On the next 01 July, two increments are allowed: one annual increment and one increment on account of promotion, computed successively from the basic pay immediately before promotion."
        ))
    }
}

private fun sixthInterimIncrement(basicPay: Int): Int = (ceil((basicPay * 0.03) / 10.0) * 10.0).toInt()
private fun nextInterimJulyAfter(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; val year = get(Calendar.YEAR); set(year, Calendar.JULY, 1, 0, 0, 0); if (timeInMillis <= date) add(Calendar.YEAR, 1) }.timeInMillis
private fun parseInlineDate(value: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time }.getOrNull()
private fun interimSixthStartDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1) }.timeInMillis
private fun interimSixthNotificationDate(): Long = Calendar.getInstance().apply { clear(); set(2008, Calendar.AUGUST, 29, 23, 59, 59) }.timeInMillis
private fun inlineSixthFirstIncrementDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun inlineSixthJuly2015Date(): Long = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun addInlineSixthYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun formatInlineDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatInlineCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
