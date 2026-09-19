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

data class FifthCpcHistoricalIncrementStep(val pay: Int, val date: Long)
data class FifthCpcHistoricalEventStep(val type: String, val scale: String, val pay: Int, val date: Long)

private val FifthHistoricalBlue = Color(0xFF1769AA)
private val FifthHistoricalText = Color(0xFF172B4D)
private val FifthHistoricalSecondary = Color(0xFF5B6B7A)

@Composable
fun FifthCpcHistoricalIncrementSection(
    initialPay: Int,
    revisedScale: String,
    firstIncrementDate: Long?,
    conversionDate: Long,
    onContinueToSixth: ((FifthCpcScale, Int) -> Unit)? = null
) {
    var incrementSteps by remember(initialPay, revisedScale, conversionDate, firstIncrementDate) {
        mutableStateOf<List<FifthCpcHistoricalIncrementStep>>(emptyList())
    }
    var eventScale by remember(revisedScale) {
        mutableStateOf(findFifthScaleForHistoricalJourney(revisedScale))
    }
    var eventPay by remember { mutableStateOf<Int?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var showEventSection by remember { mutableStateOf(false) }
    var eventHistory by remember(initialPay, revisedScale, conversionDate, firstIncrementDate) { mutableStateOf<List<FifthCpcHistoricalEventStep>>(emptyList()) }

    val initialScale = remember(revisedScale) {
        findFifthScaleForHistoricalJourney(revisedScale)
    }
    val latest = incrementSteps.lastOrNull()
    val eventIsCurrent = eventDate != null && (latest == null || eventDate!! >= latest.date)
    val currentScale = if (eventIsCurrent) eventScale else initialScale
    val currentPay = when {
        eventIsCurrent && eventPay != null -> eventPay!!
        latest != null -> latest.pay
        else -> initialPay
    }
    val currentDate = when {
        eventIsCurrent && eventDate != null -> eventDate!!
        latest != null -> latest.date
        else -> conversionDate
    }
    val stages = remember(currentScale) {
        currentScale?.let { parseFifthCpcScaleStages(it.title) }.orEmpty()
    }
    val nextDate = addFifthHistoricalYear(currentDate)
    val nextPay = stages.firstOrNull { it > currentPay }
    val endDate = fifthCpcEndDate()
    val canAdd = nextPay != null && nextDate != null && nextDate <= endDate

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color.White),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("5th CPC Next Increment", color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Continue the converted 5th CPC pay from 01 January 1996. Each increment moves to the next stage of the applicable 5th CPC scale.",
                    color = FifthHistoricalSecondary, fontSize = 12.sp
                )
                Text("5th CPC Scale: " + revisedScale, color = FifthHistoricalText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Current 5th CPC Basic Pay: " + formatFifthHistoricalCurrency(currentPay), color = FifthHistoricalText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Current effective date: " + formatFifthHistoricalDate(currentDate), color = FifthHistoricalSecondary, fontSize = 12.sp)
                if (nextDate != null && nextPay != null && nextDate <= endDate) {
                    Text(
                        "Next increment: " + formatFifthHistoricalDate(nextDate) + " → " + formatFifthHistoricalCurrency(nextPay),
                        color = FifthHistoricalBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (incrementSteps.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("5th CPC Increment Progression", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    incrementSteps.forEachIndexed { index, step ->
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = FifthHistoricalBlue.copy(alpha = .06f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Increment " + (index + 1), color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Date: " + formatFifthHistoricalDate(step.date), color = FifthHistoricalText, fontSize = 13.sp)
                                    Text("5th CPC Basic Pay: " + formatFifthHistoricalCurrency(step.pay), color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                }
                                TextButton(onClick = {
                                    incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) }
                                }) { Text("Delete", fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }

        if (eventHistory.isNotEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("5th CPC Event History", color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    eventHistory.forEachIndexed { index, event ->
                        Surface(Modifier.fillMaxWidth(), color = FifthHistoricalBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("Event " + (index + 1) + ": " + event.type, color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("Date: " + formatFifthHistoricalDate(event.date), color = FifthHistoricalText, fontSize = 13.sp)
                                Text("Scale: " + event.scale, color = FifthHistoricalText, fontSize = 13.sp)
                                Text("Fixed Basic Pay: " + formatFifthHistoricalCurrency(event.pay), color = FifthHistoricalBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }

        if (!showEventSection) {
            Button(
                onClick = { showEventSection = true },
                enabled = currentScale != null && currentPay > 0,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add 5th CPC Event", fontWeight = FontWeight.Bold)
            }
        }

        if (showEventSection && currentScale != null) {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Color.White),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "5th CPC Event",
                            color = FifthHistoricalBlue,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        TextButton(onClick = { showEventSection = false }) {
                            Text("Collapse", fontWeight = FontWeight.Bold)
                        }
                    }
                    FifthCpcEventSection(
                        currentPay = currentPay,
                        currentScale = currentScale,
                        currentDate = currentDate,
                        onEventApplied = { appliedEventType, newScale, newPay, newDate ->
                            eventHistory = eventHistory + FifthCpcHistoricalEventStep(
                                type = appliedEventType,
                                scale = newScale.title,
                                pay = newPay,
                                date = newDate
                            )
                            eventScale = newScale
                            eventPay = newPay
                            eventDate = newDate
                            showEventSection = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val pay = nextPay ?: return@Button
                val date = nextDate ?: return@Button
                incrementSteps = incrementSteps + FifthCpcHistoricalIncrementStep(pay, date)
            },
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (nextDate != null && nextDate > endDate) "01 January 2006 Reached" else "5th CPC Next Increment", fontWeight = FontWeight.Bold)
        }

        if (nextDate != null && nextDate > endDate) {
            Text(
                "The 5th CPC increment progression has reached the 01 January 2006 boundary. The next stage is the 5th CPC → 6th CPC fixation.",
                color = FifthHistoricalSecondary, fontSize = 12.sp
            )
        }

        if (onContinueToSixth != null && currentDate >= endDate) {
            val mappedScale = currentScale
            if (mappedScale != null) {
                Button(
                    onClick = { onContinueToSixth(mappedScale, currentPay) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Continue to 6th CPC", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun parseFifthCpcScaleStages(scale: String): List<Int> {
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

private fun findFifthScaleForHistoricalJourney(revisedScale: String): FifthCpcScale? {
    val normalized = revisedScale.removePrefix("Rs. ").substringBefore(" (").trim()
    return FifthToSixthCpcData.scales.firstOrNull {
        it.title.removePrefix("Rs. ").substringBefore(" (").trim() == normalized
    }
}

private fun fifthCpcEndDate(): Long =
    Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis

private fun addFifthHistoricalYear(date: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = date
        add(Calendar.YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun formatFifthHistoricalDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

private fun formatFifthHistoricalCurrency(value: Int): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
