package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val SixSevenBlue = Color(0xFF1769AA)
private val SixSevenHeaderBlue = Color(0xFF1976B8)
private val SixSevenBackground = Color(0xFFF7FAFC)
private val SixSevenTextPrimary = Color(0xFF172B4D)
private val SixSevenTextSecondary = Color(0xFF5B6B7A)

private enum class SeventhCpcNextAction { NEXT_INCREMENT, PROMOTION_MACP }
private enum class PromotionFixationBasis { EVENT_DATE, DNI }

@Composable
fun SixthToSeventhCpcScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var selectedPayBand by remember { mutableStateOf<SixthCpcPayBand?>(null) }
    var selectedGradePay by remember { mutableStateOf<Int?>(null) }
    var payInPayBandText by remember { mutableStateOf("") }
    var payBandMenu by remember { mutableStateOf(false) }
    var gradePayMenu by remember { mutableStateOf(false) }
    var nextAction by remember { mutableStateOf<SeventhCpcNextAction?>(null) }
    var incrementPays by remember(selectedPayBand, selectedGradePay, payInPayBandText) { mutableStateOf<List<Int>>(emptyList()) }

    val payInPayBand = payInPayBandText.toIntOrNull()
    val result = if (selectedPayBand != null && selectedGradePay != null && payInPayBand != null) {
        calculateSixthToSeventhCpc(payInPayBand, selectedGradePay!!, selectedPayBand!!)
    } else null

    Column(Modifier.fillMaxSize().background(SixSevenBackground)) {
        Surface(modifier = Modifier.fillMaxWidth(), color = SixSevenHeaderBlue, shadowElevation = 3.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Back", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("6th CPC → 7th CPC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Pay Conversion", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("6th CPC Pay Details", color = SixSevenBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Enter the Pay in Pay Band and Grade Pay drawn immediately before coming over to the 7th CPC revised pay structure.", color = SixSevenTextSecondary, fontSize = 13.sp)

                    Box {
                        OutlinedButton(onClick = { payBandMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("6th CPC Pay Band", fontSize = 12.sp, color = SixSevenTextSecondary)
                                Text(selectedPayBand?.title ?: "Select Pay Band", color = SixSevenTextPrimary, fontSize = 15.sp)
                            }
                            Text("▼")
                        }
                        DropdownMenu(expanded = payBandMenu, onDismissRequest = { payBandMenu = false }) {
                            SixthToSeventhCpcData.payBands.forEach { band ->
                                DropdownMenuItem(text = { Text(band.title) }, onClick = {
                                    selectedPayBand = band
                                    selectedGradePay = null
                                    payBandMenu = false
                                    nextAction = null
                                })
                            }
                        }
                    }

                    Box {
                        OutlinedButton(onClick = { if (selectedPayBand != null) gradePayMenu = true }, enabled = selectedPayBand != null, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("Grade Pay", fontSize = 12.sp, color = SixSevenTextSecondary)
                                Text(selectedGradePay?.let { formatSixSevenCurrency(it) } ?: "Select Grade Pay", color = SixSevenTextPrimary, fontSize = 15.sp)
                            }
                            Text("▼")
                        }
                        DropdownMenu(expanded = gradePayMenu && selectedPayBand != null, onDismissRequest = { gradePayMenu = false }) {
                            selectedPayBand?.gradePays?.forEach { gp ->
                                DropdownMenuItem(text = { Text(formatSixSevenCurrency(gp)) }, onClick = { selectedGradePay = gp; gradePayMenu = false; nextAction = null })
                            }
                        }
                    }

                    OutlinedTextField(
                        value = payInPayBandText,
                        onValueChange = { if (it.all { ch -> ch.isDigit() }) payInPayBandText = it },
                        label = { Text("Pay in Pay Band") },
                        placeholder = { Text("e.g. 13500") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            result?.let { calculation ->
                Text("Conversion Result", color = SixSevenTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audit Trail", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        ConversionRow("Pay in Pay Band", calculation.payInPayBand)
                        ConversionRow("Grade Pay", calculation.gradePay)
                        ConversionRow("Existing Pay (Pay Band + GP)", calculation.existingPay)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Fitment calculation", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                        Text("${formatSixSevenCurrency(calculation.existingPay)} × ${calculation.fitmentFactor} = ${String.format(Locale.US, "%.2f", calculation.multipliedPay)}", color = SixSevenTextSecondary, fontSize = 14.sp)
                        ConversionRow("Rounded to nearest rupee", calculation.roundedPay)
                        Text("Applicable 7th CPC Level: Level ${calculation.level}", color = SixSevenBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Equal / next higher cell in Level ${calculation.level}", color = SixSevenTextSecondary, fontSize = 13.sp)
                        Surface(Modifier.fillMaxWidth().padding(top = 4.dp), color = SixSevenBlue.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("7th CPC Revised Basic Pay", color = SixSevenTextSecondary, fontSize = 13.sp)
                                Text(formatSixSevenCurrency(calculation.revisedBasicPay), color = SixSevenBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Next Increment", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Date: ${calculation.nextIncrementDate}", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                        calculation.nextIncrementPay?.let { Text("Pay thereon: ${formatSixSevenCurrency(it)}", color = SixSevenTextSecondary, fontSize = 14.sp) }
                        Text("This date is shown for pay fixed as on 01 January 2016 and is subject to the applicable Rule 10 conditions.", color = SixSevenTextSecondary, fontSize = 12.sp)
                    }
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rule Basis", color = SixSevenTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        calculation.ruleBasis.forEachIndexed { index, rule ->
                            Text("${index + 1}. $rule", color = SixSevenTextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                if (nextAction == SeventhCpcNextAction.NEXT_INCREMENT) {
                    NextIncrementProgression(
                        calculation = calculation,
                        incrementPays = incrementPays,
                        onDelete = { index -> incrementPays = incrementPays.toMutableList().also { it.removeAt(index) } }
                    )
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            nextAction = SeventhCpcNextAction.NEXT_INCREMENT
                            if (incrementPays.isEmpty()) {
                                getSixthToSeventhNextCell(calculation.level, incrementPays.lastOrNull() ?: calculation.revisedBasicPay)?.let { nextPay ->
                                    incrementPays = listOf(nextPay)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SixSevenBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Next Increment", fontWeight = FontWeight.Bold) }
                    Button(
                        onClick = { nextAction = SeventhCpcNextAction.PROMOTION_MACP },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = SixSevenBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Promotion / MACP", fontWeight = FontWeight.Bold) }
                }

                if (nextAction == SeventhCpcNextAction.NEXT_INCREMENT) {
                    val currentPay = incrementPays.lastOrNull() ?: calculation.revisedBasicPay
                    val finalReached = getSixthToSeventhNextCell(calculation.level, currentPay) == null
                    if (!finalReached) {
                        Button(
                            onClick = {
                                getSixthToSeventhNextCell(calculation.level, incrementPays.lastOrNull() ?: calculation.revisedBasicPay)?.let { nextPay ->
                                    incrementPays = incrementPays + nextPay
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SixSevenBlue),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Add Next Increment", fontWeight = FontWeight.Bold) }
                    }
                } else if (nextAction == SeventhCpcNextAction.PROMOTION_MACP) {
                    PromotionMacpFromConversion(
                        currentLevel = calculation.level,
                        currentPay = incrementPays.lastOrNull() ?: calculation.revisedBasicPay,
                        onBackToOptions = { nextAction = null },
                        onDelete = { nextAction = null }
                    )
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text(
                    "This is an indicative conversion tool. Verify the result against the applicable CCS (RP) Rules, 2016, Government orders/clarifications and the employee's service/pay records before official use.",
                    Modifier.padding(16.dp), color = SixSevenTextPrimary, fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun NextIncrementProgression(
    calculation: SixthToSeventhResult,
    incrementPays: List<Int>,
    onDelete: (Int) -> Unit
) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Increment Progression — Level ${calculation.level}", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Starting cell: ${formatSixSevenCurrency(calculation.revisedBasicPay)}", color = SixSevenTextSecondary, fontSize = 13.sp)
            incrementPays.forEachIndexed { index, pay ->
                Surface(Modifier.fillMaxWidth(), color = SixSevenBlue.copy(alpha = 0.06f), shape = RoundedCornerShape(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Next Increment ${index + 1}", color = SixSevenTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(formatSixSevenCurrency(pay), color = SixSevenBlue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(onClick = { onDelete(index) }) {
                            Text("Delete", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            val currentPay = incrementPays.lastOrNull() ?: calculation.revisedBasicPay
            val finalReached = getSixthToSeventhNextCell(calculation.level, currentPay) == null
            if (finalReached) {
                Text("Final cell reached", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PromotionMacpFromConversion(
    currentLevel: String,
    currentPay: Int,
    onBackToOptions: () -> Unit,
    onDelete: () -> Unit
) {
    var promotedLevel by remember { mutableStateOf<String?>(null) }
    var promotionDate by remember { mutableStateOf<Long?>(null) }
    var dniDate by remember { mutableStateOf<Long?>(null) }
    var promotedMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var dniMenu by remember { mutableStateOf(false) }
    var basis by remember { mutableStateOf(PromotionFixationBasis.EVENT_DATE) }

    val matrix = PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY)
    val dniOptions = remember(promotionDate) { promotionDate?.let { getPayFixationDniOptions(it) } ?: emptyList() }
    val result = if (promotedLevel != null && promotionDate != null && (basis == PromotionFixationBasis.EVENT_DATE || dniDate != null)) {
        calculatePayFixation(currentLevel, currentPay, promotedLevel!!, promotionDate, dniDate, EmployeeCategory.ORDINARY)
    } else null

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Promotion / MACP Fixation", color = SixSevenTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pay carried forward from latest increment", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text("Present Pay Level: Level $currentLevel", color = SixSevenTextSecondary, fontSize = 13.sp)
                ConversionRow("Latest Basic Pay", currentPay)
                Text("This is the latest pay shown in the increment progression. Promotion / MACP fixation starts from this pay.", color = SixSevenTextSecondary, fontSize = 12.sp)
                Text("Select the Level to which promotion / MACP is granted.", color = SixSevenTextSecondary, fontSize = 13.sp)
                Box {
                    OutlinedButton(onClick = { promotedMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(promotedLevel?.let { "Level $it" } ?: "Select Promoted / Upgraded Pay Level", modifier = Modifier.weight(1f))
                        Text("▼")
                    }
                    DropdownMenu(expanded = promotedMenu, onDismissRequest = { promotedMenu = false }) {
                        matrix.levels.filter { matrix.isHigherLevel(currentLevel, it) }.forEach { level ->
                            DropdownMenuItem(text = { Text("Level $level") }, onClick = { promotedLevel = level; promotedMenu = false })
                        }
                    }
                }

                Text("Date of Promotion / MACP", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(promotionDate?.let { formatSixSevenDate(it) } ?: "Select date", modifier = Modifier.weight(1f))
                }

                Text("Fixation option", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = basis == PromotionFixationBasis.EVENT_DATE, onClick = { basis = PromotionFixationBasis.EVENT_DATE; dniDate = null })
                    Text("From date of event", color = SixSevenTextPrimary, fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = basis == PromotionFixationBasis.DNI, onClick = { basis = PromotionFixationBasis.DNI })
                    Text("From DNI", color = SixSevenTextPrimary, fontSize = 13.sp)
                }

                if (basis == PromotionFixationBasis.DNI) {
                    Text("Date of Next Increment (DNI)", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Box {
                        OutlinedButton(
                            onClick = { if (dniOptions.isNotEmpty()) dniMenu = true },
                            enabled = dniOptions.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(dniDate?.let { formatSixSevenDate(it) } ?: "Select DNI", modifier = Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(expanded = dniMenu, onDismissRequest = { dniMenu = false }) {
                            dniOptions.forEach { option ->
                                DropdownMenuItem(text = { Text(formatSixSevenDate(option)) }, onClick = { dniDate = option; dniMenu = false })
                            }
                        }
                    }
                }
            }
        }

        result?.let { fixation ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(if (basis == PromotionFixationBasis.EVENT_DATE) "Result — From Date of Event" else "Result — From DNI", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    if (basis == PromotionFixationBasis.EVENT_DATE) {
                        Text("Date: From ${formatSixSevenDate(promotionDate!!)}", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                        ConversionRow("Pay after one increment in Level $currentLevel", fixation.option1.payWithIncrement)
                        ConversionRow("Fixed Pay in Level $promotedLevel", fixation.option1.finalFixedPay)
                        fixation.option1.nextDni?.let { Text("Next DNI: ${formatSixSevenDate(it)}", color = SixSevenTextSecondary, fontSize = 13.sp) }
                        fixation.option1.payAfterNextDni?.let { ConversionRow("Pay on next DNI", it) }
                    } else {
                        Text("Fixation from DNI: ${formatSixSevenDate(dniDate!!)}", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                        ConversionRow("Pay until DNI in Level $promotedLevel", fixation.option2.payUntilDni)
                        ConversionRow("Annual increment in Level $currentLevel", fixation.option2.payWithAnnualIncrement)
                        ConversionRow("Promotion / MACP increment", fixation.option2.payWithPromotionIncrement)
                        ConversionRow("Fixed Pay in Level $promotedLevel", fixation.option2.finalFixedPay)
                        fixation.option2.nextDni?.let { Text("Next DNI: ${formatSixSevenDate(it)}", color = SixSevenTextSecondary, fontSize = 13.sp) }
                        fixation.option2.payAfterNextDni?.let { ConversionRow("Pay on next DNI", it) }
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Promotion / MACP Calculation", color = SixSevenTextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        OutlinedButton(onClick = onBackToOptions, modifier = Modifier.fillMaxWidth()) { Text("Back to Options") }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { promotionDate = state.selectedDateMillis; dniDate = null; dniMenu = false; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) }
            }
        ) { DatePicker(state) }
    }
}

@Composable
private fun ConversionRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SixSevenTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatSixSevenCurrency(value), color = SixSevenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun getSixthToSeventhNextCell(level: String, currentPay: Int): Int? =
    if (level == "13") {
        val stages = listOf(123100, 126800, 130600, 134500, 138500, 142700, 147000, 151400, 155900, 160600, 165400, 170400, 175500, 180800, 186200, 191800, 197600, 203500, 209600, 215900)
        val index = stages.indexOf(currentPay)
        if (index >= 0 && index < stages.lastIndex) stages[index + 1] else null
    } else {
        val stages = PayMatrixData.getPayStages(level)
        val index = stages.indexOf(currentPay)
        if (index >= 0 && index < stages.lastIndex) stages[index + 1] else null
    }

private fun formatSixSevenCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)

private fun formatSixSevenDate(value: Long): String = java.text.SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(java.util.Date(value))