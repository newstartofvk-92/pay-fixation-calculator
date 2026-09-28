package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

private val CpcConvertBlue = Color(0xFF1769AA)
private val CpcConvertBackground = Color(0xFFF7FAFC)
private val CpcConvertPrimary = Color(0xFF172B4D)
private val CpcConvertSecondary = Color(0xFF5B6B7A)

private enum class StandaloneCpcConversion(val title: String) {
    FOURTH_TO_FIFTH("4th CPC → 5th CPC"),
    FIFTH_TO_SIXTH("5th CPC → 6th CPC"),
    SIXTH_TO_SEVENTH("6th CPC → 7th CPC")
}

@Composable
fun CpcConversionOnlyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var conversion by remember { mutableStateOf<StandaloneCpcConversion?>(null) }
    var conversionMenu by remember { mutableStateOf(false) }
    var fourthScale by remember { mutableStateOf<FourthCpcScale?>(null) }
    var fourthScaleMenu by remember { mutableStateOf(false) }
    var fourthPayText by remember { mutableStateOf("") }
    var fifthScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var fifthScaleMenu by remember { mutableStateOf(false) }
    var fifthPayText by remember { mutableStateOf("") }
    var sixthBand by remember { mutableStateOf<SixthCpcPayBand?>(null) }
    var sixthBandMenu by remember { mutableStateOf(false) }
    var sixthGradePay by remember { mutableStateOf<Int?>(null) }
    var sixthGradePayMenu by remember { mutableStateOf(false) }
    var sixthPayText by remember { mutableStateOf("") }
    var fourthResult by remember { mutableStateOf<FourthToFifthResult?>(null) }
    var fifthResult by remember { mutableStateOf<FifthToSixthResult?>(null) }
    var sixthResult by remember { mutableStateOf<SixthToSeventhResult?>(null) }
    var conversionError by remember { mutableStateOf<String?>(null) }

    fun clearResult() {
        fourthResult = null
        fifthResult = null
        sixthResult = null
        conversionError = null
    }

    val fourthPay = fourthPayText.toIntOrNull()
    val fifthPay = fifthPayText.toIntOrNull()
    val sixthPay = sixthPayText.toIntOrNull()
    val fourthPayValid = fourthScale != null && fourthPay != null && fourthPay in fourthScale!!.existingStages
    val fifthPayValid = fifthPay != null && fifthPay > 0
    val sixthPayValid = sixthPay != null && sixthPay >= 0

    Column(Modifier.fillMaxSize().background(CpcConvertBackground)) {
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1976B8), shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.padding(horizontal = 4.dp))
                Column {
                    Text("CPC Conversion Only", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Standalone pay conversion", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("CPC Conversion", color = CpcConvertPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Choose a conversion and enter the source CPC pay details.", color = CpcConvertSecondary, fontSize = 14.sp)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Conversion", color = CpcConvertBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    OutlinedButton(onClick = { conversionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(conversion?.title ?: "Select CPC Conversion", Modifier.weight(1f), color = CpcConvertPrimary)
                        Text("▼")
                    }
                    DropdownMenu(expanded = conversionMenu, onDismissRequest = { conversionMenu = false }) {
                        StandaloneCpcConversion.values().forEach { option ->
                            DropdownMenuItem(text = { Text(option.title) }, onClick = {
                                conversion = option
                                conversionMenu = false
                                clearResult()
                            })
                        }
                    }

                    when (conversion) {
                        StandaloneCpcConversion.FOURTH_TO_FIFTH -> {
                            OutlinedButton(onClick = { fourthScaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(fourthScale?.let { "${it.grade}: ${it.existingScale}" } ?: "Select 4th CPC Scale", Modifier.weight(1f), color = CpcConvertPrimary)
                                Text("▼")
                            }
                            DropdownMenu(expanded = fourthScaleMenu, onDismissRequest = { fourthScaleMenu = false }) {
                                FourthToFifthCpcData.scales.forEach { scale ->
                                    DropdownMenuItem(text = { Text("${scale.grade}: ${scale.existingScale}") }, onClick = {
                                        fourthScale = scale
                                        fourthScaleMenu = false
                                        clearResult()
                                    })
                                }
                            }
                            OutlinedTextField(
                                value = fourthPayText,
                                onValueChange = { fourthPayText = it.filter(Char::isDigit); clearResult() },
                                label = { Text("Existing 4th CPC Basic Pay") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (fourthPayText.isNotBlank() && fourthPay != null && fourthScale != null && !fourthPayValid) {
                                ValidationText("Enter a pay stage available in the selected 4th CPC scale.")
                            } else if (fourthPayText.isNotBlank() && fourthPay == null) {
                                ValidationText("Enter a valid whole-rupee basic pay.")
                            }
                            Button(
                                enabled = fourthPayValid,
                                onClick = {
                                    clearResult()
                                    try {
                                        fourthResult = calculateFourthToFifthCpc(existingBasicPay = fourthPay!!, scale = fourthScale!!)
                                    } catch (error: IllegalArgumentException) {
                                        conversionError = error.message ?: "The entered pay is not valid for this scale."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CpcConvertBlue)
                            ) { Text("Convert to 5th CPC") }
                        }

                        StandaloneCpcConversion.FIFTH_TO_SIXTH -> {
                            OutlinedButton(onClick = { fifthScaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(fifthScale?.title ?: "Select 5th CPC Scale", Modifier.weight(1f), color = CpcConvertPrimary)
                                Text("▼")
                            }
                            DropdownMenu(expanded = fifthScaleMenu, onDismissRequest = { fifthScaleMenu = false }) {
                                FifthToSixthCpcData.scales.forEach { scale ->
                                    DropdownMenuItem(text = { Text(scale.title) }, onClick = {
                                        fifthScale = scale
                                        fifthScaleMenu = false
                                        clearResult()
                                    })
                                }
                            }
                            OutlinedTextField(
                                value = fifthPayText,
                                onValueChange = { fifthPayText = it.filter(Char::isDigit); clearResult() },
                                label = { Text("Existing 5th CPC Basic Pay") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (fifthPayText.isNotBlank() && !fifthPayValid) ValidationText("Enter a basic pay greater than zero.")
                            Button(
                                enabled = fifthScale != null && fifthPayValid,
                                onClick = {
                                    clearResult()
                                    try {
                                        fifthResult = calculateFifthToSixthCpc(existingBasicPay = fifthPay!!, scale = fifthScale!!)
                                    } catch (error: IllegalArgumentException) {
                                        conversionError = error.message ?: "Enter a positive basic pay."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CpcConvertBlue)
                            ) { Text("Convert to 6th CPC") }
                        }

                        StandaloneCpcConversion.SIXTH_TO_SEVENTH -> {
                            OutlinedButton(onClick = { sixthBandMenu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(sixthBand?.title ?: "Select 6th CPC Pay Band", Modifier.weight(1f), color = CpcConvertPrimary)
                                Text("▼")
                            }
                            DropdownMenu(expanded = sixthBandMenu, onDismissRequest = { sixthBandMenu = false }) {
                                SixthToSeventhCpcData.payBands.forEach { band ->
                                    DropdownMenuItem(text = { Text(band.title) }, onClick = {
                                        sixthBand = band
                                        sixthGradePay = null
                                        sixthBandMenu = false
                                        clearResult()
                                    })
                                }
                            }
                            OutlinedTextField(
                                value = sixthPayText,
                                onValueChange = { sixthPayText = it.filter(Char::isDigit); clearResult() },
                                label = { Text("Pay in Pay Band") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (sixthPayText.isNotBlank() && !sixthPayValid) ValidationText("Enter a valid non-negative pay-in-pay-band amount.")
                            OutlinedButton(
                                onClick = { sixthGradePayMenu = true },
                                enabled = sixthBand != null,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(sixthGradePay?.let(::formatStandaloneCpcMoney) ?: "Select Grade Pay", Modifier.weight(1f), color = CpcConvertPrimary)
                                Text("▼")
                            }
                            DropdownMenu(expanded = sixthGradePayMenu, onDismissRequest = { sixthGradePayMenu = false }) {
                                sixthBand?.gradePays?.forEach { gradePay ->
                                    DropdownMenuItem(text = { Text(formatStandaloneCpcMoney(gradePay)) }, onClick = {
                                        sixthGradePay = gradePay
                                        sixthGradePayMenu = false
                                        clearResult()
                                    })
                                }
                            }
                            Button(
                                enabled = sixthBand != null && sixthGradePay != null && sixthBand!!.gradePays.contains(sixthGradePay!!) && sixthPayValid,
                                onClick = {
                                    clearResult()
                                    sixthResult = calculateSixthToSeventhCpc(
                                        payInPayBand = sixthPay!!,
                                        gradePay = sixthGradePay!!,
                                        payBand = sixthBand!!
                                    )
                                    if (sixthResult == null) conversionError = "These 6th CPC pay details could not be converted. Check the selected pay band and grade pay."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CpcConvertBlue)
                            ) { Text("Convert to 7th CPC") }
                        }

                        null -> Unit
                    }
                }
            }

            conversionError?.let { ValidationText(it) }
            fourthResult?.let { result ->
                ResultCard("4th CPC → 5th CPC") {
                    ResultValue("Existing Basic Pay", result.existingBasicPay)
                    ResultValue("DA", result.dearnessAllowance)
                    ResultValue("1st Interim Relief", result.firstInterimRelief)
                    ResultValue("2nd Interim Relief", result.secondInterimRelief)
                    ResultValue("Existing Emoluments", result.existingEmoluments)
                    ResultValue("Fitment Weightage", result.fitmentWeightage)
                    ResultValue("Fitment Total", result.fitmentTotal)
                    Text("Corresponding 5th CPC Scale: ${result.scale.revisedScale}", color = CpcConvertSecondary, fontSize = 13.sp)
                    ProminentPay("5th CPC Revised Basic Pay", result.revisedBasicPay)
                    Text("Conversion Date: ${result.conversionDate}", color = CpcConvertSecondary, fontSize = 13.sp)
                    RuleBasis(result.ruleBasis)
                }
            }
            fifthResult?.let { result ->
                ResultCard("5th CPC → 6th CPC") {
                    ResultValue("Existing Basic Pay", result.existingBasicPay)
                    Text("Multiplied Pay: ${formatStandaloneCpcDecimal(result.multipliedPay)}", color = CpcConvertPrimary, fontWeight = FontWeight.Bold)
                    ResultValue("Rounded Pay", result.roundedPay)
                    Text("Pay Band: ${result.scale.payBand}", color = CpcConvertSecondary, fontSize = 13.sp)
                    ResultValue("Pay in Pay Band", result.payInPayBand)
                    ResultValue("Grade Pay", result.gradePay)
                    ProminentPay("6th CPC Revised Basic Pay", result.revisedBasicPay)
                    Text("Conversion Date: ${result.conversionDate}", color = CpcConvertSecondary, fontSize = 13.sp)
                    Text("Next Increment Date: ${result.nextIncrementDate}", color = CpcConvertSecondary, fontSize = 13.sp)
                    RuleBasis(result.ruleBasis)
                }
            }
            sixthResult?.let { result ->
                ResultCard("6th CPC → 7th CPC") {
                    ResultValue("Pay in Pay Band", result.payInPayBand)
                    ResultValue("Grade Pay", result.gradePay)
                    ResultValue("Existing Pay", result.existingPay)
                    Text("Fitment Factor: ${result.fitmentFactor}", color = CpcConvertPrimary, fontWeight = FontWeight.Bold)
                    Text("Multiplied Pay: ${formatStandaloneCpcDecimal(result.multipliedPay)}", color = CpcConvertPrimary, fontWeight = FontWeight.Bold)
                    ResultValue("Rounded Pay", result.roundedPay)
                    Text("7th CPC Level: ${result.level}", color = CpcConvertBlue, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    ProminentPay("7th CPC Revised Basic Pay", result.revisedBasicPay)
                    Text("Next Increment Date: ${result.nextIncrementDate}", color = CpcConvertSecondary, fontSize = 13.sp)
                    result.nextIncrementPay?.let { ResultValue("Next Increment Pay", it) }
                    RuleBasis(result.ruleBasis)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun ValidationText(message: String) {
    Text(message, color = Color(0xFFC62828), fontSize = 12.sp)
}

@Composable
private fun ResultCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("CPC Conversion Result", color = CpcConvertBlue, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = CpcConvertSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ResultValue(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CpcConvertSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatStandaloneCpcMoney(value), color = CpcConvertPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
private fun ProminentPay(label: String, value: Int) {
    Surface(Modifier.fillMaxWidth(), color = CpcConvertBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, color = CpcConvertSecondary, fontSize = 13.sp)
            Text(formatStandaloneCpcMoney(value), color = CpcConvertBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun RuleBasis(rules: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Rule Basis", color = CpcConvertPrimary, fontWeight = FontWeight.ExtraBold)
        rules.forEachIndexed { index, rule -> Text("${index + 1}. $rule", color = CpcConvertSecondary, fontSize = 12.sp) }
    }
}

private fun formatStandaloneCpcMoney(value: Int): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)

private fun formatStandaloneCpcDecimal(value: Double): String = String.format(Locale.US, "%.2f", value)
