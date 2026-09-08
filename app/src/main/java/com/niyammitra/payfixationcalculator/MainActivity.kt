package com.niyammitra.payfixationcalculator

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

private val NiyamBlue = Color(0xFF1769AA)
private val NiyamBackground = Color(0xFFF7FAFC)
private val NiyamTextPrimary = Color(0xFF172B4D)
private val NiyamTextSecondary = Color(0xFF5B6B7A)
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BillingManager.initialize(this)
        MobileAds.initialize(this)
        HistoryInterstitialAd.load(this)
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
    var showAdFreeDialog by remember { mutableStateOf(false) }

    if (showHistory) {
        HistoryScreen(
            history = history,
            onBack = {
                val activity = context as? Activity
                if (activity != null) {
                    HistoryInterstitialAd.showOnHistoryBack(activity) { showHistory = false }
                } else {
                    showHistory = false
                }
            },
            onDelete = { id ->
                HistoryStore.delete(context, id)
                history = HistoryStore.getAll(context)
            },
            onClear = { showClearHistoryDialog = true },
            onOpen = { selectedHistory = it }
        )
        if (showClearHistoryDialog) {
            AlertDialog(
                onDismissRequest = { showClearHistoryDialog = false },
                title = { Text("Clear History?") },
                text = { Text("All saved calculations will be permanently removed from this device.") },
                confirmButton = {
                    TextButton(onClick = {
                        HistoryStore.clear(context)
                        history = emptyList()
                        showClearHistoryDialog = false
                    }) { Text("Clear", color = Color(0xFFD64545), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
            )
        }
        selectedHistory?.let { entry -> HistoryDetailDialog(entry) { selectedHistory = null } }
        return
    }

    val payStages = currentLevel?.let { PayMatrixData.getPayStages(it) } ?: emptyList()
    val dniOptions = remember(promotionDate) { promotionDate?.let { getPayFixationDniOptions(it) } ?: emptyList() }
    LaunchedEffect(promotionDate) { dniDate = dniOptions.firstOrNull() }

    val result = if (currentLevel != null && currentPay != null && promotedLevel != null) {
        calculatePayFixation(currentLevel!!, currentPay!!, promotedLevel!!, promotionDate, dniDate)
    } else null

    Column(Modifier.fillMaxSize().background(NiyamBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.rkcapps_logo), "RKCApps", Modifier.size(width = 72.dp, height = 52.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("NiyamMitra", color = NiyamBlue, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("Pay Fixation Calculator", color = NiyamTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = {
                history = HistoryStore.getAll(context)
                val activity = context as? Activity
                if (activity != null) {
                    HistoryInterstitialAd.showIfDue(activity) { showHistory = true }
                } else {
                    showHistory = true
                }
            }) { Text("History", color = NiyamBlue, fontWeight = FontWeight.Bold) }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!BillingManager.isPremium) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(Color.White)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Go Ad-Free", color = NiyamBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Remove all ads permanently for ${BillingManager.getPrice()} one time.", color = NiyamTextSecondary, fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { showAdFreeDialog = true }) {
                            Text("Remove Ads", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            SelectionCard("Current Status") {
                OutlinedTextField(value = officialName, onValueChange = { officialName = it }, label = { Text("Name of Official (for History)") }, placeholder = { Text("Enter name, if required") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DropdownField("Present Pay Level", currentLevel?.let { "Level $it" } ?: "Select your Present Pay Level", PayMatrixData.levels, levelMenu, { levelMenu = it }, { level -> currentLevel = level; currentPay = null; payMenu = false })
                DropdownField("Current Basic Pay", currentPay?.let { formatCurrency(it) } ?: "Select your Current Basic Pay", payStages.map { formatCurrency(it) }, payMenu, { payMenu = it }, { value -> currentPay = payStages.firstOrNull { formatCurrency(it) == value } }, currentLevel != null)
            }

            SelectionCard("Promotion Details") {
                DropdownField("Promoted Pay Level", promotedLevel?.let { "Level $it" } ?: "Select your Promoted Pay Level", PayMatrixData.levels, promotedMenu, { promotedMenu = it }, { level -> promotedLevel = level })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DateField("Date of Promotion", promotionDate, { showDatePicker = true }, Modifier.weight(1f))
                    Column(Modifier.weight(1f)) {
                        Text("Date of Next Increment", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Box(Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { if (dniOptions.isNotEmpty()) dniMenu = true }, enabled = dniOptions.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                                Text(dniDate?.let { formatDate(it) } ?: "Select", modifier = Modifier.weight(1f)); Text("▼")
                            }
                            DropdownMenu(expanded = dniMenu, onDismissRequest = { dniMenu = false }) {
                                dniOptions.forEach { option -> DropdownMenuItem(text = { Text(formatDate(option)) }, onClick = { dniDate = option; dniMenu = false }) }
                            }
                        }
                    }
                }
            }

            if (result != null) {
                Text("Fixation Illustrations", color = NiyamTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                ResultCard("Option 1: Fixation from Date of Promotion", promotionDate, listOf(
                    "Pay in lower Level ($currentLevel)" to result.option1.lowerLevelPay,
                    "Add one increment in lower Level ($currentLevel)" to result.option1.payWithIncrement,
                    "Placement in promoted Level ($promotedLevel)" to result.option1.finalFixedPay
                ), result.option1.finalFixedPay, result.option1.nextDni, result.option1.payAfterNextDni)
                ResultCard("Option 2: Fixation from Date of Next Increment", dniDate, listOf(
                    "Pay from date of promotion until DNI (placed at next higher cell in Level $promotedLevel)" to result.option2.payUntilDni,
                    "On DNI, annual increment in lower Level ($currentLevel)" to result.option2.payWithAnnualIncrement,
                    "On DNI, one increment on account of promotion in Level $currentLevel" to result.option2.payWithPromotionIncrement,
                    "Final placement in promoted Level ($promotedLevel)" to result.option2.finalFixedPay
                ), result.option2.finalFixedPay, result.option2.nextDni, result.option2.payAfterNextDni, result.option2.payUntilDni)

                val benefit = result.option2.finalFixedPay - result.option1.finalFixedPay
                if (benefit != 0) Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (benefit > 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
                    Text(if (benefit > 0) "Option 2 is beneficial. It results in ₹$benefit higher basic pay after DNI." else "Option 1 appears more beneficial in this specific case.", Modifier.padding(16.dp), color = if (benefit > 0) Color(0xFF2E7D32) else Color(0xFFE65100))
                }
                Button(onClick = {
                    val now = System.currentTimeMillis()
                    HistoryStore.add(context, CalculationHistory(now, now, officialName.trim(), currentLevel!!, currentPay!!, promotedLevel!!, promotionDate, dniDate, result.option1.finalFixedPay, result.option2.finalFixedPay))
                    history = HistoryStore.getAll(context)
                }, modifier = Modifier.fillMaxWidth()) { Text("Save Calculation to History") }
            }
            Spacer(Modifier.height(30.dp))
        }
        if (!BillingManager.isPremium) {
            BannerAd(Modifier.fillMaxWidth().navigationBarsPadding())
        }
    }

    if (showDatePicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { promotionDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) }
        }) { DatePicker(state) }
    }

    if (showAdFreeDialog && !BillingManager.isPremium) {
        AdFreePurchaseDialog(context = context, onClose = { showAdFreeDialog = false })
    }
}

@Composable
private fun AdFreePurchaseDialog(context: android.content.Context, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("NiyamMitra Ad-Free") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Remove all advertisements from Pay Fixation Calculator permanently.")
                Text("Lifetime ad-free access", fontWeight = FontWeight.Bold, color = NiyamBlue)
                Text("One-time purchase: ${BillingManager.getPrice()}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("No subscription. Your purchase can be restored on this Google Play account.", color = NiyamTextSecondary, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val activity = context as? Activity
                if (activity == null || BillingManager.launchPurchase(activity) == null) {
                    Toast.makeText(context, "Google Play purchase is not ready yet. Please try again in a moment.", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Buy Ad-Free") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { BillingManager.restorePurchases(context) }) { Text("Restore Purchase") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun HistoryScreen(history: List<CalculationHistory>, onBack: () -> Unit, onDelete: (Long) -> Unit, onClear: () -> Unit, onOpen: (CalculationHistory) -> Unit) {
    Column(Modifier.fillMaxSize().background(NiyamBackground).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back", color = NiyamBlue, fontWeight = FontWeight.Bold) }
            Text("Calculation History", Modifier.weight(1f), color = NiyamTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear", color = Color(0xFFD64545)) }
        }
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No saved calculations yet.", color = NiyamTextSecondary, fontSize = 16.sp) }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                history.forEach { entry ->
                    Card(Modifier.fillMaxWidth().clickable { onOpen(entry) }, colors = CardDefaults.cardColors(Color.White), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.officialName.ifBlank { "Unnamed Official" }, fontWeight = FontWeight.Bold, color = NiyamBlue, fontSize = 16.sp)
                                    Text("Level ${entry.currentLevel} → Level ${entry.promotedLevel}", color = NiyamTextPrimary, fontSize = 14.sp)
                                    Text("Current Pay: ${formatCurrency(entry.currentPay)}", color = NiyamTextPrimary, fontSize = 14.sp)
                                    Text("Saved: ${formatDateTime(entry.savedAt)}", color = NiyamTextSecondary, fontSize = 12.sp)
                                }
                                TextButton(onClick = { onDelete(entry.id) }) { Text("Delete", color = Color(0xFFD64545)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun HistoryDetailDialog(entry: CalculationHistory, onClose: () -> Unit) {
    val result = remember(entry) {
        calculatePayFixation(entry.currentLevel, entry.currentPay, entry.promotedLevel, entry.promotionDate, entry.dniDate)
    }
    val benefit = result.option2.finalFixedPay - result.option1.finalFixedPay

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (entry.officialName.isBlank()) "Saved Calculation" else entry.officialName) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Current Status", color = NiyamBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Present Pay Level: Level ${entry.currentLevel}")
                Text("Current Basic Pay: ${formatCurrency(entry.currentPay)}")
                Text("Promotion Details", color = NiyamBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 4.dp))
                Text("Promoted Pay Level: Level ${entry.promotedLevel}")
                Text("Date of Promotion: ${entry.promotionDate?.let { formatDate(it) } ?: "Not selected"}")
                Text("Selected DNI: ${entry.dniDate?.let { formatDate(it) } ?: "Not selected"}")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Fixation Illustrations", color = NiyamTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                ResultCard("Option 1: Fixation from Date of Promotion", entry.promotionDate, listOf(
                    "Pay in lower Level (${entry.currentLevel})" to result.option1.lowerLevelPay,
                    "Add one increment in lower Level (${entry.currentLevel})" to result.option1.payWithIncrement,
                    "Placement in promoted Level (${entry.promotedLevel})" to result.option1.finalFixedPay
                ), result.option1.finalFixedPay, result.option1.nextDni, result.option1.payAfterNextDni)
                ResultCard("Option 2: Fixation from Date of Next Increment", entry.dniDate, listOf(
                    "Pay from date of promotion until DNI (placed at next higher cell in Level ${entry.promotedLevel})" to result.option2.payUntilDni,
                    "On DNI, annual increment in lower Level (${entry.currentLevel})" to result.option2.payWithAnnualIncrement,
                    "On DNI, one increment on account of promotion in Level ${entry.currentLevel}" to result.option2.payWithPromotionIncrement,
                    "Final placement in promoted Level (${entry.promotedLevel})" to result.option2.finalFixedPay
                ), result.option2.finalFixedPay, result.option2.nextDni, result.option2.payAfterNextDni, result.option2.payUntilDni)
                if (benefit != 0) {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(if (benefit > 0) Color(0xFFE8F5E9) else Color(0xFFFFF3E0))) {
                        Text(if (benefit > 0) "Option 2 is beneficial. It results in ₹$benefit higher basic pay after DNI." else "Option 1 appears more beneficial in this specific case.", Modifier.padding(16.dp), color = if (benefit > 0) Color(0xFF2E7D32) else Color(0xFFE65100))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (BillingManager.isPremium) return

    val adView = remember(context) { AdView(context).apply {
        adUnitId = TEST_BANNER_AD_UNIT_ID
        val displayMetrics = context.resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
        loadAd(AdRequest.Builder().build())
    } }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    AndroidView(modifier = modifier.wrapContentHeight(), factory = { adView })
}

@Composable
private fun SelectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = NiyamBlue); content() }
    }
}

