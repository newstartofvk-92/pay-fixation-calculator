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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val FiveSixBlue = Color(0xFF1769AA)
private val FiveSixHeaderBlue = Color(0xFF1976B8)
private val FiveSixBackground = Color(0xFFF7FAFC)
private val FiveSixTextPrimary = Color(0xFF172B4D)
private val FiveSixTextSecondary = Color(0xFF5B6B7A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FifthToSixthCpcScreen(
    onBack: () -> Unit,
    onContinueToSeventh: ((String, Int, Int) -> Unit)? = null,
    initialScaleTitle: String? = null,
    initialBasicPay: Int? = null,
    initialStartDate: Long? = null
) {
    BackHandler(onBack = onBack)

    var selectedScale by remember(initialScaleTitle) {
        mutableStateOf(initialScaleTitle?.let { title ->
            FifthToSixthCpcData.scales.firstOrNull { it.title == title }
        })
    }
    var scaleMenu by remember { mutableStateOf(false) }
    var basicPayText by remember(initialBasicPay) { mutableStateOf(initialBasicPay?.toString() ?: "") }
    var selectedDni by remember { mutableStateOf<Long?>(null) }
    var showDniPicker by remember { mutableStateOf(false) }

    val basicPay = basicPayText.toIntOrNull()
    val startDate = initialStartDate
    val endDate = fifthCpcEndDateForScreen()

    val validStartingPosition = startDate != null &&
        selectedScale != null &&
        basicPay != null &&
        basicPay > 0 &&
        selectedDni != null &&
        selectedDni!! > startDate &&
        selectedDni!! <= endDate

    Column(Modifier.fillMaxSize().background(FiveSixBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FiveSixHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("5th CPC Pay Journey", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Historical Pay Progression", color = Color.White.copy(alpha = .88f), fontSize = 13.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("5th CPC Starting Pay", color = FiveSixBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Enter the pay position applicable on the selected starting date. The existing 5th CPC increment and event workflow will then continue from this position.", color = FiveSixTextSecondary, fontSize = 13.sp)
                    Text("Starting Date", color = FiveSixTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(startDate?.let(::formatFifthSixScreenDate) ?: "Starting date not supplied", color = FiveSixTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedScale?.title ?: "Select 5th CPC Pay Scale", Modifier.weight(1f), color = FiveSixTextPrimary, fontSize = 15.sp)
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FifthToSixthCpcData.scales.forEach { scale ->
                                DropdownMenuItem(text = { Text(scale.title) }, onClick = { selectedScale = scale; scaleMenu = false })
                            }
                        }
                    }

                    selectedScale?.let {
                        Text("6th CPC mapping: " + it.payBand + "  |  Grade Pay: ₹" + it.gradePay, color = FiveSixTextSecondary, fontSize = 12.sp)
                    }

                    OutlinedTextField(
                        value = basicPayText,
                        onValueChange = { if (it.all(Char::isDigit)) basicPayText = it },
                        label = { Text("5th CPC Basic Pay") },
                        placeholder = { Text("e.g. 8300") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedButton(onClick = { showDniPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            selectedDni?.let { "DNI: " + formatFifthSixScreenDate(it) } ?: "Select Date of Next Increment (DNI)",
                            Modifier.weight(1f)
                        )
                        Text("📅")
                    }

                    if (selectedDni != null && startDate != null && selectedDni!! <= startDate) {
                        Text("DNI must be after the selected starting date.", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                    if (selectedDni != null && selectedDni!! > endDate) {
                        Text("DNI must be on or before 01 January 2006.", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }
            }

            if (validStartingPosition && selectedScale != null && basicPay != null && startDate != null) {
                FifthCpcHistoricalIncrementSection(
                    initialPay = basicPay,
                    revisedScale = selectedScale!!.title,
                    firstIncrementDate = selectedDni,
                    conversionDate = startDate,
                    initialDate = startDate,
                    initialDni = selectedDni,
                    onContinueToSeventh = onContinueToSeventh
                )
            } else {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                    Text("Enter the 5th CPC scale, basic pay and DNI to begin the historical 5th CPC progression.", Modifier.padding(18.dp), color = FiveSixTextSecondary, fontSize = 13.sp)
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("The existing 5th CPC increment and event calculation components are reused here. This screen does not duplicate or replace their calculation logic.", Modifier.padding(16.dp), color = FiveSixTextPrimary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDniPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDni ?: startDate)
        DatePickerDialog(
            onDismissRequest = { showDniPicker = false },
            confirmButton = {
                TextButton(onClick = { selectedDni = pickerState.selectedDateMillis; showDniPicker = false }) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDniPicker = false }) { Text("Cancel") }
        }) { DatePicker(state = pickerState) }
    }
}

private fun fifthCpcEndDateForScreen(): Long =
    java.util.Calendar.getInstance().apply { clear(); set(2006, java.util.Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis

private fun formatFifthSixScreenDate(value: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

