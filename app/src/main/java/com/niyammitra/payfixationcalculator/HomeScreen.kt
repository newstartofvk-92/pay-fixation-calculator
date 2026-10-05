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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    var sixthContinuation by remember { mutableStateOf<SixthCpcContinuationContext?>(null) }
    var restoreVersion by remember { mutableStateOf(0) }
    var carriedFifthScaleTitle by remember { mutableStateOf<String?>(null) }
    var carriedFifthBasicPay by remember { mutableStateOf<Int?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(HistoryStore.getAll(context)) }
    var selectedHistory by remember { mutableStateOf<CalculationHistory?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var selectedMode by remember { mutableStateOf<PayFixationMode?>(null) }
    var showCpcHistory by remember { mutableStateOf(false) }
    var cpcHistoryOverlay by remember { mutableStateOf(false) }
    var showHistoryHub by remember { mutableStateOf(false) }
    var cpcHistory by remember { mutableStateOf(CpcHistoryStore.getAll(context)) }
    var restoreStandalone by remember { mutableStateOf<StandaloneCpcConversionSnapshot?>(null) }
    var restoreStandaloneId by remember { mutableStateOf<String?>(null) }
    var restoreStandaloneOfficialName by remember { mutableStateOf<String?>(null) }
    var restoreStandaloneDesignation by remember { mutableStateOf<String?>(null) }
    var restoreJourney by remember { mutableStateOf<CompleteJourneySnapshot?>(null) }
    var showStartDateScreen by remember { mutableStateOf(false) }
    var selectedStartDate by remember { mutableStateOf<Long?>(null) }
    var detectedCommission by remember { mutableStateOf<PayCommission?>(null) }

    fun restoreCpcRecord(record: CpcHistoryRecord) {
        restoreVersion++
        showCalculator = false
        showHistoryHub = false
        showStartDateScreen = false
        sixthContinuation = null
        showFourthToFifth = false
        showFifthToSixth = false
        showSixthToSeventh = false
        restoreStandalone = (record.payload as? StandaloneConversionPayload)?.snapshot
        restoreStandaloneId = record.uniqueId.takeIf { record.workflowType == CpcHistoryWorkflow.CPC_CONVERSION_ONLY }
        val parsedTitleFields = parseCpcConversionTitle(record.title)
        restoreStandaloneOfficialName = record.officialName ?: parsedTitleFields?.first
        restoreStandaloneDesignation = record.designation ?: parsedTitleFields?.second
        restoreJourney = (record.payload as? CompleteJourneyPayload)?.snapshot?.let(::normalizeJourneyForCpcRestore)
        when (cpcHistoryRestoreDestination(record)) {
            CpcHistoryRestoreDestination.CPC_CONVERSION_ONLY -> selectedMode = PayFixationMode.CPC_CONVERSION_ONLY
            CpcHistoryRestoreDestination.FOURTH_JOURNEY,
            CpcHistoryRestoreDestination.FIFTH_JOURNEY,
            CpcHistoryRestoreDestination.SIXTH_JOURNEY,
            CpcHistoryRestoreDestination.SEVENTH_JOURNEY -> if (restoreJourney != null) {
                val snapshot = restoreJourney!!
                selectedStartDate = snapshot.startingDateMillis
                detectedCommission = when (record.startingCpc) {
                    CpcHistoryStage.FOURTH -> PayCommission.FOURTH
                    CpcHistoryStage.FIFTH -> PayCommission.FIFTH
                    CpcHistoryStage.SIXTH -> PayCommission.SIXTH
                    CpcHistoryStage.SEVENTH -> PayCommission.SEVENTH
                }
                selectedMode = PayFixationMode.COMPLETE_JOURNEY
                when (cpcHistoryRestoreDestination(record)) {
                    CpcHistoryRestoreDestination.FOURTH_JOURNEY -> showFourthToFifth = true
                    CpcHistoryRestoreDestination.FIFTH_JOURNEY -> showFifthToSixth = true
                    CpcHistoryRestoreDestination.SIXTH_JOURNEY -> showSixthToSeventh = true
                    CpcHistoryRestoreDestination.SEVENTH_JOURNEY -> showCalculator = true
                    CpcHistoryRestoreDestination.CPC_CONVERSION_ONLY -> Unit
                }
            }
        }
        showCpcHistory = false
        cpcHistoryOverlay = false
    }
    val openCpcHistoryOverlay = {
        cpcHistory = CpcHistoryStore.getAll(context)
        cpcHistoryOverlay = true
        showCpcHistory = true
    }
    
    if (showCpcHistory && !cpcHistoryOverlay) {
        BackHandler { showCpcHistory = false }
        CpcHistoryScreen(
            records = cpcHistory,
            onBack = { showCpcHistory = false },
            onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) },
            onRestore = ::restoreCpcRecord
        )
        return
    }
    if (showHistoryHub) {
        HistoryHubScreen(onBack = { showHistoryHub = false }, onLegacy = { history = HistoryStore.getAll(context); showHistory = true; showHistoryHub = false }, onCpc = { cpcHistory = CpcHistoryStore.getAll(context); showCpcHistory = true; cpcHistoryOverlay = false; showHistoryHub = false })
        return
    }
    if (showCalculator) {
        val completeSeventhJourney = selectedMode == PayFixationMode.COMPLETE_JOURNEY && detectedCommission == PayCommission.SEVENTH
        BackHandler {
            showCalculator = false
            if (completeSeventhJourney) showStartDateScreen = true
        }
        key(restoreVersion) { PayFixationCalculatorScreen(
            onBack = {
                showCalculator = false
                if (completeSeventhJourney) showStartDateScreen = true
            },
            onHome = {
                showCalculator = false
                selectedMode = null
                selectedStartDate = null
                detectedCommission = null
                restoreJourney = null
            },
            onOpenCpcHistory = openCpcHistoryOverlay,
            completeJourneyStartDateMillis = selectedStartDate.takeIf { completeSeventhJourney },
            restoredCompleteJourney = restoreJourney.takeIf { completeSeventhJourney },
            journeySequenceIntegrity = restoreJourney?.sequenceIntegrity ?: CpcSequenceIntegrity.ORIGINAL
        ) }
        if (showCpcHistory && cpcHistoryOverlay) CpcHistoryOverlay(cpcHistory, onBack = { showCpcHistory = false; cpcHistoryOverlay = false }, onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) }, onRestore = ::restoreCpcRecord)
        return
    }
    if (showStartDateScreen) {
        PayFixationStartDateScreen(
            onBack = {
                showStartDateScreen = false
                selectedMode = null
                selectedStartDate = null
                detectedCommission = null
                restoreJourney = null
                sixthContinuation = null
            },
            onHome = { showStartDateScreen = false; selectedMode = null; selectedStartDate = null; detectedCommission = null; sixthContinuation = null },
            initialStartDateMillis = selectedStartDate,
            onContinue = { date, commission ->
                selectedStartDate = date
                detectedCommission = commission
                showStartDateScreen = false

                when (commission) {
                    PayCommission.FOURTH -> showFourthToFifth = true
                    PayCommission.FIFTH -> showFifthToSixth = true
                    PayCommission.SIXTH -> showSixthToSeventh = true
                    PayCommission.SEVENTH -> showCalculator = true
                }
            }
        )
        return
    }
    if (showFourthToFifth) {
        key(restoreVersion) { FourthToFifthCpcScreen(
            onBack = { showFourthToFifth = false },
            restoredSnapshot = restoreJourney?.fourth,
            restoredFifthSnapshot = restoreJourney?.fifth,
            sequenceIntegrity = restoreJourney?.sequenceIntegrity ?: CpcSequenceIntegrity.ORIGINAL,
            onHistory = openCpcHistoryOverlay,
            onContinueToSeventh = { payBand, gradePay, payInPayBand, effectiveDate, journey ->
                carriedPayBand = payBand
                carriedPayInPayBand = payInPayBand
                carriedGradePay = gradePay
                sixthContinuation = SixthCpcContinuationContext(payBand, gradePay, payInPayBand, effectiveDate, journey)
                restoreJourney = journey
                selectedStartDate = effectiveDate
                showFourthToFifth = false
                showSixthToSeventh = true
            }
        ) }
        if (showCpcHistory && cpcHistoryOverlay) CpcHistoryOverlay(cpcHistory, onBack = { showCpcHistory = false; cpcHistoryOverlay = false }, onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) }, onRestore = ::restoreCpcRecord)
        return
    }
    if (showFifthToSixth) {
        key(restoreVersion) { FifthToSixthCpcScreen(onBack = { showFifthToSixth = false }, onContinueToSeventh = { payBand, gradePay, payInPayBand, effectiveDate, journey -> carriedPayBand = payBand; carriedPayInPayBand = payInPayBand; carriedGradePay = gradePay; sixthContinuation = SixthCpcContinuationContext(payBand, gradePay, payInPayBand, effectiveDate, journey); restoreJourney = journey; selectedStartDate = effectiveDate; showFifthToSixth = false; showSixthToSeventh = true }, initialScaleTitle = restoreJourney?.fifth?.scaleId ?: carriedFifthScaleTitle, initialBasicPay = restoreJourney?.fifth?.startingBasicPay ?: carriedFifthBasicPay, initialStartDate = restoreJourney?.fifth?.startingDateMillis ?: selectedStartDate, initialDni = restoreJourney?.fifth?.startingDniMillis, restoredSnapshot = restoreJourney?.fifth, sequenceIntegrity = restoreJourney?.sequenceIntegrity ?: CpcSequenceIntegrity.ORIGINAL, onHistory = openCpcHistoryOverlay) }
        if (showCpcHistory && cpcHistoryOverlay) CpcHistoryOverlay(cpcHistory, onBack = { showCpcHistory = false; cpcHistoryOverlay = false }, onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) }, onRestore = ::restoreCpcRecord)
        return
    }
    if (showSixthToSeventh) {
        val restoredSixth = restoreJourney?.sixth ?: restoreJourney?.fifth?.sixthContinuation
        key(restoreVersion) { SixthToSeventhCpcScreen(onBack = {
            showSixthToSeventh = false
            sixthContinuation?.let { context ->
                restoreJourney = context.journey
                if (context.journey.startingCpc == CpcHistoryStage.FOURTH) showFourthToFifth = true else showFifthToSixth = true
            }
            sixthContinuation = null
        }, initialPayBand = sixthContinuation?.payBandId ?: restoredSixth?.payBandId ?: carriedPayBand, initialGradePay = sixthContinuation?.gradePay ?: restoredSixth?.gradePay ?: carriedGradePay, initialPayInPayBand = sixthContinuation?.payInPayBand ?: restoredSixth?.startingPayInPayBand ?: carriedPayInPayBand, initialStartDate = sixthContinuation?.effectiveDateMillis ?: restoredSixth?.startingDateMillis ?: selectedStartDate, restoredSnapshot = if (sixthContinuation != null) null else restoredSixth, continuationContext = sixthContinuation, sequenceIntegrity = restoreJourney?.sequenceIntegrity ?: CpcSequenceIntegrity.ORIGINAL, onHistory = openCpcHistoryOverlay) }
        if (showCpcHistory && cpcHistoryOverlay) CpcHistoryOverlay(cpcHistory, onBack = { showCpcHistory = false; cpcHistoryOverlay = false }, onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) }, onRestore = ::restoreCpcRecord)
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
            onSelected = { selectedMode = it; restoreJourney = null; restoreStandalone = null; restoreStandaloneId = null; restoreStandaloneOfficialName = null; restoreStandaloneDesignation = null; sixthContinuation = null; selectedStartDate = null; detectedCommission = null },
            onHistory = { showHistoryHub = true },
            onCpcHistory = { cpcHistory = CpcHistoryStore.getAll(context); showCpcHistory = true },
            onLegacyCalculator = { selectedMode = null; showCalculator = true },
            onAbout = { showAboutDialog = true }
        )

        PayFixationMode.COMPLETE_JOURNEY -> PayFixationModeJourneyEntryScreen(
            onStartDate = { showStartDateScreen = true },
            onLegacySelection = { },
            onHistory = { showHistoryHub = true },
            onAbout = { showAboutDialog = true },
            onHome = { selectedMode = null; selectedStartDate = null; detectedCommission = null; restoreJourney = null; sixthContinuation = null },
            selectedStartDate = selectedStartDate,
            detectedCommission = detectedCommission
        )

        PayFixationMode.CPC_CONVERSION_ONLY -> {
            BackHandler { selectedMode = null }
            key(restoreVersion) { CpcConversionOnlyScreen(
                onBack = { selectedMode = null },
                onHome = { selectedMode = null },
                initialSnapshot = restoreStandalone,
                initialRecordId = restoreStandaloneId,
                initialOfficialName = restoreStandaloneOfficialName,
                initialDesignation = restoreStandaloneDesignation,
                onHistory = openCpcHistoryOverlay,
                onSaveRecord = { snapshot, recordId, title, officialName, designation ->
                    val now = System.currentTimeMillis()
                    CpcHistoryStore.save(context, CpcHistoryRecord(
                        uniqueId = recordId ?: java.util.UUID.randomUUID().toString(),
                        workflowType = CpcHistoryWorkflow.CPC_CONVERSION_ONLY,
                        title = title,
                        savedAtMillis = now,
                        startingCpc = when (snapshot.kind) {
                            StandaloneConversionKind.FOURTH_TO_FIFTH -> CpcHistoryStage.FOURTH
                            StandaloneConversionKind.FIFTH_TO_SIXTH -> CpcHistoryStage.FIFTH
                            StandaloneConversionKind.SIXTH_TO_SEVENTH -> CpcHistoryStage.SIXTH
                        },
                        currentStage = when (snapshot.kind) {
                            StandaloneConversionKind.FOURTH_TO_FIFTH -> CpcHistoryStage.FIFTH
                            StandaloneConversionKind.FIFTH_TO_SIXTH -> CpcHistoryStage.SIXTH
                            StandaloneConversionKind.SIXTH_TO_SEVENTH -> CpcHistoryStage.SEVENTH
                        },
                        payload = StandaloneConversionPayload(snapshot),
                        officialName = officialName,
                        designation = designation
                    ))
                    cpcHistory = CpcHistoryStore.getAll(context)
                    android.widget.Toast.makeText(context, "CPC conversion saved", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) }
        }
    }

    if (showCpcHistory && cpcHistoryOverlay) CpcHistoryOverlay(cpcHistory, onBack = { showCpcHistory = false; cpcHistoryOverlay = false }, onDelete = { id -> CpcHistoryStore.delete(context, id); cpcHistory = CpcHistoryStore.getAll(context) }, onRestore = ::restoreCpcRecord)

    if (showAboutDialog) AboutDialog(onClose = { showAboutDialog = false })
}

