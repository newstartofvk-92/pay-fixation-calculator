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

@Composable
fun PayFixationStartingPositionScreen(
    commission: PayCommission,
    startDateMillis: Long,
    initialPosition: PayFixationStartingPosition? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onContinue: (PayFixationStartingPosition) -> Unit
) {
    var scaleOrLevel by remember(initialPosition) { mutableStateOf(initialPosition?.scaleOrLevel.orEmpty()) }
    var basicPay by remember(initialPosition) { mutableStateOf(initialPosition?.basicPay?.toString().orEmpty()) }
    var payBand by remember(initialPosition) { mutableStateOf(initialPosition?.payBand.orEmpty()) }
    var gradePay by remember(initialPosition) { mutableStateOf(initialPosition?.gradePay?.toString().orEmpty()) }
    var payInPayBand by remember(initialPosition) { mutableStateOf(initialPosition?.payInPayBand?.toString().orEmpty()) }
    var dni by remember(initialPosition) { mutableStateOf(initialPosition?.dniText.orEmpty()) }

    BackHandler(onBack = onBack)

    val seventhLevel = scaleOrLevel.removePrefix("Level ").trim()
    val seventhPay = basicPay.toIntOrNull()
    val seventhMatrix = PayMatrixSelection.forCategory(EmployeeCategory.ORDINARY)
    val seventhDni = parseStartingPositionDate(dni)

    val canContinue = when (commission) {
        PayCommission.FOURTH, PayCommission.FIFTH ->
            scaleOrLevel.isNotBlank() && basicPay.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SIXTH ->
            payBand.isNotBlank() && gradePay.toIntOrNull() != null &&
                payInPayBand.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SEVENTH ->
            seventhLevel in seventhMatrix.levels && seventhPay != null && seventhPay in seventhMatrix.getPayStages(seventhLevel) &&
                seventhDni != null && seventhDni >= startDateMillis
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7FAFC))) {
        Surface(Modifier.fillMaxWidth(), color = Color(0xFF1976B8), shadowElevation = 3.dp) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
                TextButton(onClick = onHome) { Text("Home", color = Color.White, fontWeight = FontWeight.Bold) }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Pay Fixation Calculator", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text("NiyamMitra", color = Color.White.copy(alpha = 0.88f), fontSize = 12.sp)
                }
            }
        }

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
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
                    OutlinedTextField(
                        value = scaleOrLevel, onValueChange = { scaleOrLevel = it },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Pay Matrix Level") },
                        placeholder = { Text("e.g. Level 6") }, singleLine = true
                    )
                    OutlinedTextField(
                        value = basicPay, onValueChange = { basicPay = it.filter(Char::isDigit) },
                        modifier = Modifier.fillMaxWidth(), label = { Text("Basic Pay") },
                        placeholder = { Text("Enter basic pay") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                    )
                }
            }

            OutlinedTextField(
                value = dni, onValueChange = { dni = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Date of Next Increment (DNI)") },
                placeholder = { Text("dd/MM/yyyy") }, singleLine = true
            )

            Button(
                onClick = {
                    onContinue(PayFixationStartingPosition(
                        scaleOrLevel = scaleOrLevel.trim(),
                        basicPay = basicPay.toIntOrNull(),
                        payBand = payBand.trim().ifBlank { null },
                        gradePay = gradePay.toIntOrNull(),
                        payInPayBand = payInPayBand.toIntOrNull(),
                        dniMillis = parseStartingPositionDate(dni),
                        dniText = dni.trim()
                    ))
                },
                enabled = canContinue, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            if (commission == PayCommission.SEVENTH && scaleOrLevel.isNotBlank() && basicPay.isNotBlank() && dni.isNotBlank() && !canContinue) {
                Text("Enter a valid 7th CPC level, a basic pay cell from that level, and a DNI on or after the starting date.", color = Color(0xFFC62828), fontSize = 12.sp)
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
