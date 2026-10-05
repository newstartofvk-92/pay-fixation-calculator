package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FourFiveBlue = Color(0xFF1769AA)
private val FourFiveHeaderBlue = Color(0xFF1976B8)
private val FourFiveBackground = Color(0xFFF7FAFC)
private val FourFiveTextPrimary = Color(0xFF172B4D)
private val FourFiveTextSecondary = Color(0xFF5B6B7A)

data class FourthToFifthIncrementStep(val pay: Int, val date: Long, val sequence: Int = 0)
data class FourthStartingDateEditReset(
    val increments: List<FourthToFifthIncrementStep>,
    val events: List<FourthCpcEventSnapshot>,
    val fifthSnapshot: FifthCpcJourneySnapshot?
)
fun resetFourthJourneyAfterStartingDateEdit() = FourthStartingDateEditReset(emptyList(), emptyList(), null)

@Composable
fun FourthToFifthCpcScreen(
    onBack: () -> Unit,
    onContinueToSeventh: ((String, Int, Int, Long, CompleteJourneySnapshot) -> Unit)? = null,
    restoredSnapshot: FourthCpcJourneySnapshot? = null,
    restoredFifthSnapshot: FifthCpcJourneySnapshot? = null,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL,
    onHistory: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val restoredScale = remember(restoredSnapshot?.scaleId) { FourthToFifthCpcData.scales.firstOrNull { it.existingScale == restoredSnapshot?.scaleId } }
    var selectedScale by remember(restoredSnapshot) { mutableStateOf(restoredScale) }
    var basicPayText by remember(restoredSnapshot) { mutableStateOf(restoredSnapshot?.startingBasicPay?.toString() ?: "") }
    var payDateText by remember(restoredSnapshot) { mutableStateOf(restoredSnapshot?.payDateMillis?.let { SimpleDateFormat("ddMMyyyy", Locale.US).format(Date(it)) } ?: "") }
    var nextIncrementDateText by remember(restoredSnapshot) { mutableStateOf(restoredSnapshot?.nextIncrementDateMillis?.let { SimpleDateFormat("ddMMyyyy", Locale.US).format(Date(it)) } ?: "") }
    var scaleMenu by remember { mutableStateOf(false) }
    var incrementSteps by remember(selectedScale, basicPayText, restoredSnapshot) { mutableStateOf(restoredSnapshot?.increments.orEmpty().map { FourthToFifthIncrementStep(it.pay, it.dateMillis, it.sequence) }) }
    var eventFlow by remember(restoredSnapshot) {
        mutableStateOf(FourthCpcEventUiState(acceptedEvents = restoredSnapshot?.events.orEmpty()))
    }
    val acceptedEvents = eventFlow.acceptedEvents
    var fifthHistorySnapshot by remember(restoredFifthSnapshot) { mutableStateOf(restoredFifthSnapshot) }
    var conversionActivated by remember(restoredSnapshot) { mutableStateOf(restoredSnapshot?.conversionActivated ?: false) }
    var showConversionPrompt by remember { mutableStateOf(false) }

    val basicPay = basicPayText.toIntOrNull()
    val payDate = parseFourthFiveDate(formatFourthFiveDateInput(payDateText))
    val validStartingPosition = selectedScale != null && basicPay != null && payDate != null && payDate >= fourthCpcStartDate() && payDate <= fourthFiveConversionDate() && basicPay in selectedScale!!.existingStages
    val formattedNextIncrementDate = formatFourthFiveDateInput(nextIncrementDateText)
    val parsedNextIncrementDate = parseFourthFiveDate(formattedNextIncrementDate)
    val result = if (selectedScale != null && basicPay != null) {
        runCatching { calculateFourthToFifthCpc(basicPay, selectedScale!!) }.getOrNull()
    } else null
    val dateError = nextIncrementDateText.isNotBlank() && parsedNextIncrementDate == null
    val validNextIncrementDate = parsedNextIncrementDate?.takeIf { payDate != null && it > payDate && it <= fourthFiveConversionDate() }
    val replayedTimeline = if (validStartingPosition && selectedScale != null && basicPay != null) {
        recalculateFourthCpcTimeline(basicPay, selectedScale!!, incrementSteps, acceptedEvents, sequenceIntegrity)
    } else null
    val currentPay = replayedTimeline?.pay ?: basicPay
    val currentIncrementDate = replayedTimeline?.lastEffectiveDate
    val currentScale = replayedTimeline?.scale ?: selectedScale
    val conversionPay = currentPay
    val conversionScale = currentScale
    val timelineDate = currentIncrementDate ?: payDate
    val conversionReached = conversionPay != null && conversionScale != null &&
        timelineDate != null && timelineDate <= fourthFiveConversionDate()
    val conversionResult = if (conversionActivated && conversionReached && conversionPay != null && conversionScale != null) {
        runCatching { calculateFourthToFifthCpc(conversionPay, conversionScale) }.getOrNull()
    } else null
    val canAddIncrement = validStartingPosition && currentPay != null &&
        (currentIncrementDate == null || currentIncrementDate < fourthFiveConversionEndDate()) &&
        (currentIncrementDate != null || validNextIncrementDate != null) &&
        currentScale != null && calculateFourthCpcNextIncrement(currentPay, currentScale) != null

    Column(Modifier.fillMaxSize().background(FourFiveBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FourFiveHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("4th CPC → 5th CPC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Pay Revision", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                }
                onHistory?.let { TextButton(onClick = it) { Text("History", color = Color.White, fontWeight = FontWeight.Bold) } }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("4th CPC Pay Details", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Select the applicable 4th CPC scale and enter the basic pay drawn on any date during the 4th CPC period.", color = FourFiveTextSecondary, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedScale?.let { "${it.grade}: ${it.existingScale}" } ?: "Select 4th CPC Scale", Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FourthToFifthCpcData.scales.forEachIndexed { index, scale ->
                                if (index > 0 && FourthToFifthCpcData.scales[index - 1].grade.substringBefore(" (") != scale.grade.substringBefore(" (")) {
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("${scale.grade}: ${scale.existingScale}") },
                                    onClick = {
                                        selectedScale = scale
                                        basicPayText = ""
                                        nextIncrementDateText = ""
                                        incrementSteps = emptyList()
                                        eventFlow = FourthCpcEventUiState()
                                        fifthHistorySnapshot = null
                                        conversionActivated = false
                                        scaleMenu = false
                                    }
                                )
                            }
                        }
                    }
                    selectedScale?.let { Text("Corresponding 5th CPC scale: ${it.revisedScale}", color = FourFiveTextSecondary, fontSize = 12.sp) }
                    OutlinedTextField(
                        value = basicPayText,
                        onValueChange = { newValue ->
                            basicPayText = newValue.filter(Char::isDigit)
                            incrementSteps = emptyList()
                            eventFlow = FourthCpcEventUiState()
                            fifthHistorySnapshot = null
                            conversionActivated = false
                        },
                        label = { Text("Basic Pay") },
                        placeholder = { Text("e.g. 870") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payDateText,
                        onValueChange = { newValue ->
                            val reset = resetFourthJourneyAfterStartingDateEdit()
                            payDateText = newValue.filter(Char::isDigit).take(8)
                            incrementSteps = reset.increments
                            eventFlow = FourthCpcEventUiState(acceptedEvents = reset.events)
                            fifthHistorySnapshot = reset.fifthSnapshot
                            conversionActivated = false
                        },
                        label = { Text("Pay Date (dd/MM/yyyy)") },
                        placeholder = { Text("e.g. 01/07/1988") },
                        supportingText = { Text("Enter a date from 01 January 1986 through 01 January 1996.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = FourthFiveDateVisualTransformation,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedScale != null && basicPay != null && result == null) {
                        Text("Enter a basic pay within the selected 4th CPC scale.", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }
            }

            if (validStartingPosition) {
                result?.let { calculation ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("4th CPC Next Increment / DNI", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Enter the date on which the next increment would have accrued in the existing 4th CPC scale. This date is used to continue the historical 4th CPC pay progression.", color = FourFiveTextSecondary, fontSize = 12.sp)
                            OutlinedTextField(
                                value = nextIncrementDateText,
                                onValueChange = { newValue ->
                                    nextIncrementDateText = newValue.filter(Char::isDigit).take(8)
                                    eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft)
                                },
                                label = { Text("Next Increment / DNI Date") },
                                placeholder = { Text("dd/MM/yyyy") },
                                supportingText = {
                                    Text(if (dateError) "Enter a valid date after the pay date and on or before 01/01/1996."
                                    else "Example: 01/07/1989")
                                },
                                isError = dateError,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = FourthFiveDateVisualTransformation,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (parsedNextIncrementDate != null && payDate != null && parsedNextIncrementDate <= payDate) {
                                Text("The next increment date must be after the entered pay date.", color = Color(0xFFC62828), fontSize = 12.sp)
                            } else if (parsedNextIncrementDate != null && parsedNextIncrementDate > fourthFiveConversionDate()) {
                                Text("The next increment date cannot be after 01 January 1996.", color = Color(0xFFC62828), fontSize = 12.sp)
                            }
                        }
                    }
                }

                result?.let { calculation ->
                    if (incrementSteps.isNotEmpty()) {
                        FourthToFifthIncrementProgressionCard(calculation, incrementSteps) { index ->
                            incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) }
                            eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft)
                            conversionActivated = false
                            fifthHistorySnapshot = null
                        }
                    }
                }

                if (acceptedEvents.isNotEmpty() || incrementSteps.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Current 4th CPC Position", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            currentPay?.let { RowValue("Basic Pay", it) }
                            currentScale?.let { Text("Scale: ${it.grade}: ${it.existingScale}", color = FourFiveTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                            timelineDate?.let { Text("Effective Date: ${formatFourthFiveDate(it)}", color = FourFiveTextSecondary, fontSize = 13.sp) }
                            Text("The next increment is calculated from this chronological pay position.", color = FourFiveTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (!eventFlow.showEventTypes && eventFlow.draftType == null) {
                    Button(
                        onClick = { eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.OpenEventTypes) },
                        enabled = currentPay != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add 4th CPC Event", fontWeight = FontWeight.Bold) }
                }

                if (eventFlow.showEventTypes) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FourthCpcEventType.values().forEach { type ->
                            OutlinedButton(
                                onClick = { eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.SelectType(type)) },
                                modifier = Modifier.weight(1f)
                            ) { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        }
                    }
                    TextButton(onClick = { eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft) }) { Text("Cancel") }
                }

                if (acceptedEvents.isNotEmpty()) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Accepted 4th CPC Events", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            acceptedEvents.forEachIndexed { index, event ->
                                val label = if (event.eventType == CpcJourneyEventKind.ACP) "ACP" else "Promotion"
                                val target = FourthToFifthCpcData.scales.firstOrNull { it.existingScale == event.targetScaleId }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text("Event ${index + 1}: $label", fontWeight = FontWeight.Bold, color = FourFiveTextPrimary)
                                        Text("Date: ${formatFourthFiveDate(event.eventDateMillis)}", color = FourFiveTextSecondary, fontSize = 12.sp)
                                        Text("${target?.grade ?: event.targetScaleId} · Pay: ${formatFourFiveCurrency(event.resultingPay)}", color = FourFiveTextSecondary, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = {
                                        val reduced = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.Delete(index))
                                        val timeline = if (basicPay != null && selectedScale != null) {
                                            recalculateFourthCpcTimeline(basicPay, selectedScale!!, incrementSteps, reduced.acceptedEvents, sequenceIntegrity)
                                        } else null
                                        eventFlow = if (timeline != null) reduced.copy(acceptedEvents = timeline.events) else reduced
                                        if (timeline != null) incrementSteps = timeline.increments
                                        eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft)
                                        val continuation = invalidateFourthCpcContinuation(
                                            FourthCpcContinuationState(conversionActivated, fifthHistorySnapshot)
                                        )
                                        conversionActivated = continuation.conversionActivated
                                        fifthHistorySnapshot = continuation.fifthSnapshot
                                        showConversionPrompt = false
                                    }) { Text("Delete Event") }
                                }
                                if (index < acceptedEvents.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }

                if (eventFlow.draftType != null && currentScale != null && currentPay != null) {
                    FourthCpcEventSection(
                        currentPay = currentPay,
                        currentScale = currentScale,
                        currentDate = currentIncrementDate ?: payDate ?: fourthCpcStartDate(),
                        eventType = eventFlow.draftType!!,
                        onEventApplied = { _, _, newDate ->
                            incrementSteps = incrementSteps.filter { it.date < newDate }
                            nextIncrementDateText = ""
                            val continuation = invalidateFourthCpcContinuation(
                                FourthCpcContinuationState(conversionActivated, fifthHistorySnapshot)
                            )
                            conversionActivated = continuation.conversionActivated
                            fifthHistorySnapshot = continuation.fifthSnapshot
                        },
                        onEventAppliedDetailed = { newScale, newPay, newDate, kind ->
                            val event = FourthCpcEventSnapshot(acceptedEvents.size + 1, newDate, kind, newScale.existingScale, newPay,
                                (incrementSteps.maxOfOrNull { it.sequence } ?: 0).coerceAtLeast(acceptedEvents.maxOfOrNull { it.sequence } ?: 0) + 1)
                            eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.Accept(event))
                        },
                        onCancel = { eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft) }
                    )
                }

                Button(
                    onClick = {
                        val scale = currentScale ?: return@Button
                        val pay = currentPay ?: return@Button
                        val nextPay = calculateFourthCpcNextIncrement(pay, scale) ?: return@Button
                        val nextDate = currentIncrementDate?.let { addFourthFiveYear(it) } ?: validNextIncrementDate ?: return@Button
                        if (nextDate <= fourthFiveConversionEndDate()) {
                            incrementSteps = incrementSteps + FourthToFifthIncrementStep(nextPay, nextDate, (incrementSteps.maxOfOrNull { it.sequence } ?: 0).coerceAtLeast(acceptedEvents.maxOfOrNull { it.sequence } ?: 0) + 1)
                            eventFlow = reduceFourthCpcEventState(eventFlow, FourthCpcEventAction.CancelDraft)
                            conversionActivated = false
                            fifthHistorySnapshot = null
                        } else {
                            showConversionPrompt = true
                        }
                    },
                    enabled = validStartingPosition && canAddIncrement,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("4th CPC Next Increment", fontWeight = FontWeight.Bold) }

                if (incrementSteps.isEmpty() && validNextIncrementDate != null) {
                    Text("The first added increment will be shown on ${formatFourthFiveDate(validNextIncrementDate)}. Subsequent increments advance by one year.", color = FourFiveTextSecondary, fontSize = 12.sp)
                }

                if (conversionReached && !conversionActivated && conversionResult == null) {
                    Button(
                        onClick = { conversionActivated = true },
                        enabled = conversionPay != null && conversionScale != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Convert to 5th CPC", fontWeight = FontWeight.Bold) }
                    Text(
                        "The 4th CPC timeline is ready for conversion effective 01 January 1996. Tap above to calculate the 5th CPC revised basic pay.",
                        color = FourFiveTextSecondary,
                        fontSize = 12.sp
                    )
                }

                if (showConversionPrompt) {
                    AlertDialog(
                        onDismissRequest = { showConversionPrompt = false },
                        title = { Text("4th CPC period completed") },
                        text = { Text("The next 4th CPC increment falls after 01 January 1996. Please convert the current pay position to 5th CPC to continue the pay journey.") },
                        confirmButton = {
                            TextButton(onClick = {
                                showConversionPrompt = false
                                conversionActivated = true
                            }) { Text("Convert to 5th CPC", fontWeight = FontWeight.Bold) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConversionPrompt = false }) { Text("Later") }
                        }
                    )
                }

                conversionResult?.let { calculation ->
                    Text(
                        "Conversion Result",
                        color = FourFiveTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(Color.White),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "Audit Trail",
                                color = FourFiveBlue,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            RowValue("Existing Basic Pay", calculation.existingBasicPay)
                            RowValue("DA @ 148%", calculation.dearnessAllowance)
                            RowValue("1st Interim Relief", calculation.firstInterimRelief)
                            RowValue("2nd Interim Relief", calculation.secondInterimRelief)
                            RowValue("Existing Emoluments", calculation.existingEmoluments)
                            HorizontalDivider(Modifier.padding(vertical = 4.dp))
                            RowValue("40% Fitment Weightage", calculation.fitmentWeightage)
                            RowValue("Fitment Total", calculation.fitmentTotal)
                            Text(
                                "Corresponding 5th CPC Scale",
                                color = FourFiveTextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                calculation.scale.revisedScale,
                                color = FourFiveTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                color = FourFiveBlue.copy(alpha = .06f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(
                                        "5th CPC Revised Basic Pay",
                                        color = FourFiveTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        formatFourFiveCurrency(calculation.revisedBasicPay),
                                        color = FourFiveBlue,
                                        fontSize = 23.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        "Pay on 01 January 1996",
                                        color = FourFiveTextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    val firstFifthIncrementDate =
                        timelineDate?.let { getFifthCpcNextIncrementDate(it) }

                    FifthCpcHistoricalIncrementSection(
                        initialPay = calculation.revisedBasicPay,
                        revisedScale = calculation.scale.revisedScale,
                        firstIncrementDate = firstFifthIncrementDate,
                        conversionDate = fourthFiveConversionDate(),
                        onHistorySnapshot = { fifthHistorySnapshot = it },
                        restoredSnapshot = fifthHistorySnapshot,
                        sequenceIntegrity = sequenceIntegrity,
                        onContinueToSeventh = onContinueToSeventh?.let { continueJourney ->
                            { band, gradePay, payInBand, effectiveDate ->
                                val currentFourth = FourthCpcJourneySnapshot(
                                    scaleId = selectedScale!!.existingScale,
                                    scaleTitle = selectedScale!!.grade,
                                    startingBasicPay = basicPay!!,
                                    payDateMillis = payDate!!,
                                    nextIncrementDateMillis = parsedNextIncrementDate,
                                    increments = incrementSteps.mapIndexed { index, step -> CpcIncrementSnapshot(index + 1, step.pay, step.date, step.sequence.takeIf { it > 0 } ?: index + 1) },
                                    events = acceptedEvents,
                                    conversionActivated = conversionActivated
                                )
                                continueJourney(
                                    band, gradePay, payInBand, effectiveDate,
                                    CompleteJourneySnapshot(
                                        startingCpc = CpcHistoryStage.FOURTH,
                                        startingDateMillis = payDate!!,
                                        fourth = currentFourth,
                                        fifth = fifthHistorySnapshot
                                    )
                                )
                            }
                        }
                    )
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                    Text("Rule 8: the next increment is granted on the date it would have accrued in the existing scale. Case-specific provisos, bunching and other special adjustments require separate verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("This calculator implements the standard Rule 7 replacement-scale calculation. Special pay, NPA, personal pay, bunching, stagnation increments and post-01.01.1996 fixation require case-specific verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
            }
            if (validStartingPosition && selectedScale != null && basicPay != null && payDate != null) {
                SaveCompleteJourneyButton(CompleteJourneySnapshot(
                    startingCpc = CpcHistoryStage.FOURTH,
                    startingDateMillis = payDate,
                    fourth = FourthCpcJourneySnapshot(
                        scaleId = selectedScale!!.existingScale,
                        scaleTitle = selectedScale!!.grade,
                        startingBasicPay = basicPay,
                        payDateMillis = payDate,
                        nextIncrementDateMillis = parsedNextIncrementDate,
                        increments = incrementSteps.mapIndexed { index, step -> CpcIncrementSnapshot(index + 1, step.pay, step.date, step.sequence.takeIf { it > 0 } ?: index + 1) },
                        events = acceptedEvents,
                        conversionActivated = conversionActivated
                    ),
                    fifth = fifthHistorySnapshot
                ), sequenceIntegrity = sequenceIntegrity)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FourthToFifthIncrementProgressionCard(calculation: FourthToFifthResult, steps: List<FourthToFifthIncrementStep>, onDelete: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("4th CPC Increment Progression", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Starting 4th CPC pay: ${formatFourFiveCurrency(calculation.existingBasicPay)}", color = FourFiveTextSecondary, fontSize = 13.sp)
            steps.forEachIndexed { index, step ->
                Surface(Modifier.fillMaxWidth(), color = FourFiveBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Increment ${index + 1}", color = FourFiveTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Date: ${formatFourthFiveDate(step.date)}", color = FourFiveTextPrimary, fontSize = 13.sp)
                            Text("4th CPC Basic Pay: ${formatFourFiveCurrency(step.pay)}", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(onClick = { onDelete(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun fourthCpcStartDate(): Long = Calendar.getInstance().apply { clear(); set(1986, Calendar.JANUARY, 1) }.timeInMillis

private fun fourthFiveConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun fourthFiveConversionEndDate(): Long = fourthFiveConversionDate()
private fun getFifthCpcNextIncrementDate(existingIncrementDate: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = existingIncrementDate
        add(Calendar.YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun addFourthFiveYear(date: Long): Long = Calendar.getInstance().apply {
    timeInMillis = date
    add(Calendar.YEAR, 1)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun parseFourthFiveDate(value: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time }.getOrNull()
private fun formatFourthFiveDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

private fun formatFourthFiveDateInput(digits: String): String = buildString {
    digits.filter(Char::isDigit).take(8).forEachIndexed { index, digit ->
        if (index == 2 || index == 4) append('/')
        append(digit)
    }
}

private object FourthFiveDateVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val transformed = formatFourthFiveDateInput(text.text)
        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    offset <= 8 -> offset + 2
                    else -> transformed.length
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    offset <= 10 -> offset - 2
                    else -> text.text.length
                }.coerceIn(0, text.text.length)
            }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(transformed), offsetMapping)
    }
}

@Composable private fun RowValue(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = FourFiveTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(formatFourFiveCurrency(value), color = FourFiveTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
private fun formatFourFiveCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
