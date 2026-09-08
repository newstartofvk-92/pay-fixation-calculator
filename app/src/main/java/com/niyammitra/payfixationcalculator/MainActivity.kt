package com.niyammitra.payfixationcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.niyammitra.payfixationcalculator.ui.theme.PayFixationCalculatorTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val NiyamBlue = Color(0xFF1769AA)
private val NiyamBackground = Color(0xFFF7FAFC)
private val NiyamTextPrimary = Color(0xFF172B4D)
private val NiyamTextSecondary = Color(0xFF5B6B7A)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PayFixationCalculatorTheme { PayFixationCalculatorScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFixationCalculatorScreen() {
    var currentLevel by remember { mutableStateOf(PayMatrixData.levels[6]) }
    var currentPay by remember { mutableStateOf(PayMatrixData.getPayStages(currentLevel).getOrNull(5) ?: 47600) }
    var promotedLevel by remember { mutableStateOf(PayMatrixData.levels[7]) }
    var promotionDate by remember { mutableStateOf<Long?>(null) }
    var dniDate by remember { mutableStateOf<Long?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var payMenu by remember { mutableStateOf(false) }
    var promotedMenu by remember { mutableStateOf(false) }
    var dniMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dniOptions = remember(promotionDate) {
        promotionDate?.let { getPayFixationDniOptions(it) } ?: emptyList()
    }
    LaunchedEffect(dniOptions) {
        if (dniOptions.isNotEmpty() && dniDate == null) dniDate = dniOptions[0]
    }
    val result = remember(currentLevel, currentPay, promotedLevel, promotionDate, dniDate) {
        calculatePayFixation(currentLevel, currentPay, promotedLevel, promotionDate, dniDate)
    }

    Column(Modifier.fillMaxSize().background(NiyamBackground).statusBarsPadding()) {
        Text("Pay Fixation Tool", Modifier.padding(horizontal = 20.dp, vertical = 18.dp), color = NiyamTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            SelectionCard("Current Status") {
                DropdownField("Current Pay Level", "Level $currentLevel", PayMatrixData.levels, levelMenu, { levelMenu = it }) { level ->
                    currentLevel = level
                    currentPay = PayMatrixData.getPayStages(level).first()
                }
                DropdownField("Current Basic Pay", formatCurrency(currentPay), PayMatrixData.getPayStages(currentLevel).map { formatCurrency(it) }, payMenu, { payMenu = it }) { value ->
                    currentPay = PayMatrixData.getPayStages(currentLevel).first { formatCurrency(it) == value }
                }
            }

            SelectionCard("Promotion Details") {
                DropdownField("Promoted Pay Level", "Level $promotedLevel", PayMatrixData.levels, promotedMenu, { promotedMenu = it }) { promotedLevel = it }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DateField("Date of Promotion", promotionDate, { showDatePicker = true }, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        Text("Date of Next Increment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Box(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { if (dniOptions.isNotEmpty()) dniMenu = true }, enabled = dniOptions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text(dniDate?.let { formatDate(it) } ?: "Select", modifier = Modifier.weight(1f))
                                Text("▼")
                            }
                            DropdownMenu(dniMenu, { dniMenu = false }) {
                                dniOptions.forEach { option -> DropdownMenuItem({ Text(formatDate(option)) }, { dniDate = option; dniMenu = false }) }
                            }
                        }
                    }
                }
            }

            Text("Fixation Illustrations", color = NiyamTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            ResultCard("Option 1: Fixation from Date of Promotion", promotionDate, listOf(
                "Pay in lower Level ($currentLevel)" to result.option1.lowerLevelPay,
                "Add one increment in lower Level ($currentLevel)" to result.option1.payWithIncrement,
                "Placement in promoted Level ($promotedLevel)" to result.option1.finalFixedPay
            ), result.option1.finalFixedPay, result.option1.nextDni, result.option1.payAfterNextDni)
            ResultCard("Option 2: Fixation from Date of Next Increment", dniDate, listOf(
                "Pay from date of promotion until DNI (placed at next higher cell in Level $promotedLevel)" to result.option2.payUntilDni,
                "On DNI, annual increment in lower Level ($currentLevel)" to result.option2.payWithAnnualIncrement,
                "On DNI, one increment on account of promotion in Level $currentLevel" to result.option2.payWithPromotionIncrement,
                "Final placement in promoted Level ($promotedLevel)" to result.option2.finalFixedPay
            ), result.option2.finalFixedPay, result.option2.nextDni, result.option2.payAfterNextDni, result.option2.payUntilDni)

            val benefit = result.option2.finalFixedPay - result.option1.finalFixedPay
            if (benefit != 0) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (benefit > 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
                Text(if (benefit > 0) "Option 2 is beneficial. It results in ₹$benefit higher basic pay after DNI." else "Option 1 appears more beneficial in this specific case.", Modifier.padding(16.dp), color = if (benefit > 0) Color(0xFF2E7D32) else Color(0xFFE65100))
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { promotionDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) }
        }) { DatePicker(state) }
    }
}

@Composable
private fun SelectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = NiyamBlue)
            content()
        }
    }
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, expanded: Boolean, setExpanded: (Boolean) -> Unit, onSelected: (String) -> Unit) {
    Box {
        OutlinedButton(onClick = { setExpanded(true) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, fontSize = 12.sp, color = NiyamTextSecondary)
                Text(value, color = NiyamTextPrimary, fontSize = 15.sp)
            }
            Text("▼")
        }
        DropdownMenu(expanded, { setExpanded(false) }) {
            options.forEach { option -> DropdownMenuItem({ Text(option) }, { onSelected(option); setExpanded(false) }) }
        }
    }
}

@Composable
private fun DateField(label: String, millis: Long?, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp)) {
            Text(millis?.let { formatDate(it) } ?: "Select", color = if (millis != null) NiyamTextPrimary else Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ResultCard(title: String, date: Long?, steps: List<Pair<String, Int>>, finalPay: Int, futureDni: Long?, futurePay: Int?, interimPay: Int? = null) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = NiyamBlue, fontSize = 16.sp)
            date?.let { Text("DNI Selected: ${formatDate(it)}", fontSize = 12.sp, color = Color.Gray) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            steps.forEach { (description, pay) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(description, fontSize = 13.sp, color = NiyamTextPrimary)
                    Text("Pay: ${formatCurrency(pay)}", fontSize = 13.sp, color = NiyamBlue, fontWeight = FontWeight.Bold)
                }
            }
            Surface(Modifier.padding(top = 10.dp), color = NiyamBlue.copy(alpha = .05f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    interimPay?.let { Text("Interim Pay: ${formatCurrency(it)}", fontSize = 13.sp, color = NiyamTextSecondary) }
                    Text("Final Fixed Pay: ${formatCurrency(finalPay)}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = NiyamBlue)
                    if (futureDni != null && futurePay != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Next DNI: ${formatDate(futureDni)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NiyamTextPrimary)
                        Text("Pay thereon: ${formatCurrency(futurePay)}", fontSize = 13.sp, color = NiyamTextSecondary)
                    }
                }
            }
        }
    }
}

private fun formatCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value).replace("₹", "₹")
private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
