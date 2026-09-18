package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FourthCpcEventSection(currentPay: Int, currentScale: FourthCpcScale, currentDate: Long) {
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var target by remember { mutableStateOf<FourthCpcScale?>(null) }
    var eventType by remember { mutableStateOf("Promotion") }
    var menu by remember { mutableStateOf(false) }
    var picker by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("4th CPC Event", fontWeight = FontWeight.ExtraBold)
        OutlinedButton(onClick = { picker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let(::fourthEventDate) ?: "Select Event Date") }
        Row {
            RadioButton(eventType == "Promotion", { eventType = "Promotion" }); Text("Promotion")
            Spacer(Modifier.width(8.dp))
            RadioButton(eventType == "ACP", { eventType = "ACP" }); Text("ACP")
        }
        Box {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text(target?.let { it.grade + ": " + it.existingScale } ?: "Select higher 4th CPC scale") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                FourthToFifthCpcData.scales.filter { it.grade != currentScale.grade }.forEach { s ->
                    DropdownMenuItem(text = { Text(s.grade + ": " + s.existingScale) }, onClick = { target = s; menu = false })
                }
            }
        }
        Button(onClick = {
            val t = target ?: return@Button
            val oneIncrement = calculateFourthCpcNextIncrement(currentPay, currentScale) ?: currentPay
            result = t.existingStages.firstOrNull { it >= oneIncrement } ?: t.existingStages.lastOrNull() ?: oneIncrement
        }, enabled = eventDate != null && eventDate!! >= currentDate && eventDate!! <= fourthEventConversionDate() && target != null, modifier = Modifier.fillMaxWidth()) {
            Text("Apply 4th CPC Event")
        }
        result?.let { Text(eventType + " fixed pay: ₹" + it, fontWeight = FontWeight.Bold) }
    }
    if (picker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate ?: currentDate)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; picker = false }) { Text("OK") } }, dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

private fun fourthEventDate(value: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(value))
private fun fourthEventConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1) }.timeInMillis