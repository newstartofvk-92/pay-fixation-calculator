package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FifthCpcEventSection(
    currentPay: Int,
    currentScale: FifthCpcScale,
    currentDate: Long,
    onEventApplied: (String, FifthCpcScale, Int, Long) -> Unit
) {
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var targetScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var eventType by remember { mutableStateOf("Promotion") }
    var placementMethod by remember { mutableStateOf("Next Higher After Increment") }
    var implementationOption by remember { mutableStateOf("From Event Date") }
    var menuExpanded by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Int?>(null) }

    val currentStages = remember(currentScale) {
        parseFifthCpcScaleStagesForEvent(currentScale.title)
    }
    val target = targetScale
    val targetStages = remember(target) {
        target?.let { parseFifthCpcScaleStagesForEvent(it.title) }.orEmpty()
    }

    val feederIncrementedPay = currentStages.firstOrNull { it > currentPay } ?: currentPay
    val implementationDate = if (implementationOption == "From DNI") calculateEventDni(currentDate) else eventDate
    val placementBasePay = if (eventType == "Scale Upgradation" && placementMethod == "Next Higher Without Increment") currentPay else feederIncrementedPay
    val fixedPay = if (target != null && targetStages.isNotEmpty()) {
        targetStages.firstOrNull { it >= placementBasePay } ?: targetStages.last()
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("5th CPC Event", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Text(
            "For a promotion/ACP in the 5th CPC period, one increment is allowed in the feeder scale and pay is then fixed at the equal or next higher stage in the higher scale.",
            fontSize = 12.sp
        )

        OutlinedButton(onClick = { pickerOpen = true }, modifier = Modifier.fillMaxWidth()) {
            Text(eventDate?.let(::formatFifthEventDate) ?: "Select Event Date")
        }

        if (eventType == "Promotion" || eventType == "ACP") {
            Text("Pay Fixation Implementation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(implementationOption == "From Event Date", { implementationOption = "From Event Date" })
                Text("From Event Date", modifier = Modifier.padding(top = 12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(implementationOption == "From DNI", { implementationOption = "From DNI" })
                Text("From DNI", modifier = Modifier.padding(top = 12.dp))
            }
            Text("DNI: " + formatFifthEventDate(calculateEventDni(currentDate)), fontSize = 12.sp)
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            RadioButton(eventType == "Promotion", { eventType = "Promotion" })
            Text("Promotion", modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.width(12.dp))
            RadioButton(eventType == "ACP", { eventType = "ACP" })
            Text("ACP", modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.width(8.dp))
            RadioButton(eventType == "Scale Upgradation", { eventType = "Scale Upgradation" })
            Text("Scale Upgradation", modifier = Modifier.padding(top = 12.dp))
        }

        if (eventType == "Scale Upgradation") {
            Text("Placement Method", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(placementMethod == "Next Higher After Increment", { placementMethod = "Next Higher After Increment" })
                Text("Next higher after increment", modifier = Modifier.padding(top = 12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(placementMethod == "Next Higher Without Increment", { placementMethod = "Next Higher Without Increment" })
                Text("Next higher without increment", modifier = Modifier.padding(top = 12.dp))
            }
        }

        Box {
            OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(targetScale?.let { it.payBand + ": " + it.title } ?: "Select higher 5th CPC scale")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                FifthToSixthCpcData.scales
                    .filter { it.title != currentScale.title }
                    .forEach { scale ->
                        DropdownMenuItem(
                            text = { Text(scale.payBand + ": " + scale.title) },
                            onClick = {
                                targetScale = scale
                                menuExpanded = false
                                result = null
                            }
                        )
                    }
            }
        }

        if (target != null && fixedPay != null) {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(eventType + " fixation", fontWeight = FontWeight.Bold)
                    Text("Pay before event: ₹" + currentPay)
                    Text(if (eventType == "Scale Upgradation" && placementMethod == "Next Higher Without Increment") "Placement without feeder-scale increment" else "One feeder-scale increment: ₹" + feederIncrementedPay)
                    Text("Pay fixed in higher scale: ₹" + fixedPay)
                    Text("Event date: " + (eventDate?.let(::formatFifthEventDate) ?: "Not selected"))
                    Text("Fixation implemented from: " + (implementationDate?.let(::formatFifthEventDate) ?: "Not selected"))
                }
            }
        }

        Button(
            onClick = {
                val date = eventDate ?: return@Button
                val scale = target ?: return@Button
                val pay = fixedPay ?: return@Button
                result = pay
                val effectiveDate = implementationDate ?: return@Button
                onEventApplied(eventType, scale, pay, effectiveDate)
            },
            enabled = eventDate != null &&
                eventDate!! >= currentDate &&
                eventDate!! <= fifthCpcEventEndDate() &&
                target != null &&
                fixedPay != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply 5th CPC Event", fontWeight = FontWeight.Bold)
        }

        result?.let { Text(eventType + " fixed pay: ₹" + it, fontWeight = FontWeight.Bold) }
    }

    if (pickerOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate ?: currentDate)
        DatePickerDialog(
            onDismissRequest = { pickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    eventDate = state.selectedDateMillis
                    pickerOpen = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickerOpen = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

private fun parseFifthCpcScaleStagesForEvent(scale: String): List<Int> {
    val body = scale.removePrefix("Rs. ").substringBefore(" (").trim()
    val numbers = Regex("\\d+").findAll(body).map { it.value.toInt() }.toList()
    if (numbers.size < 2) return numbers.distinct()
    val stages = mutableListOf<Int>()
    var current = numbers[0]
    stages += current
    var index = 1
    while (index + 1 < numbers.size) {
        val increment = numbers[index]
        val boundary = numbers[index + 1]
        if (increment <= 0 || boundary <= current) {
            index += 2
            continue
        }
        while (current + increment <= boundary) {
            current += increment
            stages += current
        }
        if (current < boundary) {
            current = boundary
            stages += current
        }
        index += 2
    }
    return stages.distinct().sorted()
}

private fun calculateEventDni(currentDate: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = currentDate
        add(Calendar.YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun fifthCpcEventEndDate(): Long =
    Calendar.getInstance().apply {
        clear()
        set(2006, Calendar.JANUARY, 1, 0, 0, 0)
    }.timeInMillis

private fun formatFifthEventDate(value: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(value))
