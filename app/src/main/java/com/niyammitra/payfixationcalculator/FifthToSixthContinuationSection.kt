package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val ContinuationBlue = Color(0xFF1769AA)
private val ContinuationText = Color(0xFF172B4D)
private val ContinuationSecondary = Color(0xFF5B6B7A)

data class InlineSixthCpcIncrementStep(val pay: Int, val date: Long, val sequence: Int = 0)

@Composable
fun FifthToSixthContinuationSection(
    conversion: FifthToSixthResult,
    onContinueToSeventh: ((String, Int, Int, Long) -> Unit)? = null,
    onHistorySnapshot: ((SixthCpcJourneySnapshot) -> Unit)? = null,
    restoredSnapshot: SixthCpcJourneySnapshot? = null,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
) {
    var incrementSteps by remember(conversion.revisedBasicPay, conversion.scale.title, restoredSnapshot) {
        mutableStateOf(restoredSnapshot?.increments.orEmpty().map { InlineSixthCpcIncrementStep(it.payInPayBand + it.gradePay, it.dateMillis, it.sequence) })
    }
    var acceptedEventChains by remember(conversion.revisedBasicPay, conversion.scale.title, restoredSnapshot) { mutableStateOf(restoredSnapshot?.eventChains.orEmpty()) }
    var seventhHistorySnapshot by remember(restoredSnapshot) { mutableStateOf(restoredSnapshot?.seventhContinuation) }
    var eventLatestPayBand by remember { mutableStateOf<String?>(null) }
    var eventLatestGradePay by remember { mutableStateOf<Int?>(null) }
    var eventLatestPayInBand by remember { mutableStateOf<Int?>(null) }
    var eventLatestDate by remember { mutableStateOf<Long?>(null) }
    var automaticSeventhPayInBand by remember { mutableStateOf<Int?>(null) }
    var hasSixthCpcEvent by remember { mutableStateOf(false) }
    var hasReceivedSixthPosition by remember { mutableStateOf(false) }
    var gradePayText by remember(conversion.gradePay) { mutableStateOf(conversion.gradePay.toString()) }
    var stateError by remember { mutableStateOf<String?>(null) }
    var previousConversion by remember(restoredSnapshot) { mutableStateOf(conversion) }

    val requestedGradePay = gradePayText.toIntOrNull()
    val selectedBand = SixthToSeventhCpcData.payBands.firstOrNull { it.title.substringBefore(":").trim() == conversion.scale.payBand.substringBefore(":").trim() }
    val gradePayValid = requestedGradePay != null && requestedGradePay in (selectedBand?.gradePays ?: emptyList())
    val editedGradePay = requestedGradePay?.takeIf { gradePayValid } ?: conversion.gradePay
    LaunchedEffect(conversion) {
        if (previousConversion != conversion) {
            seventhHistorySnapshot = null
            automaticSeventhPayInBand = null
            previousConversion = conversion
        }
    }
    fun updateInlineIncrements(updated: List<InlineSixthCpcIncrementStep>) {
        if (incrementSteps == updated) return
        val current = SixthCpcJourneyState(
            SixthCpcJourneyStartingPosition(inlineSixthConversionDate(), conversion.scale.payBand, editedGradePay, conversion.payInPayBand),
            incrementSteps.map { SixthCpcHistoricalIncrement(it.pay - editedGradePay, editedGradePay, it.date, it.sequence) },
            acceptedEventChains,
            sequenceIntegrity
        )
        val next = current.copy(increments = updated.map {
            SixthCpcHistoricalIncrement(it.pay - editedGradePay, editedGradePay, it.date, it.sequence)
        })
        val continuation = SixthCpcContinuationMutationState(current, seventhHistorySnapshot).withSixthCpc(next)
        if (continuation.sixthCpc != current) {
            seventhHistorySnapshot = continuation.seventhCpc
            automaticSeventhPayInBand = null
        }
        incrementSteps = updated
    }
    val latest = incrementSteps.lastOrNull()
    val latestPayInBand = latest?.let { it.pay - editedGradePay } ?: conversion.payInPayBand
    val currentSixthBasicPay = latestPayInBand + editedGradePay
    val reaches2015 = latest?.date == inlineSixthJuly2015Date()
    val continuityPayBand = eventLatestPayBand ?: conversion.scale.payBand
    val continuityGradePay = eventLatestGradePay ?: editedGradePay
    val continuityPayInBand = eventLatestPayInBand ?: automaticSeventhPayInBand ?: latestPayInBand
    val reaches2016 = seventhHistorySnapshot != null || automaticSeventhPayInBand != null || eventLatestDate?.let { it >= inlineSixthJuly2015Date() } == true || reaches2015
    SideEffect {
        onHistorySnapshot?.invoke(SixthCpcJourneySnapshot(
            startingDateMillis = inlineSixthConversionDate(),
            payBandId = conversion.scale.payBand,
            gradePay = editedGradePay,
            startingPayInPayBand = conversion.payInPayBand,
            increments = incrementSteps.mapIndexed { index, step -> SixthCpcIncrementSnapshot(index + 1, step.pay - editedGradePay, editedGradePay, step.date, step.sequence.takeIf { it > 0 } ?: index + 1) },
            eventChains = acceptedEventChains,
            seventhContinuation = seventhHistorySnapshot
        ))
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6th CPC Conversion", color = ContinuationBlue, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text("The next increment has crossed 31 December 2005. The pay has been carried into the 6th CPC on the same screen.", color = ContinuationSecondary, fontSize = 13.sp)
                Text("5th CPC Pay: ${formatInlineCurrency(conversion.existingBasicPay)}", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("5th CPC Scale: ${conversion.scale.title}", color = ContinuationSecondary, fontSize = 13.sp)
                HorizontalDivider()
                Text("6th CPC Fitment", color = ContinuationText, fontWeight = FontWeight.Bold)
                Text("${formatInlineCurrency(conversion.existingBasicPay)} × 1.86 = ${String.format(Locale.US, "%.2f", conversion.multipliedPay)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Rounded pay: ${formatInlineCurrency(conversion.roundedPay)}", color = ContinuationSecondary, fontSize = 13.sp)
                Text("Pay in ${conversion.scale.payBand}: ${formatInlineCurrency(conversion.payInPayBand)}", color = ContinuationSecondary, fontSize = 13.sp)

                OutlinedTextField(
                    value = gradePayText,
                    onValueChange = { value ->
                        val digits = value.filter(Char::isDigit)
                        gradePayText = digits
                        val newGp = digits.toIntOrNull()
                        if (newGp != null && selectedBand != null && newGp in selectedBand.gradePays) {
                            val candidate = SixthCpcJourneyState(
                                SixthCpcJourneyStartingPosition(inlineSixthConversionDate(), conversion.scale.payBand, newGp, conversion.payInPayBand),
                                increments = incrementSteps.map { SixthCpcHistoricalIncrement(0, newGp, it.date, it.sequence) },
                                events = acceptedEventChains,
                                sequenceIntegrity = sequenceIntegrity
                            )
                            val mutation = replaySixthCpcJourneyFromStartingPosition(candidate, candidate.startingPosition)
                            if (mutation.accepted) {
                                val replayed = mutation.replay!!
                                incrementSteps = replayed.increments.map { InlineSixthCpcIncrementStep(it.payInPayBand + it.gradePay, it.date, it.sequence) }
                                acceptedEventChains = replayed.events
                                eventLatestPayBand = null
                                eventLatestGradePay = null
                                eventLatestPayInBand = null
                                eventLatestDate = null
                                automaticSeventhPayInBand = null
                                seventhHistorySnapshot = null
                                stateError = null
                            } else stateError = mutation.error ?: "The 6th CPC journey could not be recalculated."
                        }
                    },
                    label = { Text("Grade Pay") },
                    supportingText = { Text("Edit the Grade Pay if the applicable Grade Pay is different from the mapped value.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (!gradePayValid) Text("Grade Pay must be valid for ${conversion.scale.payBand}.", color = Color(0xFFC62828), fontSize = 12.sp)

                Surface(Modifier.fillMaxWidth(), color = ContinuationBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("6th CPC Revised Basic Pay", color = ContinuationSecondary, fontSize = 13.sp)
                        Text(formatInlineCurrency(currentSixthBasicPay), color = ContinuationBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Pay fixed as on 01 January 2006", color = ContinuationSecondary, fontSize = 12.sp)
                        Text("Pay in Pay Band ${formatInlineCurrency(latestPayInBand)} + Grade Pay ${formatInlineCurrency(editedGradePay)}", color = ContinuationSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
        stateError?.let { Text(it, color = Color(0xFFC62828), fontSize = 12.sp) }

        if (!hasSixthCpcEvent) {
            Button(
                onClick = {
                    val currentPayInBand = latest?.let { it.pay - editedGradePay } ?: conversion.payInPayBand
                    val nextPay = calculateSixthCpcNextIncrement(currentPayInBand, editedGradePay, conversion.scale.payBandMaximum) ?: return@Button
                    val nextDate = latest?.let { addInlineSixthYear(it.date) } ?: inlineSixthFirstIncrementDate()
                    if (nextDate <= inlineSixthJuly2015Date()) {
                        updateInlineIncrements(incrementSteps + InlineSixthCpcIncrementStep(nextPay, nextDate,
                            nextSixthCpcApplicationSequence(incrementSteps.map { SixthCpcHistoricalIncrement(0, editedGradePay, it.date, it.sequence) }, acceptedEventChains)))
                        if (nextDate == inlineSixthJuly2015Date()) automaticSeventhPayInBand = nextPay - editedGradePay
                    }
                },
                enabled = !reaches2015 && gradePayValid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ContinuationBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (reaches2015) "01 July 2015 Reached — 7th CPC Starts Automatically"
                    else "Next Increment",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        SixthCpcEventsSection(
            startingPayInPayBand = conversion.payInPayBand,
            startingGradePay = editedGradePay,
            startingPayBand = conversion.scale.payBand,
            startingPositionDate = inlineSixthConversionDate(),
            latestAllowedEventDate = inlineSixthJuly2015Date(),
            initialEventChains = acceptedEventChains,
            initialIncrements = incrementSteps.map { SixthCpcHistoricalIncrement(0, editedGradePay, it.date, it.sequence) },
            initialSequence = nextSixthCpcApplicationSequence(incrementSteps.map { SixthCpcHistoricalIncrement(0, editedGradePay, it.date, it.sequence) }, acceptedEventChains) - 1,
            sequenceIntegrity = sequenceIntegrity,
            onContinueToSeventh = onContinueToSeventh,
            onIncrementsStateChange = { updated ->
                val mapped = updated.map { InlineSixthCpcIncrementStep(it.payInPayBand + it.gradePay, it.date, it.sequence) }
                updateInlineIncrements(mapped)
            },
            onLatestStateChange = { band, gp, payInBand, date ->
                val changed = hasReceivedSixthPosition &&
                    (eventLatestPayBand != band || eventLatestGradePay != gp || eventLatestPayInBand != payInBand || eventLatestDate != date)
                if (changed) {
                    seventhHistorySnapshot = null
                    automaticSeventhPayInBand = null
                }
                eventLatestPayBand = band
                eventLatestGradePay = gp
                eventLatestPayInBand = payInBand
                eventLatestDate = date
                hasReceivedSixthPosition = true
                if (date >= inlineSixthJuly2015Date()) automaticSeventhPayInBand = payInBand
            },
            onEventsStateChange = { hasSixthCpcEvent = it },
            onAcceptedEventChainsChange = {
                if (acceptedEventChains != it) {
                    acceptedEventChains = it
                    seventhHistorySnapshot = null
                    automaticSeventhPayInBand = null
                    eventLatestPayBand = null
                    eventLatestGradePay = null
                    eventLatestPayInBand = null
                    eventLatestDate = null
                    hasReceivedSixthPosition = false
                }
            }
        )

        if (reaches2016) {
            SeventhCpcContinuitySection(
                payBand = continuityPayBand,
                gradePay = continuityGradePay,
                payInPayBand = continuityPayInBand,
                restoredSnapshot = seventhHistorySnapshot,
                sequenceIntegrity = sequenceIntegrity,
                onHistorySnapshot = { seventhHistorySnapshot = it }
            )
        }
    }
}

private fun inlineSixthFirstIncrementDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun inlineSixthConversionDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun inlineSixthJuly2015Date(): Long = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
private fun addInlineSixthYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun formatInlineDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun formatInlineCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()).format(value)
