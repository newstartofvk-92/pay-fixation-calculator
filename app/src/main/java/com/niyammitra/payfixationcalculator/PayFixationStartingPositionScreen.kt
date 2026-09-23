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
    val dniMillis: Long?
)

@Composable
fun PayFixationStartingPositionScreen(
    commission: PayCommission,
    startDateMillis: Long,
    onBack: () -> Unit,
    onContinue: (PayFixationStartingPosition) -> Unit
) {
    var scaleOrLevel by remember { mutableStateOf("") }
    var basicPay by remember { mutableStateOf("") }
    var payBand by remember { mutableStateOf("") }
    var gradePay by remember { mutableStateOf("") }
    var payInPayBand by remember { mutableStateOf("") }
    var dni by remember { mutableStateOf("") }

    BackHandler(onBack = onBack)

    val canContinue = when (commission) {
        PayCommission.FOURTH, PayCommission.FIFTH ->
            scaleOrLevel.isNotBlank() && basicPay.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SIXTH ->
            payBand.isNotBlank() && gradePay.toIntOrNull() != null &&
                payInPayBand.toIntOrNull() != null && dni.isNotBlank()
        PayCommission.SEVENTH ->
            scaleOrLevel.isNotBlank() && basicPay.toIntOrNull() != null && dni.isNotBlank()
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
                placeholder = { Text("Enter DNI") }, singleLine = true
            )

            Button(
                onClick = {
                    onContinue(PayFixationStartingPosition(
                        scaleOrLevel = scaleOrLevel.trim(),
                        basicPay = basicPay.toIntOrNull(),
                        payBand = payBand.trim().ifBlank { null },
                        gradePay = gradePay.toIntOrNull(),
                        payInPayBand = payInPayBand.toIntOrNull(),
                        dniMillis = null
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

private fun formatStartDateForPositionScreen(dateMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.getDefault())
    formatter.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return formatter.format(java.util.Date(dateMillis))
}
