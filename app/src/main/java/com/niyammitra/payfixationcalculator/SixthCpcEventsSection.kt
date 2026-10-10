package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor

enum class SixthCpcEventKind { PROMOTION, FINANCIAL_UPGRADATION, PAY_SCALE_UPGRADATION }
enum class HistoricalSixthCpcRoute {
    SCALE_6500_10500_FROM_5500_9000,
    SCALE_7450_11500_FROM_6500_10500,
    RULE_13_IN_SIXTH_CPC
}

data class SixthCpcScaleUpgradeResult(
    val eventDate: Long,
    val oldPayInPayBand: Int,
    val oldGradePay: Int,
    val oldPayBand: String,
    val newPayInPayBand: Int,
    val newGradePay: Int,
    val newPayBand: String,
    val revisedBasicPay: Int,
    val nextIncrementDate: Long,
    val historicalRoute: HistoricalSixthCpcRoute? = null,
    val sourcePreRevisedBasicPay: Int? = null,
    val fixationIncrement: Int? = null
)

data class SixthCpcEventIncrement(val payInPayBand: Int, val gradePay: Int, val date: Long, val sequence: Int = 0)

data class SixthCpcEventChain(
    val kind: SixthCpcEventKind,
    val result: SixthCpcEventResult? = null,
    val scaleUpgrade: SixthCpcScaleUpgradeResult? = null,
    val increments: List<SixthCpcEventIncrement> = emptyList(),
    val sequence: Int = 0,
    /** Ephemeral identity for safe in-memory editing; intentionally not persisted. */
    val localId: String = java.util.UUID.randomUUID().toString()
) {
    // localId is UI-session identity, not saved journey data. Keep value equality
    // stable across JSON restore/resave while retaining identity for editor actions.
    override fun equals(other: Any?): Boolean = other is SixthCpcEventChain &&
        kind == other.kind && result == other.result && scaleUpgrade == other.scaleUpgrade &&
        increments == other.increments && sequence == other.sequence

    override fun hashCode(): Int {
        var resultCode = kind.hashCode()
        resultCode = 31 * resultCode + (result?.hashCode() ?: 0)
        resultCode = 31 * resultCode + (scaleUpgrade?.hashCode() ?: 0)
        resultCode = 31 * resultCode + increments.hashCode()
        resultCode = 31 * resultCode + sequence
        return resultCode
    }
}

private val EventBlue = Color(0xFF1769AA)
private val EventPrimary = Color(0xFF172B4D)
private val EventSecondary = Color(0xFF5B6B7A)

private fun july2015Date() = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1) }.timeInMillis

private fun payBandMinimum(title: String): Int = when (title.substringBefore(":")) {
    "PB-1" -> 5200
    "PB-2" -> 9300
    "PB-3" -> 15600
    "PB-4" -> 37400
    else -> 0
}

/** Rule 13: 3% of Basic Pay, discard decimal fraction, then round the increment up to next Rs.10. */
private fun historical6500Minimum(): Int = 12090
private fun historical7450Minimum(): Int = 13860
private fun calculate6500Fitment(existingPayInBand: Int): Int = maxOf(existingPayInBand, historical6500Minimum())
private fun calculate7450Fitment(existingPayInBand: Int): Int = maxOf(existingPayInBand, historical7450Minimum())
private fun calculateRule13Increment(payInPayBand: Int, gradePay: Int): Pair<Int, Int> {
    val basic = payInPayBand + gradePay
    val wholeRupees = floor(basic * 0.03).toInt()
    val increment = (ceil(wholeRupees / 10.0) * 10.0).toInt()
    return (payInPayBand + increment) to increment
}

private fun currentPayInBand(chain: SixthCpcEventChain) = chain.increments.lastOrNull()?.payInPayBand
    ?: chain.result?.newPayInPayBand ?: chain.scaleUpgrade?.newPayInPayBand
private fun currentGradePay(chain: SixthCpcEventChain) = chain.increments.lastOrNull()?.gradePay
    ?: chain.result?.newGradePay ?: chain.scaleUpgrade?.newGradePay
