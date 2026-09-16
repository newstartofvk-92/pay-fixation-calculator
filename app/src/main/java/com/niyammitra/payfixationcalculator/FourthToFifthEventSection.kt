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
import androidx.compose.runtime.LaunchedEffect
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

enum class FifthCpcEventType { PROMOTION, ACP }
enum class FifthCpcFixationOption { FROM_EVENT_DATE, FROM_DNI }

data class FifthCpcEventResult(
    val eventDate: Long,
    val eventType: FifthCpcEventType,
    val fixationOption: FifthCpcFixationOption,
    val oldPay: Int,
    val firstIncrementedPay: Int,
    val secondIncrementedPay: Int? = null,
    val newPay: Int,
    val targetScale: FifthCpcScale,
    val nextIncrementDate: Long,
    val ruleBasis: List<String>
)

fun calculateFifthCpcEvent(eventDate: Long, currentPay: Int, currentScale: FifthCpcScale, targetScale: FifthCpcScale, fixationOption: FifthCpcFixationOption, eventType: FifthCpcEventType = FifthCpcEventType.PROMOTION, knownDniDate: Long? = null): FifthCpcEventResult {
    require(currentPay > 0) { "Current 5th CPC basic pay must be positive." }
    require(eventDate >= fifthCpcConversionDate() && eventDate <= fifthCpcEndDate()) { "The event date must fall between 01 January 1996 and 31 December 2005." }
    require(targetScale.title != currentScale.title) { "The target scale must differ from the current scale." }
    val firstIncrementedPay = calculateFifthCpcNextStage(currentPay, currentScale) ?: currentPay
    val fixationBase = if (fixationOption == FifthCpcFixationOption.FROM_DNI) calculateFifthCpcNextStage(firstIncrementedPay, currentScale) ?: firstIncrementedPay else firstIncrementedPay
    val newPay = findEqualOrNextHigherFifthCpcStage(fixationBase, targetScale)
    val effectiveDni = if (fixationOption == FifthCpcFixationOption.FROM_DNI) {
        require(knownDniDate != null) { "A known 5th CPC DNI is required for the From Date of DNI option." }
        nextFifthCpcDniOnOrAfter(eventDate, knownDniDate)
    } else eventDate
    val nextIncrementDate = addFifthCpcYear(effectiveDni)
    val ruleBasis = buildList {
        add("Event type: ${if (eventType == FifthCpcEventType.PROMOTION) "Promotion" else "ACP financial upgradation"}.")
        add("Pay fixation uses the applicable Fundamental Rule promotion/fixation method under the 5th CPC pay structure.")
        add("One increment in the current 5th CPC scale is allowed for the event before placement in the higher scale.")
        if (fixationOption == FifthCpcFixationOption.FROM_DNI) add("From-DNI option: the existing 5th CPC increment date is used; no fixed 01 July date is assumed.") else add("From-Date-of-Event option: the event date is used as the fixation date; the next increment follows the applicable qualifying period.")
        add("The resulting pay is placed at the stage equal to, or next higher than, the fixation amount in the selected higher scale.")
        add("Under the 5th CPC framework, the next increment is linked to the date on which it would have accrued under the existing scale; the uniform 01 July rule belongs to the 6th CPC revised pay structure.")
    }
    return FifthCpcEventResult(eventDate, eventType, fixationOption, currentPay, firstIncrementedPay, if (fixationOption == FifthCpcFixationOption.FROM_DNI) fixationBase else null, newPay, targetScale, nextIncrementDate, ruleBasis)
}

fun calculateFifthCpcNextStage(currentPay: Int, scale: FifthCpcScale): Int? = fifthCpcStages(scale.title).firstOrNull { it > currentPay }
fun findEqualOrNextHigherFifthCpcStage(currentPay: Int, scale: FifthCpcScale): Int { val stages = fifthCpcStages(scale.title); if (stages.isEmpty()) return maxOf(scale.payBandMinimum, currentPay); return stages.firstOrNull { it >= currentPay } ?: stages.last() }

private fun fifthCpcStages(notation: String): List<Int> {
    val numbers = Regex("\\d+").findAll(notation.substringBefore(" (PB-" )).map { it.value.toInt() }.toList()
    if (numbers.isEmpty()) return emptyList()
    if (numbers.size == 1) return numbers
    val result = mutableListOf<Int>(); var current = numbers[0]; result += current; var index = 1
    while (index + 1 < numbers.size) { val increment = numbers[index]; val end = numbers[index + 1]; if (increment <= 0 || end < current) break; while (current + increment <= end) { current += increment; result += current }; if (current < end) { current = end; result += current }; index += 2 }
    return result.distinct()
}

