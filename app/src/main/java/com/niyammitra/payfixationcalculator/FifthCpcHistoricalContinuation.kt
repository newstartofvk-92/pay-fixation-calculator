package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

data class FifthCpcIncrementStep(val pay: Int, val date: Long)

private val FifthHistoricalBlue = Color(0xFF1769AA)
private val FifthHistoricalPrimary = Color(0xFF172B4D)
private val FifthHistoricalSecondary = Color(0xFF5B6B7A)

@Composable
fun FifthCpcHistoricalContinuation(
    initialScale: FifthCpcScale,
    initialPay: Int,
    conversionDate: Long,
    firstIncrementDate: Long?,
    onContinueToSixth: ((FifthCpcScale, Int) -> Unit)?
) {
    var selectedScale by remember(initialScale.title, initialPay, conversionDate) { mutableStateOf(initialScale) }
    var currentPay by remember(initialScale.title, initialPay, conversionDate) { mutableStateOf(initialPay) }
    var currentDate by remember(initialScale.title, initialPay, conversionDate) { mutableStateOf(conversionDate) }
    var firstDni by remember(initialScale.title, initialPay, conversionDate, firstIncrementDate) { mutableStateOf(firstIncrementDate) }
    var increments by remember(initialScale.title, initialPay, conversionDate) { mutableStateOf(emptyList<FifthCpcIncrementStep>()) }
    var showEventForm by remember { mutableStateOf(false) }
    var targetScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var eventType by remember { mutableStateOf("Promotion") }
    var scaleMenu by remember { mutableStateOf(false) }

    val nextIncrementDate = increments.lastOrNull()?.let { addYear(it.date) }
        ?: firstDni?.takeIf { it > currentDate }
        ?: addYear(currentDate)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("5th CPC Continuation", color = FifthHistoricalPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            "The 5th CPC position produced by the 4th→5th CPC conversion is carried forward automatically. Add 5th CPC increments or a promotion / ACP event; the latest position becomes the input for the next step.",
            color = FifthHistoricalSecondary, fontSize = 12.sp
        )

        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("5th CPC Position — 01 January 1996", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Scale: ${selectedScale.title}", color = FifthHistoricalPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                FifthRow("5th CPC Basic Pay", currentPay)
                FifthRow("Effective Date", currentDate)
                Text("Next Increment / DNI: ${dateText(nextIncrementDate)}", color = FifthHistoricalSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (increments.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("5th CPC Increment Progression", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    increments.forEachIndexed { index, step ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text("Increment ${index + 1}", color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(dateText(step.date), color = FifthHistoricalPrimary, fontSize = 13.sp)
                                Text(money(step.pay), color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            TextButton(onClick = {
                                increments = increments.take(index)
                                if (index == 0) {
                                    currentPay = initialPay
                                    currentDate = conversionDate
                                } else {
                                    val previous = increments[index - 1]
                                    currentPay = previous.pay
                                    currentDate = previous.date
                                }
                            }) { Text("Delete") }
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                val nextPay = nextFifthCpcPay(selectedScale, currentPay) ?: return@Button
                currentPay = nextPay
                currentDate = nextIncrementDate
                increments = increments + FifthCpcIncrementStep(nextPay, nextIncrementDate)
            },
            enabled = nextFifthCpcPay(selectedScale, currentPay) != null && nextIncrementDate < january2006(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
            shape = RoundedCornerShape(12.dp)
        ) { Text("5th CPC Next Increment", fontWeight = FontWeight.Bold) }

        if (!showEventForm) {
            Button(
                onClick = { showEventForm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Add 5th CPC Event", fontWeight = FontWeight.Bold) }
        }

        if (showEventForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("5th CPC Event", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current: ${selectedScale.title} | Basic ${money(currentPay)} | ${dateText(currentDate)}", color = FifthHistoricalSecondary, fontSize = 12.sp)
                    Row {
                        RadioButton(eventType == "Promotion", { eventType = "Promotion" })
                        Text("Promotion", modifier = Modifier.padding(top = 12.dp))
                        Spacer(Modifier.width(12.dp))
                        RadioButton(eventType == "ACP", { eventType = "ACP" })
                        Text("ACP", modifier = Modifier.padding(top = 12.dp))
                    }
                    OutlinedButton(onClick = { eventDate = currentDate }, modifier = Modifier.fillMaxWidth()) {
                        Text(eventDate?.let(::dateText) ?: "Use Current Position Date")
                    }
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(targetScale?.title ?: "Select higher 5th CPC scale", Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FifthToSixthCpcData.scales.filter { it.title != selectedScale.title }.forEach { scale ->
                                DropdownMenuItem(text = { Text(scale.title) }, onClick = {
                                    targetScale = scale
                                    scaleMenu = false
                                })
                            }
                        }
                    }
                    Text("Event fixation uses one increment in the existing 5th CPC scale, followed by placement at the next available stage in the selected higher scale.", color = FifthHistoricalSecondary, fontSize = 12.sp)
                    Button(
                        onClick = {
                            val target = targetScale ?: return@Button
                            val date = eventDate ?: return@Button
                            val incremented = nextFifthCpcPay(selectedScale, currentPay) ?: currentPay
                            val fixed = fifthScaleStages(target.title).firstOrNull { it >= incremented } ?: target.payBandMaximum
                            selectedScale = target
                            currentPay = fixed
                            currentDate = date
                            increments = emptyList()
                            firstDni = addYear(date)
                            targetScale = null
                            eventDate = null
                            showEventForm = false
                        },
                        enabled = targetScale != null && eventDate != null && eventDate!! >= conversionDate && eventDate!! < january2006(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Apply 5th CPC Event", fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { showEventForm = false }) { Text("Cancel") }
                }
            }
        }

        if (currentDate >= january2006() || nextIncrementDate >= january2006()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFEAF5FC)), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("5th CPC journey reaches 01 January 2006", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("The final 5th CPC position is now ready for the 5th→6th CPC conversion.", color = FifthHistoricalSecondary, fontSize = 12.sp)
                    Button(
                        onClick = { onContinueToSixth?.invoke(selectedScale, currentPay) },
                        enabled = onContinueToSixth != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Continue to 6th CPC", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun nextFifthCpcPay(scale: FifthCpcScale, pay: Int): Int? = fifthScaleStages(scale.title).firstOrNull { it > pay }

private fun fifthScaleStages(title: String): List<Int> {
    val notation = title.removePrefix("Rs. ").substringBefore(" (").trim()
    val numbers = Regex("\\d+").findAll(notation).map { it.value.toInt() }.toList()
    if (numbers.size == 1) return numbers
    val result = mutableListOf<Int>()
    var current = numbers.first()
    result += current
    var i = 1
    while (i + 1 < numbers.size) {
        val increment = numbers[i]
        val end = numbers[i + 1]
        if (increment <= 0 || end < current) break
        while (current + increment <= end) {
            current += increment
            result += current
        }
        if (current < end) {
            current = end
            result += current
        }
        i += 2
    }
    return result.distinct().sorted()
}

private fun addYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun january2006(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun dateText(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun money(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)

@Composable
private fun FifthRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = FifthHistoricalSecondary, fontSize = 13.sp)
        Text(money(value), color = FifthHistoricalPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}
