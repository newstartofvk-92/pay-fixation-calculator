package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

data class SixthCpcEventIncrement(val payInPayBand: Int, val gradePay: Int, val date: Long)
data class SixthCpcEventChain(val result: SixthCpcEventResult, val increments: List<SixthCpcEventIncrement> = emptyList())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixthCpcEventsSection(
    calculation: FifthToSixthResult,
    onContinueToSeventh: ((String, Int, Int) -> Unit)?
) {
    var events by remember(calculation.revisedBasicPay, calculation.gradePay) { mutableStateOf<List<SixthCpcEventChain>>(emptyList()) }
    var showEventForm by remember { mutableStateOf(false) }
    var eventType by remember { mutableStateOf("Financial Upgradation") }
    var fixationOption by remember { mutableStateOf(SixthCpcFixationOption.FROM_EVENT_DATE) }
    var targetGradePay by remember { mutableStateOf<Int?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var targetMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val currentPayInBand = events.lastOrNull()?.let { it.increments.lastOrNull()?.payInPayBand ?: it.result.newPayInPayBand } ?: calculation.payInPayBand
    val currentGradePay = events.lastOrNull()?.result?.newGradePay ?: calculation.gradePay
    val currentPayBand = events.lastOrNull()?.result?.newPayBand ?: calculation.scale.payBand
    val autoScheme = eventDate?.let { financialUpgradationForSixthCpcEvent(it) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Promotion / Financial Upgradation", color = Color(0xFF172B4D), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Events are applied sequentially to the latest 6th CPC pay. Historical increment and event records are retained.", color = Color(0xFF5B6B7A), fontSize = 12.sp)

        events.forEachIndexed { index, chain ->
            val event = chain.result
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${event.eventType} ${index + 1}", Modifier.weight(1f), color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        TextButton(onClick = { events = events.take(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                    Text("Date: ${formatSixthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    event.financialUpgradation?.let { Text("Financial Upgradation: ${it.name}", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    Text("Fixation: ${if (event.fixationOption == SixthCpcFixationOption.FROM_EVENT_DATE) "From Date of Event" else "From Date of DNI (1 July)"}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                    SixthEventRow("Old Pay in Pay Band", event.oldPayInPayBand)
                    SixthEventRow("Old Grade Pay", event.oldGradePay)
                    SixthEventRow("Fixation Increment(s)", event.increment)
                    SixthEventRow("New Pay in Pay Band", event.newPayInPayBand)
                    SixthEventRow("New Grade Pay", event.newGradePay)
                    SixthEventRow("New Basic Pay", event.revisedBasicPay)
                    Text("Pay Band: ${event.newPayBand}", color = Color(0xFF5B6B7A), fontSize = 13.sp)
                    Text("Next DNI: ${formatSixthEventDate(event.nextIncrementDate)}", color = Color(0xFF5B6B7A), fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    if (chain.increments.isNotEmpty()) {
                        Text("Post-event increment progression", color = Color(0xFF1769AA), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        chain.increments.forEachIndexed { incrementIndex, increment ->
                            Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Increment ${incrementIndex + 1} — ${formatSixthEventDate(increment.date)}", color = Color(0xFF172B4D), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Pay in Pay Band: ${formatSixthEventCurrency(increment.payInPayBand)} + GP ${formatSixthEventCurrency(increment.gradePay)} = ${formatSixthEventCurrency(increment.payInPayBand + increment.gradePay)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                                    }
                                    TextButton(onClick = {
                                        val updated = chain.increments.toMutableList().also { it.removeAt(incrementIndex) }
                                        events = events.toMutableList().also { it[index] = chain.copy(increments = updated) }
                                    }) { Text("Delete", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = {
                            val current = chain.increments.lastOrNull()?.payInPayBand ?: event.newPayInPayBand
                            val next = calculateSixthCpcNextIncrement(current, event.newGradePay, bandForGradePay(event.newGradePay).payBandMaximum)
                            if (next != null) {
                                val nextPayInBand = next - event.newGradePay
                                val nextDate = chain.increments.lastOrNull()?.let { addSixthEventYears(it.date, 1) } ?: event.nextIncrementDate
                                val updated = chain.increments + SixthCpcEventIncrement(nextPayInBand, event.newGradePay, nextDate)
                                events = events.toMutableList().also { it[index] = chain.copy(increments = updated) }
                            }
                        }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Next Increment", fontWeight = FontWeight.Bold) }
                        Button(onClick = {
                            targetGradePay = null
                            eventDate = null
                            eventType = "Financial Upgradation"
                            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
                            showEventForm = true
                        }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Promotion / Financial Upgradation", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Button(onClick = {
            targetGradePay = null
            eventDate = null
            eventType = "Financial Upgradation"
            fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE
            showEventForm = true
        }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Promotion / Financial Upgradation", fontWeight = FontWeight.Bold) }

        if (showEventForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New 6th CPC Event", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current: $currentPayBand | Pay in Pay Band ${formatSixthEventCurrency(currentPayInBand)} | Grade Pay ${formatSixthEventCurrency(currentGradePay)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)

                    Text("1. Date of event", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(eventDate?.let { formatSixthEventDate(it) } ?: "Select Event Date", Modifier.weight(1f))
                    }

                    Text("2. Event type", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = eventType == "Financial Upgradation", onClick = { eventType = "Financial Upgradation" })
                        Text("Financial Upgradation", fontSize = 12.sp)
                        RadioButton(selected = eventType == "Promotion", onClick = { eventType = "Promotion" })
                        Text("Promotion", fontSize = 12.sp)
                    }

                    if (eventType == "Financial Upgradation" && eventDate != null) {
                        val schemeText = if (autoScheme == SixthCpcFinancialUpgradation.ACP) "ACP — applicable up to 31 August 2008" else "MACP — applicable from 01 September 2008"
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .07f), shape = RoundedCornerShape(12.dp)) {
                            Text(schemeText, Modifier.padding(14.dp), color = Color(0xFF1769AA), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }

                    if (eventType == "Financial Upgradation" && autoScheme == SixthCpcFinancialUpgradation.ACP) {
                        Text("3. Select ACP promotional pay scale", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box {
                            OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(targetGradePay?.let { "ACP scale: ${bandForGradePay(it).title} | GP ${formatSixthEventCurrency(it)}" } ?: "Select ACP promotional pay scale", Modifier.weight(1f))
                                Text("▼")
                            }
                            DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                                sixthCpcGradePayHierarchy().filter { it > currentGradePay }.forEach { gp ->
                                    DropdownMenuItem(text = { Text("${bandForGradePay(gp).title} | GP ${formatSixthEventCurrency(gp)}") }, onClick = { targetGradePay = gp; targetMenu = false })
                                }
                            }
                        }
                    } else if (eventType == "Financial Upgradation" && autoScheme == SixthCpcFinancialUpgradation.MACP) {
                        val nextGp = nextSixthCpcMacpGradePay(currentGradePay)
                        targetGradePay = nextGp
                        Text("3. MACP target", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(nextGp?.let { "Automatically selected: ${bandForGradePay(it).title} | GP ${formatSixthEventCurrency(it)}" } ?: "No higher Grade Pay is available in the configured hierarchy.", color = Color(0xFF1769AA), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    } else {
                        Text("3. Select promotional Grade Pay", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box {
                            OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(targetGradePay?.let { "Target GP ${formatSixthEventCurrency(it)}" } ?: "Select promotional Grade Pay", Modifier.weight(1f))
                                Text("▼")
                            }
                            DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                                sixthCpcGradePayHierarchy().filter { it > currentGradePay }.forEach { gp ->
                                    DropdownMenuItem(text = { Text("${bandForGradePay(gp).title} | GP ${formatSixthEventCurrency(gp)}") }, onClick = { targetGradePay = gp; targetMenu = false })
                                }
                            }
                        }
                    }

                    Text("4. Fixation option", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = fixationOption == SixthCpcFixationOption.FROM_EVENT_DATE, onClick = { fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE })
                        Text("From Date of Event", fontSize = 12.sp)
                        RadioButton(selected = fixationOption == SixthCpcFixationOption.FROM_DNI, onClick = { fixationOption = SixthCpcFixationOption.FROM_DNI })
                        Text("From Date of DNI", fontSize = 12.sp)
                    }
                    Text("6th CPC has one normal annual increment date: 1 July.", color = Color(0xFF5B6B7A), fontSize = 11.sp)

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { showEventForm = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (targetGradePay != null && eventDate != null) {
                                val currentPb = events.lastOrNull()?.let { it.increments.lastOrNull()?.payInPayBand ?: it.result.newPayInPayBand } ?: calculation.payInPayBand
                                val currentGp = events.lastOrNull()?.result?.newGradePay ?: calculation.gradePay
                                val scheme = if (eventType == "Financial Upgradation") financialUpgradationForSixthCpcEvent(eventDate!!) else null
                                val result = calculateSixthCpcPromotionOrMacp(currentPb, currentGp, targetGradePay!!, eventDate!!, eventType, fixationOption, scheme)
                                events = events + SixthCpcEventChain(result)
                                showEventForm = false
                            }
                        }, enabled = targetGradePay != null && eventDate != null, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA))) { Text("Apply Event", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        val latestEvent = events.lastOrNull()
        if (onContinueToSeventh != null) {
            Button(onClick = {
                val latestPayInBand = latestEvent?.let { it.increments.lastOrNull()?.payInPayBand ?: it.result.newPayInPayBand } ?: currentPayInBand
                val latestGp = latestEvent?.result?.newGradePay ?: currentGradePay
                val latestBand = latestEvent?.result?.newPayBand ?: currentPayBand
                onContinueToSeventh(latestBand.substringBefore(":").trim(), latestPayInBand, latestGp)
            }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Continue to 7th CPC", fontWeight = FontWeight.Bold) }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6th CPC Event Rule Basis", color = Color(0xFF172B4D), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("ACP applies for financial-upgradation events up to 31.08.2008. For these events the user specifies the applicable ACP promotional pay scale.", color = Color(0xFF172B4D), fontSize = 12.sp)
                Text("MACP applies from 01.09.2008. The application automatically selects the immediate next Grade Pay in the configured MACP hierarchy.", color = Color(0xFF172B4D), fontSize = 12.sp)
                Text("Both regimes use the applicable FR 22(1)(a)(1) fixation option: from the event date or from the date of next increment. During the 6th CPC period the normal DNI is 1 July.", color = Color(0xFF172B4D), fontSize = 12.sp)
                Text("Special cases, including cadre-specific ACP scales, FR 22 provisos, NPA/special pay, bunching and other service-specific instructions, require separate verification.", color = Color(0xFF5B6B7A), fontSize = 12.sp)
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; targetGradePay = null; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) } }) { DatePicker(state) }
    }
}

@Composable
private fun SixthEventRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF5B6B7A), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatSixthEventCurrency(value), color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun addSixthEventYears(date: Long, years: Int): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, years) }.timeInMillis
private fun formatSixthEventDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatSixthEventCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
