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
import kotlin.math.max

enum class FifthCpcFixationOption { FROM_EVENT_DATE, FROM_DNI }

data class FifthCpcEventResult(
    val eventDate: Long,
    val fixationOption: FifthCpcFixationOption,
    val oldPay: Int,
    val firstIncrementedPay: Int,
    val secondIncrementedPay: Int? = null,
    val newPay: Int,
    val targetScale: FifthCpcScale,
    val nextIncrementDate: Long,
    val ruleBasis: List<String>
)

fun calculateFifthCpcEvent(eventDate: Long, currentPay: Int, currentScale: FifthCpcScale, targetScale: FifthCpcScale, fixationOption: FifthCpcFixationOption): FifthCpcEventResult {
    require(currentPay > 0) { "Current 5th CPC basic pay must be positive." }
    require(eventDate >= fifthCpcConversionDate() && eventDate <= fifthCpcEndDate()) { "The 5th CPC event date must fall between 01 January 1996 and 31 December 2005." }
    val firstIncrementedPay = calculateFifthCpcNextStage(currentPay, currentScale) ?: currentPay
    val fixationBase = if (fixationOption == FifthCpcFixationOption.FROM_DNI) calculateFifthCpcNextStage(firstIncrementedPay, currentScale) ?: firstIncrementedPay else firstIncrementedPay
    val newPay = findEqualOrNextHigherFifthCpcStage(fixationBase, targetScale)
    val nextIncrementDate = if (fixationOption == FifthCpcFixationOption.FROM_DNI) addFifthCpcYear(nextJulyOnOrAfterFifthCpc(eventDate)) else nextJulyOnOrAfterFifthCpc(eventDate)
    val ruleBasis = buildList {
        add("The event is processed under the 5th CPC revised-pay structure using the applicable Fundamental Rule pay-fixation method.")
        add("One increment in the lower/current 5th CPC scale is taken for promotion / financial upgradation fixation.")
        if (fixationOption == FifthCpcFixationOption.FROM_DNI) add("From-DNI option: the intervening annual increment is first allowed in the current scale, followed by the event-related increment before placement in the higher scale.")
        add("The resulting pay is placed at the stage equal to or next higher than the fixation amount in the selected higher 5th CPC scale.")
        add("The 5th CPC annual increment cycle is based on 01 July, subject to the applicable rule and service-history provisos.")
    }
    return FifthCpcEventResult(eventDate, fixationOption, currentPay, firstIncrementedPay, if (fixationOption == FifthCpcFixationOption.FROM_DNI) fixationBase else null, newPay, targetScale, nextIncrementDate, ruleBasis)
}

fun calculateFifthCpcNextStage(currentPay: Int, scale: FifthCpcScale): Int? = fifthCpcStages(scale.title).firstOrNull { it > currentPay }

fun findEqualOrNextHigherFifthCpcStage(currentPay: Int, scale: FifthCpcScale): Int {
    val stages = fifthCpcStages(scale.title)
    if (stages.isEmpty()) return max(scale.payBandMinimum, currentPay)
    return stages.firstOrNull { it >= currentPay } ?: stages.last()
}

private fun fifthCpcStages(notation: String): List<Int> {
    val numbers = Regex("\\d+").findAll(notation.substringBefore(" (PB-" )).map { it.value.toInt() }.toList()
    if (numbers.isEmpty()) return emptyList()
    if (numbers.size == 1) return numbers
    val result = mutableListOf<Int>()
    var current = numbers[0]
    result += current
    var index = 1
    while (index + 1 < numbers.size) {
        val increment = numbers[index]
        val end = numbers[index + 1]
        if (increment <= 0 || end < current) break
        while (current + increment <= end) { current += increment; result += current }
        if (current < end) { current = end; result += current }
        index += 2
    }
    return result.distinct()
}

