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
import kotlin.math.ceil

private val InterimBlue = Color(0xFF1769AA)
private val InterimText = Color(0xFF172B4D)
private val InterimSecondary = Color(0xFF5B6B7A)
private val InterimWarning = Color(0xFFFFF8E1)

enum class SixthCpcInterimEventType { PROMOTION, UPGRADATION }

data class SixthCpcInterimEventResult(
    val eventDate: Long,
    val eventType: SixthCpcInterimEventType,
    val payInPayBandBeforeEvent: Int,
    val existingGradePay: Int,
    val fixationIncrement: Int,
    val fixedPayInPayBand: Int,
    val targetScale: FifthCpcScale,
    val revisedBasicPay: Int,
    val ruleBasis: List<String>
)

private const val INTERIM_START_YEAR = 2006
private const val INTERIM_END_YEAR = 2008

fun sixthCpcInterimStartDate(): Long = Calendar.getInstance().apply {
    clear(); set(INTERIM_START_YEAR, Calendar.JANUARY, 1, 0, 0, 0)
}.timeInMillis

fun sixthCpcInterimEndDate(): Long = Calendar.getInstance().apply {
    clear(); set(INTERIM_END_YEAR, Calendar.AUGUST, 29, 0, 0, 0)
}.timeInMillis

private fun sixthCpcJulyDate(year: Int): Long = Calendar.getInstance().apply {
    clear(); set(year, Calendar.JULY, 1, 0, 0, 0)
}.timeInMillis

private fun applySixthCpcIncrementsUntil(
    startingPayInBand: Int,
    gradePay: Int,
    payBandMaximum: Int,
    eventDate: Long
): Int {
    var payInBand = startingPayInBand
    var year = INTERIM_START_YEAR
    while (year <= INTERIM_END_YEAR) {
        val incrementDate = sixthCpcJulyDate(year)
        if (incrementDate > eventDate || incrementDate == sixthCpcInterimStartDate()) {
            year++
            continue
        }
        val nextBasic = calculateSixthCpcNextIncrement(payInBand, gradePay, payBandMaximum) ?: break
        payInBand = nextBasic - gradePay
        year++
    }
    return payInBand
}

private fun calculateInterimEvent(
    conversion: FifthToSixthResult,
    eventDate: Long,
    eventType: SixthCpcInterimEventType,
    targetScale: FifthCpcScale
): SixthCpcInterimEventResult {
    require(eventDate >= sixthCpcInterimStartDate() && eventDate <= sixthCpcInterimEndDate())

    val payInBandBeforeEvent = applySixthCpcIncrementsUntil(
        conversion.payInPayBand,
        conversion.gradePay,
        conversion.scale.payBandMaximum,
        eventDate
    )

    val incrementBase = payInBandBeforeEvent + conversion.gradePay
    val fixationIncrement = (ceil((incrementBase * 0.03) / 10.0) * 10.0).toInt()
    val incrementedPayInBand = payInBandBeforeEvent + fixationIncrement
    val fixedPayInBand = maxOf(incrementedPayInBand, targetScale.payBandMinimum)
    val revisedBasicPay = fixedPayInBand + targetScale.gradePay

    return SixthCpcInterimEventResult(
        eventDate = eventDate,
        eventType = eventType,
        payInPayBandBeforeEvent = payInBandBeforeEvent,
        existingGradePay = conversion.gradePay,
        fixationIncrement = fixationIncrement,
        fixedPayInPayBand = fixedPayInBand,
        targetScale = targetScale,
        revisedBasicPay = revisedBasicPay,
        ruleBasis = listOf(
            "CCS (Revised Pay) Rules, 2008, Rule 5: where a Government servant was placed in a higher pay scale between 01.01.2006 and the date of notification of the Rules due to promotion/upgradation, the servant could elect to switch over to the revised pay structure from the date of promotion/upgradation.",
            "CCS (Revised Pay) Rules, 2008, Rule 13: on promotion after 01.01.2006, one increment equal to 3% of (pay in Pay Band + existing Grade Pay), rounded to the next multiple of Rs.10, is added to pay in Pay Band; the Grade Pay of the promotional post is then added.",
            "The CCS (Revised Pay) Rules, 2008 were notified on 29.08.2008 and deemed effective from 01.01.2006."
        )
    )
}

