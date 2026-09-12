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

private val FourFiveBlue = Color(0xFF1769AA)
private val FourFiveHeaderBlue = Color(0xFF1976B8)
private val FourFiveBackground = Color(0xFFF7FAFC)
private val FourFiveTextPrimary = Color(0xFF172B4D)
private val FourFiveTextSecondary = Color(0xFF5B6B7A)

@Composable
fun FourthToFifthCpcScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var selectedScale by remember { mutableStateOf<FourthCpcScale?>(null) }
    var basicPayText by remember { mutableStateOf("") }
    var scaleMenu by remember { mutableStateOf(false) }
    val basicPay = basicPayText.toIntOrNull()
    val result = if (selectedScale != null && basicPay != null) {
        runCatching { calculateFourthToFifthCpcPrecise(basicPay, selectedScale!!) }.getOrNull()
    } else null

    Column(Modifier.fillMaxSize().background(FourFiveBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FourFiveHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Back", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("4th CPC → 5th CPC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Pay Revision", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("4th CPC Pay Details", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Select the applicable 4th CPC scale and enter the basic pay drawn on 01 January 1996.", color = FourFiveTextSecondary, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("4th CPC Scale", fontSize = 12.sp, color = FourFiveTextSecondary)
                                Text(selectedScale?.grade ?: "Select 4th CPC Scale", color = FourFiveTextPrimary, fontSize = 15.sp)
                            }
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FourthToFifthCpcData.scales.forEach { scale ->
                                DropdownMenuItem(text = { Text("${scale.grade}: ${scale.existingScale}") }, onClick = { selectedScale = scale; basicPayText = ""; scaleMenu = false })
                            }
                        }
                    }
                    selectedScale?.let { Text("Corresponding 5th CPC scale: ${it.revisedScale}", color = FourFiveTextSecondary, fontSize = 12.sp) }
                    OutlinedTextField(value = basicPayText, onValueChange = { if (it.all(Char::isDigit)) basicPayText = it }, label = { Text("Basic Pay as on 01.01.1996") }, placeholder = { Text("e.g. 870") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (selectedScale != null && basicPay != null && result == null) Text("Enter a basic pay within the selected 4th CPC scale.", color = Color(0xFFC62828), fontSize = 12.sp)
                }
            }

            result?.let { calculation ->
                Text("Conversion Result", color = FourFiveTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audit Trail", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        RowValue("Existing Basic Pay", calculation.existingBasicPay)
                        RowValue("DA @ 148%", calculation.dearnessAllowance)
                        RowValue("1st Interim Relief", calculation.firstInterimRelief)
                        RowValue("2nd Interim Relief", calculation.secondInterimRelief)
                        RowValue("Existing Emoluments", calculation.existingEmoluments)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        RowValue("40% Fitment Weightage", calculation.fitmentWeightage)
                        RowValue("Fitment Total", calculation.fitmentTotal)
                        Text("Corresponding 5th CPC Scale", color = FourFiveTextSecondary, fontSize = 12.sp)
                        Text(calculation.scale.revisedScale, color = FourFiveTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Surface(Modifier.fillMaxWidth().padding(top = 4.dp), color = FourFiveBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.padding(14.dp)) {
                                Text("5th CPC Revised Basic Pay", color = FourFiveTextSecondary, fontSize = 13.sp)
                                Text(formatFourFiveCurrency(calculation.revisedBasicPay), color = FourFiveBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Pay on 01 January 1996", color = FourFiveTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Text(calculation.nextIncrementNote, color = FourFiveTextSecondary, fontSize = 12.sp)
                    }
                }
            }

            result?.let { calculation ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Rule Basis", color = FourFiveTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        calculation.ruleBasis.forEachIndexed { index, rule -> Text("${index + 1}. $rule", color = FourFiveTextPrimary, fontSize = 12.sp) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("This calculator implements the standard Rule 7 replacement-scale calculation. Special pay, NPA, personal pay, bunching, stagnation increments and post-01.01.1996 fixation require case-specific verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RowValue(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = FourFiveTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(formatFourFiveCurrency(value), color = FourFiveTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

private fun formatFourFiveCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
