package com.niyammitra.payfixationcalculator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.niyammitra.payfixationcalculator.ui.theme.PayFixationCalculatorTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val PrimaryBlue = Color(0xFF1D4ED8)
private val BackgroundColor = Color(0xFFF8FAFC)
private val SurfaceColor = Color(0xFFFFFFFF)
private val TextPrimary = Color(0xFF0F172A)
private val TextSecondary = Color(0xFF64748B)
private val SuccessGreen = Color(0xFF16A34A)
private val SuccessContainer = Color(0xFFDCFCE7)
private val WarningAmber = Color(0xFFD97706)
private val WarningContainer = Color(0xFFFEF3C7)

private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MobileAds.initialize(this)
        setContent { PayFixationCalculatorTheme { PayFixationCalculatorScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFixationCalculatorScreen() {
    val context = LocalContext.current
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(HistoryStore.getAll(context)) }
    var officialName by remember { mutableStateOf("") }
    var currentLevel by remember { mutableStateOf<String?>(null) }
    var currentPay by remember { mutableStateOf<Int?>(null) }
    var promotedLevel by remember { mutableStateOf<String?>(null) }
    var promotionDate by remember { mutableStateOf<Long?>(null) }
    var dniDate by remember { mutableStateOf<Long?>(null) }
    var levelMenu by remember { mutableStateOf(false) }
    var payMenu by remember { mutableStateOf(false) }
    var promotedMenu by remember { mutableStateOf(false) }
    var dniMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedHistory by remember { mutableStateOf<CalculationHistory?>(null) }

    if (showHistory) {
        HistoryScreen(history, { showHistory = false }, { id -> HistoryStore.delete(context, id); history = HistoryStore.getAll(context) }, { showClearHistoryDialog = true }) { selectedHistory = it }
        if (showClearHistoryDialog) AlertDialog(onDismissRequest = { showClearHistoryDialog = false }, title = { Text("Clear History?", fontWeight = FontWeight.Bold) }, text = { Text("All saved calculations will be permanently removed from this device.") }, confirmButton = { TextButton(onClick = { HistoryStore.clear(context); history = emptyList(); showClearHistoryDialog = false }) { Text("Clear All", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) } }, dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } })
        selectedHistory?.let { entry -> HistoryDetailDialog(entry) { selectedHistory = null } }
        return
    }

    val payStages = currentLevel?.let { PayMatrixData.getPayStages(it) } ?: emptyList()
    val dniOptions = remember(promotionDate) { promotionDate?.let { getPayFixationDniOptions(it) } ?: emptyList() }
    LaunchedEffect(promotionDate) { dniDate = dniOptions.firstOrNull() }
    val result = if (currentLevel != null && currentPay != null && promotedLevel != null) calculatePayFixation(currentLevel!!, currentPay!!, promotedLevel!!, promotionDate, dniDate) else null

    Column(Modifier.fillMaxSize().background(BackgroundColor).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(SurfaceColor).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Image(painterResource(id = R.drawable.rkcapps_logo), "RKCApps Logo", Modifier.size(26.dp), contentScale = ContentScale.Fit) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text("Pay Fixation Tool", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text("7th CPC Pay Fixation & Option Comparison", color = TextSecondary, fontSize = 12.sp) }
            OutlinedButton(onClick = { history = HistoryStore.getAll(context); showHistory = true }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) { Icon(Icons.Default.History, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("History", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        }
        HorizontalDivider(color = Color(0xFFE2E8F0))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            SelectionCard("Current Status", Icons.Default.Person) {
                OutlinedTextField(value = officialName, onValueChange = { officialName = it }, label = { Text("Name of Official (Optional)") }, placeholder = { Text("e.g. Rajesh Kumar") }, singleLine = true, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                DropdownField("Present Pay Level", currentLevel?.let { "Level $it" } ?: "Select Present Pay Level", PayMatrixData.levels, levelMenu, { levelMenu = it }, { level -> currentLevel = level; currentPay = null; payMenu = false }, icon = Icons.Default.Layers)
                DropdownField("Current Basic Pay", currentPay?.let { formatCurrency(it) } ?: "Select Current Basic Pay", payStages.map { formatCurrency(it) }, payMenu, { payMenu = it }, { value -> currentPay = payStages.firstOrNull { formatCurrency(it) == value } }, currentLevel != null, Icons.Default.AttachMoney)
            }
            SelectionCard("Promotion Details", Icons.Default.TrendingUp) {
                DropdownField("Promoted Pay Level", promotedLevel?.let { "Level $it" } ?: "Select Promoted Pay Level", PayMatrixData.levels, promotedMenu, { promotedMenu = it }, { level -> promotedLevel = level }, icon = Icons.Default.Layers)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DateField("Date of Promotion", promotionDate, { showDatePicker = true }, Modifier.weight(1f), Icons.Default.DateRange)
                    Column(Modifier.weight(1f)) { Text("Date of Next Increment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary); Box(Modifier.padding(top = 8.dp)) { OutlinedButton(onClick = { if (dniOptions.isNotEmpty()) dniMenu = true }, enabled = dniOptions.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Event, null, tint = TextSecondary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(dniDate?.let { formatDate(it) } ?: "Select", Modifier.weight(1f), color = TextPrimary, fontSize = 13.sp); Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) }; DropdownMenu(expanded = dniMenu, onDismissRequest = { dniMenu = false }) { dniOptions.forEach { option -> DropdownMenuItem(text = { Text(formatDate(option)) }, onClick = { dniDate = option; dniMenu = false }) } } } }
            }
            if (result != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.Calculate, null, tint = PrimaryBlue); Text("Fixation Illustrations & Options", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                val option2Better = result.option2.finalFixedPay > result.option1.finalFixedPay
                ResultCard("Option 1", "Fixation from Date of Promotion", promotionDate, listOf("Pay in lower Level ($currentLevel)" to result.option1.lowerLevelPay, "Add one increment in lower Level ($currentLevel)" to result.option1.payWithIncrement, "Placement in promoted Level ($promotedLevel)" to result.option1.finalFixedPay), result.option1.finalFixedPay, result.option1.nextDni, result.option1.payAfterNextDni, isRecommended = !option2Better)
                ResultCard("Option 2", "Fixation from Date of Next Increment", dniDate, listOf("Pay from promotion until DNI (Level $promotedLevel)" to result.option2.payUntilDni, "On DNI, annual increment in lower Level ($currentLevel)" to result.option2.payWithAnnualIncrement, "On DNI, promotion increment in Level $currentLevel" to result.option2.payWithPromotionIncrement, "Final placement in promoted Level ($promotedLevel)" to result.option2.finalFixedPay), result.option2.finalFixedPay, result.option2.nextDni, result.option2.payAfterNextDni, isRecommended = option2Better)
                val benefit = result.option2.finalFixedPay - result.option1.finalFixedPay
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(if (option2Better) SuccessContainer else WarningContainer), border = BorderStroke(1.dp, if (option2Better) SuccessGreen.copy(alpha = 0.4f) else WarningAmber.copy(alpha = 0.4f))) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(if (option2Better) Icons.Default.CheckCircle else Icons.Default.Info, null, tint = if (option2Better) SuccessGreen else WarningAmber, modifier = Modifier.size(24.dp)); Column(Modifier.weight(1f)) { Text(if (option2Better) "Option 2 is Recommended" else "Option 1 is Recommended", fontWeight = FontWeight.Bold, color = if (option2Better) Color(0xFF14532D) else Color(0xFF7C2D12), fontSize = 15.sp); Text(if (option2Better) "Option 2 provides ₹$benefit higher basic pay after DNI." else "Option 1 provides better immediate fixation benefit.", color = if (option2Better) Color(0xFF166534) else Color(0xFF9A3412), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp)) } } }
                Button(onClick = { val now = System.currentTimeMillis(); HistoryStore.add(context, CalculationHistory(now, now, officialName.trim(), currentLevel!!, currentPay!!, promotedLevel!!, promotionDate, dniDate, result.option1.finalFixedPay, result.option2.finalFixedPay)); history = HistoryStore.getAll(context) }, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue), modifier = Modifier.fillMaxWidth().height(50.dp)) { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Save Calculation to History", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(30.dp))
        }
        BannerAd(Modifier.fillMaxWidth().navigationBarsPadding())
    }
    if (showDatePicker) { val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate); DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton(onClick = { promotionDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold, color = PrimaryBlue) } }, dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state) } }
}

