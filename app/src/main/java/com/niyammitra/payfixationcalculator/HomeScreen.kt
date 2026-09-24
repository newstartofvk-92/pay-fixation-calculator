package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext

private val HomeNiyamBlue = Color(0xFF1769AA)
private val HomeNiyamHeaderBlue = Color(0xFF1976B8)
private val HomeNiyamBackground = Color(0xFFF7FAFC)
private val HomeNiyamTextPrimary = Color(0xFF172B4D)
private val HomeNiyamTextSecondary = Color(0xFF5B6B7A)

private enum class PayFixationMode {
    COMPLETE_JOURNEY,
    CPC_CONVERSION_ONLY
}

@Composable
fun V2AppScreen() {
    val context = LocalContext.current
    var selectedFixationType by remember { mutableStateOf<FixationType?>(null) }
    var showCalculator by remember { mutableStateOf(false) }
    var showFourthToFifth by remember { mutableStateOf(false) }
    var showFifthToSixth by remember { mutableStateOf(false) }
    var showSixthToSeventh by remember { mutableStateOf(false) }
    var carriedPayBand by remember { mutableStateOf<String?>(null) }
    var carriedPayInPayBand by remember { mutableStateOf<Int?>(null) }
    var carriedGradePay by remember { mutableStateOf<Int?>(null) }
    var carriedFifthScaleTitle by remember { mutableStateOf<String?>(null) }
    var carriedFifthBasicPay by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(HistoryStore.getAll(context)) }
    var selectedHistory by remember { mutableStateOf<CalculationHistory?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<PayFixationMode?>(null) }
    var showStartDateScreen by remember { mutableStateOf(false) }
    var selectedStartDate by remember { mutableStateOf<Long?>(null) }
    var detectedCommission by remember { mutableStateOf<PayCommission?>(null) }
    
    if (showCalculator) { BackHandler { showCalculator = false }; PayFixationCalculatorScreen(); return }
    if (showStartDateScreen) {
        PayFixationStartDateScreen(
            onBack = { showStartDateScreen = false },
            onContinue = { date, commission ->
                selectedStartDate = date
                detectedCommission = commission
                showStartDateScreen = false

                when (commission) {
                    PayCommission.FOURTH -> showFourthToFifth = true
                    PayCommission.FIFTH -> showFifthToSixth = true
                    PayCommission.SIXTH -> showSixthToSeventh = true
                    PayCommission.SEVENTH -> selectedMode = PayFixationMode.COMPLETE_JOURNEY
                }
            }
        )
        return
    }
    if (showFourthToFifth) {
        FourthToFifthCpcScreen(
            onBack = { showFourthToFifth = false },
            onContinueToSeventh = { payBand, payInPayBand, gradePay ->
                carriedPayBand = payBand
                carriedPayInPayBand = payInPayBand
                carriedGradePay = gradePay
                showFourthToFifth = false
                showSixthToSeventh = true
            }
        )
        return
    }
    if (showFifthToSixth) {
        FifthToSixthCpcScreen(onBack = { showFifthToSixth = false }, onContinueToSeventh = { payBand, payInPayBand, gradePay -> carriedPayBand = payBand; carriedPayInPayBand = payInPayBand; carriedGradePay = gradePay; showFifthToSixth = false; showSixthToSeventh = true }, initialScaleTitle = carriedFifthScaleTitle, initialBasicPay = carriedFifthBasicPay, initialStartDate = selectedStartDate)
        return
    }
    if (showSixthToSeventh) {
        SixthToSeventhCpcScreen(onBack = { showSixthToSeventh = false }, initialPayBand = carriedPayBand, initialGradePay = carriedGradePay, initialPayInPayBand = carriedPayInPayBand, initialStartDate = selectedStartDate)
        return
    }
    if (showHistory) {
        BackHandler { showHistory = false }
        HistoryScreen(history = history, onBack = { showHistory = false }, onDelete = { id -> HistoryStore.delete(context, id); history = HistoryStore.getAll(context) }, onClear = { showClearHistoryDialog = true }, onOpen = { selectedHistory = it }, onAbout = { showAboutDialog = true })
        if (showClearHistoryDialog) AlertDialog(onDismissRequest = { showClearHistoryDialog = false }, title = { Text("Clear History?") }, text = { Text("All saved calculations will be permanently removed from this device.") }, confirmButton = { TextButton(onClick = { HistoryStore.clear(context); history = emptyList(); showClearHistoryDialog = false }) { Text("Clear", color = Color(0xFFD64545), fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } })
        selectedHistory?.let { entry -> HistoryDetailDialog(entry) { selectedHistory = null } }
        if (showAboutDialog) AboutDialog(onClose = { showAboutDialog = false })
        return
    }

    when (selectedMode) {
        null -> PayFixationModeSelectionScreen(
            onSelected = { selectedMode = it },
            onHistory = { history = HistoryStore.getAll(context); showHistory = true },
            onAbout = { showAboutDialog = true }
        )

        PayFixationMode.COMPLETE_JOURNEY -> PayFixationModeJourneyEntryScreen(
            onStartDate = { showStartDateScreen = true },
            onLegacySelection = { },
            onHistory = { history = HistoryStore.getAll(context); showHistory = true },
            onAbout = { showAboutDialog = true },
            selectedStartDate = selectedStartDate,
            detectedCommission = detectedCommission
        )

        PayFixationMode.CPC_CONVERSION_ONLY -> {
            BackHandler { selectedMode = null }
            ConversionOnlyPlaceholder(
                onBack = { selectedMode = null }
            )
        }
    }

    if (showAboutDialog) AboutDialog(onClose = { showAboutDialog = false })
}