@Composable
private fun DropdownField(label: String, value: String, options: List<String>, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onSelected: (String) -> Unit, enabled: Boolean = true) {
    Box {
        OutlinedButton(onClick = { onExpandedChange(true) }, enabled = enabled && options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(label, fontSize = 12.sp, color = NiyamTextSecondary); Text(value, color = NiyamTextPrimary, fontSize = 15.sp) }
            Text("▼")
        }
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { onExpandedChange(false) }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); onExpandedChange(false) }) }
        }
    }
}

@Composable
private fun DateField(label: String, millis: Long?, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp)) {
            Text(millis?.let { formatDate(it) } ?: "Select", color = if (millis != null) NiyamTextPrimary else Color.Gray, fontSize = 14.sp)
        }
    }
}

@Composable
private fun ResultCard(title: String, date: Long?, steps: List<Pair<String, Int>>, finalPay: Int, futureDni: Long?, futurePay: Int?, interimPay: Int? = null) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = NiyamBlue, fontSize = 16.sp)
            date?.let { Text(if (title.startsWith("Option 1")) "Date of Promotion: ${formatDate(it)}" else "Selected DNI: ${formatDate(it)}", fontSize = 12.sp, color = Color.Gray) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            steps.forEach { (description, pay) ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(description, fontSize = 13.sp, color = NiyamTextPrimary)
                    date?.let { Text("Date: ${formatDate(it)}", fontSize = 11.sp, color = NiyamTextSecondary, modifier = Modifier.padding(top = 2.dp)) }
                    Text("Pay: ${formatCurrency(pay)}", fontSize = 13.sp, color = NiyamBlue, fontWeight = FontWeight.Bold)
                }
            }
            Surface(Modifier.padding(top = 10.dp), color = NiyamBlue.copy(alpha = .05f), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    interimPay?.let { Text("Interim Pay: ${formatCurrency(it)}", fontSize = 13.sp, color = NiyamTextSecondary) }
                    Text("Final Fixed Pay: ${formatCurrency(finalPay)}", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = NiyamBlue)
                    if (futureDni != null && futurePay != null) {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("Next DNI: ${formatDate(futureDni)}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = NiyamTextPrimary)
                        Text("Pay thereon: ${formatCurrency(futurePay)}", fontSize = 13.sp, color = NiyamTextSecondary)
                    }
                }
            }
        }
    }
}

private fun formatCurrency(value: Int): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).format(value)
private fun formatDate(millis: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(Date(millis))
private fun formatDateTime(millis: Long): String = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(millis))
