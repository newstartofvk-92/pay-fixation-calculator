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

data class SixthCpcEventIncrement(val payInPayBand: Int, val gradePay: Int, val date: Long)

data class SixthCpcEventChain(
    val kind: SixthCpcEventKind,
    val result: SixthCpcEventResult? = null,
    val scaleUpgrade: SixthCpcScaleUpgradeResult? = null,
    val increments: List<SixthCpcEventIncrement> = emptyList()
)

private val EventBlue = Color(0xFF1769AA)
private val EventPrimary = Color(0xFF172B4D)
private val EventSecondary = Color(0xFF5B6B7A)

private fun january2006Date() = Calendar.getInstance().apply { clear(); set(2006, Calendar.JANUARY, 1) }.timeInMillis
private fun august2008NotificationDate() = Calendar.getInstance().apply { clear(); set(2008, Calendar.AUGUST, 29) }.timeInMillis
private fun july2015Date() = Calendar.getInstance().apply { clear(); set(2015, Calendar.JULY, 1) }.timeInMillis
private fun isInterimSixthCpcEventDate(date: Long) = date in january2006Date()..august2008NotificationDate()

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
    ?: chain.result?.nextIncrementDate ?: chain.scaleUpgrade?.nextIncrementDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SixthCpcEventsSection(
    calculation: FifthToSixthResult,
    startingPayInPayBand: Int = calculation.payInPayBand,
    startingGradePay: Int = calculation.gradePay,
    startingPayBand: String = calculation.scale.payBand,
    onContinueToSeventh: ((String, Int, Int) -> Unit)? = null,
    onLatestStateChange: ((String, Int, Int, Long) -> Unit)? = null
) {
    var events by remember(calculation.revisedBasicPay, startingPayInPayBand, startingGradePay, startingPayBand) { mutableStateOf(emptyList<SixthCpcEventChain>()) }
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

    val latest = events.lastOrNull()
    val payInBand = latest?.let(::currentPayInBand) ?: startingPayInPayBand
    val gradePay = latest?.let(::currentGradePay) ?: startingGradePay
    val payBand = latest?.let(::currentPayBand) ?: startingPayBand
    val basicPay = payInBand + gradePay
    val isInterim = eventDate?.let(::isInterimSixthCpcEventDate) == true
    val financialScheme = eventDate?.let(::financialUpgradationForSixthCpcEvent)
    val latestDate = latest?.let(::currentDate)

    LaunchedEffect(latest, payInBand, gradePay, payBand, latestDate) {
        if (latest != null && latestDate != null) onLatestStateChange?.invoke(payBand, gradePay, payInBand, latestDate)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("6th CPC Events", color = EventPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Text("Add promotion, financial-upgradation or pay-scale events chronologically. The latest result becomes the input for the next event.", color = EventSecondary, fontSize = 12.sp)

        events.forEachIndexed { index, chain ->
            EventCard(chain, index, onDelete = { events = events.take(index) }, onNextIncrement = {
                val pb = currentPayInBand(chain) ?: return@EventCard
                val gp = currentGradePay(chain) ?: return@EventCard
                val next = calculateSixthCpcNextIncrement(pb, gp, bandForGradePay(gp).payBandMaximum) ?: return@EventCard
                val nextPb = next - gp
                val date = currentDate(chain) ?: return@EventCard
                val nextDate = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
                if (nextDate < july2015Date()) events = events.toMutableList().also { it[index] = chain.copy(increments = chain.increments + SixthCpcEventIncrement(nextPb, gp, nextDate)) }
            }, onAddEvent = { showForm = true })
        }

        if (events.isEmpty()) Button(onClick = { showForm = true }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Add Event", fontWeight = FontWeight.Bold) }
        if (latest != null && latestDate != null && latestDate >= july2015Date() && onContinueToSeventh != null) {
            Button(onClick = { onContinueToSeventh(payBand, gradePay, payInBand) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Continue to 7th CPC", fontWeight = FontWeight.Bold) }
        }

        if (showForm) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("New 6th CPC Event", color = EventBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Current position: $payBand | Pay in Pay Band ${money(payInBand)} | GP ${money(gradePay)} | Basic ${money(basicPay)}", color = EventSecondary, fontSize = 12.sp)
                    OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) { Text(eventDate?.let(::dateText) ?: "Select Event Date") }

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

                    if (!isInterim && eventKind != SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
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
                        eventDate == null -> false
                        eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION -> true
                        else -> targetGp != null
                    }

                    Button(onClick = {
                        val date = eventDate ?: return@Button
                        if (eventKind == SixthCpcEventKind.PAY_SCALE_UPGRADATION) {
                            val oldPb = payInBand
                            val oldGp = gradePay
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
                            events = events + SixthCpcEventChain(
                                kind = SixthCpcEventKind.PAY_SCALE_UPGRADATION,
                                scaleUpgrade = SixthCpcScaleUpgradeResult(
                                    date, oldPb, oldGp, payBand, newPb, newGp, newBand,
                                    newPb + newGp, nextJuly(date), historicalRoute, oldPb, fixationIncrement
                                )
                            )
                        } else {
                            val gp = targetGp ?: return@Button
                            val result = calculateSixthCpcPromotionOrMacp(
                                payInBand, gradePay, gp, date,
                                if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) "Financial Upgradation" else "Promotion",
                                fixationOption,
                                if (eventKind == SixthCpcEventKind.FINANCIAL_UPGRADATION) financialScheme else null
                            )
                            events = events + SixthCpcEventChain(eventKind, result = result)
                        }
                        showForm = false
                        targetBand = null
                        targetGp = null
                        eventDate = null
                        fifthBasicText = ""
                    }, enabled = canSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(12.dp)) { Text("Save Event", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = eventDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { eventDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm") } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state) }
    }
}

@Composable
private fun EventCard(chain: SixthCpcEventChain, index: Int, onDelete: () -> Unit, onNextIncrement: () -> Unit, onAddEvent: () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Event ${index + 1}: ${when (chain.kind) { SixthCpcEventKind.PROMOTION -> "Promotion"; SixthCpcEventKind.FINANCIAL_UPGRADATION -> "Financial Upgradation"; SixthCpcEventKind.PAY_SCALE_UPGRADATION -> "Pay Scale Upgradation / Revision" }}", Modifier.weight(1f), color = EventBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                TextButton(onClick = onDelete) { Text("Delete") }
            }
            chain.result?.let { r ->
                Text("Date: ${dateText(r.eventDate)}", color = EventPrimary, fontWeight = FontWeight.Bold)
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
                Text(
                    "Increment " + (i + 1) + " — " + dateText(inc.date),
                    color = EventPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                RowValue("Pay in Pay Band", inc.payInPayBand)
                RowValue("Grade Pay", inc.gradePay)
                RowValue("Basic Pay", inc.payInPayBand + inc.gradePay)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onNextIncrement, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(10.dp)) { Text("Next Increment") }
                Button(onClick = onAddEvent, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EventBlue), shape = RoundedCornerShape(10.dp)) { Text("Add Another Event") }
            }
        }
    }
}

@Composable private fun EventRadio(label: String, selected: Boolean, onClick: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) { RadioButton(selected = selected, onClick = onClick); Text(label, Modifier.padding(top = 12.dp), color = EventPrimary, fontSize = 13.sp) } }
@Composable private fun RowValue(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = EventSecondary, fontSize = 12.sp); Text(money(value), color = EventPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp) } }
private fun money(value: Int): String = NumberFormat.getIntegerInstance(Locale("en", "IN")).format(value).let { "₹$it" }
private fun dateText(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
private fun nextJuly(date: Long): Long { val c = Calendar.getInstance().apply { timeInMillis = date }; val year = c.get(Calendar.YEAR); val july = Calendar.getInstance().apply { clear(); set(year, Calendar.JULY, 1) }; return if (date <= july.timeInMillis) july.timeInMillis else Calendar.getInstance().apply { clear(); set(year + 1, Calendar.JULY, 1) }.timeInMillis }
