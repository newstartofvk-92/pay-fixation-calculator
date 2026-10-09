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

data class FifthCpcHistoricalIncrementStep(val pay: Int, val date: Long, val sequence: Int = 0)
data class FifthCpcHistoricalEventStep(
    val type: String, val scale: String, val pay: Int, val eventDate: Long,
    val implementationDate: Long, val dniDate: Long,
    val placementMethod: String = "", val implementationOption: String = "", val sequence: Int = 0,
    val omAppliedBeforeEvent: Boolean = false,
    // UI-only identity: deliberately excluded from persisted CPC history snapshots.
    val eventIdentity: String = UUID.randomUUID().toString()
)

private data class FifthCpcTimelineItem(
    val date: Long,
    val kind: String,
    val pay: Int,
    val sequence: Int = 0,
    val scale: String? = null,
    val incrementIndex: Int? = null,
    val eventIndex: Int? = null,
    val eventType: String? = null,
    val eventDate: Long? = null
)

private val FifthHistoricalBlue = Color(0xFF1769AA)
private val FifthHistoricalText = Color(0xFF172B4D)
private val FifthHistoricalSecondary = Color(0xFF5B6B7A)

@Composable
fun FifthCpcHistoricalIncrementSection(
    initialPay: Int,
    revisedScale: String,
    firstIncrementDate: Long?,
    conversionDate: Long,
    initialDate: Long = conversionDate,
    initialDni: Long? = firstIncrementDate,
    onContinueToSeventh: ((String, Int, Int, Long) -> Unit)? = null,
    onHistorySnapshot: ((FifthCpcJourneySnapshot) -> Unit)? = null,
    restoredSnapshot: FifthCpcJourneySnapshot? = null,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
) {
    var incrementSteps by remember(initialPay, revisedScale, conversionDate, firstIncrementDate, restoredSnapshot) {
        mutableStateOf(restoredSnapshot?.increments.orEmpty().map { FifthCpcHistoricalIncrementStep(it.pay, it.dateMillis, it.sequence) })
    }
    var showEventSection by remember { mutableStateOf(false) }
    var continuationState by remember(restoredSnapshot) {
        mutableStateOf(FifthCpcContinuationState(
            historySnapshot = restoredSnapshot?.sixthContinuation,
            restoredSnapshot = restoredSnapshot?.sixthContinuation,
            visible = restoredSnapshot?.sixthContinuation != null
        ))
    }
    var eventHistory by remember(initialPay, revisedScale, conversionDate, firstIncrementDate, restoredSnapshot) {
        mutableStateOf(restoredSnapshot?.events.orEmpty().map { FifthCpcHistoricalEventStep(
            type = normalizeFifthCpcEventType(it.eventType.name), scale = it.targetScaleId, pay = it.resultingPay,
            eventDate = it.eventDateMillis, implementationDate = it.implementationDateMillis,
            dniDate = it.resultingDniMillis, placementMethod = it.placementMethod,
            implementationOption = it.implementationOption, sequence = it.sequence,
            omAppliedBeforeEvent = it.omAppliedBeforeEvent
        ) })
    }
    var editingEventIdentity by remember { mutableStateOf<String?>(null) }

    val initialScale = remember(revisedScale) {
        findFifthScaleForHistoricalJourney(revisedScale)
    }
    val replay = initialScale?.let {
        replayFifthCpcJourney(
            initialPay, it, initialDate, initialDni, incrementSteps, eventHistory,
            sequenceIntegrity
        )
    }

    // Once an event changes the employee's scale, that scale remains the
    // active scale for all subsequent increments. A later increment may
    // replace the current pay/date position, but it must NOT revert the
    // employee to the original pre-event scale.
    val currentScale = replay?.scale ?: initialScale
    val currentPay = replay?.pay ?: initialPay
    val currentDate = replay?.date ?: initialDate
    val stages = remember(currentScale) {
        currentScale?.let { parseFifthCpcScaleStages(it.title) }.orEmpty()
    }
    val currentDni = replay?.dni ?: initialDni
    val editingInput = editingEventIdentity
        ?.let { identity -> eventHistory.indexOfFirst { it.eventIdentity == identity }.takeIf { it >= 0 } }
        ?.let { replay?.eventInputs?.getOrNull(it) }
    val endDate = fifthCpcEndDate()
    val reachesSixthBoundary = currentDate >= endDate || (currentDni != null && currentDni > endDate)
    val omAdjustment = replay?.omAdjustment
    val omEligible = replay?.normalDniForOm != null
    val finalFifthPay = currentPay
    val sixthCpcInputPay = finalFifthPay
    SideEffect {
        onHistorySnapshot?.invoke(FifthCpcJourneySnapshot(
            startingDateMillis = initialDate,
            scaleId = initialScale?.title ?: revisedScale,
            startingBasicPay = initialPay,
            startingDniMillis = initialDni,
            increments = (replay?.increments ?: incrementSteps).mapIndexed { index, step -> CpcIncrementSnapshot(index + 1, step.pay, step.date, step.sequence.takeIf { it > 0 } ?: index + 1) },
            events = (replay?.events ?: eventHistory).mapIndexed { index, event ->
                val kind = when (event.type.lowercase().replace('_', ' ')) {
                    "acp" -> CpcJourneyEventKind.ACP
                    "macp" -> CpcJourneyEventKind.MACP
                    "financial upgradation" -> CpcJourneyEventKind.FINANCIAL_UPGRADATION
                    "scale upgradation", "pay-scale upgradation", "pay scale upgradation" -> CpcJourneyEventKind.PAY_SCALE_UPGRADATION
                    else -> CpcJourneyEventKind.PROMOTION
                }
                FifthCpcEventSnapshot(index + 1, kind, event.eventDate, event.implementationDate,
                    event.scale, event.pay, event.dniDate,
                    if (event.implementationOption.equals("From DNI", true)) CpcFixationBasis.DNI else CpcFixationBasis.EVENT_DATE,
                    event.placementMethod, event.implementationOption, event.sequence.takeIf { it > 0 } ?: index + 1,
                    event.omAppliedBeforeEvent)
            },
            sixthContinuation = continuationState.historySnapshot,
            omAdjustment = omAdjustment
        ))
    }
    val nextDate = currentDni
    val effectiveScale = currentScale
    val nextPay = effectiveScale?.let { calculateNextFifthCpcStage(currentPay, it.title) }
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
                Text("Initial 5th CPC Scale: " + revisedScale, color = FifthHistoricalSecondary, fontSize = 13.sp)
                Text("Active 5th CPC Scale: " + (currentScale?.title ?: "Not available"), color = FifthHistoricalText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Current 5th CPC Basic Pay: " + formatFifthHistoricalCurrency(currentPay), color = FifthHistoricalText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Current effective date: " + formatFifthHistoricalDate(currentDate), color = FifthHistoricalSecondary, fontSize = 12.sp)
                Text("Current DNI / Next Increment: " + (currentDni?.let(::formatFifthHistoricalDate) ?: "Not available"), color = FifthHistoricalSecondary, fontSize = 12.sp)
                if (nextDate != null && nextPay != null && nextDate <= endDate) {
                    Text(
                        "Next increment: " + formatFifthHistoricalDate(nextDate) + " → " + formatFifthHistoricalCurrency(nextPay),
                        color = FifthHistoricalBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (initialPay > 0 || incrementSteps.isNotEmpty() || eventHistory.isNotEmpty() || omAdjustment != null) {
            val timelineItems = buildList {
                add(FifthCpcTimelineItem(initialDate, "starting", initialPay))
                (replay?.increments ?: incrementSteps).forEachIndexed { index, step ->
                    add(
                        FifthCpcTimelineItem(
                            date = step.date,
                            kind = "increment",
                            pay = step.pay,
                            sequence = step.sequence,
                            incrementIndex = index
                        )
                    )
                }
                (replay?.events ?: eventHistory).forEachIndexed { index, event ->
                    add(
                        FifthCpcTimelineItem(
                            date = event.implementationDate,
                            kind = "event",
                            pay = event.pay,
                            sequence = event.sequence,
                            scale = event.scale,
                            eventType = event.type,
                            eventIndex = index,
                            eventDate = event.eventDate
                        )
                    )
                }
                omAdjustment?.let { add(FifthCpcTimelineItem(it.incrementDateMillis, "om", it.adjustedBasicPay)) }
                if (reachesSixthBoundary) add(FifthCpcTimelineItem(currentDate, "final", currentPay))
            }.sortedWith(compareBy<FifthCpcTimelineItem> { it.date }
                .thenBy { when (it.kind) { "starting" -> -1; "om" -> 0; "final" -> 2; else -> 1 } }
                .thenBy { if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) it.sequence else if (it.kind == "event") 0 else 1 }
                .thenBy { it.sequence })

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
                    if (sequenceIntegrity == CpcSequenceIntegrity.INFERRED) {
                        Text(
                            "This saved journey predates shared event sequencing. The original order of same-date events and increments could not be recovered.",
                            color = FifthHistoricalSecondary,
                            fontSize = 12.sp
                        )
                    }

                    timelineItems.forEach { item ->
                        Surface(
                            Modifier.fillMaxWidth(),
                            color = FifthHistoricalBlue.copy(alpha = .06f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp)) {
                                Column(Modifier.weight(1f)) {
                                    if (item.kind == "starting") {
                                        Text("Starting position", color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Effective date: ${formatFifthHistoricalDate(item.date)}", color = FifthHistoricalText, fontSize = 13.sp)
                                        Text("Scale: ${initialScale?.title ?: revisedScale}", color = FifthHistoricalText, fontSize = 13.sp)
                                        Text("5th CPC Basic Pay: ${formatFifthHistoricalCurrency(item.pay)}", color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    } else if (item.kind == "om") {
                                        Text("Special increment under O.M. dated 19 March 2012", color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Effective date: ${formatFifthHistoricalDate(item.date)}", color = FifthHistoricalText, fontSize = 13.sp)
                                        omAdjustment?.let { adjustment ->
                                            Text("Normal DNI: ${formatFifthHistoricalDate(adjustment.normalDniMillis)}", color = FifthHistoricalText, fontSize = 12.sp)
                                            Text("Pay before: ${formatFifthHistoricalCurrency(adjustment.basicPayBefore)} · Special increment: ${formatFifthHistoricalCurrency(adjustment.incrementAmount)}", color = FifthHistoricalText, fontSize = 12.sp)
                                            Text("Next revised-pay increment: ${formatFifthHistoricalDate(adjustment.nextRevisedIncrementDateMillis)}", color = FifthHistoricalText, fontSize = 12.sp)
                                        }
                                        Text("Adjusted 5th CPC Basic Pay: ${formatFifthHistoricalCurrency(item.pay)}", color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    } else if (item.kind == "final") {
                                        Text("Final 5th CPC position / 6th CPC conversion", color = FifthHistoricalSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Effective date: ${formatFifthHistoricalDate(item.date)}", color = FifthHistoricalText, fontSize = 13.sp)
                                        Text("5th CPC Basic Pay: ${formatFifthHistoricalCurrency(item.pay)}", color = FifthHistoricalBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                    } else if (item.kind == "event") {
                                        Text(
                                            item.eventType + " Event",
                                            color = FifthHistoricalSecondary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "Effective date: " + formatFifthHistoricalDate(item.date),
                                            color = FifthHistoricalText,
                                            fontSize = 13.sp
                                        )
                                        item.eventDate?.takeIf { it != item.date }?.let { originalDate ->
                                            Text("Event date: ${formatFifthHistoricalDate(originalDate)}", color = FifthHistoricalSecondary, fontSize = 12.sp)
                                        }
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
                                            continuationState = invalidateFifthCpcContinuation(continuationState)
                                        }
                                    }) {
                                        Text("Delete", fontWeight = FontWeight.Bold)
                                    }
                                } else if (item.kind == "event") {
                                    Column {
                                        TextButton(onClick = {
                                            editingEventIdentity = eventHistory.getOrNull(item.eventIndex ?: -1)?.eventIdentity
                                            showEventSection = true
                                        }) { Text("Edit", fontWeight = FontWeight.Bold) }
                                        TextButton(onClick = {
                                            item.eventIndex?.let { index ->
                                                val deleted = eventHistory.getOrNull(index)
                                                if (deleted != null) {
                                                    eventHistory = deleteFifthCpcHistoricalEvent(eventHistory, deleted.eventIdentity)
                                                    if (editingEventIdentity == deleted.eventIdentity) {
                                                        editingEventIdentity = null
                                                        showEventSection = false
                                                    }
                                                }
                                                continuationState = invalidateFifthCpcContinuation(continuationState)
                                            }
                                        }) { Text("Delete", fontWeight = FontWeight.Bold) }
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
                onClick = { editingEventIdentity = null; showEventSection = true },
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
                        TextButton(onClick = { showEventSection = false; editingEventIdentity = null }) {
                            Text("Collapse", fontWeight = FontWeight.Bold)
                        }
                    }
                    FifthCpcEventSection(
                        currentPay = editingInput?.pay ?: currentPay,
                        currentScale = editingInput?.scale ?: currentScale,
                        currentDate = editingInput?.date ?: currentDate,
                        currentDni = editingInput?.dni ?: currentDni,
                        // Replay has already applied the OM position to currentPay when due.
                        // Passing it again would apply the special increment twice.
                        omAdjustment = null,
                        initialEvent = editingEventIdentity?.let { identity -> eventHistory.firstOrNull { it.eventIdentity == identity } },
                        onEventApplied = { acceptedEvent ->
                            val eventStillExists = editingEventIdentity == null || eventHistory.any {
                                it.eventIdentity == editingEventIdentity
                            }
                            if (eventStillExists) {
                                eventHistory = upsertFifthCpcHistoricalEvent(
                                    existingEvents = eventHistory,
                                    acceptedEvent = acceptedEvent,
                                    editingIdentity = editingEventIdentity,
                                    nextSequence = maxOf(
                                        incrementSteps.maxOfOrNull { it.sequence } ?: 0,
                                        eventHistory.maxOfOrNull { it.sequence } ?: 0
                                    ) + 1
                                )
                                continuationState = invalidateFifthCpcContinuation(continuationState)
                            }
                            showEventSection = false
                            editingEventIdentity = null
                        }
                    )
                }
            }
        }

        Button(
            onClick = {
                val pay = nextPay ?: return@Button
                val date = nextDate ?: return@Button
                incrementSteps = incrementSteps + FifthCpcHistoricalIncrementStep(pay, date, maxOf(incrementSteps.maxOfOrNull { it.sequence } ?: 0, eventHistory.maxOfOrNull { it.sequence } ?: 0) + 1)
                continuationState = invalidateFifthCpcContinuation(continuationState)
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
                        if (omAdjustment == null && omEligible) {
                            Text("The OM increment could not be determined from the selected 5th CPC scale and pay stage. 6th CPC fixation is unavailable until the scale/pay position is corrected.", color = Color(0xFFC62828), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { continuationState = continuationState.copy(visible = true) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FifthHistoricalBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continue to 6th CPC", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (continuationState.visible) {
                    val conversion = if (omEligible && omAdjustment == null) null else runCatching {
                        calculateFifthToSixthCpc(sixthCpcInputPay, mappedScale)
                    }.getOrNull()

                    conversion?.let {
                        FifthToSixthContinuationSection(
                            conversion = it,
                            onContinueToSeventh = onContinueToSeventh,
                            restoredSnapshot = continuationState.restoredSnapshot,
                            sequenceIntegrity = sequenceIntegrity,
                            onHistorySnapshot = { continuationState = continuationState.copy(historySnapshot = it) }
                        )
                    }
                }
            }
        }
    }
}

internal fun parseFifthCpcScaleStages(scale: String): List<Int> {
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

internal fun calculateNextFifthCpcStage(currentPay: Int, scaleTitle: String): Int? {
    // The scale itself is the authoritative source of the annual increment.
    // Do not derive the next pay from a pre-generated stage list.
    val normalizedTitle = scaleTitle
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

internal fun findFifthScaleForHistoricalJourney(revisedScale: String): FifthCpcScale? {
    val normalized = revisedScale.removePrefix("Rs. ").substringBefore(" (").trim()
    return FifthToSixthCpcData.scales.firstOrNull {
        it.title.removePrefix("Rs. ").substringBefore(" (").trim() == normalized
    }
}

private fun fifthCpcEndDate(): Long =
    Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis

internal fun addFifthHistoricalYear(date: Long): Long =
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