@Composable
private fun HistoryScreen(history: List<CalculationHistory>, onBack: () -> Unit, onDelete: (Long) -> Unit, onClear: () -> Unit, onOpen: (CalculationHistory) -> Unit) {
    Column(Modifier.fillMaxSize().background(BackgroundColor).statusBarsPadding()) { Row(Modifier.fillMaxWidth().background(SurfaceColor).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = PrimaryBlue) }; Text("Calculation History", Modifier.weight(1f), color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold); if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear All", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold) } }; HorizontalDivider(color = Color(0xFFE2E8F0)); if (history.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.HistoryToggleOff, null, tint = TextSecondary, modifier = Modifier.size(48.dp)); Text("No saved calculations yet.", color = TextSecondary, fontSize = 16.sp); Text("Calculations you save will appear here.", color = TextSecondary.copy(alpha = 0.7f), fontSize = 13.sp) } } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { history.forEach { entry -> Card(Modifier.fillMaxWidth().clickable { onOpen(entry) }, colors = CardDefaults.cardColors(SurfaceColor), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(entry.officialName.ifBlank { "Unnamed Official" }, fontWeight = FontWeight.Bold, color = PrimaryBlue, fontSize = 16.sp); Spacer(Modifier.height(4.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { Surface(color = PrimaryBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp)) { Text("Level ${entry.currentLevel} → ${entry.promotedLevel}", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }; Text("Pay: ${formatCurrency(entry.currentPay)}", color = TextSecondary, fontSize = 13.sp) }; Spacer(Modifier.height(6.dp)); Text("Saved: ${formatDateTime(entry.savedAt)}", color = TextSecondary.copy(alpha = 0.8f), fontSize = 12.sp) } ; IconButton(onClick = { onDelete(entry.id) }) { Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFDC2626)) } } } } }; Spacer(Modifier.height(20.dp)) } }
}