@Composable
private fun PayFixationModeSelectionScreen(
    onSelected: (PayFixationMode) -> Unit,
    onHistory: () -> Unit,
    onCpcHistory: () -> Unit,
    onLegacyCalculator: () -> Unit,
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
            androidx.compose.material3.OutlinedButton(onClick = onCpcHistory, modifier = Modifier.fillMaxWidth()) {
                Text("Saved CPC Journeys & Conversions")
            }
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

            PayFixationModeCard(
                title = "Pay Fixation under 7th CPC",
                description = "Calculate promotion or MACP pay fixation under the 7th CPC using the existing calculator.",
                badge = "7TH CPC",
                onClick = onLegacyCalculator
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
private fun PayFixationModeJourneyEntryScreen(
    onStartDate: () -> Unit,
    onLegacySelection: () -> Unit,
    onHistory: () -> Unit,
    onAbout: () -> Unit,
    onHome: () -> Unit,
    selectedStartDate: Long?,
    detectedCommission: PayCommission?
) {
    Column(
        modifier = Modifier.fillMaxSize().background(HomeNiyamBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader(onHistory = onHistory, onAbout = onAbout, onHome = onHome)
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
private fun HomeHeader(onHistory: (() -> Unit)? = null, onAbout: (() -> Unit)? = null, onHome: (() -> Unit)? = null) {
    Surface(modifier = Modifier.fillMaxWidth(), color = HomeNiyamHeaderBlue, shadowElevation = 3.dp) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text("NM", color = HomeNiyamHeaderBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Pay Fixation Calculator", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold); Text("NiyamMitra", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium) }
            if (onHistory != null && onAbout != null) Row(verticalAlignment = Alignment.CenterVertically) {
                onHome?.let { TextButton(onClick = it) { Text("Home", color = Color.White, fontWeight = FontWeight.Bold) } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable(onClick = onHistory)) { Icon(Icons.Default.History, contentDescription = "History", modifier = Modifier.size(24.dp), tint = Color.White); Text("History", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Box(modifier = Modifier.padding(horizontal = 10.dp).height(34.dp).width(1.dp).background(Color.White.copy(alpha = 0.35f)))
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp).clickable(onClick = onAbout)) { Icon(Icons.Default.Info, contentDescription = "About", modifier = Modifier.size(24.dp), tint = Color.White); Text("About", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun HistoryHubScreen(onBack: () -> Unit, onLegacy: () -> Unit, onCpc: () -> Unit) {
    Column(Modifier.fillMaxSize().background(HomeNiyamBackground)) {
        Surface(Modifier.fillMaxWidth(), color = HomeNiyamHeaderBlue) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White, fontWeight = FontWeight.Bold) }
                Text("History", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Choose saved calculations", color = HomeNiyamTextSecondary)
            androidx.compose.material3.OutlinedButton(onClick = onLegacy, modifier = Modifier.fillMaxWidth()) { Text("7th CPC Pay Fixation History") }
            androidx.compose.material3.OutlinedButton(onClick = onCpc, modifier = Modifier.fillMaxWidth()) { Text("CPC Journeys & Conversions") }
        }
    }
}

@Composable
private fun CpcHistoryOverlay(
    records: List<CpcHistoryRecord>,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (CpcHistoryRecord) -> Unit
) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Color.White) {
            CpcHistoryScreen(records = records, onBack = onBack, onDelete = onDelete, onRestore = onRestore)
        }
    }
}
