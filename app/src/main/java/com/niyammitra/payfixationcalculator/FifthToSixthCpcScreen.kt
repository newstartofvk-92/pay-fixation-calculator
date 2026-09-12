package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val FiveSixBlue = Color(0xFF1769AA)
private val FiveSixHeaderBlue = Color(0xFF1976B8)
private val FiveSixBackground = Color(0xFFF7FAFC)
private val FiveSixTextPrimary = Color(0xFF172B4D)
private val FiveSixTextSecondary = Color(0xFF5B6B7A)

data class SixthCpcIncrementStep(val pay: Int, val date: Long)

@Composable
fun FifthToSixthCpcScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedScale by remember { mutableStateOf<FifthCpcScale?>(null) }
    var scaleMenu by remember { mutableStateOf(false) }
    var basicPayText by remember { mutableStateOf("") }
    var incrementSteps by remember(selectedScale, basicPayText) { mutableStateOf<List<SixthCpcIncrementStep>>(emptyList()) }

    val basicPay = basicPayText.toIntOrNull()
    val result = if (selectedScale != null && basicPay != null && basicPay > 0) calculateFifthToSixthCpc(basicPay, selectedScale!!) else null

    Column(Modifier.fillMaxSize().background(FiveSixBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FiveSixHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Back", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(16.dp))
                Column { Text("5th CPC → 6th CPC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Pay Conversion", color = Color.White.copy(alpha = .88f), fontSize = 13.sp) }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("5th CPC Pay Details", color = FiveSixBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Select the applicable pre-revised 5th CPC scale and enter the basic pay drawn as on 01 January 2006.", color = FiveSixTextSecondary, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedScale?.title ?: "Select 5th CPC Pay Scale", Modifier.weight(1f), color = FiveSixTextPrimary, fontSize = 15.sp)
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FifthToSixthCpcData.scales.forEach { scale -> DropdownMenuItem(text = { Text(scale.title) }, onClick = { selectedScale = scale; scaleMenu = false }) }
                        }
                    }
                    selectedScale?.let { scale ->
                        Text("6th CPC: ${scale.payBand}  |  Grade Pay: ${formatFiveSixCurrency(scale.gradePay)}", color = FiveSixTextSecondary, fontSize = 12.sp)
                    }
                    OutlinedTextField(value = basicPayText, onValueChange = { if (it.all(Char::isDigit)) basicPayText = it }, label = { Text("5th CPC Basic Pay on 01 January 2006") }, placeholder = { Text("e.g. 4800") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            }

            result?.let { calculation ->
                Text("Conversion Result", color = FiveSixTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audit Trail", color = FiveSixBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        FiveSixRow("5th CPC Basic Pay", calculation.existingBasicPay)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Text("Fitment calculation", color = FiveSixTextPrimary, fontWeight = FontWeight.Bold)
                        Text("${formatFiveSixCurrency(calculation.existingBasicPay)} × 1.86 = ${String.format(Locale.US, "%.2f", calculation.multipliedPay)}", color = FiveSixTextSecondary, fontSize = 14.sp)
                        FiveSixRow("Rounded up to next Rs.10", calculation.roundedPay)
                        FiveSixRow("Pay in ${calculation.scale.payBand}", calculation.payInPayBand)
                        FiveSixRow("Grade Pay", calculation.gradePay)
                        Surface(Modifier.fillMaxWidth().padding(top = 4.dp), color = FiveSixBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp)) { Text("6th CPC Revised Basic Pay", color = FiveSixTextSecondary, fontSize = 13.sp); Text(formatFiveSixCurrency(calculation.revisedBasicPay), color = FiveSixBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold) }
                        }
                        Text("Pay fixed as on ${calculation.conversionDate}", color = FiveSixTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("DNI: ${calculation.nextIncrementDate}", color = FiveSixTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                if (incrementSteps.isNotEmpty()) {
                    SixthCpcIncrementProgressionCard(
                        calculation = calculation,
                        steps = incrementSteps,
                        onDelete = { index -> incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) } }
                    )
                }

                Button(
                    onClick = {
                        val latest = incrementSteps.lastOrNull()
                        val currentPayInBand = latest?.let { it.pay - calculation.gradePay } ?: calculation.payInPayBand
                        val nextPay = calculateSixthCpcNextIncrement(currentPayInBand, calculation.gradePay, calculation.scale.payBandMaximum)
                        if (nextPay != null) {
                            val nextDate = latest?.let { addSixthCpcYears(it.date, 1) } ?: sixthCpcFirstIncrementDate()
                            incrementSteps = incrementSteps + SixthCpcIncrementStep(nextPay, nextDate)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FiveSixBlue),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Next Increment", fontWeight = FontWeight.Bold) }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Increment Date Sequence", color = FiveSixBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Conversion pay is shown as on 01 January 2006. The first added increment is dated 01 July 2006, and every further added increment is dated one year after the preceding increment.", color = FiveSixTextSecondary, fontSize = 12.sp)
                }
            }

            result?.let { calculation ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rule Basis", color = FiveSixTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        calculation.ruleBasis.forEachIndexed { index, rule -> Text("${index + 1}. $rule", color = FiveSixTextPrimary, fontSize = 12.sp) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("This is an indicative conversion tool. Verify the result against the CCS (Revised Pay) Rules, 2008, applicable Government orders/clarifications and the employee's service/pay records before official use. Bunching, special pay/NPA, upgraded or merged scales and other special cases may require separate treatment.", Modifier.padding(16.dp), color = FiveSixTextPrimary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SixthCpcIncrementProgressionCard(calculation: FifthToSixthResult, steps: List<SixthCpcIncrementStep>, onDelete: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Increment Progression — ${calculation.scale.payBand}", color = FiveSixBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Pay fixed on 01 January 2006: ${formatFiveSixCurrency(calculation.revisedBasicPay)}", color = FiveSixTextSecondary, fontSize = 13.sp)
            steps.forEachIndexed { index, step ->
                Surface(Modifier.fillMaxWidth(), color = FiveSixBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Increment ${index + 1}", color = FiveSixTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Date: ${formatSixthCpcDate(step.date)}", color = FiveSixTextPrimary, fontSize = 13.sp)
                            Text("Pay thereon: ${formatFiveSixCurrency(step.pay)}", color = FiveSixBlue, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(onClick = { onDelete(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                }
            }
            val currentPayInBand = (steps.lastOrNull()?.pay ?: calculation.revisedBasicPay) - calculation.gradePay
            if (calculateSixthCpcNextIncrement(currentPayInBand, calculation.gradePay, calculation.scale.payBandMaximum) == null) {
                Text("Final Pay Band stage reached", color = Color(0xFF2E7D32), fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun sixthCpcFirstIncrementDate(): Long = Calendar.getInstance().apply { clear(); set(2006, Calendar.JULY, 1, 0, 0, 0) }.timeInMillis

private fun addSixthCpcYears(date: Long, years: Int): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, years) }.timeInMillis

private fun formatSixthCpcDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

@Composable
private fun FiveSixRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FiveSixTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatFiveSixCurrency(value), color = FiveSixTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatFiveSixCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
