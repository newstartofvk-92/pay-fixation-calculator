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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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

@Composable
fun SixthToSeventhCpcScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    var selectedPayBand by remember { mutableStateOf<SixthCpcPayBand?>(null) }
    var selectedGradePay by remember { mutableStateOf<Int?>(null) }
    var payInPayBandText by remember { mutableStateOf("") }
    var payBandMenu by remember { mutableStateOf(false) }
    var gradePayMenu by remember { mutableStateOf(false) }

    // Stores each successive 7th CPC cell reached through the Next Increment button.
    val incrementResults = remember { mutableStateListOf<Int>() }

    val payInPayBand = payInPayBandText.toIntOrNull()
    val result = if (selectedPayBand != null && selectedGradePay != null && payInPayBand != null) {
        calculateSixthToSeventhCpc(payInPayBand, selectedGradePay!!, selectedPayBand!!)
    } else null

    // A change in the conversion inputs starts a fresh increment progression.
    val conversionKey = Triple(selectedPayBand, selectedGradePay, payInPayBand)
    var previousConversionKey by remember { mutableStateOf<Triple<SixthCpcPayBand?, Int?, Int?>?>(null) }
    if (previousConversionKey != conversionKey) {
        incrementResults.clear()
        previousConversionKey = conversionKey
    }

    val currentCell = incrementResults.lastOrNull() ?: result?.revisedBasicPay
    val levelStages = result?.let { PayMatrixData.getPayStages(it.level) }.orEmpty()
    val currentIndex = currentCell?.let { levelStages.indexOf(it) } ?: -1
    val canIncrement = currentIndex >= 0 && currentIndex < levelStages.lastIndex
    val finalCellReached = currentIndex >= 0 && currentIndex == levelStages.lastIndex

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
                                DropdownMenuItem(text = { Text(formatSixSevenCurrency(gp)) }, onClick = { selectedGradePay = gp; gradePayMenu = false })
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

                if (incrementResults.isNotEmpty()) {
                    incrementResults.forEachIndexed { index, pay ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text("Next Increment ${index + 1}", color = SixSevenBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Level ${calculation.level} — Cell ${levelStages.indexOf(pay) + 1}", color = SixSevenTextSecondary, fontSize = 13.sp)
                                Text(formatSixSevenCurrency(pay), color = SixSevenBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
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

                if (canIncrement) {
                    Button(
                        onClick = {
                            val nextIndex = currentIndex + 1
                            if (nextIndex <= levelStages.lastIndex) {
                                incrementResults.add(levelStages[nextIndex])
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Next Increment")
                    }
                } else if (finalCellReached) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFE8F5E9)), shape = RoundedCornerShape(16.dp)) {
                        Text(
                            "Final cell reached",
                            Modifier.padding(16.dp).fillMaxWidth(),
                            color = SixSevenTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
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
private fun ConversionRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = SixSevenTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatSixSevenCurrency(value), color = SixSevenTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatSixSevenCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
