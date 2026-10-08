package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class PayFixationStartingPosition(
    val scaleOrLevel: String,
    val basicPay: Int?,
    val payBand: String?,
    val gradePay: Int?,
    val payInPayBand: Int?,
    val dniMillis: Long?,
    val dniText: String
)

internal fun seventhSnapshotFromStartingPosition(
    startDateMillis: Long,
    position: PayFixationStartingPosition
): SeventhCpcJourneySnapshot? {
    val basicPay = position.basicPay ?: return null
    val dni = position.dniMillis ?: return null
    return SeventhCpcJourneySnapshot(
        startingLevel = position.scaleOrLevel.removePrefix("Level ").trim(),
        startingBasicPay = basicPay,
        conversionDateMillis = startDateMillis,
        startingDniMillis = dni
    )
}

internal fun validStartingSeventhPayCells(level: String): List<Int> {
    val matrix = PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY)
    return if (level in matrix.levels) matrix.getPayStages(level) else emptyList()
}

internal fun validStartingSeventhDniOptions(startDateMillis: Long): List<Long> =
    getPayFixationDniOptions(startDateMillis).filter { it >= startDateMillis }.distinct().sorted()

internal fun validInitialStartingSeventhBasicPay(level: String, basicPay: Int?): Int? =
    basicPay?.takeIf { it in validStartingSeventhPayCells(level) }

