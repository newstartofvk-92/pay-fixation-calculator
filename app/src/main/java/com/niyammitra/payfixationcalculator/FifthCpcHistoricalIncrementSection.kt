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
data class FifthCpcHistoricalEventStep(val type: String, val scale: String, val pay: Int, val eventDate: Long, val implementationDate: Long)

private data class FifthCpcTimelineItem(
    val date: Long,
    val kind: String,
    val pay: Int,
    val scale: String? = null,
    val incrementIndex: Int? = null,
    val eventType: String? = null
)

private val FifthHistoricalBlue = Color(0xFF1769AA)
private val FifthHistoricalText = Color(0xFF172B4D)
private val FifthHistoricalSecondary = Color(0xFF5B6B7A)

@Composable
fun FifthCpcHistoricalIncrementSection(
    initialPay: Int,
    revisedScale: String,
    firstIncrementDate: Long?,
    conversionDate: Long
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
    var showSixthCpcContinuation by remember { mutableStateOf(false) }
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
    val nextDate = when {
        eventIsCurrent -> addFifthEventIncrementYear(currentDate)
        latest != null -> addFifthHistoricalYear(currentDate)
        else -> firstIncrementDate
    }
    val effectiveScale = if (eventIsCurrent) eventScale else initialScale
    val nextPay = calculateNextFifthCpcStage(currentPay, effectiveScale)
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

        if (incrementSteps.isNotEmpty() || eventHistory.isNotEmpty()) {
            val timelineItems = buildList {
                incrementSteps.forEachIndexed { index, step ->
                    add(
                        FifthCpcTimelineItem(
                            date = step.date,
                            kind = "increment",
                            pay = step.pay,
                            incrementIndex = index
                        )
                    )
                }
                eventHistory.forEach { event ->
                    add(
                        FifthCpcTimelineItem(
                            date = event.implementationDate,
                            kind = "event",
                            pay = event.pay,
                            scale = event.scale,
                            eventType = event.type
                        )
                    )
                }
            }.sortedWith(
                compareBy<FifthCpcTimelineItem> { it.date }
                    .thenBy { if (it.kind == "event") 0 else 1 }
            )

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Color.White),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(
                        "5th CPC Pay Progression",
                        color = FifthHistoricalBlue,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    timelineItems.forEach { item ->
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = FifthHistoricalBlue.copy(alpha = .06f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    if (item.kind == "event") {
                                        Text(
                                            item.eventType + " Event",
                                            color = FifthHistoricalSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Date: " + formatFifthHistoricalDate(item.date),
                                            color = FifthHistoricalText,
                                            fontSize = 13.sp
                                        )
                                        item.scale?.let {
                                            Text(
                                                "Scale: " + it,
                                                color = FifthHistoricalText,
                                                fontSize = 13.sp
                                            )
                                        }
                                        Text(
                                            "Fixed Basic Pay: " + formatFifthHistoricalCurrency(item.pay),
                                            color = FifthHistoricalBlue,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    } else {
                                        Text(
                                            "Increment " + ((item.incrementIndex ?: 0) + 1),
                                            color = FifthHistoricalSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Date: " + formatFifthHistoricalDate(item.date),
                                            color = FifthHistoricalText,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            "5th CPC Basic Pay: " + formatFifthHistoricalCurrency(item.pay),
                                            color = FifthHistoricalBlue,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }

                                if (item.kind == "increment") {
                                    TextButton(onClick = {
                                        item.incrementIndex?.let { index ->
                                            incrementSteps = incrementSteps.toMutableList().also {
                                                if (index in it.indices) it.removeAt(index)
                                            }
                                        }
                                    }) {
                                        Text("Delete", fontWeight = FontWeight.Bold)
                                    }
                                }
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
                        onEventApplied = { appliedEventType, newScale, newPay, appliedEventDate, implementationDate ->
                            eventHistory = eventHistory + FifthCpcHistoricalEventStep(
                                type = appliedEventType,
                                scale = newScale.title,
                                pay = newPay,
                                eventDate = appliedEventDate,
                                implementationDate = implementationDate
                            )
                            eventScale = newScale
                            eventPay = newPay
                            eventDate = implementationDate
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
                "The 5th CPC timeline has reached the 01 January 2006 boundary. The next stage is the 5th CPC → 6th CPC fixation.",
                color = FifthHistoricalSecondary, fontSize = 12.sp
            )
        }

        if (currentDate >= endDate || (nextDate != null && nextDate > endDate)) {
            val mappedScale = currentScale
            if (mappedScale != null) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(Color(0xFFEAF5FC)),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "5th CPC → 6th CPC",
                            color = FifthHistoricalBlue,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "The 5th CPC journey has reached the 01 January 2006 boundary. Continue with the existing 5th CPC → 6th CPC implementation using the final 5th CPC pay position.",
                            color = FifthHistoricalSecondary,
                            fontSize = 12.sp
                        )
                        Button(
                            onClick = { showSixthCpcContinuation = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continue to 6th CPC", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (showSixthCpcContinuation) {
                    val conversion = runCatching {
                        calculateFifthToSixthCpc(currentPay, mappedScale)
                    }.getOrNull()

                    conversion?.let {
                        FifthToSixthContinuationSection(conversion = it)
                    }
                }
            }
        }
    }
}

private fun parseFifthCpcScaleStages(scale: String): List<Int> {
    val body = scale
        .removePrefix("Rs. ")
        .substringBefore(" (")
        .trim()

    val values = Regex("\\d+")
        .findAll(body)
        .map { it.value.toInt() }
        .toList()

    if (values.isEmpty()) return emptyList()
    if (values.size == 1) return listOf(values[0])

    val stages = mutableListOf<Int>()
    var index = 0

    while (index < values.lastIndex) {
        val start = values[index]
        val increment = values[index + 1]

        if (increment <= 0) {
            index += 1
            continue
        }

        val end = if (index + 2 <= values.lastIndex) values[index + 2] else null

        if (end == null || end <= start) {
            stages += start
            index += 2
            continue
        }

        if (stages.isEmpty() || stages.last() != start) {
            stages += start
        }

        var current = start
        while (current + increment <= end) {
            current += increment
            stages += current
        }

        if (current != end) {
            stages += end
        }

        index += 2
    }

    return stages.distinct()
}

private fun calculateNextFifthCpcStage(currentPay: Int, scale: FifthCpcScale?): Int? {
    if (scale == null) return null

    // The scale itself is the authoritative source of the annual increment.
    // Do not derive the next pay from a pre-generated stage list.
    val normalizedTitle = scale.title
        .removePrefix("Rs. ")
        .substringBefore(" (")
        .trim()

    val values = Regex("\\d+")
        .findAll(normalizedTitle)
        .map { it.value.toInt() }
        .toList()

    if (values.size < 3) return null

    // 5th CPC notation: start-increment-end[-increment-end...].
    // For 6500-200-10500 this means every stage advances by exactly 200.
    var index = 0
    while (index + 2 < values.size) {
        val start = values[index]
        val increment = values[index + 1]
        val end = values[index + 2]

        if (increment > 0 && currentPay in start..end) {
            val next = currentPay + increment
            if (next <= end) return next
        }

        index += 2
    }

    return null
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

private fun addFifthEventIncrementYear(date: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = date
        add(Calendar.YEAR, 1)
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun formatFifthHistoricalDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

private fun formatFifthHistoricalCurrency(value: Int): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
