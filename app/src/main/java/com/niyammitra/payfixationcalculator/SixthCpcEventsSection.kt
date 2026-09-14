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
import androidx.compose.material3.ExperimentalMaterial3Api
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

enum class SixthCpcEventKind { PROMOTION, FINANCIAL_UPGRADATION, PAY_SCALE_UPGRADATION }

data class SixthCpcScaleUpgradeResult(val eventDate: Long, val oldPayInPayBand: Int, val oldGradePay: Int, val oldPayBand: String, val newPayInPayBand: Int, val newGradePay: Int, val newPayBand: String, val revisedBasicPay: Int, val nextIncrementDate: Long)
data class SixthCpcEventIncrement(val payInPayBand: Int, val gradePay: Int, val date: Long)
data class SixthCpcEventChain(val kind: SixthCpcEventKind, val result: SixthCpcEventResult? = null, val scaleUpgrade: SixthCpcScaleUpgradeResult? = null, val increments: List<SixthCpcEventIncrement> = emptyList())

private fun eventBasicPay(chain: SixthCpcEventChain): Int? = chain.increments.lastOrNull()?.let { it.payInPayBand + it.gradePay } ?: chain.result?.revisedBasicPay ?: chain.scaleUpgrade?.revisedBasicPay
private fun eventPayInBand(chain: SixthCpcEventChain): Int? = chain.increments.lastOrNull()?.payInPayBand ?: chain.result?.newPayInPayBand ?: chain.scaleUpgrade?.newPayInPayBand
private fun eventGradePay(chain: SixthCpcEventChain): Int? = chain.increments.lastOrNull()?.gradePay ?: chain.result?.newGradePay ?: chain.scaleUpgrade?.newGradePay
private fun eventPayBand(chain: SixthCpcEventChain): String? = chain.result?.newPayBand ?: chain.scaleUpgrade?.newPayBand
private fun eventLastDate(chain: SixthCpcEventChain): Long? = chain.increments.lastOrNull()?.date ?: chain.result?.eventDate ?: chain.scaleUpgrade?.eventDate
private fun july2015Date(): Long = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixthCpcEventsSection(calculation: FifthToSixthResult, startingPayInBand: Int = calculation.payInPayBand, startingGradePay: Int = calculation.gradePay, startingPayBand: String = calculation.scale.payBand, onContinueToSeventh: ((String, Int, Int) -> Unit)? = null, onLatestStateChange: ((String, Int, Int, Long) -> Unit)? = null) {
    var events by remember(calculation.revisedBasicPay, startingPayInBand, startingGradePay, startingPayBand) { mutableStateOf<List<SixthCpcEventChain>>(emptyList()) }
    var showEventForm by remember { mutableStateOf(false) }
    var eventKind by remember { mutableStateOf(SixthCpcEventKind.FINANCIAL_UPGRADATION) }
    var fixationOption by remember { mutableStateOf(SixthCpcFixationOption.FROM_EVENT_DATE) }
    var targetGradePay by remember { mutableStateOf<Int?>(null) }
    var targetPayBand by remember { mutableStateOf<SixthCpcPayBand?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var targetMenu by remember { mutableStateOf(false) }
    var payBandMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val latest = events.lastOrNull()
    val currentPayInBand = latest?.let { eventPayInBand(it) } ?: startingPayInBand
    val currentGradePay = latest?.let { eventGradePay(it) } ?: startingGradePay
    val currentPayBand = latest?.let { eventPayBand(it) } ?: startingPayBand
    val currentBasicPay = currentPayInBand + currentGradePay
    val autoScheme = eventDate?.let { financialUpgradationForSixthCpcEvent(it) }

    if (latest != null) {
        val latestPay = eventBasicPay(latest); val latestGp = eventGradePay(latest); val latestBand = eventPayBand(latest); val latestDate = eventLastDate(latest)
        if (latestPay != null && latestGp != null && latestBand != null && latestDate != null) onLatestStateChange?.invoke(latestBand, latestGp, latestPay - latestGp, latestDate)
    }

    fun resetForm() { targetGradePay = null; targetPayBand = null; eventDate = null; eventKind = SixthCpcEventKind.FINANCIAL_UPGRADATION; fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE; targetMenu = false; payBandMenu = false; showEventForm = true }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("6th CPC Events", color = Color(0xFF172B4D), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Continue chronologically from the latest 6th CPC pay. Events and their subsequent increments feed the next stage automatically.", color = Color(0xFF5B6B7A), fontSize = 12.sp)

        events.forEachIndexed { index, chain ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Event ${index + 1}: ${eventKindLabel(chain.kind)}", Modifier.weight(1f), color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); TextButton(onClick = { events = events.take(index) }) { Text("Delete", fontWeight = FontWeight.Bold) } }
                    chain.result?.let { event ->
                        Text("Date: ${formatSixthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        event.financialUpgradation?.let { Text("Scheme: ${it.name}", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                        Text("Fixation: ${if (event.fixationOption == SixthCpcFixationOption.FROM_EVENT_DATE) "From Date of Event" else "From Date of DNI (1 July)"}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                        SixthEventRow("Old Pay in Pay Band", event.oldPayInPayBand); SixthEventRow("Old Grade Pay", event.oldGradePay); SixthEventRow("Fixation Increment(s)", event.increment); SixthEventRow("New Pay in Pay Band", event.newPayInPayBand); SixthEventRow("New Grade Pay", event.newGradePay); SixthEventRow("New Basic Pay", event.revisedBasicPay)
                        Text("Pay Band: ${event.newPayBand}", color = Color(0xFF5B6B7A), fontSize = 13.sp); Text("Next DNI: ${formatSixthEventDate(event.nextIncrementDate)}", color = Color(0xFF5B6B7A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    chain.scaleUpgrade?.let { event ->
                        Text("Date: ${formatSixthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Placement / Conversion only — no promotion increment applied", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        SixthEventRow("Old Pay in Pay Band", event.oldPayInPayBand); SixthEventRow("Old Grade Pay", event.oldGradePay); SixthEventRow("Last Basic Pay carried forward", event.oldPayInPayBand + event.oldGradePay); SixthEventRow("Placed Pay in Pay Band", event.newPayInPayBand); SixthEventRow("New Grade Pay", event.newGradePay); SixthEventRow("Revised Basic Pay", event.revisedBasicPay)
                        Text("Upgraded / Revised Pay Band: ${event.newPayBand}", color = Color(0xFF5B6B7A), fontSize = 13.sp); Text("Next DNI: ${formatSixthEventDate(event.nextIncrementDate)}", color = Color(0xFF5B6B7A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (chain.increments.isNotEmpty()) {
                        Text("Post-event increment progression", color = Color(0xFF1769AA), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        chain.increments.forEachIndexed { incrementIndex, increment ->
                            Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text("Increment ${incrementIndex + 1} — ${formatSixthEventDate(increment.date)}", color = Color(0xFF172B4D), fontSize = 13.sp, fontWeight = FontWeight.Bold); Text("Pay in Pay Band: ${formatSixthEventCurrency(increment.payInPayBand)} + GP ${formatSixthEventCurrency(increment.gradePay)} = ${formatSixthEventCurrency(increment.payInPayBand + increment.gradePay)}", color = Color(0xFF5B6B7A), fontSize = 12.sp) }
                                    TextButton(onClick = { val updated = chain.increments.toMutableList().also { it.removeAt(incrementIndex) }; events = events.toMutableList().also { it[index] = chain.copy(increments = updated) } }) { Text("Delete", fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val canIncrement = chain.increments.lastOrNull()?.date?.let { it < july2015Date() } ?: (chain.result?.nextIncrementDate ?: chain.scaleUpgrade?.nextIncrementDate)?.let { it <= july2015Date() } ?: true
                        Button(onClick = {
                            val current = eventPayInBand(chain) ?: return@Button; val gp = eventGradePay(chain) ?: return@Button; val next = calculateSixthCpcNextIncrement(current, gp, bandForGradePay(gp).payBandMaximum)
                            if (next != null) { val nextPb = next - gp; val nextDate = chain.increments.lastOrNull()?.let { addSixthEventYears(it.date, 1) } ?: (chain.result?.nextIncrementDate ?: chain.scaleUpgrade?.nextIncrementDate ?: nextJulyOnOrAfter(System.currentTimeMillis())); if (nextDate <= july2015Date()) events = events.toMutableList().also { it[index] = chain.copy(increments = chain.increments + SixthCpcEventIncrement(nextPb, gp, nextDate)) } }
                        }, enabled = canIncrement, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text(if (canIncrement) "Next Increment" else "6th CPC Sequence Complete", fontWeight = FontWeight.Bold) }
                        Button(onClick = { resetForm() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Another Event", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        if (events.isEmpty()) Button(onClick = { resetForm() }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Event", fontWeight = FontWeight.Bold) }

        if (showEventForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New 6th CPC Event", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current: $currentPayBand | Pay in Pay Band ${formatSixthEventCurrency(currentPayInBand)} | Grade Pay ${formatSixthEventCurrency(currentGradePay)} | Basic Pay ${formatSixthEventCurrency(currentBasicPay)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                    Text("1. Date of event", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedButton(onClick = { showDatePicker = true }, Modifier.fillMaxWidth()) { Text(eventDate?.let { formatSixthEventDate(it) } ?: "Select Event Date", Modifier.weight(1f)) }
                    Text("2. Type of event", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    EventTypeRadio("Promotion", eventKind == SixthCpcEventKind.PROMOTION) { eventKind = SixthCpcEventKind.PROMOTION; targetGradePay = null }
                    EventTypeRadio("Financial Upgradation (ACP / MACP)", eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) { eventKind = SixthCpcEventKind.FINANCIAL_UPGRADATION; targetGradePay = null }
                    EventTypeRadio("Pay Scale Upgradation / Revision (Placement / Conversion)", eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) { eventKind = SixthCpcEventKind.PAY_SCALE_UPGRADATION; targetGradePay = null; targetPayBand = null }
                    if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION && eventDate != null) Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .07f), shape = RoundedCornerShape(12.dp)) { Text(if (autoScheme == SixthCpcFinancialUpgradation.ACP) "ACP — applicable up to 31 August 2008" else "MACP — applicable from 01 September 2008", Modifier.padding(14.dp), color = Color(0xFF1769AA), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                    if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                        Text("3. Select upgraded / revised pay band", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Box { OutlinedButton(onClick = { payBandMenu = true }, Modifier.fillMaxWidth()) { Text(targetPayBand?.title ?: "Select upgraded / revised pay band", Modifier.weight(1f)); Text("▼") }; DropdownMenu(expanded = payBandMenu, onDismissRequest = { payBandMenu = false }) { SixthToSeventhCpcData.payBands.forEach { band -> DropdownMenuItem(text = { Text(band.title) }, onClick = { targetPayBand = band; targetGradePay = null; payBandMenu = false }) } } }
                        targetPayBand?.let { band ->
                            Text("Select Grade Pay applicable to the upgraded / revised scale", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Box { OutlinedButton(onClick = { targetMenu = true }, Modifier.fillMaxWidth()) { Text(targetGradePay?.let { formatSixthEventCurrency(it) } ?: "Select Grade Pay", Modifier.weight(1f)); Text("▼") }; DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) { band.gradePays.forEach { gp -> DropdownMenuItem(text = { Text(formatSixthEventCurrency(gp)) }, onClick = { targetGradePay = gp; targetMenu = false }) } } }
                            Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("Last Basic Pay fetched automatically", color = Color(0xFF5B6B7A), fontSize = 12.sp); Text(formatSixthEventCurrency(currentBasicPay), color = Color(0xFF1769AA), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Placed Pay in Pay Band = Last Basic Pay fetched. No promotion increment is added.", color = Color(0xFF5B6B7A), fontSize = 11.sp) } }
                        }
                    } else {
                        if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION && autoScheme == SixthCpcFinancialUpgradation.ACP) Text("3. Select ACP promotional pay scale", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        else if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) { Text("3. MACP target", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); val nextGp = nextSixthCpcMacpGradePay(currentGradePay); targetGradePay = nextGp; Text(nextGp?.let { "Automatically selected: ${bandForGradePay(it).title} | GP ${formatSixthEventCurrency(it)}" } ?: "No higher Grade Pay is available.", color = Color(0xFF1769AA), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
                        else Text("3. Select promotional Grade Pay", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (eventKind == SixthCpcEventKind.PROMOTION || autoScheme == SixthCpcFinancialUpgradation.ACP) Box { OutlinedButton(onClick = { targetMenu = true }, Modifier.fillMaxWidth()) { Text(targetGradePay?.let { "Target GP ${formatSixthEventCurrency(it)}" } ?: "Select Grade Pay / ACP scale", Modifier.weight(1f)); Text("▼") }; DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) { sixthCpcGradePayHierarchy().filter { it > currentGradePay }.forEach { gp -> DropdownMenuItem(text = { Text("${bandForGradePay(gp).title} | GP ${formatSixthEventCurrency(gp)}") }, onClick = { targetGradePay = gp; targetMenu = false }) } } }
                    }
                    if (eventKind != SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                        Text("4. Fixation option", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = fixationOption == SixthCpcFixationOption.FROM_EVENT_DATE, onClick = { fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE }); Text("From Date of Event", fontSize = 12.sp); RadioButton(selected = fixationOption == SixthCpcFixationOption.FROM_DNI, onClick = { fixationOption = SixthCpcFixationOption.FROM_DNI }); Text("From Date of DNI", fontSize = 12.sp) }
                        Text("6th CPC has one normal annual increment date: 1 July.", color = Color(0xFF5B6B7A), fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { showEventForm = false }) { Text("Cancel") }
                        Button(onClick = {
                            val date = eventDate ?: return@Button
                            if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                                val band = targetPayBand; val gp = targetGradePay
                                // Per the agreed workflow, placement pay in the upgraded/revised scale is the LAST BASIC PAY fetched from the preceding stage.
                                val placedPayInBand = currentBasicPay
                                if (band != null && gp != null && gp > currentGradePay) {
                                    events = events + SixthCpcEventChain(SixthCpcEventKind.PAY_SCALE_UPGRADATION, scaleUpgrade = SixthCpcScaleUpgradeResult(date, currentPayInBand, currentGradePay, currentPayBand, placedPayInBand, gp, band.title, placedPayInBand + gp, nextJulyOnOrAfter(date)))
                                    showEventForm = false
                                }
                            } else if (targetGradePay != null) {
                                val scheme = if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) financialUpgradationForSixthCpcEvent(date) else null
                                val result = calculateSixthCpcPromotionOrMacp(currentPayInBand, currentGradePay, targetGradePay!!, date, if (eventKind == SixthCpcEventKind.PROMOTION) "Promotion" else "Financial Upgradation", fixationOption, scheme)
                                events = events + SixthCpcEventChain(eventKind, result = result); showEventForm = false
                            }
                        }, enabled = eventDate != null && if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) targetPayBand != null && targetGradePay != null && targetGradePay!! > currentGradePay else targetGradePay != null, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Apply Event", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("6th CPC Event Rule Basis", color = Color(0xFF172B4D), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("ACP applies for financial-upgradation events up to 31.08.2008; MACP applies from 01.09.2008. The normal annual increment date under the 6th CPC is 1 July.", color = Color(0xFF172B4D), fontSize = 12.sp); Text("Pay Scale Upgradation / Revision is separate from promotion and financial upgradation. The latest Basic Pay is fetched automatically and is used directly as the placed Pay in Pay Band. The selected Grade Pay is added to determine the revised Basic Pay; no promotion increment is added.", color = Color(0xFF172B4D), fontSize = 12.sp) } }
    }
    if (showDatePicker) { val state = rememberDatePickerState(initialSelectedDateMillis = eventDate); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = state) } }
}

@Composable private fun EventTypeRadio(label: String, selected: Boolean, onClick: () -> Unit) { Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = selected, onClick = onClick); Text(label, fontSize = 12.sp) } }
private fun eventKindLabel(kind: SixthCpcEventKind): String = when (kind) { SixthCpcEventKind.PROMOTION -> "Promotion"; SixthCpcEventKind.FINANCIAL_UPGRADATION -> "Financial Upgradation"; SixthCpcEventKind.PAY_SCALE_UPGRADATION -> "Pay Scale Upgradation / Revision" }
private fun nextJulyOnOrAfter(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; val year = get(Calendar.YEAR); set(Calendar.MONTH, Calendar.JULY); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); if (timeInMillis < date) set(Calendar.YEAR, year + 1) }.timeInMillis
private fun addSixthEventYears(date: Long, years: Int): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, years) }.timeInMillis
private fun formatSixthEventDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatSixthEventCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
@Composable private fun SixthEventRow(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color(0xFF5B6B7A), fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(formatSixthEventCurrency(value), color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
