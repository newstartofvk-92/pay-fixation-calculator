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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ContinuityBlue = Color(0xFF1769AA)
private val ContinuityTextPrimary = Color(0xFF172B4D)
private val ContinuityTextSecondary = Color(0xFF5B6B7A)

enum class ContinuityPromotionBasis { EVENT_DATE, DNI }

data class ContinuityIncrementStep(val pay: Int, val date: Long)

@Composable
fun SeventhCpcContinuitySection(payBand: String, gradePay: Int, payInPayBand: Int) {
    val normalizedPayBand = payBand.substringBefore(":").trim()
    val seventhBand = remember(normalizedPayBand, gradePay) {
        SixthToSeventhCpcData.payBands.firstOrNull { band ->
            band.title.substringBefore(":").trim() == normalizedPayBand && gradePay in band.gradePays
        }
    }
    if (seventhBand == null) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
            Text("7th CPC conversion could not be matched automatically to the 6th CPC Pay Band / Grade Pay. Verify the applicable pay structure before proceeding.", Modifier.padding(16.dp), color = ContinuityTextPrimary, fontSize = 12.sp)
        }
        return
    }
    val calculation = remember(normalizedPayBand, gradePay, payInPayBand) {
        calculateSixthToSeventhCpc(payInPayBand, gradePay, seventhBand)
    } ?: run {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
            Text("7th CPC conversion could not be calculated from the carried-forward 6th CPC pay. Verify the pay inputs.", Modifier.padding(16.dp), color = ContinuityTextPrimary, fontSize = 12.sp)
        }
        return
    }

    var incrementSteps by remember(calculation.revisedBasicPay) { mutableStateOf<List<ContinuityIncrementStep>>(emptyList()) }
    val latest7thPay = incrementSteps.lastOrNull()?.pay ?: calculation.revisedBasicPay
    val knownDni = incrementSteps.lastOrNull()?.let { addSeventhYears(it.date, 1) } ?: julyFirst2016ForContinuity()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("7th CPC — Automatic Continuation", color = ContinuityTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("The latest 6th CPC pay is carried forward automatically. The 7th CPC conversion is applied as on 01 January 2016; the user does not re-enter the 6th CPC pay.", color = ContinuityTextSecondary, fontSize = 12.sp)
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
                    if (getSixthToSeventhNextCellShared(calculation.level, incrementSteps.lastOrNull()?.pay ?: calculation.revisedBasicPay) == null) {
                        Text("Final cell reached", color = ContinuityTextPrimary, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        Button(onClick = {
            val currentPay = incrementSteps.lastOrNull()?.pay ?: calculation.revisedBasicPay
            val nextDate = incrementSteps.lastOrNull()?.let { addSeventhYears(it.date, 1) } ?: julyFirst2016ForContinuity()
            getSixthToSeventhNextCellShared(calculation.level, currentPay)?.let { nextPay ->
                incrementSteps = incrementSteps + ContinuityIncrementStep(nextPay, nextDate)
            }
        }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = ContinuityBlue), shape = RoundedCornerShape(12.dp)) {
            Text("Next Increment", fontWeight = FontWeight.Bold)
        }

        SeventhCpcPromotionMacpContinuation(currentLevel = calculation.level, currentPay = latest7thPay, knownDni = knownDni)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeventhCpcPromotionMacpContinuation(currentLevel: String, currentPay: Int, knownDni: Long) {
    var promotedLevel by remember { mutableStateOf<String?>(null) }
    var promotionDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var promotedMenu by remember { mutableStateOf(false) }
    var basis by remember { mutableStateOf(ContinuityPromotionBasis.EVENT_DATE) }
    var eventApplied by remember { mutableStateOf(false) }
    var appliedPay by remember { mutableStateOf<Int?>(null) }
    var appliedLevel by remember { mutableStateOf<String?>(null) }
    var appliedDni by remember { mutableStateOf<Long?>(null) }
    var postSteps by remember { mutableStateOf<List<ContinuityIncrementStep>>(emptyList()) }

    val fixation = if (promotedLevel != null && promotionDate != null) {
        calculatePayFixation(currentLevel, currentPay, promotedLevel!!, promotionDate, knownDni, EmployeeCategory.ORDINARY)
    } else null
    val finalPay = fixation?.let { if (basis == ContinuityPromotionBasis.EVENT_DATE) it.option1.finalFixedPay else it.option2.finalFixedPay }
    val firstPostIncrementDate = fixation?.let { if (basis == ContinuityPromotionBasis.EVENT_DATE) it.option1.nextDni else it.option2.nextDni }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Promotion / MACP", color = ContinuityTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pay carried forward from the latest 7th CPC stage", color = ContinuityBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Present Pay Level: Level $currentLevel", color = ContinuityTextSecondary, fontSize = 13.sp)
                ContinuityRow("Latest Basic Pay", currentPay)
                Text("Known DNI: ${formatContinuityDate(knownDni)}", color = ContinuityTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Date of Promotion / MACP", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(promotionDate?.let { formatContinuityDate(it) } ?: "Select Event Date", Modifier.weight(1f))
                }
                Text("Fixation option", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = basis == ContinuityPromotionBasis.EVENT_DATE, onClick = { basis = ContinuityPromotionBasis.EVENT_DATE; eventApplied = false })
                    Text("From Date of Event", Modifier.padding(top = 12.dp), color = ContinuityTextPrimary, fontSize = 13.sp)
                }
                Row(Modifier.fillMaxWidth()) {
                    RadioButton(selected = basis == ContinuityPromotionBasis.DNI, onClick = { basis = ContinuityPromotionBasis.DNI; eventApplied = false })
                    Text("From Date of DNI", Modifier.padding(top = 12.dp), color = ContinuityTextPrimary, fontSize = 13.sp)
                }
                Text("Promoted / Upgraded Pay Level", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { promotedMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(promotedLevel?.let { "Level $it" } ?: "Select promoted / upgraded Level", Modifier.weight(1f))
                        Text("▼")
                    }
                    DropdownMenu(expanded = promotedMenu, onDismissRequest = { promotedMenu = false }) {
                        PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY).levels.filter { it > currentLevel }.forEach { level ->
                            DropdownMenuItem(text = { Text("Level $level") }, onClick = {
                                promotedLevel = level
                                promotedMenu = false
                                eventApplied = false
                            })
                        }
                    }
                }

                fixation?.let { result ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Fixation Result", color = ContinuityBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    if (basis == ContinuityPromotionBasis.EVENT_DATE) {
                        ContinuityRow("Pay in lower Level", result.option1.lowerLevelPay)
                        ContinuityRow("One increment in lower Level", result.option1.payWithIncrement)
                        ContinuityRow("Final placement", result.option1.finalFixedPay)
                    } else {
                        ContinuityRow("Pay until DNI", result.option2.payUntilDni)
                        ContinuityRow("Annual increment on DNI", result.option2.payWithAnnualIncrement)
                        ContinuityRow("Promotion / MACP increment", result.option2.payWithPromotionIncrement)
                        ContinuityRow("Final placement", result.option2.finalFixedPay)
                    }
                    Surface(Modifier.fillMaxWidth(), color = ContinuityBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Selected option final basic pay", color = ContinuityTextSecondary, fontSize = 13.sp)
                            Text(formatContinuityCurrency(finalPay ?: result.option1.finalFixedPay), color = ContinuityBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Button(
                        onClick = {
                            val pay = finalPay ?: return@Button
                            eventApplied = true
                            appliedPay = pay
                            appliedLevel = promotedLevel
                            appliedDni = firstPostIncrementDate
                            postSteps = emptyList()
                        },
                        enabled = finalPay != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ContinuityBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (eventApplied) "Event Applied" else "Apply Event", fontWeight = FontWeight.Bold)
                    }
                }

                if (eventApplied && appliedPay != null && appliedLevel != null) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("Event Applied — Continue from this Pay", color = ContinuityBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Pay Level: Level $appliedLevel", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    ContinuityRow("Pay after Promotion / MACP", appliedPay!!)
                    appliedDni?.let { Text("Next Increment / DNI: ${formatContinuityDate(it)}", color = ContinuityTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                    if (postSteps.isNotEmpty()) {
                        postSteps.forEachIndexed { index, step ->
                            Surface(Modifier.fillMaxWidth(), color = ContinuityBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Post-event Increment ${index + 1}", color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("Date: ${formatContinuityDate(step.date)}", color = ContinuityTextSecondary, fontSize = 12.sp)
                                        Text("Pay thereon: ${formatContinuityCurrency(step.pay)}", color = ContinuityBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                    TextButton(onClick = { postSteps = postSteps.toMutableList().also { it.removeAt(index) } }) { Text("Delete", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }

                    val postCurrentPay = postSteps.lastOrNull()?.pay ?: appliedPay!!
                    val postNextDate = postSteps.lastOrNull()?.let { addSeventhYears(it.date, 1) } ?: appliedDni
                    val postNextPay = appliedLevel?.let { getSixthToSeventhNextCellShared(it, postCurrentPay) }
                    Button(
                        onClick = {
                            val date = postNextDate ?: return@Button
                            postNextPay?.let { nextPay -> postSteps = postSteps + ContinuityIncrementStep(nextPay, date) }
                        },
                        enabled = postNextPay != null && postNextDate != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = ContinuityBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Next Increment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { promotionDate = state.selectedDateMillis; showDatePicker = false; eventApplied = false }) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            }
        ) { DatePicker(state) }
    }
}

@Composable
private fun ContinuityRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ContinuityTextSecondary, fontSize = 13.sp)
        Text(formatContinuityCurrency(value), color = ContinuityTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatContinuityCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(value)
private fun formatContinuityDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun julyFirst2016ForContinuity(): Long = Calendar.getInstance().apply { clear(); set(2016, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun addSeventhYears(date: Long, years: Int): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, years) }.timeInMillis