@Composable
private fun HistoryDetailDialog(entry: CalculationHistory, onClose: () -> Unit) { AlertDialog(onDismissRequest = onClose, title = { Text(if (entry.officialName.isBlank()) "Saved Calculation" else entry.officialName, fontWeight = FontWeight.Bold) }, text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Present Pay Level: Level ${entry.currentLevel}"); Text("Current Basic Pay: ${formatCurrency(entry.currentPay)}"); Text("Promoted Pay Level: Level ${entry.promotedLevel}"); Text("Date of Promotion: ${entry.promotionDate?.let { formatDate(it) } ?: "Not selected"}"); Text("Selected DNI: ${entry.dniDate?.let { formatDate(it) } ?: "Not selected"}"); HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Color(0xFFE2E8F0)); Text("Option 1 Final Pay: ${formatCurrency(entry.option1FinalPay)}", fontWeight = FontWeight.Bold, color = PrimaryBlue); Text("Option 2 Final Pay: ${formatCurrency(entry.option2FinalPay)}", fontWeight = FontWeight.Bold, color = PrimaryBlue) } }, confirmButton = { TextButton(onClick = onClose) { Text("Close", fontWeight = FontWeight.Bold, color = PrimaryBlue) } }) }

@Composable
private fun BannerAd(modifier: Modifier = Modifier) { val context = LocalContext.current; val adView = remember(context) { AdView(context).apply { adUnitId = TEST_BANNER_AD_UNIT_ID; val displayMetrics = context.resources.displayMetrics; val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt(); setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth)); loadAd(AdRequest.Builder().build()) } }; DisposableEffect(adView) { onDispose { adView.destroy() } }; AndroidView(modifier = modifier.wrapContentHeight(), factory = { adView }) }