@Composable
fun SixthCpcInterimEventSection(
    conversion: FifthToSixthResult,
    onLatestStateChange: ((String, Int, Int, Long) -> Unit)? = null
) {
    var enabled by remember(conversion.revisedBasicPay, conversion.scale.title) { mutableStateOf(false) }
    var eventType by remember { mutableStateOf(SixthCpcInterimEventType.PROMOTION) }
    var dateText by remember { mutableStateOf("15/09/2006") }
    var selectedTargetScale by remember(conversion.scale.title) { mutableStateOf(conversion.scale) }
    var menuExpanded by remember { mutableStateOf(false) }

    val parsedDate = remember(dateText) {
        runCatching {
            SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(dateText)?.time
        }.getOrNull()
    }
    val validDate = parsedDate?.takeIf { it >= sixthCpcInterimStartDate() && it <= sixthCpcInterimEndDate() }
    val result = if (enabled && validDate != null) {
        calculateInterimEvent(conversion, validDate, eventType, selectedTargetScale)
    } else null

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("6th CPC Interim Period: Promotion / Upgradation", color = InterimBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "Use this section for a promotion or upgradation occurring from 01 January 2006 up to 29 August 2008, the notification date of the CCS (Revised Pay) Rules, 2008.",
                color = InterimSecondary, fontSize = 13.sp
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Interim-period event", color = InterimText, fontWeight = FontWeight.Bold)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            if (enabled) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = eventType == SixthCpcInterimEventType.PROMOTION,
                        onClick = { eventType = SixthCpcInterimEventType.PROMOTION },
                        label = { Text("Promotion") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = eventType == SixthCpcInterimEventType.UPGRADATION,
                        onClick = { eventType = SixthCpcInterimEventType.UPGRADATION },
                        label = { Text("Upgradation") },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it.filter { ch -> ch.isDigit() || ch == '/' }.take(10) },
                    label = { Text("Event / Upgradation Date") },
                    placeholder = { Text("dd/MM/yyyy") },
                    supportingText = { Text("Permitted: 01/01/2006 to 29/08/2008") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Box {
                    OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedTargetScale.title, Modifier.weight(1f), color = InterimText)
                        Text("▼")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        FifthToSixthCpcData.scales.forEach { scale ->
                            DropdownMenuItem(
                                text = { Text("${scale.title} — ${scale.payBand}, GP ${formatInterimCurrency(scale.gradePay)}") },
                                onClick = { selectedTargetScale = scale; menuExpanded = false }
                            )
                        }
                    }
                }
                Text("Target 6th CPC structure / Grade Pay", color = InterimSecondary, fontSize = 12.sp)

                result?.let { fixation ->
                    HorizontalDivider()
                    Text("Interim Event Fixation", color = InterimText, fontWeight = FontWeight.Bold)
                    Text("Pay in Pay Band before event: ${formatInterimCurrency(fixation.payInPayBandBeforeEvent)}", color = InterimSecondary, fontSize = 13.sp)
                    Text("Existing Grade Pay: ${formatInterimCurrency(fixation.existingGradePay)}", color = InterimSecondary, fontSize = 13.sp)
                    Text("3% fixation increment: ${formatInterimCurrency(fixation.fixationIncrement)}", color = InterimSecondary, fontSize = 13.sp)
                    Text("Fixed Pay in Pay Band: ${formatInterimCurrency(fixation.fixedPayInPayBand)}", color = InterimSecondary, fontSize = 13.sp)
                    Text("Target Grade Pay: ${formatInterimCurrency(fixation.targetScale.gradePay)}", color = InterimSecondary, fontSize = 13.sp)

                    Surface(Modifier.fillMaxWidth(), color = InterimBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Basic Pay after ${if (eventType == SixthCpcInterimEventType.PROMOTION) "Promotion" else "Upgradation"}", color = InterimSecondary, fontSize = 13.sp)
                            Text(formatInterimCurrency(fixation.revisedBasicPay), color = InterimBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Text("Rule basis", color = InterimText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    fixation.ruleBasis.forEach { Text("• $it", color = InterimSecondary, fontSize = 12.sp) }

                    LaunchedEffect(fixation) {
                        onLatestStateChange?.invoke(
                            fixation.targetScale.payBand,
                            fixation.targetScale.gradePay,
                            fixation.fixedPayInPayBand,
                            fixation.eventDate
                        )
                    }
                }

                Surface(Modifier.fillMaxWidth(), color = InterimWarning, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Important: Rule 5 also permitted an employee to continue in the existing scale until the next/subsequent increment. This section currently models the revised-pay fixation route from the interim event date; cases retaining the old 5th CPC scale require the employee's actual pre-revised pay and the applicable Rule 11/option treatment.",
                        Modifier.padding(14.dp), color = InterimText, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

private fun formatInterimCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
