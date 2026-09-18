package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
    var payDateText by remember { mutableStateOf("") }
    var nextIncrementDateText by remember { mutableStateOf("") }
    var scaleMenu by remember { mutableStateOf(false) }
    var incrementSteps by remember(selectedScale, basicPayText) { mutableStateOf<List<FourthToFifthIncrementStep>>(emptyList()) }
    var showEvents by remember { mutableStateOf(false) }
    var eventScale by remember { mutableStateOf<FourthCpcScale?>(null) }
    var eventPay by remember { mutableStateOf<Int?>(null) }
    var eventDate by remember { mutableStateOf<Long?>(null) }
    var conversionActivated by remember { mutableStateOf(false) }

    val basicPay = basicPayText.toIntOrNull()
    val payDate = parseFourthFiveDate(formatFourthFiveDateInput(payDateText))
    val validStartingPosition = selectedScale != null && basicPay != null && payDate != null && payDate >= fourthCpcStartDate() && payDate <= fourthFiveConversionDate() && basicPay in selectedScale!!.existingStages
    val formattedNextIncrementDate = formatFourthFiveDateInput(nextIncrementDateText)
    val parsedNextIncrementDate = parseFourthFiveDate(formattedNextIncrementDate)
    val result = if (selectedScale != null && basicPay != null) {
        runCatching { calculateFourthToFifthCpc(basicPay, selectedScale!!) }.getOrNull()
    } else null
    val dateError = nextIncrementDateText.isNotBlank() && parsedNextIncrementDate == null
    val validNextIncrementDate = parsedNextIncrementDate?.takeIf { payDate != null && it > payDate && it <= fourthFiveConversionDate() }
    val latestIncrement = incrementSteps.lastOrNull()
    val currentPay = eventPay ?: latestIncrement?.pay ?: basicPay
    val currentIncrementDate = eventDate ?: latestIncrement?.date
    val currentScale = eventScale ?: selectedScale
    val conversionPay = currentPay
    val conversionScale = currentScale
    val timelineDate = currentIncrementDate ?: payDate
    val conversionReached = conversionPay != null && conversionScale != null &&
        timelineDate != null && timelineDate <= fourthFiveConversionDate()
    val conversionResult = if (conversionActivated && conversionReached && conversionPay != null && conversionScale != null) {
        runCatching { calculateFourthToFifthCpc(conversionPay, conversionScale) }.getOrNull()
    } else null
    val canAddIncrement = validStartingPosition && currentPay != null &&
        (currentIncrementDate == null || currentIncrementDate < fourthFiveConversionEndDate()) &&
        (currentIncrementDate != null || validNextIncrementDate != null) &&
        currentScale != null && calculateFourthCpcNextIncrement(currentPay, currentScale) != null

    Column(Modifier.fillMaxSize().background(FourFiveBackground)) {
        Surface(Modifier.fillMaxWidth(), color = FourFiveHeaderBlue, shadowElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp))
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
                    Text("Select the applicable 4th CPC scale and enter the basic pay drawn on any date during the 4th CPC period.", color = FourFiveTextSecondary, fontSize = 13.sp)
                    Box {
                        OutlinedButton(onClick = { scaleMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedScale?.let { "${it.grade}: ${it.existingScale}" } ?: "Select 4th CPC Scale", Modifier.weight(1f))
                            Text("▼")
                        }
                        DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }) {
                            FourthToFifthCpcData.scales.forEachIndexed { index, scale ->
                                if (index > 0 && FourthToFifthCpcData.scales[index - 1].grade.substringBefore(" (") != scale.grade.substringBefore(" (")) {
                                    HorizontalDivider()
                                }
                                DropdownMenuItem(
                                    text = { Text("${scale.grade}: ${scale.existingScale}") },
                                    onClick = {
                                        selectedScale = scale
                                        basicPayText = ""
                                        nextIncrementDateText = ""
                                        incrementSteps = emptyList()
                                        eventScale = null
                                        eventPay = null
                                        eventDate = null
                                        showEvents = false
                                        conversionActivated = false
                                        scaleMenu = false
                                    }
                                )
                            }
                        }
                    }
                    selectedScale?.let { Text("Corresponding 5th CPC scale: ${it.revisedScale}", color = FourFiveTextSecondary, fontSize = 12.sp) }
                    OutlinedTextField(
                        value = basicPayText,
                        onValueChange = { newValue ->
                            basicPayText = newValue.filter(Char::isDigit)
                            incrementSteps = emptyList()
                            eventScale = null
                            eventPay = null
                            eventDate = null
                            showEvents = false
                            conversionActivated = false
                        },
                        label = { Text("Basic Pay") },
                        placeholder = { Text("e.g. 870") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = payDateText,
                        onValueChange = { newValue -> payDateText = newValue.filter(Char::isDigit).take(8); incrementSteps = emptyList(); eventScale = null; eventPay = null; eventDate = null; showEvents = false; conversionActivated = false },
                        label = { Text("Pay Date (dd/MM/yyyy)") },
                        placeholder = { Text("e.g. 01/07/1988") },
                        supportingText = { Text("Enter a date from 01 January 1986 through 01 January 1996.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        visualTransformation = FourthFiveDateVisualTransformation,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedScale != null && basicPay != null && result == null) {
                        Text("Enter a basic pay within the selected 4th CPC scale.", color = Color(0xFFC62828), fontSize = 12.sp)
                    }
                }
            }

            if (validStartingPosition) {
                result?.let { calculation ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("4th CPC Next Increment / DNI", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Enter the date on which the next increment would have accrued in the existing 4th CPC scale. This date is used to continue the historical 4th CPC pay progression.", color = FourFiveTextSecondary, fontSize = 12.sp)
                            OutlinedTextField(
                                value = nextIncrementDateText,
                                onValueChange = { newValue ->
                                    nextIncrementDateText = newValue.filter(Char::isDigit).take(8)
                                    showEvents = false
                                },
                                label = { Text("Next Increment / DNI Date") },
                                placeholder = { Text("dd/MM/yyyy") },
                                supportingText = {
                                    Text(if (dateError) "Enter a valid date after the pay date and on or before 01/01/1996."
                                    else "Example: 01/07/1989")
                                },
                                isError = dateError,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                visualTransformation = FourthFiveDateVisualTransformation,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (parsedNextIncrementDate != null && payDate != null && parsedNextIncrementDate <= payDate) {
                                Text("The next increment date must be after the entered pay date.", color = Color(0xFFC62828), fontSize = 12.sp)
                            } else if (parsedNextIncrementDate != null && parsedNextIncrementDate > fourthFiveConversionDate()) {
                                Text("The next increment date cannot be after 01 January 1996.", color = Color(0xFFC62828), fontSize = 12.sp)
                            }
                        }
                    }
                }

                result?.let { calculation ->
                    if (incrementSteps.isNotEmpty()) {
                        FourthToFifthIncrementProgressionCard(calculation, incrementSteps) { index ->
                            incrementSteps = incrementSteps.toMutableList().also { it.removeAt(index) }
                            showEvents = false
                        }
                    }
                }

                if (eventPay != null && eventScale != null && eventDate != null) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Current 4th CPC Position", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            RowValue("Basic Pay", eventPay!!)
                            Text("Scale: ${eventScale!!.grade}: ${eventScale!!.existingScale}", color = FourFiveTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("Effective Date: ${formatFourthFiveDate(eventDate!!)}", color = FourFiveTextSecondary, fontSize = 13.sp)
                            Text("The next increment will now be calculated from this event position.", color = FourFiveTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                if (!showEvents) {
                    Button(
                        onClick = { showEvents = true },
                        enabled = currentPay != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Add 4th CPC Event", fontWeight = FontWeight.Bold) }
                }

                Button(
                    onClick = {
                        val scale = currentScale ?: return@Button
                        val pay = currentPay ?: return@Button
                        val nextPay = calculateFourthCpcNextIncrement(pay, scale) ?: return@Button
                        val nextDate = currentIncrementDate?.let { addFourthFiveYear(it) } ?: validNextIncrementDate ?: return@Button
                        if (nextDate <= fourthFiveConversionEndDate()) {
                            incrementSteps = incrementSteps + FourthToFifthIncrementStep(nextPay, nextDate)
                            eventPay = nextPay
                            eventDate = nextDate
                            showEvents = false
                        }
                    },
                    enabled = validStartingPosition && canAddIncrement,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("4th CPC Next Increment", fontWeight = FontWeight.Bold) }

                if (incrementSteps.isEmpty() && validNextIncrementDate != null) {
                    Text("The first added increment will be shown on ${formatFourthFiveDate(validNextIncrementDate)}. Subsequent increments advance by one year.", color = FourFiveTextSecondary, fontSize = 12.sp)
                }

                if (conversionReached && !conversionActivated) {
                    Button(
                        onClick = { conversionActivated = true },
                        enabled = conversionPay != null && conversionScale != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = FourFiveBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Convert to 5th CPC", fontWeight = FontWeight.Bold) }
                    Text(
                        "The 4th CPC timeline is ready for conversion effective 01 January 1996. Tap above to calculate the 5th CPC revised basic pay.",
                        color = FourFiveTextSecondary,
                        fontSize = 12.sp
                    )
                }

                conversionResult?.let { calculation ->
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
                            }
                        }
                    }

                    // Continue the historical journey inside the 5th CPC after
                    // the 01.01.1996 conversion. The first 5th CPC increment
                    // follows the next annual increment date from the final
                    // 4th CPC position used for conversion.
                    val firstFifthIncrementDate = timelineDate?.let { addFourthFiveYear(it) }
                    FifthCpcHistoricalIncrementSection(
                        initialPay = calculation.revisedBasicPay,
                        revisedScale = calculation.scale.revisedScale,
                        firstIncrementDate = firstFifthIncrementDate,
                        conversionDate = fourthFiveConversionDate(),
                        onContinueToSixth = onContinueToSixth
                    )
                }

                if (showEvents && currentScale != null && currentPay != null) {
                    FourthCpcEventSection(
                        currentPay = currentPay,
                        currentScale = currentScale,
                        currentDate = currentIncrementDate ?: payDate ?: fourthCpcStartDate(),
                        onEventApplied = { newScale, newPay, newDate ->
                            eventScale = newScale
                            eventPay = newPay
                            eventDate = newDate
                            incrementSteps = incrementSteps.filter { it.date < newDate }
                            nextIncrementDateText = ""
                            showEvents = false
                        }
                    )
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                    Text("Rule 8: the next increment is granted on the date it would have accrued in the existing scale. Case-specific provisos, bunching and other special adjustments require separate verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
                }
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("Rule 8: the next increment is granted on the date it would have accrued in the existing scale. The progression shown here uses that supplied date for the first revised-scale increment and advances subsequent increments by one year. Case-specific provisos, bunching and other special adjustments require separate verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
            }

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFFFF8E1)), shape = RoundedCornerShape(16.dp)) {
                Text("This calculator implements the standard Rule 7 replacement-scale calculation. Special pay, NPA, personal pay, bunching, stagnation increments and post-01.01.1996 fixation require case-specific verification.", Modifier.padding(16.dp), color = FourFiveTextPrimary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun FourthToFifthIncrementProgressionCard(calculation: FourthToFifthResult, steps: List<FourthToFifthIncrementStep>, onDelete: (Int) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("4th CPC Increment Progression", color = FourFiveBlue, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text("Starting 4th CPC pay: ${formatFourFiveCurrency(calculation.existingBasicPay)}", color = FourFiveTextSecondary, fontSize = 13.sp)
            steps.forEachIndexed { index, step ->
                Surface(Modifier.fillMaxWidth(), color = FourFiveBlue.copy(alpha = .06f), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Increment ${index + 1}", color = FourFiveTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("Date: ${formatFourthFiveDate(step.date)}", color = FourFiveTextPrimary, fontSize = 13.sp)
                            Text("4th CPC Basic Pay: ${formatFourFiveCurrency(step.pay)}", color = FourFiveBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        TextButton(onClick = { onDelete(index) }) { Text("Delete", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

private fun fourthCpcStartDate(): Long = Calendar.getInstance().apply { clear(); set(1986, Calendar.JANUARY, 1) }.timeInMillis

private fun fourthFiveConversionDate(): Long = Calendar.getInstance().apply { clear(); set(1996, Calendar.JANUARY, 1, 0, 0, 0) }.timeInMillis
private fun fourthFiveConversionEndDate(): Long = fourthFiveConversionDate()
private fun addFourthFiveYear(date: Long): Long = Calendar.getInstance().apply {
    timeInMillis = date
    add(Calendar.YEAR, 1)
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
private fun parseFourthFiveDate(value: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time }.getOrNull()
private fun formatFourthFiveDate(value: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))

private fun formatFourthFiveDateInput(digits: String): String = buildString {
    digits.filter(Char::isDigit).take(8).forEachIndexed { index, digit ->
        if (index == 2 || index == 4) append('/')
        append(digit)
    }
}

private object FourthFiveDateVisualTransformation : androidx.compose.ui.text.input.VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val transformed = formatFourthFiveDateInput(text.text)
        val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 4 -> offset + 1
                    offset <= 8 -> offset + 2
                    else -> transformed.length
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 2 -> offset
                    offset <= 5 -> offset - 1
                    offset <= 10 -> offset - 2
                    else -> text.text.length
                }.coerceIn(0, text.text.length)
            }
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(transformed), offsetMapping)
    }
}

@Composable private fun RowValue(label: String, value: Int) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(label, color = FourFiveTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(formatFourFiveCurrency(value), color = FourFiveTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp) } }
private fun formatFourFiveCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