private fun currentPayBand(chain: SixthCpcEventChain) = chain.result?.newPayBand ?: chain.scaleUpgrade?.newPayBand
private fun currentDate(chain: SixthCpcEventChain) = chain.increments.lastOrNull()?.date
    ?: chain.result?.eventDate ?: chain.scaleUpgrade?.eventDate

private fun nextIncrementDate(chain: SixthCpcEventChain) = chain.increments.lastOrNull()?.let {
    Calendar.getInstance().apply { timeInMillis = it.date; add(Calendar.YEAR, 1) }.timeInMillis
} ?: chain.result?.nextIncrementDate ?: chain.scaleUpgrade?.nextIncrementDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixthCpcEventsSection(
    startingPayInPayBand: Int,
    startingGradePay: Int,
    startingPayBand: String,
    startingPositionDate: Long? = null,
    latestAllowedEventDate: Long? = null,
    initialEventChains: List<SixthCpcEventChain> = emptyList(),
    initialIncrements: List<SixthCpcHistoricalIncrement> = emptyList(),
    initialSequence: Int = 0,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL,
    onContinueToSeventh: ((String, Int, Int, Long) -> Unit)? = null,
    onLatestStateChange: ((String, Int, Int, Long) -> Unit)? = null,
    onEventsStateChange: ((Boolean) -> Unit)? = null,
    onJourneyLinesChange: ((List<String>) -> Unit)? = null,
    onIncrementsStateChange: ((List<SixthCpcHistoricalIncrement>) -> Unit)? = null,
    onAcceptedEventChainsChange: ((List<SixthCpcEventChain>) -> Unit)? = null
) {
    var events by remember(startingPayInPayBand, startingGradePay, startingPayBand, startingPositionDate, latestAllowedEventDate, initialEventChains) { mutableStateOf(initialEventChains) }
    var showForm by remember { mutableStateOf(false) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var eventKind by remember { mutableStateOf(SixthCpcEventKind.FINANCIAL_UPGRADATION) }
    var historicalRoute by remember { mutableStateOf(HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000) }
    var targetBand by remember { mutableStateOf<SixthCpcPayBand?>(null) }
    var targetGp by remember { mutableStateOf<Int?>(null) }
    var fifthBasicText by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var bandMenu by remember { mutableStateOf(false) }
    var gpMenu by remember { mutableStateOf(false) }
    var fixationOption by remember { mutableStateOf(SixthCpcFixationOption.FROM_EVENT_DATE) }
    var editingEventId by remember { mutableStateOf<String?>(null) }
    var pendingDependentDeleteId by remember { mutableStateOf<String?>(null) }
    var journeyError by remember { mutableStateOf<String?>(null) }

    val fallbackStartDate = startingPositionDate ?: Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1) }.timeInMillis
    val journeyState = SixthCpcJourneyState(
        SixthCpcJourneyStartingPosition(fallbackStartDate, startingPayBand, startingGradePay, startingPayInPayBand),
        increments = initialIncrements,
        events = events,
        sequenceIntegrity = sequenceIntegrity
    )
    val replay = runCatching { replaySixthCpcJourney(journeyState) }.getOrNull()
    val journeyEvents = replay?.events ?: events
    val latest = journeyEvents.lastOrNull()
    val payInBand = replay?.finalPosition?.payInPayBand ?: latest?.let(::currentPayInBand) ?: startingPayInPayBand
    val gradePay = replay?.finalPosition?.gradePay ?: latest?.let(::currentGradePay) ?: startingGradePay
    val payBand = replay?.finalPosition?.payBand ?: latest?.let(::currentPayBand) ?: startingPayBand
    val basicPay = payInBand + gradePay
    val currentPositionDate = replay?.finalPosition?.dateMillis ?: latest?.let(::currentDate) ?: startingPositionDate
    val editingPosition = editingEventId?.let { sixthCpcPositionBeforeEvent(journeyState, it) }
    val formPayInBand = editingPosition?.payInPayBand ?: payInBand
    val formGradePay = editingPosition?.gradePay ?: gradePay
    val formPayBand = editingPosition?.payBand ?: payBand
    val formPositionDate = editingPosition?.dateMillis ?: currentPositionDate
    val finalTimelineEntry = replay?.timeline?.lastOrNull()
    val nextRequiredDni = editingPosition?.nextDniMillis
        ?: finalTimelineEntry?.dniMillis
        ?: currentPositionDate?.let(::sixthCpcNextAnnualIncrementDate)
    val eventMaximumDate = listOfNotNull(latestAllowedEventDate, nextRequiredDni).minOrNull()
    val eventDateInRange = eventDate?.let { date ->
        (formPositionDate == null || date >= formPositionDate) &&
            (eventMaximumDate == null || date <= eventMaximumDate)
    } == true
    val financialScheme = eventDate?.let(::financialUpgradationForSixthCpcEvent)
    val latestDate = currentPositionDate

    fun applyMutation(result: SixthCpcMutationResult) {
        if (result.accepted) {
            events = result.state.events
            onIncrementsStateChange?.invoke(result.state.increments)
            journeyError = null
        } else journeyError = result.error
    }

    fun openEventEditor(chain: SixthCpcEventChain) {
        editingEventId = chain.localId
        eventDate = chain.result?.eventDate ?: chain.scaleUpgrade?.eventDate
        eventKind = chain.kind
        fixationOption = chain.result?.fixationOption ?: SixthCpcFixationOption.FROM_EVENT_DATE
        historicalRoute = chain.scaleUpgrade?.historicalRoute ?: HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000
        targetGp = chain.result?.newGradePay ?: chain.scaleUpgrade?.newGradePay
        targetBand = chain.scaleUpgrade?.newPayBand?.let { saved -> SixthToSeventhCpcData.payBands.firstOrNull { it.title == saved } }
        showForm = true
    }

    LaunchedEffect(journeyEvents, editingEventId) {
        if (editingEventId != null && journeyEvents.none { it.localId == editingEventId }) {
            editingEventId = null
            showForm = false
            eventDate = null
        }
    }

    LaunchedEffect(latest, payInBand, gradePay, payBand, latestDate, currentPositionDate) {
        val stateDate = latestDate ?: currentPositionDate
        if (stateDate != null) onLatestStateChange?.invoke(payBand, gradePay, payInBand, stateDate)
    }
    LaunchedEffect(journeyEvents.size) {
        onEventsStateChange?.invoke(journeyEvents.isNotEmpty())
    }
    LaunchedEffect(journeyEvents) {
        onAcceptedEventChainsChange?.invoke(journeyEvents)
    }
    LaunchedEffect(replay?.increments) {
        replay?.increments?.let { onIncrementsStateChange?.invoke(it) }
    }
    LaunchedEffect(journeyEvents) {
        onJourneyLinesChange?.invoke(buildList {
            journeyEvents.forEachIndexed { index, chain ->
                val label = when (chain.kind) {
                    SixthCpcEventKind.PROMOTION -> "Promotion"
                    SixthCpcEventKind.FINANCIAL_UPGRADATION -> "Financial Upgradation"
                    SixthCpcEventKind.PAY_SCALE_UPGRADATION -> "Pay Scale Upgradation / Revision"
                }
                add("## 6th CPC Event ${index + 1}: $label")
                chain.result?.let { r ->
                    add("Event date: ${dateText(r.eventDate)}; fixation option: ${r.fixationOption}")
                    r.financialUpgradation?.let { add("Scheme: $it") }
                    add("Old pay: pay in pay band ${money(r.oldPayInPayBand)} + grade pay ${money(r.oldGradePay)}")
                    add("Fixation increment: ${money(r.increment)}; new pay in pay band ${money(r.newPayInPayBand)} + grade pay ${money(r.newGradePay)} = basic pay ${money(r.revisedBasicPay)}")
                    add("Next DNI: ${dateText(r.nextIncrementDate)}")
                }
                chain.scaleUpgrade?.let { r ->
                    add("Event date: ${dateText(r.eventDate)}; route: ${r.historicalRoute ?: "ordinary scale placement"}")
                    r.sourcePreRevisedBasicPay?.let { add("Source 5th CPC basic pay: ${money(it)}") }
                    add("Pay in pay band ${money(r.oldPayInPayBand)} + grade pay ${money(r.oldGradePay)} changed to pay in pay band ${money(r.newPayInPayBand)} + grade pay ${money(r.newGradePay)} = basic pay ${money(r.revisedBasicPay)}")
                    add("Next DNI: ${dateText(r.nextIncrementDate)}")
                }
                chain.increments.forEachIndexed { i, inc -> add("Event-chain increment ${i + 1} — ${dateText(inc.date)}: pay in pay band ${money(inc.payInPayBand)} + grade pay ${money(inc.gradePay)} = ${money(inc.payInPayBand + inc.gradePay)}") }
            }
        })
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("6th CPC Events", color = EventPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Add promotion, financial-upgradation or pay-scale events chronologically. The latest result becomes the input for the next event.", color = EventSecondary, fontSize = 12.sp)

        journeyError?.let { Text(it, color = Color(0xFFC62828), fontSize = 12.sp) }
        if (sequenceIntegrity == CpcSequenceIntegrity.INFERRED) {
            Text("Older saved 6th CPC ordering is reconstructed; same-date event order may not match the original application order.", color = EventSecondary, fontSize = 12.sp)
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("6th CPC Pay Journey", color = EventBlue, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                replay?.timeline.orEmpty().forEachIndexed { rowIndex, row ->
                    Surface(Modifier.fillMaxWidth(), color = if (row.kind == SixthCpcTimelineKind.STARTING_POSITION) Color(0xFFF2F6FA) else EventBlue.copy(alpha = .045f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("${rowIndex + 1}. ${row.description}", color = EventPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${dateText(row.dateMillis)} · ${row.position.payBand}", color = EventSecondary, fontSize = 12.sp)
                            Text("Pay in Pay Band ${money(row.position.payInPayBand)} + Grade Pay ${money(row.position.gradePay)} = Basic Pay ${money(row.position.basicPay)}", color = EventBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            if (row.eventId != null && row.dniMillis != null) Text("Next DNI: ${dateText(row.dniMillis)}", color = EventSecondary, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when (row.kind) {
                                    SixthCpcTimelineKind.ANNUAL_INCREMENT -> TextButton(onClick = {
                                        val mutation = deleteSixthCpcIncrement(journeyState, row.incrementIndex ?: return@TextButton)
                                        applyMutation(mutation)
                                    }) { Text("Delete") }
                                    SixthCpcTimelineKind.EVENT -> journeyEvents.firstOrNull { it.localId == row.eventId }?.let { chain ->
                                        chain.result?.let { result ->
                                            Text("${result.eventType} · ${result.financialUpgradation ?: "Promotion"} · ${if (result.fixationOption == SixthCpcFixationOption.FROM_DNI) "From DNI" else "From event date"}", color = EventSecondary, fontSize = 11.sp)
                                            Text("Previous pay: ${money(result.oldPayInPayBand)} + GP ${money(result.oldGradePay)}; fixation increment(s): ${money(result.increment)}", color = EventSecondary, fontSize = 11.sp)
                                        }
                                        chain.scaleUpgrade?.let { upgrade ->
                                            Text("Route: ${upgrade.historicalRoute ?: "Saved scale-upgrade route unavailable"}", color = EventSecondary, fontSize = 11.sp)
                                            Text("Previous pay: ${money(upgrade.oldPayInPayBand)} + GP ${money(upgrade.oldGradePay)}", color = EventSecondary, fontSize = 11.sp)
                                        }
                                        if (chain.scaleUpgrade?.historicalRoute == null && chain.kind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                                            Text("Editing unavailable: saved route inputs are missing.", color = EventSecondary, fontSize = 11.sp)
                                        } else {
                                            TextButton(onClick = { openEventEditor(chain) }) { Text("Edit") }
                                        }
                                        TextButton(onClick = {
                                            val deletion = deleteSixthCpcEvent(journeyState, chain.localId)
                                            if (deletion.accepted) applyMutation(deletion)
                                            else { journeyError = deletion.error; pendingDependentDeleteId = chain.localId }
                                        }) { Text("Delete") }
                                    }
                                    SixthCpcTimelineKind.EVENT_INCREMENT -> TextButton(onClick = {
                                        val id = row.eventId ?: return@TextButton
                                        val nested = row.nestedIncrementIndex ?: return@TextButton
                                        applyMutation(deleteSixthCpcEventIncrement(journeyState, id, nested))
                                    }) { Text("Delete") }
                                    SixthCpcTimelineKind.STARTING_POSITION -> Unit
                                }
                                if (rowIndex == replay?.timeline?.lastIndex && row.eventId != null &&
                                    row.kind in setOf(SixthCpcTimelineKind.EVENT, SixthCpcTimelineKind.EVENT_INCREMENT) &&
                                    row.dniMillis != null && (latestAllowedEventDate == null || row.dniMillis <= latestAllowedEventDate)) {
                                    TextButton(onClick = {
                                        applyMutation(addNextSixthCpcEventChainIncrement(journeyState, row.eventId, latestAllowedEventDate))
                                    }) { Text("Next Increment") }
                                }
                            }
                        }
                    }
                }
                Text("Final 6th CPC position: ${payBand} · ${money(payInBand)} + ${money(gradePay)} = ${money(basicPay)}", color = EventPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
        }

        Button(onClick = { editingEventId = null; showForm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Add Event", fontWeight = FontWeight.Bold) }
        if (latest != null && latestDate != null && latestDate >= july2015Date() && onContinueToSeventh != null) {
            Button(onClick = { onContinueToSeventh(payBand, gradePay, payInBand, latestDate) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Continue to 7th CPC", fontWeight = FontWeight.Bold) }
        }

        if (showForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (editingEventId == null) "New 6th CPC Event" else "Edit 6th CPC Event", color = EventBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current position: $formPayBand | Pay in Pay Band ${money(formPayInBand)} | GP ${money(formGradePay)} | Basic ${money(formPayInBand + formGradePay)}", color = EventSecondary, fontSize = 12.sp)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let(::dateText) ?: "Select Event Date") }
                    if (eventDate != null && !eventDateInRange) {
                        Text(
                            "Event date must be on or after the current position and no later than its next DNI (${nextRequiredDni?.let(::dateText) ?: "not available"}). Add the due increment before entering a later event.",
                            color = Color(0xFFC62828), fontSize = 12.sp
                        )
                    }

                    EventRadio("Promotion", eventKind == SixthCpcEventKind.PROMOTION) { eventKind = SixthCpcEventKind.PROMOTION; targetGp = null }
                    EventRadio("Financial Upgradation (ACP / MACP)", eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) { eventKind = SixthCpcEventKind.FINANCIAL_UPGRADATION; targetGp = null }
                    EventRadio("Pay Scale Upgradation / Revision", eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) { eventKind = SixthCpcEventKind.PAY_SCALE_UPGRADATION; targetGp = null; targetBand = null }

                    if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION && eventDate != null) {
                        Text(if (financialScheme == SixthCpcFinancialUpgradation.ACP) "ACP — applicable up to 31 August 2008" else "MACP — applicable from 01 September 2008", color = EventBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { bandMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetBand?.title ?: "Select upgraded / revised pay band", Modifier.weight(1f)); Text("▼") }
                            DropdownMenu(expanded = bandMenu, onDismissRequest = { bandMenu = false }) { SixthToSeventhCpcData.payBands.forEach { band -> DropdownMenuItem(text = { Text(band.title) }, onClick = { targetBand = band; targetGp = null; bandMenu = false }) } }
                        }
                        targetBand?.let { band ->
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { gpMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetGp?.let(::money) ?: "Select Grade Pay", Modifier.weight(1f)); Text("▼") }
                                DropdownMenu(expanded = gpMenu, onDismissRequest = { gpMenu = false }) { band.gradePays.forEach { gp -> DropdownMenuItem(text = { Text(money(gp)) }, onClick = { targetGp = gp; gpMenu = false }) } }
                            }
                        }
                    } else {
                        Box(Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { gpMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(targetGp?.let(::money) ?: "Select Grade Pay", Modifier.weight(1f)); Text("▼") }
                            DropdownMenu(expanded = gpMenu, onDismissRequest = { gpMenu = false }) { SixthToSeventhCpcData.payBands.flatMap { it.gradePays }.distinct().filter { it > gradePay }.forEach { gp -> DropdownMenuItem(text = { Text(money(gp)) }, onClick = { targetGp = gp; gpMenu = false }) } }
                        }
                    }

                    if (eventKind != SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                        Text("Fixation option", color = EventPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        EventRadio("From Date of Event", fixationOption == SixthCpcFixationOption.FROM_EVENT_DATE) { fixationOption = SixthCpcFixationOption.FROM_EVENT_DATE }
                        EventRadio("From Date of DNI (1 July)", fixationOption == SixthCpcFixationOption.FROM_DNI) { fixationOption = SixthCpcFixationOption.FROM_DNI }
                    }

                    if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                        Surface(Modifier.fillMaxWidth(), color = EventBlue.copy(alpha = .08f), shape = RoundedCornerShape(14.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Historical / scale-upgradation route", color = EventBlue, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                EventRadio("15.09.2006: ₹5500–9000 → ₹6500–10500", historicalRoute == HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000) { historicalRoute = HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000 }
                                EventRadio("13.11.2009: ₹6500–10500 → ₹7450–11500 / GP ₹4600", historicalRoute == HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500) { historicalRoute = HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500 }
                                EventRadio("Other historical event: 6th CPC Rule 13", historicalRoute == HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC) { historicalRoute = HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC }
                                when (historicalRoute) {
                                    HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000 -> {
                                        Text("For the ₹10,230 Pay-in-Pay-Band case, use the ₹6500–10500 fitment-table minimum of ₹12,090. Do NOT multiply ₹10,230 by 1.86 again.", color = EventSecondary, fontSize = 12.sp)
                                        Text("Expected: ₹10,230 + GP ₹4,200 → ₹12,090 + GP ₹4,200 = ₹16,290.", color = EventPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500 -> {
                                        Text("Use the ₹7450–11500 fitment-table minimum of ₹13,860 in PB-2.", color = EventSecondary, fontSize = 12.sp)
                                        Text("Expected minimum: ₹13,860 + GP ₹4,600 = ₹18,460.", color = EventPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC -> {
                                        val raw = basicPay * .03
                                        val whole = floor(raw).toInt()
                                        val increment = (ceil(whole / 10.0) * 10.0).toInt()
                                        Text("Rule 13: 3% of Basic = ${String.format(Locale.US, "%.1f", raw)} → decimal ignored = ₹$whole → fixation increment = ₹$increment.", color = EventSecondary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    val canSave = when {
                        eventDate == null || !eventDateInRange -> false
                        eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION -> true
                        else -> targetGp != null
                    }

                    Button(onClick = {
                        val date = eventDate ?: return@Button
                        val oldChain = editingEventId?.let { id -> journeyEvents.firstOrNull { it.localId == id } }
                        val sequence = oldChain?.sequence ?: nextSixthCpcApplicationSequence(emptyList(), journeyEvents, initialSequence)
                        val localId = oldChain?.localId ?: java.util.UUID.randomUUID().toString()
                        val updatedChain: SixthCpcEventChain
                        if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                            val oldPb = formPayInBand
                            val oldGp = formGradePay
                            val newPb: Int
                            val newGp: Int
                            val newBand = "PB-2: ₹9,300–34,800"
                            val fixationIncrement: Int?
                            when (historicalRoute) {
                                HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000 -> {
                                    newPb = calculate6500Fitment(oldPb)
                                    newGp = 4200
                                    fixationIncrement = null
                                }
                                HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500 -> {
                                    newPb = calculate7450Fitment(oldPb)
                                    newGp = 4600
                                    fixationIncrement = null
                                }
                                HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC -> {
                                    val calculated = calculateRule13Increment(oldPb, oldGp)
                                    newPb = calculated.first
                                    newGp = targetGp ?: oldGp
                                    fixationIncrement = calculated.second
                                }
                            }
                            updatedChain = SixthCpcEventChain(
                                kind = SixthCpcEventKind.PAY_SCALE_UPGRADATION,
                                scaleUpgrade = SixthCpcScaleUpgradeResult(
                                    date, oldPb, oldGp, formPayBand, newPb, newGp, newBand,
                                    newPb + newGp, nextJuly(date), historicalRoute, oldPb, fixationIncrement
                                ), sequence = sequence, localId = localId
                            )
                        } else {
                            val gp = targetGp ?: return@Button
                            val result = calculateSixthCpcPromotionOrMacp(
                                formPayInBand, formGradePay, gp, date,
                                if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) "Financial Upgradation" else "Promotion",
                                fixationOption,
                                if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) financialScheme else null
                            )
                            updatedChain = SixthCpcEventChain(eventKind, result = result, sequence = sequence, localId = localId)
                        }
                        val mutation = editingEventId?.let { replaceSixthCpcEvent(journeyState, it, updatedChain) }
                            ?: addSixthCpcEvent(journeyState, updatedChain, initialSequence)
                        if (mutation.accepted) {
                            events = mutation.state.events
                            journeyError = null
                            showForm = false
                            editingEventId = null
                            targetBand = null
                            targetGp = null
                            eventDate = null
                            fifthBasicText = ""
                        } else journeyError = mutation.error
                    }, enabled = canSave && eventDateInRange, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Save Event", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }

    pendingDependentDeleteId?.let { eventId ->
        val deletionPlan = planSixthCpcEventAndLaterDeletion(journeyState, eventId)
        val affectedEntries = buildList<Pair<Long, String>> {
            deletionPlan?.removedTopIncrementIndices.orEmpty().forEach { index ->
                journeyState.increments.getOrNull(index)?.let { increment ->
                    add(increment.date to "Annual increment ${index + 1} — ${dateText(increment.date)}")
                }
            }
            deletionPlan?.removedEventIds.orEmpty().forEach { removedId ->
                val eventIndex = journeyState.events.indexOfFirst { it.localId == removedId }
                journeyState.events.getOrNull(eventIndex)?.let { chain ->
                    val date = chain.result?.eventDate ?: chain.scaleUpgrade?.eventDate ?: Long.MAX_VALUE
                    val label = if (removedId == eventId) "Selected event" else "Following event ${eventIndex + 1}"
                    add(date to "$label (${chain.kind.name.lowercase().replace('_', ' ')}) — ${dateText(date)}")
                }
            }
            deletionPlan?.removedNestedIncrements.orEmpty().forEach { ref ->
                val eventIndex = journeyState.events.indexOfFirst { it.localId == ref.eventId }
                val increment = journeyState.events.getOrNull(eventIndex)?.increments?.getOrNull(ref.incrementIndex)
                increment?.let {
                    add(it.date to "Event-chain increment ${eventIndex + 1}.${ref.incrementIndex + 1} — ${dateText(it.date)}")
                }
            }
        }.sortedBy { it.first }.map { it.second }
        AlertDialog(
            onDismissRequest = { pendingDependentDeleteId = null },
            title = { Text("Remove dependent 6th CPC entries?") },
            text = {
                Text(buildString {
                    append("At least one later item cannot be recalculated from saved inputs. Confirmed deletion removes this event and the following entries:")
                    if (affectedEntries.isEmpty()) append("\n• No later items could be identified; the saved sequence is incomplete.")
                    else affectedEntries.forEach { append("\n• "); append(it) }
                    if (sequenceIntegrity == CpcSequenceIntegrity.INFERRED) append("\n\nThis older record has inferred ordering; same-date cross-type order is unknown.")
                })
            },
            confirmButton = { TextButton(onClick = {
                val deletion = confirmDeleteSixthCpcEventAndLater(journeyState, eventId)
                applyMutation(deletion)
                pendingDependentDeleteId = null
            }) { Text("Remove later entries") } },
            dismissButton = { TextButton(onClick = { pendingDependentDeleteId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun EventCard(chain: SixthCpcEventChain, index: Int, onEdit: () -> Unit, onDelete: () -> Unit, onIncrementDeleted: (Int) -> Unit, onNextIncrement: () -> Unit, onAddEvent: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Event ${index + 1}: ${when (chain.kind) { SixthCpcEventKind.PROMOTION -> "Promotion"; SixthCpcEventKind.FINANCIAL_UPGRADATION -> "Financial Upgradation"; SixthCpcEventKind.PAY_SCALE_UPGRADATION -> "Pay Scale Upgradation / Revision" }}", Modifier.weight(1f), color = EventBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            chain.result?.let { r ->
                Text("Date: ${dateText(r.eventDate)}", color = EventPrimary, fontWeight = FontWeight.Bold)
                Text("Fixation: ${when (r.fixationOption) {
                    SixthCpcFixationOption.FROM_EVENT_DATE -> "From Date of Event"
                    SixthCpcFixationOption.FROM_DNI -> "From Date of DNI (1 July)"
                }}", color = EventBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                RowValue("Old Pay in Pay Band", r.oldPayInPayBand); RowValue("Old Grade Pay", r.oldGradePay); RowValue("Fixation Increment(s)", r.increment); RowValue("New Pay in Pay Band", r.newPayInPayBand); RowValue("New Grade Pay", r.newGradePay); RowValue("New Basic Pay", r.revisedBasicPay)
                Text("Pay Band: ${r.newPayBand}", color = EventSecondary, fontSize = 12.sp); Text("Next DNI: ${dateText(r.nextIncrementDate)}", color = EventSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            chain.scaleUpgrade?.let { r ->
                Text("Date: ${dateText(r.eventDate)}", color = EventPrimary, fontWeight = FontWeight.Bold)
                Text(when (r.historicalRoute) { HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000 -> "Historical — 5500–9000 to 6500–10500"; HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500 -> "Historical — 6500–10500 to 7450–11500 / GP 4600"; HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC -> "Historical — 6th CPC Rule 13"; null -> "Ordinary scale placement" }, color = EventBlue, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                r.sourcePreRevisedBasicPay?.let { RowValue("Source 5th CPC basic", it) }
                r.fixationIncrement?.takeIf { it > 0 }?.let { RowValue("Fixation Increment", it) }
                RowValue("Old Pay in Pay Band", r.oldPayInPayBand); RowValue("Old Grade Pay", r.oldGradePay); RowValue("Placed Pay in Pay Band", r.newPayInPayBand); RowValue("New Grade Pay", r.newGradePay); RowValue("Revised Basic Pay", r.revisedBasicPay)
                Text("Pay Band: ${r.newPayBand}", color = EventSecondary, fontSize = 12.sp); Text("Next DNI: ${dateText(r.nextIncrementDate)}", color = EventSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            chain.increments.forEachIndexed { i, inc ->
                Surface(
                    Modifier.fillMaxWidth(),
                    color = EventBlue.copy(alpha = .06f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Increment " + (i + 1),
                                color = EventSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Date: " + dateText(inc.date),
                                color = EventPrimary,
                                fontSize = 13.sp
                            )
                            Text(
                                "Pay in Pay Band: " + money(inc.payInPayBand) +
                                    " + GP " + money(inc.gradePay) +
                                    " = " + money(inc.payInPayBand + inc.gradePay),
                                color = EventBlue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        TextButton(onClick = { onIncrementDeleted(i) }) {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Next Increment remains available after an event. It is the action
            // for continuing the current event chain. Add Another Event is kept
            // separately below it.
            Button(
                onClick = onNextIncrement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EventBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Next Increment", fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onAddEvent,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EventBlue),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Add Another Event", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable private fun EventRadio(label: String, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { RadioButton(selected = selected, onClick = onClick); Text(label, Modifier.padding(top = 12.dp), color = EventPrimary, fontSize = 13.sp) } }
@Composable private fun RowValue(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = EventSecondary, fontSize = 12.sp); Text(money(value), color = EventPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
private fun money(value: Int): String = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(value).let { "₹$it" }
private fun dateText(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun nextJuly(date: Long): Long { val c = Calendar.getInstance().apply { timeInMillis = date }; val year = c.get(Calendar.YEAR); val july = Calendar.getInstance().apply { clear(); set(year, Calendar.JULY, 1) }; return if (date <= july.timeInMillis) july.timeInMillis else Calendar.getInstance().apply { clear(); set(year + 1, Calendar.JULY, 1) }.timeInMillis }