private fun nextFifthCpcDniOnOrAfter(eventDate: Long, knownDniDate: Long): Long { var dni = knownDniDate; while (dni < eventDate) dni = addFifthCpcYear(dni); return dni }
private fun addFifthCpcYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun fifthCpcConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun fifthCpcEndDate(): Long = Calendar.getInstance().apply { clear(); set(2005, Calendar.DECEMBER, 31, 0, 0, 0) }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourthToFifthEventSection(currentPay: Int, currentScale: FifthCpcScale, currentDate: Long, knownDniDate: Long? = null, onLatestStateChange: ((FifthCpcScale, Int, Long) -> Unit)? = null, onContinueToSixth: ((FifthCpcScale, Int) -> Unit)? = null) {
    var events by remember(currentPay, currentScale.title, currentDate) { mutableStateOf<List<FifthCpcEventResult>>(emptyList()) }
    var postIncrements by remember(currentPay, currentScale.title, currentDate) { mutableStateOf<List<Pair<Int, Long>>>(emptyList()) }
    var showForm by remember(currentPay, currentScale.title, currentDate) { mutableStateOf(true) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var eventType by remember { mutableStateOf(FifthCpcEventType.PROMOTION) }
    var fixationOption by remember { mutableStateOf(FifthCpcFixationOption.FROM_EVENT_DATE) }
    var targetScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var scaleMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val latestEvent = events.lastOrNull()
    val basePay = postIncrements.lastOrNull()?.first ?: latestEvent?.newPay ?: currentPay
    val baseDate = postIncrements.lastOrNull()?.second ?: latestEvent?.eventDate ?: currentDate
    val baseScale = latestEvent?.targetScale ?: currentScale
    val canContinueToSixth = baseDate >= fifthCpcEndDate()
    LaunchedEffect(baseScale.title, basePay, baseDate) { onLatestStateChange?.invoke(baseScale, basePay, baseDate) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("5th CPC Events", color = Color(0xFF172B4D), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Add events chronologically. The 5th CPC uses the employee's applicable increment date; this section does not impose the 6th CPC uniform 01 July rule.", color = Color(0xFF5B6B7A), fontSize = 12.sp)
        events.forEachIndexed { index, event ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("Event ${index + 1}: ${if (event.eventType == FifthCpcEventType.PROMOTION) "Promotion" else "ACP"}", Modifier.weight(1f), color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); TextButton(onClick = { events = events.take(index); postIncrements = emptyList() }) { Text("Delete", fontWeight = FontWeight.Bold) } }
                Text("Date: ${formatFifthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Fixation: ${if (event.fixationOption == FifthCpcFixationOption.FROM_EVENT_DATE) "From Date of Event" else "From Date of DNI"}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                FifthEventRow("Old 5th CPC Basic Pay", event.oldPay); FifthEventRow("Pay after event increment", event.firstIncrementedPay); event.secondIncrementedPay?.let { FifthEventRow("Pay after intervening DNI increment", it) }; FifthEventRow("Fixed Pay in higher scale", event.newPay)
                Text("Higher 5th CPC Scale: ${event.targetScale.title}", color = Color(0xFF5B6B7A), fontSize = 13.sp); Text("Next DNI: ${formatFifthEventDate(event.nextIncrementDate)}", color = Color(0xFF5B6B7A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            } }
        }
        if (postIncrements.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("5th CPC Increment Progression", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                postIncrements.forEachIndexed { index, item -> Surface(Modifier.fillMaxWidth().padding(vertical = 2.dp), color = Color(0xFF1769AA).copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Increment ${index + 1} — ${formatFifthEventDate(item.second)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); Text("5th CPC Basic Pay: ${formatFifthEventCurrency(item.first)}", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }; TextButton(onClick = { postIncrements = postIncrements.toMutableList().also { it.removeAt(index) } }) { Text("Delete", fontWeight = FontWeight.Bold) } } } }
            } }
        }
        if (latestEvent != null) {
            val nextPostPay = calculateFifthCpcNextStage(basePay, baseScale)
            val nextPostDate = if (postIncrements.isEmpty()) latestEvent.nextIncrementDate else addFifthCpcYear(postIncrements.last().second)
            Button(onClick = { if (nextPostPay != null && nextPostDate <= fifthCpcEndDate()) postIncrements = postIncrements + (nextPostPay to nextPostDate) }, enabled = nextPostPay != null && nextPostDate <= fifthCpcEndDate(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Next Increment", fontWeight = FontWeight.Bold) }
        }
        if (events.isNotEmpty() && !showForm) Button(onClick = { showForm = true; eventDate = null; targetScale = null; fixationOption = FifthCpcFixationOption.FROM_EVENT_DATE }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Another Event", fontWeight = FontWeight.Bold) }
        if (showForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("New 5th CPC Event", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Current: ${baseScale.title} | Basic Pay ${formatFifthEventCurrency(basePay)} | Pay-state date ${formatFifthEventDate(baseDate)}", color = Color(0xFF5B6B7A), fontSize = 12.sp); knownDniDate?.let { Text("Known 5th CPC DNI: ${formatFifthEventDate(it)}", color = Color(0xFF5B6B7A), fontSize = 12.sp) }
                Text("1. Date of Event", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let { formatFifthEventDate(it) } ?: "Select Event Date", Modifier.weight(1f)); Text("📅") }
                Text("2. Event Type", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = eventType == FifthCpcEventType.PROMOTION, onClick = { eventType = FifthCpcEventType.PROMOTION }); Text("Promotion", Modifier.padding(end = 12.dp), fontSize = 12.sp); RadioButton(selected = eventType == FifthCpcEventType.ACP, onClick = { eventType = FifthCpcEventType.ACP }); Text("ACP", fontSize = 12.sp) }
                Text("3. Fixation Option", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = fixationOption == FifthCpcFixationOption.FROM_EVENT_DATE, onClick = { fixationOption = FifthCpcFixationOption.FROM_EVENT_DATE }); Text("From Date of Event", fontSize = 12.sp) }; Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = fixationOption == FifthCpcFixationOption.FROM_DNI, onClick = { fixationOption = FifthCpcFixationOption.FROM_DNI }); Text("From Date of DNI", fontSize = 12.sp) }
                Text("4. Applicable higher 5th CPC scale", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(if (eventType == FifthCpcEventType.ACP) "Select the ACP scale applicable to the employee/post hierarchy." else "Select the promotional scale applicable to the promoted post.", color = Color(0xFF5B6B7A), fontSize = 11.sp)
                Box { OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetScale?.title ?: "Select higher 5th CPC scale", Modifier.weight(1f)); Text("▼") }; DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) { FifthToSixthCpcData.scales.filter { it.title != baseScale.title }.forEach { scale -> DropdownMenuItem(text = { Text(scale.title) }, onClick = { targetScale = scale; scaleMenu = false }) } } }
                val selectedDate = eventDate; val validDate = selectedDate != null && selectedDate >= baseDate && selectedDate <= fifthCpcEndDate(); val validTarget = targetScale != null && targetScale!!.title != baseScale.title; val validDniOption = fixationOption != FifthCpcFixationOption.FROM_DNI || knownDniDate != null
                if (selectedDate != null && selectedDate < baseDate) Text("The event date is earlier than the current pay state. Complete the preceding increment/event first.", color = Color(0xFFC62828), fontSize = 11.sp)
                if (fixationOption == FifthCpcFixationOption.FROM_DNI && knownDniDate == null) Text("A known 5th CPC DNI is required for this option.", color = Color(0xFFC62828), fontSize = 11.sp)
                Button(onClick = { val date = eventDate; val target = targetScale; if (date != null && target != null) { val event = calculateFifthCpcEvent(date, basePay, baseScale, target, fixationOption, eventType, knownDniDate); events = events + event; postIncrements = emptyList(); showForm = false; eventDate = null; targetScale = null; eventType = FifthCpcEventType.PROMOTION; fixationOption = FifthCpcFixationOption.FROM_EVENT_DATE } }, enabled = validDate && validTarget && validDniOption, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Apply Event", fontWeight = FontWeight.Bold) }
            } }
        }
        if (canContinueToSixth && onContinueToSixth != null) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFE8F5E9)), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) { Text("Ready for 6th CPC", color = Color(0xFF1B5E20), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Text("Latest 5th CPC state: ${formatFifthEventCurrency(basePay)} in ${baseScale.title}. This pay will be carried to the 5th CPC → 6th CPC calculator as pay on 01 January 2006.", color = Color(0xFF33691E), fontSize = 12.sp); Button(onClick = { onContinueToSixth(baseScale, basePay) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), shape = RoundedCornerShape(12.dp)) { Text("Continue to 6th CPC", fontWeight = FontWeight.Bold) } } }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) { Text("Rule 8: the next increment in the 5th CPC revised scale follows the date on which the employee would have drawn the increment in the existing scale, subject to the specific provisos. The uniform 01 July annual increment date is a 6th CPC rule and is not applied here.", Modifier.padding(16.dp), color = Color(0xFF172B4D), fontSize = 11.sp) }
    }
    if (showDatePicker) { val state = rememberDatePickerState(initialSelectedDateMillis = eventDate ?: baseDate); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }, content = { DatePicker(state = state) }) }
}

@Composable
private fun FifthEventRow(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color(0xFF5B6B7A), fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(formatFifthEventCurrency(value), color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
private fun formatFifthEventDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatFifthEventCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(value)
