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
fun SixthCpcEventsSection(calculation: FifthToSixthResult, onContinueToSeventh: ((String, Int, Int) -> Unit)?) {
    var events by remember(calculation.revisedBasicPay, calculation.gradePay) { mutableStateOf<List<SixthCpcEventChain>>(emptyList()) }
    var showEventForm by remember { mutableStateOf(false) }
    var eventType by remember { mutableStateOf("Promotion") }
    var targetGradePay by remember { mutableStateOf<Int?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var targetMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val currentPayInBand = events.lastOrNull()?.let { it.increments.lastOrNull()?.payInPayBand ?: it.result.newPayInPayBand } ?: calculation.payInPayBand
    val currentGradePay = events.lastOrNull()?.result?.newGradePay ?: calculation.gradePay
    val currentPayBand = events.lastOrNull()?.result?.newPayBand ?: calculation.scale.payBand

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Promotion / MACP Events", color = Color(0xFF172B4D), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Events are applied sequentially to the latest 6th CPC pay. An event does not erase the preceding increment history.", color = Color(0xFF5B6B7A), fontSize = 12.sp)

        events.forEachIndexed { index, chain ->
            val event = chain.result
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${event.eventType} ${index + 1}", Modifier.weight(1f), color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        TextButton(onClick = { events = events.take(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                    Text("Date: ${formatSixthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    SixthEventRow("Old Pay in Pay Band", event.oldPayInPayBand)
                    SixthEventRow("Old Grade Pay", event.oldGradePay)
                    SixthEventRow("3% Increment", event.increment)
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
                            eventType = "Promotion"
                            showEventForm = true
                        }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Promotion / MACP", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        Button(onClick = { targetGradePay = null; eventDate = null; eventType = "Promotion"; showEventForm = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Promotion / MACP Event", fontWeight = FontWeight.Bold) }

        if (showEventForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New 6th CPC Event", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current: $currentPayBand | Pay in Pay Band ${formatSixthEventCurrency(currentPayInBand)} | Grade Pay ${formatSixthEventCurrency(currentGradePay)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                    Text("Event type", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = eventType == "Promotion", onClick = { eventType = "Promotion" }); Text("Promotion", fontSize = 13.sp)
                        RadioButton(selected = eventType == "MACP", onClick = { eventType = "MACP" }); Text("MACP", fontSize = 13.sp)
                    }
                    Box {
                        OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetGradePay?.let { "Target Grade Pay: ${formatSixthEventCurrency(it)}" } ?: "Select Target Grade Pay", Modifier.weight(1f)); Text("▼") }
                        DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                            val targets = if (eventType == "MACP") listOfNotNull(nextSixthCpcMacpGradePay(currentGradePay)) else sixthCpcGradePayHierarchy().filter { it > currentGradePay }
                            targets.forEach { gp -> DropdownMenuItem(text = { Text("GP ${formatSixthEventCurrency(gp)} — ${bandForGradePay(gp).title}") }, onClick = { targetGradePay = gp; targetMenu = false }) }
                        }
                    }
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let { "Event Date: ${formatSixthEventDate(it)}" } ?: "Select Event Date", Modifier.weight(1f)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { showEventForm = false }) { Text("Cancel") }
                        Button(onClick = {
                            if (targetGradePay != null && eventDate != null) {
                                val currentPb = events.lastOrNull()?.let { it.increments.lastOrNull()?.payInPayBand ?: it.result.newPayInPayBand } ?: calculation.payInPayBand
                                val currentGp = events.lastOrNull()?.result?.newGradePay ?: calculation.gradePay
                                val result = calculateSixthCpcPromotionOrMacp(currentPb, currentGp, targetGradePay!!, eventDate!!, eventType)
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
                Text("6th CPC Promotion / MACP Rule Basis", color = Color(0xFF172B4D), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Promotion: Rule 13 provides one increment based on 3% of Pay in Pay Band plus existing Grade Pay, rounded to the next Rs.10, followed by the Grade Pay of the promotion post.", color = Color(0xFF172B4D), fontSize = 12.sp)
                Text("MACP: financial upgradation is to the immediate next higher Grade Pay in the prescribed hierarchy; it is personal financial upgradation and does not itself constitute functional promotion.", color = Color(0xFF172B4D), fontSize = 12.sp)
                Text("Special cases, option under FR 22, NPA/special pay, bunching and cadre-specific promotion hierarchies require separate verification.", color = Color(0xFF5B6B7A), fontSize = 12.sp)
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) } }) { DatePicker(state) }
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