private fun nextJulyOnOrAfterFifthCpc(date: Long): Long {
    val source = Calendar.getInstance().apply { timeInMillis = date }
    val year = source.get(Calendar.YEAR)
    val july = Calendar.getInstance().apply { clear(); set(year, Calendar.JULY, 1, 0, 0, 0) }
    return if (date <= july.timeInMillis) july.timeInMillis else Calendar.getInstance().apply { clear(); set(year + 1, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
}

private fun addFifthCpcYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun fifthCpcConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun fifthCpcEndDate(): Long = Calendar.getInstance().apply { clear(); set(2005, Calendar.DECEMBER, 31, 0, 0, 0) }.timeInMillis
private fun fifthCpcJuly2005Date(): Long = Calendar.getInstance().apply { clear(); set(2005, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourthToFifthEventSection(currentPay: Int, currentScale: FifthCpcScale, currentDate: Long, onLatestStateChange: ((FifthCpcScale, Int, Long) -> Unit)? = null, onContinueToSixth: ((FifthCpcScale, Int) -> Unit)? = null) {
    var events by remember(currentPay, currentScale.title, currentDate) { mutableStateOf<List<FifthCpcEventResult>>(emptyList()) }
    var postIncrements by remember(currentPay, currentScale.title, currentDate) { mutableStateOf<List<Pair<Int, Long>>>(emptyList()) }
    var showForm by remember { mutableStateOf(false) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var fixationOption by remember { mutableStateOf(FifthCpcFixationOption.FROM_EVENT_DATE) }
    var targetScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var scaleMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val latestEvent = events.lastOrNull()
    val basePay = postIncrements.lastOrNull()?.first ?: latestEvent?.newPay ?: currentPay
    val baseDate = postIncrements.lastOrNull()?.second ?: latestEvent?.nextIncrementDate ?: currentDate
    val baseScale = latestEvent?.targetScale ?: currentScale
    val canContinueToSixth = baseDate >= fifthCpcJuly2005Date()
    onLatestStateChange?.invoke(baseScale, basePay, baseDate)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("5th CPC Events", color = Color(0xFF172B4D), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Continue chronologically from the latest 5th CPC pay. Promotion / ACP-type financial events and subsequent increments feed the 01 January 2006 hand-off.", color = Color(0xFF5B6B7A), fontSize = 12.sp)

        events.forEachIndexed { index, event ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Event ${index + 1}: Promotion / ACP", Modifier.weight(1f), color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        TextButton(onClick = { events = events.take(index); postIncrements = emptyList() }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                    Text("Date: ${formatFifthEventDate(event.eventDate)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Fixation: ${if (event.fixationOption == FifthCpcFixationOption.FROM_EVENT_DATE) "From Date of Event" else "From Date of DNI (01 July)"}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                    FifthEventRow("Old 5th CPC Basic Pay", event.oldPay)
                    FifthEventRow("Increment in current scale", event.firstIncrementedPay)
                    event.secondIncrementedPay?.let { FifthEventRow("Additional increment at DNI", it) }
                    FifthEventRow("Fixed Pay in higher scale", event.newPay)
                    Text("Higher 5th CPC Scale: ${event.targetScale.title}", color = Color(0xFF5B6B7A), fontSize = 13.sp)
                    Text("Next DNI: ${formatFifthEventDate(event.nextIncrementDate)}", color = Color(0xFF5B6B7A), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    event.ruleBasis.forEach { Text("• $it", color = Color(0xFF5B6B7A), fontSize = 11.sp) }
                }
            }
        }

        if (postIncrements.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Post-event 5th CPC Increment Progression", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    postIncrements.forEachIndexed { index, item ->
                        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1769AA).copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Increment ${index + 1} — ${formatFifthEventDate(item.second)}", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("5th CPC Basic Pay: ${formatFifthEventCurrency(item.first)}", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                TextButton(onClick = { postIncrements = postIncrements.toMutableList().also { it.removeAt(index) } }) { Text("Delete", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

        if (latestEvent != null) {
            val nextPostPay = calculateFifthCpcNextStage(basePay, baseScale)
            val nextPostDate = if (postIncrements.isEmpty()) baseDate else Calendar.getInstance().apply { timeInMillis = baseDate; add(Calendar.YEAR, 1) }.timeInMillis
            Button(onClick = { if (nextPostPay != null && nextPostDate <= fifthCpcEndDate()) postIncrements = postIncrements + (nextPostPay to nextPostDate) }, enabled = nextPostPay != null && nextPostDate <= fifthCpcEndDate(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) {
                Text("Next Increment", fontWeight = FontWeight.Bold)
            }
        }

        if (latestEvent == null || !canContinueToSixth) {
            Button(onClick = { showForm = true }, enabled = baseDate < fifthCpcEndDate(), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Add Event", fontWeight = FontWeight.Bold) }
        }

        if (showForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New 5th CPC Event", color = Color(0xFF1769AA), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current: ${baseScale.title} | Basic Pay ${formatFifthEventCurrency(basePay)} | State date ${formatFifthEventDate(baseDate)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                    Text("1. Date of Event", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let { formatFifthEventDate(it) } ?: "Select Event Date", Modifier.weight(1f)) }
                    Text("2. Type", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Promotion / ACP", color = Color(0xFF1769AA), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("3. Fixation Option", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(Modifier.fillMaxWidth()) { RadioButton(selected = fixationOption == FifthCpcFixationOption.FROM_EVENT_DATE, onClick = { fixationOption = FifthCpcFixationOption.FROM_EVENT_DATE }); Text("From Date of Event", Modifier.padding(top = 12.dp), fontSize = 12.sp) }
                    Row(Modifier.fillMaxWidth()) { RadioButton(selected = fixationOption == FifthCpcFixationOption.FROM_DNI, onClick = { fixationOption = FifthCpcFixationOption.FROM_DNI }); Text("From Date of DNI (01 July)", Modifier.padding(top = 12.dp), fontSize = 12.sp) }
                    Text("4. Select higher 5th CPC scale", color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetScale?.title ?: "Select higher 5th CPC scale", Modifier.weight(1f)); Text("▼") }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) { FifthToSixthCpcData.scales.filter { it.title != baseScale.title }.forEach { scale -> DropdownMenuItem(text = { Text(scale.title) }, onClick = { targetScale = scale; scaleMenu = false }) } }
                    }
                    val validDate = eventDate != null && eventDate!! >= baseDate && eventDate!! <= fifthCpcEndDate()
                    val validTarget = targetScale != null && targetScale != baseScale
                    Button(onClick = {
                        val date = eventDate ?: return@Button
                        val target = targetScale ?: return@Button
                        val event = calculateFifthCpcEvent(date, basePay, baseScale, target, fixationOption)
                        events = events + event
                        postIncrements = emptyList()
                        showForm = false
                        eventDate = null
                        targetScale = null
                        fixationOption = FifthCpcFixationOption.FROM_EVENT_DATE
                    }, enabled = validDate && validTarget, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Apply Event", fontWeight = FontWeight.Bold) }
                    Text("The event date must not precede the latest pay state and must fall within the 5th CPC period ending 31 December 2005.", color = Color(0xFF5B6B7A), fontSize = 11.sp)
                }
            }
        }

        if (canContinueToSixth && onContinueToSixth != null) {
            Button(onClick = { onContinueToSixth(baseScale, basePay) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1769AA)), shape = RoundedCornerShape(12.dp)) { Text("Continue to 6th CPC with Latest Pay", fontWeight = FontWeight.Bold) }
        }

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
            Text("This event layer uses a standard 5th CPC stage-based fixation model. ACP applicability, special personal pay, stagnation increments, bunching, and case-specific FR provisions should be verified against the employee's actual service record and applicable orders.", Modifier.padding(16.dp), color = Color(0xFF172B4D), fontSize = 12.sp)
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) } }) { DatePicker(state) }
    }
}

@Composable
private fun FifthEventRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF5B6B7A), fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatFifthEventCurrency(value), color = Color(0xFF172B4D), fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatFifthEventDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatFifthEventCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
