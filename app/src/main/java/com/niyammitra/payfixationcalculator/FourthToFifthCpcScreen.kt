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

data class FourthToFifthIncrementStep(val pay: Int, val date: Long)

@Composable
fun FourthToFifthCpcScreen(
    onBack: () -> Unit,
    onContinueToSixth: ((FifthCpcScale, Int) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    var selectedScale by remember { mutableStateOf<FourthCpcScale?>(null) }
    var basicPayText by remember { mutableStateOf("") }
    var nextIncrementDateText by remember { mutableStateOf("") }
    var scaleMenu by remember { mutableStateOf(false) }
    var incrementSteps by remember(selectedScale, basicPayText) { mutableStateOf<List<FourthToFifthIncrementStep>>(emptyList()) }

    val basicPay = basicPayText.toIntOrNull()
    val parsedNextIncrementDate = parseFourthFiveDate(nextIncrementDateText)
    val result = if (selectedScale != null && basicPay != null) {
        runCatching { calculateFourthToFifthCpcPrecise(basicPay, selectedScale!!) }.getOrNull()
    } else null
    val dateError = nextIncrementDateText.isNotBlank() && parsedNextIncrementDate == null
    val validNextIncrementDate = parsedNextIncrementDate?.takeIf { it > fourthFiveConversionDate() }
    val latestIncrement = incrementSteps.lastOrNull()
    val currentPay = latestIncrement?.pay ?: result?.revisedBasicPay
    val currentIncrementDate = latestIncrement?.date
    val canAddIncrement = result != null && currentPay != null &&
        (currentIncrementDate == null || currentIncrementDate < fourthFiveConversionEndDate()) &&
        (currentIncrementDate != null || validNextIncrementDate != null) &&
        calculateFourthToFifthNextIncrement(currentPay, selectedScale!!) != null

    Column(Modifier.fillMaxSize().background(FourFiveBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FourFiveHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("‹", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold); Text("Back", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(16.dp))
                Column { Text("4th CPC → 5th CPC", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Pay Revision", color = Color.White.copy(alpha = .88f), fontSize = 13.sp) }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("4th CPC Pay Details", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Select the applicable 4th CPC scale and enter the basic pay drawn on 01 January 1996.", color = FourFiveTextSecondary, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text("4th CPC Scale", fontSize = 12.sp, color = FourFiveTextSecondary); Text(selectedScale?.grade ?: "Select 4th CPC Scale", color = FourFiveTextPrimary, fontSize = 15.sp) }
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FourthToFifthCpcData.scales.forEach { scale -> DropdownMenuItem(text = { Text("${scale.grade}: ${scale.existingScale}") }, onClick = { selectedScale = scale; basicPayText = ""; nextIncrementDateText = ""; incrementSteps = emptyList(); scaleMenu = false }) }
                        }
                    }
                    selectedScale?.let { Text("Corresponding 5th CPC scale: ${it.revisedScale}", color = FourFiveTextSecondary, fontSize = 12.sp) }
                    OutlinedTextField(value = basicPayText, onValueChange = { if (it.all(Char::isDigit)) { basicPayText = it; incrementSteps = emptyList() } }, label = { Text("Basic Pay as on 01.01.1996") }, placeholder = { Text("e.g. 870") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (selectedScale != null && basicPay != null && result == null) Text("Enter a basic pay within the selected 4th CPC scale.", color = Color(0xFFC62828), fontSize = 12.sp)
                }
            }

            result?.let { calculation ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Next Increment Date", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Under Rule 8, enter the date on which the employee would have drawn the next increment in the existing 4th CPC scale. This date becomes the first increment date in the revised 5th CPC scale.", color = FourFiveTextSecondary, fontSize = 12.sp)
                        OutlinedTextField(value = nextIncrementDateText, onValueChange = { nextIncrementDateText = it.filter { ch -> ch.isDigit() || ch == '/' } }, label = { Text("Next Increment Date in 4th CPC scale") }, placeholder = { Text("dd/MM/yyyy") }, supportingText = { Text(if (dateError) "Enter a valid date in dd/MM/yyyy format." else "Example: 01/07/1996") }, isError = dateError, singleLine = true, modifier = Modifier.fillMaxWidth())
                        if (parsedNextIncrementDate != null && parsedNextIncrementDate <= fourthFiveConversionDate()) Text("The next increment date must be after 01 January 1996.", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }

                Text("Conversion Result", color = FourFiveTextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Audit Trail", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        RowValue("Existing Basic Pay", calculation.existingBasicPay); RowValue("DA @ 148%", calculation.dearnessAllowance); RowValue("1st Interim Relief", calculation.firstInterimRelief); RowValue("2nd Interim Relief", calculation.secondInterimRelief); RowValue("Existing Emoluments", calculation.existingEmoluments)
                        HorizontalDivider(Modifier.padding(vertical = 4.dp)); RowValue("40% Fitment Weightage", calculation.fitmentWeightage); RowValue("Fitment Total", calculation.fitmentTotal)
                        Text("Corresponding 5th CPC Scale", color = FourFiveTextSecondary, fontSize = 12.sp); Text(calculation.scale.revisedScale, color = FourFiveTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Surface(Modifier.fillMaxWidth().padding(top = 4.dp), color = FourFiveBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) { Column(Modifier.padding(14.dp)) { Text("5th CPC Revised Basic Pay", color = FourFiveTextSecondary, fontSize = 13.sp); Text(formatFourFiveCurrency(calculation.revisedBasicPay), color = FourFiveBlue, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold); Text("Pay on 01 January 1996", color = FourFiveTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
                    }
                }

                if (incrementSteps.isNotEmpty()) FourthToFifthIncrementProgressionCard(calculation, incrementSteps) { index -> incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) } }

                Button(onClick = {
                    val scale = selectedScale ?: return@Button
                    val pay = currentPay ?: return@Button
                    val nextPay = calculateFourthToFifthNextIncrement(pay, scale) ?: return@Button
                    val nextDate = currentIncrementDate?.let { addFourthFiveYear(it) } ?: validNextIncrementDate ?: return@Button
                    if (nextDate <= fourthFiveConversionEndDate()) incrementSteps = incrementSteps + FourthToFifthIncrementStep(nextPay, nextDate)
                }, enabled = canAddIncrement, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue), shape = RoundedCornerShape(12.dp)) { Text("Next Increment", fontWeight = FontWeight.Bold) }

                if (incrementSteps.isEmpty() && validNextIncrementDate != null) Text("The first added increment will be shown on ${formatFourthFiveDate(validNextIncrementDate)}. Subsequent increments advance by one year.", color = FourFiveTextSecondary, fontSize = 12.sp)

                val fifthScale = FifthToSixthCpcData.scales.firstOrNull { it.title == calculation.scale.revisedScale }
                if (fifthScale != null && currentPay != null) {
                    // The event timeline starts from the actual current pay state. If the first
                    // 5th-CPC increment has not yet been applied, the state date remains 01.01.1996;
                    // the separately entered next-increment date is only a future event date.
                    FourthToFifthEventSection(currentPay = currentPay, currentScale = fifthScale, currentDate = currentIncrementDate ?: fourthFiveConversionDate(), onContinueToSixth = onContinueToSixth)
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) { Text("Rule 8: the next increment is granted on the date it would have accrued in the existing scale. The progression shown here uses that supplied date for the first revised-scale increment and advances subsequent increments by one year. Case-specific provisos, bunching and other special adjustments require separate verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp) }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) { Text("This calculator implements the standard Rule 7 replacement-scale calculation. Special pay, NPA, personal pay, bunching, stagnation increments and post-01.01.1996 fixation require case-specific verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FourthToFifthIncrementProgressionCard(calculation: FourthToFifthResult, steps: List<FourthToFifthIncrementStep>, onDelete: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("5th CPC Increment Progression", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Pay fixed on 01 January 1996: ${formatFourFiveCurrency(calculation.revisedBasicPay)}", color = FourFiveTextSecondary, fontSize = 13.sp)
            steps.forEachIndexed { index, step ->
                Surface(Modifier.fillMaxWidth(), color = FourFiveBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) { Text("Increment ${index + 1}", color = FourFiveTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold); Text("Date: ${formatFourthFiveDate(step.date)}", color = FourFiveTextPrimary, fontSize = 13.sp); Text("5th CPC Basic Pay: ${formatFourFiveCurrency(step.pay)}", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                        TextButton(onClick = { onDelete(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun fourthFiveConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun fourthFiveConversionEndDate(): Long = Calendar.getInstance().apply { clear(); set(2005, Calendar.DECEMBER, 31, 0, 0, 0) }.timeInMillis
private fun addFourthFiveYear(date: Long): Long = Calendar.getInstance().apply { timeInMillis = date; add(Calendar.YEAR, 1) }.timeInMillis
private fun parseFourthFiveDate(value: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time }.getOrNull()
private fun formatFourthFiveDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
@Composable private fun RowValue(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = FourFiveTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(formatFourFiveCurrency(value), color = FourFiveTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
private fun formatFourFiveCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
