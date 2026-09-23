package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Start Pay Fixation",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF172B4D)
        )

        Text(
            "Enter the date from which you want to start the complete pay-fixation journey. The applicable CPC will be determined automatically.",
            fontSize = 14.sp,
            color = Color(0xFF5B6B7A)
        )

        Text(
            "Starting Date",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF172B4D)
        )

        TextButton(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                selectedDate?.let(::formatStartDate) ?: "Select starting date",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        selectedDate?.let { date ->
            val commission = detectPayCommission(date)

            Text(
                "Applicable CPC",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF172B4D)
            )

            Text(
                commission.displayName(),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1769AA)
            )

            Text(
                "The next screen will ask only for the starting pay details applicable to this CPC.",
                fontSize = 13.sp,
                color = Color(0xFF5B6B7A)
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { onContinue(date, commission) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Continue")
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedDate = pickerState.selectedDateMillis
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm")
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

private fun formatStartDate(millis: Long): String =
    SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))
