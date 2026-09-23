package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class PayCommission {
    FOURTH,
    FIFTH,
    SIXTH,
    SEVENTH
}

fun detectPayCommission(startDateMillis: Long): PayCommission {
    val calendar = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = startDateMillis
    }

    val year = calendar.get(java.util.Calendar.YEAR)
    val month = calendar.get(java.util.Calendar.MONTH)
    val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

    fun isOnOrAfter(y: Int, m: Int, d: Int): Boolean {
        return when {
            year != y -> year > y
            month != m -> month > m
            else -> day >= d
        }
    }

    return when {
        isOnOrAfter(2016, java.util.Calendar.JANUARY, 1) -> PayCommission.SEVENTH
        isOnOrAfter(2006, java.util.Calendar.JULY, 1) -> PayCommission.SIXTH
        isOnOrAfter(1996, java.util.Calendar.JANUARY, 1) -> PayCommission.FIFTH
        else -> PayCommission.FOURTH
    }
}

fun PayCommission.displayName(): String = when (this) {
    PayCommission.FOURTH -> "4th CPC"
    PayCommission.FIFTH -> "5th CPC"
    PayCommission.SIXTH -> "6th CPC"
    PayCommission.SEVENTH -> "7th CPC"
}

private fun formatStartDate(dateMillis: Long): String {
    val formatter = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    formatter.timeZone = TimeZone.getTimeZone("UTC")
    return formatter.format(Date(dateMillis))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFixationStartDateScreen(
    onBack: () -> Unit,
    onContinue: (startDateMillis: Long, commission: PayCommission) -> Unit
) {
    var selectedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7FAFC))
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1976B8),
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPaddingCompat()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        "Pay Fixation Calculator",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "NiyamMitra",
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Start Pay Fixation",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF172B4D)
            )

            Text(
                "Select the date from which you want to start the complete pay-fixation journey. The applicable CPC will be detected automatically.",
                fontSize = 14.sp,
                color = Color(0xFF5B6B7A)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Starting Date",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1769AA)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = Color(0xFFD0D7DE),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Date from which pay fixation starts",
                                    fontSize = 12.sp,
                                    color = Color(0xFF5B6B7A)
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    selectedDate?.let(::formatStartDate) ?: "Select starting date",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedDate != null) Color(0xFF172B4D) else Color(0xFF7A869A)
                                )
                            }
                            Text("▼", color = Color(0xFF1769AA), fontSize = 14.sp)
                        }
                    }
                }
            }

            selectedDate?.let { date ->
                val commission = detectPayCommission(date)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Applicable Pay Commission",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5B6B7A)
                        )
                        Text(
                            commission.displayName(),
                            fontSize = 23.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1769AA)
                        )
                        Text(
                            "The starting pay fields for this CPC will be requested next.",
                            fontSize = 13.sp,
                            color = Color(0xFF5B6B7A)
                        )
                    }
                }

                Button(
                    onClick = { onContinue(date, commission) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        "Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDate)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    selectedDate = pickerState.selectedDateMillis
                    showDatePicker = false
                }) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun androidx.compose.ui.Modifier.statusBarsPaddingCompat(): Modifier =
    this.padding(top = 24.dp)