@Composable
private fun PayFixationModeSelectionScreen(
    onSelected: (PayFixationMode) -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(HomeNiyamBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader(onHistory = onHistory, onAbout = onAbout)

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Pay Fixation Calculator",
                color = HomeNiyamTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Choose how you want to calculate or convert pay.",
                color = HomeNiyamTextSecondary,
                fontSize = 14.sp
            )

            PayFixationModeCard(
                title = "Complete Pay Fixation Journey",
                description = "Start from a historical date and carry the pay through the applicable CPCs, events, increments and later pay fixation.",
                badge = "JOURNEY",
                onClick = { onSelected(PayFixationMode.COMPLETE_JOURNEY) }
            )

            PayFixationModeCard(
                title = "CPC Conversion Only",
                description = "Convert an existing pay position from one CPC to another without running the complete historical journey.",
                badge = "CONVERSION",
                onClick = { onSelected(PayFixationMode.CPC_CONVERSION_ONLY) }
            )
        }
    }
}

@Composable
private fun PayFixationModeCard(
    title: String,
    description: String,
    badge: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(58.dp).background(
                    HomeNiyamBlue.copy(alpha = 0.10f),
                    RoundedCornerShape(15.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badge,
                    color = HomeNiyamBlue,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    color = HomeNiyamTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    description,
                    color = HomeNiyamTextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ConversionOnlyPlaceholder(onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(HomeNiyamBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "CPC Conversion Only",
                color = HomeNiyamTextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "This screen is reserved for the direct CPC conversion workflow. The existing 4th→5th, 5th→6th and 6th→7th conversion engines will be connected here next.",
                color = HomeNiyamTextSecondary,
                fontSize = 14.sp
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Text(
                    "Existing CPC calculation logic remains unchanged.",
                    modifier = Modifier.padding(20.dp),
                    color = HomeNiyamTextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onBack) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun PayFixationModeJourneyEntryScreen(
    onStartDate: () -> Unit,
    onLegacySelection: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
    selectedStartDate: Long?,
    detectedCommission: PayCommission?
) {
    Column(
        modifier = Modifier.fillMaxSize().background(HomeNiyamBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader(onHistory = onHistory, onAbout = onAbout)
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Complete Pay Fixation Journey", color = HomeNiyamTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Start with the date of your choice. The applicable CPC will be detected automatically.", color = HomeNiyamTextSecondary, fontSize = 14.sp)

            PayFixationModeCard(
                title = "Start from a Date",
                description = "Select the starting date first. You will not have to choose the CPC manually.",
                badge = "DATE",
                onClick = onStartDate
            )

            if (selectedStartDate != null && detectedCommission != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Detected starting CPC", color = HomeNiyamTextSecondary, fontSize = 13.sp)
                        Text(detectedCommission.displayName(), color = HomeNiyamBlue, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                        Text("The existing CPC workflow will be opened next.", color = HomeNiyamTextSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFixationSelectionScreen(onSelected: (FixationType) -> Unit, onHistory: () -> Unit, onAbout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(HomeNiyamBackground), horizontalAlignment = Alignment.CenterHorizontally) {
        HomeHeader(onHistory = onHistory, onAbout = onAbout)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Select Fixation Type", color = HomeNiyamTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Choose the pay-revision or pay-fixation workflow you want to work out.", color = HomeNiyamTextSecondary, fontSize = 14.sp)
            FixationType.values().forEach { type -> FixationTypeCard(type = type, enabled = true, onClick = { onSelected(type) }) }
        }
    }
}

@Composable
private fun FixationTypeCard(type: FixationType, enabled: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF0F2F5)), elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(46.dp).background(if (enabled) HomeNiyamBlue.copy(alpha = 0.10f) else Color(0xFFE1E4E8), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text(when (type) { FixationType.FOURTH_TO_FIFTH -> "4→5"; FixationType.FIFTH_TO_SIXTH -> "5→6"; FixationType.SIXTH_TO_SEVENTH -> "6→7"; FixationType.SEVENTH_CPC -> "7th" }, color = if (enabled) HomeNiyamBlue else HomeNiyamTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(type.title, color = if (enabled) HomeNiyamTextPrimary else HomeNiyamTextSecondary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(4.dp)); Text(type.description, color = HomeNiyamTextSecondary, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun HomeHeader(onHistory: (() -> Unit)? = null, onAbout: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth(), color = HomeNiyamHeaderBlue, shadowElevation = 3.dp) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("NM", color = HomeNiyamHeaderBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Pay Fixation Calculator", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold); Text("NiyamMitra", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            if (onHistory != null && onAbout != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable(onClick = onHistory)) { Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(24.dp), tint = Color.White); Text("History", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Box(modifier = Modifier.padding(horizontal = 10.dp).height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.35f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable(onClick = onAbout)) { Icon(Icons.Default.Info, contentDescription = "About", modifier = Modifier.size(24.dp), tint = Color.White); Text("About", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