@Composable
private fun SelectionCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) { Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(SurfaceColor), elevation = CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Box(Modifier.size(32.dp).background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = PrimaryBlue, modifier = Modifier.size(18.dp)) }; Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp) }; content() } } }

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onSelected: (String) -> Unit, enabled: Boolean = true, icon: ImageVector) { Box { OutlinedButton(onClick = { onExpandedChange(true) }, enabled = enabled && options.isNotEmpty(), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) { Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(label, fontSize = 11.sp, color = TextSecondary); Text(value, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium) }; Icon(Icons.Default.ArrowDropDown, null, tint = TextSecondary) }; DropdownMenu(expanded = expanded && enabled, onDismissRequest = { onExpandedChange(false) }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); onExpandedChange(false) }) } } } }

@Composable
private fun DateField(label: String, millis: Long?, onClick: () -> Unit, modifier: Modifier, icon: ImageVector) { Column(modifier) { Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = TextPrimary); Box(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp)) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(16.dp)); Text(millis?.let { formatDate(it) } ?: "Select date", color = if (millis != null) TextPrimary else TextSecondary, fontSize = 13.sp) } } } }

@Composable
private fun ResultCard(optionNumber: String, title: String, date: Long?, steps: List<Pair<String, Int>>, finalPay: Int, futureDni: Long?, futurePay: Int?, interimPay: Int? = null, isRecommended: Boolean = false) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(SurfaceColor), elevation = CardDefaults.cardElevation(if (isRecommended) 4.dp else 2.dp), border = if (isRecommended) BorderStroke(2.dp, SuccessGreen) else null) {
        Column(Modifier.padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Surface(color = PrimaryBlue, shape = RoundedCornerShape(6.dp)) { Text(optionNumber, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }; Text(title, fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp) }
                    date?.let { Text(if (optionNumber == "Option 1") "Date of Promotion: ${formatDate(it)}" else "Selected DNI: ${formatDate(it)}", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(top = 4.dp)) }
                }
                if (isRecommended) Surface(color = SuccessContainer, shape = RoundedCornerShape(8.dp)) { Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Default.Star, null, tint = SuccessGreen, modifier = Modifier.size(14.dp)); Text("Best Option", color = SuccessGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFE2E8F0))
            steps.forEachIndexed { index, (description, pay) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${index + 1}. $description", fontSize = 13.sp, color = TextPrimary)
                        val eventDate = when {
                            optionNumber == "Option 1" -> date
                            index == 0 -> date
                            index == 1 || index == 2 -> date
                            else -> date
                        }
                        eventDate?.let { Text("Date: ${formatDate(it)}", fontSize = 11.sp, color = TextSecondary) }
                    }
                    Spacer(Modifier.width(8.dp)); Text(formatCurrency(pay), fontSize = 13.sp, color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
            }
            Surface(Modifier.fillMaxWidth().padding(top = 14.dp), color = if (isRecommended) SuccessContainer.copy(alpha = 0.6f) else PrimaryBlue.copy(alpha = 0.06f), shape = RoundedCornerShape(14.dp)) {
                Column(Modifier.padding(14.dp)) {
                    interimPay?.let { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Interim Pay:", fontSize = 13.sp, color = TextSecondary); Text(formatCurrency(it), fontSize = 13.sp, color = TextSecondary, fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(4.dp)) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Final Fixed Pay:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary); Text(formatCurrency(finalPay), fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (isRecommended) SuccessGreen else PrimaryBlue) }
                    if (futureDni != null && futurePay != null) { HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFCBD5E1)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Icon(Icons.Default.Event, null, tint = TextSecondary, modifier = Modifier.size(14.dp)); Text("Next DNI (${formatDate(futureDni)}):", fontSize = 12.sp, color = TextSecondary) }; Text(formatCurrency(futurePay), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary) }
                    }
                }
            }
        }
    }
}

private fun formatCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(millis))
private fun formatDateTime(millis: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))