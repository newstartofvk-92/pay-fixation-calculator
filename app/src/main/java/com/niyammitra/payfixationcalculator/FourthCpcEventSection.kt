package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourthCpcEventSection(
    currentPay: Int,
    currentScale: FourthCpcScale,
    currentDate: Long,
    eventType: FourthCpcEventType,
    onEventApplied: (FourthCpcScale, Int, Long) -> Unit,
    onEventAppliedDetailed: ((FourthCpcScale, Int, Long, CpcJourneyEventKind) -> Unit)? = null,
    onCancel: () -> Unit
) {
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var target by remember { mutableStateOf<FourthCpcScale?>(null) }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    val eventPosition = target?.let { calculateFourthCpcEventPosition(currentPay, currentScale, it) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("New 4th CPC ${eventType.name.lowercase().replaceFirstChar { it.uppercase() }}", Modifier.weight(1f), fontWeight = FontWeight.ExtraBold)
            TextButton(onClick = onCancel) { Text("Cancel Draft") }
        }
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let(::fourthEventDate) ?: "Select Event Date") }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(target?.let { it.grade + ": " + it.existingScale } ?: "Select higher 4th CPC scale") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                FourthToFifthCpcData.scales.filter { it.grade != currentScale.grade }.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.grade + ": " + s.existingScale + if (s.existingStages.isEmpty()) " (progression unavailable)" else "") },
                        enabled = s.existingStages.isNotEmpty(),
                        onClick = { target = s; result = null; menu = false }
                    )
                }
            }
        }
        when (eventPosition) {
            is FourthCpcEventPositionResolution.Unresolved -> Text(eventPosition.message, color = androidx.compose.ui.graphics.Color(0xFFC62828), fontSize = 12.sp)
            else -> target?.progressionNote?.let { Text(it, color = androidx.compose.ui.graphics.Color(0xFFC62828), fontSize = 12.sp) }
        }
        Button(onClick = {
            when (val resolved = eventPosition) {
                is FourthCpcEventPositionResolution.Available -> {
                    result = resolved.position.pay
                    eventDate?.let {
                        onEventApplied(resolved.position.scale, resolved.position.pay, it)
                        onEventAppliedDetailed?.invoke(resolved.position.scale, resolved.position.pay, it, if (eventType == FourthCpcEventType.ACP) CpcJourneyEventKind.ACP else CpcJourneyEventKind.PROMOTION)
                    }
                }
                is FourthCpcEventPositionResolution.Unresolved -> Unit
                null -> Unit
            }
        }, enabled = eventDate != null && eventDate!! >= currentDate && eventDate!! <= fourthEventConversionDate() &&
            eventPosition is FourthCpcEventPositionResolution.Available, modifier = Modifier.fillMaxWidth()) {
            Text("Add ${eventType.name.lowercase().replaceFirstChar { it.uppercase() }} Event")
        }
        result?.let { Text(eventType.name.lowercase().replaceFirstChar { it.uppercase() } + " fixed pay: ₹" + it, fontWeight = FontWeight.Bold) }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate ?: currentDate)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; picker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

private fun fourthEventDate(value: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(value))
private fun fourthEventConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1) }.timeInMillis