@Composable
fun PayFixationStartingPositionScreen(
    commission: PayCommission,
    startDateMillis: Long,
    initialPosition: PayFixationStartingPosition? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onContinue: (PayFixationStartingPosition) -> Unit
) {
    val seventhMatrix = remember { PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY) }
    val initialSeventhLevel = initialPosition?.scaleOrLevel?.removePrefix("Level ")?.trim()
        ?.takeIf { it in seventhMatrix.levels }.orEmpty()
    val seventhDniOptions = remember(startDateMillis) { validStartingSeventhDniOptions(startDateMillis) }
    var scaleOrLevel by remember(initialPosition, commission) {
        mutableStateOf(if (commission == PayCommission.SEVENTH) initialSeventhLevel else initialPosition?.scaleOrLevel.orEmpty())
    }
    var basicPay by remember(initialPosition, commission, initialSeventhLevel) {
        val selectedInitialPay = if (commission == PayCommission.SEVENTH) {
            validInitialStartingSeventhBasicPay(initialSeventhLevel, initialPosition?.basicPay)
        } else initialPosition?.basicPay
        mutableStateOf(selectedInitialPay?.toString().orEmpty())
    }
    var payBand by remember(initialPosition) { mutableStateOf(initialPosition?.payBand.orEmpty()) }
    var gradePay by remember(initialPosition) { mutableStateOf(initialPosition?.gradePay?.toString().orEmpty()) }
    var payInPayBand by remember(initialPosition) { mutableStateOf(initialPosition?.payInPayBand?.toString().orEmpty()) }
    var dni by remember(initialPosition) { mutableStateOf(initialPosition?.dniText.orEmpty()) }
    var selectedSeventhDni by remember(initialPosition, seventhDniOptions) {
        mutableStateOf(initialPosition?.dniMillis?.takeIf { it in seventhDniOptions })
    }
    var seventhLevelMenuExpanded by remember { mutableStateOf(false) }
    var seventhPayMenuExpanded by remember { mutableStateOf(false) }
    var seventhDniMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    val seventhLevel = scaleOrLevel.removePrefix("Level ").trim()
    val seventhPay = basicPay.toIntOrNull()
    val seventhDni = selectedSeventhDni
    val selectedSeventhPayCells = if (seventhLevel in seventhMatrix.levels) seventhMatrix.getPayStages(seventhLevel) else emptyList()

    val canContinue = when (commission) {
        PayCommission.FOURTH, PayCommission.FIFTH ->
            scaleOrLevel.isNotBlank() && basicPay.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SIXTH ->
            payBand.isNotBlank() && gradePay.toIntOrNull() != null &&
                payInPayBand.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SEVENTH ->
            seventhLevel in seventhMatrix.levels && seventhPay != null && seventhPay in selectedSeventhPayCells &&
                seventhDni != null && seventhDni in seventhDniOptions
    }

    Column(Modifier.fillMaxSize().imePadding().background(Color(0xFFF7FAFC))) {
        PayFixationAppHeader(
            title = "Pay Fixation Calculator",
            subtitle = "NiyamMitra",
            onBack = onBack,
            onHome = onHome,
            branded = true
        )

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Starting Pay Position", color = Color(0xFF172B4D), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Enter the pay position applicable on the selected starting date.", color = Color(0xFF5B6B7A), fontSize = 14.sp)

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Detected CPC", color = Color(0xFF5B6B7A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(commission.displayName(), color = Color(0xFF1769AA), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Starting date: " + formatStartDateForPositionScreen(startDateMillis), color = Color(0xFF5B6B7A), fontSize = 13.sp)
                }
            }

            when (commission) {
                PayCommission.FOURTH, PayCommission.FIFTH -> {
                    OutlinedTextField(
                        value = scaleOrLevel, onValueChange = { scaleOrLevel = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Pay Scale") },
                        placeholder = { Text("e.g. ₹2000-60-2300-EB-75-3200") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = basicPay, onValueChange = { basicPay = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Basic Pay") },
                        placeholder = { Text("Enter basic pay") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                PayCommission.SIXTH -> {
                    OutlinedTextField(
                        value = payBand, onValueChange = { payBand = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Pay Band") },
                        placeholder = { Text("e.g. PB-2") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = gradePay, onValueChange = { gradePay = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Grade Pay") },
                        placeholder = { Text("Enter grade pay") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                    OutlinedTextField(
                        value = payInPayBand, onValueChange = { payInPayBand = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Pay in Pay Band") },
                        placeholder = { Text("Enter pay in pay band") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
                PayCommission.SEVENTH -> {
                    Box {
                        OutlinedButton(onClick = { seventhLevelMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("Pay Matrix Level", fontSize = 12.sp, color = Color(0xFF5B6B7A))
                                Text(seventhLevel.takeIf { it.isNotBlank() }?.let { "Level $it" } ?: "Select Pay Matrix Level", color = Color(0xFF172B4D), fontSize = 15.sp)
                            }
                            Text("▼")
                        }
                        DropdownMenu(expanded = seventhLevelMenuExpanded, onDismissRequest = { seventhLevelMenuExpanded = false }) {
                            seventhMatrix.levels.forEach { level ->
                                DropdownMenuItem(
                                    text = { Text("Level $level") },
                                    onClick = {
                                        scaleOrLevel = level
                                        basicPay = ""
                                        seventhPayMenuExpanded = false
                                        seventhLevelMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    Box {
                        OutlinedButton(
                            onClick = { seventhPayMenuExpanded = true },
                            enabled = seventhLevel in seventhMatrix.levels,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                                Text("Basic Pay", fontSize = 12.sp, color = Color(0xFF5B6B7A))
                                Text(seventhPay?.let(::formatStartingPositionCurrency) ?: "Select Basic Pay", color = Color(0xFF172B4D), fontSize = 15.sp)
                            }
                            Text("▼")
                        }
                        DropdownMenu(expanded = seventhPayMenuExpanded && seventhLevel in seventhMatrix.levels, onDismissRequest = { seventhPayMenuExpanded = false }) {
                            selectedSeventhPayCells.forEach { pay ->
                                DropdownMenuItem(
                                    text = { Text(formatStartingPositionCurrency(pay)) },
                                    onClick = { basicPay = pay.toString(); seventhPayMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }

            if (commission == PayCommission.SEVENTH) {
                Box {
                    OutlinedButton(onClick = { seventhDniMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                            Text("Date of Next Increment (DNI)", fontSize = 12.sp, color = Color(0xFF5B6B7A))
                            Text(selectedSeventhDni?.let(::formatStartDateForPositionScreen) ?: "Select DNI", color = Color(0xFF172B4D), fontSize = 15.sp)
                        }
                        Text("▼")
                    }
                    DropdownMenu(expanded = seventhDniMenuExpanded, onDismissRequest = { seventhDniMenuExpanded = false }) {
                        seventhDniOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(formatStartDateForPositionScreen(option)) },
                                onClick = { selectedSeventhDni = option; seventhDniMenuExpanded = false }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = dni, onValueChange = { dni = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Date of Next Increment (DNI)") },
                    placeholder = { Text("dd/MM/yyyy") }, singleLine = true
                )
            }

            Button(
                onClick = {
                    onContinue(PayFixationStartingPosition(
                        scaleOrLevel = scaleOrLevel.trim(),
                        basicPay = basicPay.toIntOrNull(),
                        payBand = payBand.trim().ifBlank { null },
                        gradePay = gradePay.toIntOrNull(),
                        payInPayBand = payInPayBand.toIntOrNull(),
                        dniMillis = if (commission == PayCommission.SEVENTH) selectedSeventhDni else parseStartingPositionDate(dni),
                        dniText = if (commission == PayCommission.SEVENTH) selectedSeventhDni?.let(::formatStartingPositionDniInput).orEmpty() else dni.trim()
                    ))
                },
                enabled = canContinue, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun parseStartingPositionDate(value: String): Long? {
    val normalized = value.trim()
    val patterns = listOf("dd/MM/yyyy", "dd-MM-yyyy", "ddMMyyyy")
    return patterns.firstNotNullOfOrNull { pattern ->
        val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.ROOT).apply {
            isLenient = false
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val position = java.text.ParsePosition(0)
        formatter.parse(normalized, position)?.time?.takeIf { position.index == normalized.length }
    }
}

private fun formatStartDateForPositionScreen(dateMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(dateMillis))
}

private fun formatStartingPositionDniInput(dateMillis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(dateMillis))

private fun formatStartingPositionCurrency(amount: Int): String =
    NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(amount)
