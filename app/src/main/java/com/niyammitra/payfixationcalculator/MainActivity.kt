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

// Main NiyamMitra UI colors used throughout the calculator screen.
private val NiyamBlue = Color(0xFF1769AA)
private val NiyamHeaderBlue = Color(0xFF1976B8)
private val NiyamBackground = Color(0xFFF7FAFC)
private val NiyamTextPrimary = Color(0xFF172B4D)
private val NiyamTextSecondary = Color(0xFF5B6B7A)

// Production banner ID created for the NiyamMitra Pay Fixation Calculator.
private const val BANNER_AD_UNIT_ID = "ca-app-pub-1512519890788753/1856516842"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Google Play Billing first so the UI can immediately know
        // whether this user has lifetime ad-free entitlement.
        BillingManager.initialize(this)

        // Initialize the Google Mobile Ads SDK and preload the History interstitial.
        MobileAds.initialize(this)
        HistoryInterstitialAd.load(this)

        setContent { PayFixationCalculatorTheme { PayFixationCalculatorScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFixationCalculatorScreen() {
    val context = LocalContext.current

    // Screen/navigation state. The calculator itself remains on this screen;
    // History is shown as an alternate screen state rather than a new Activity.
    var showHistory by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(HistoryStore.getAll(context)) }

    // User inputs used by the existing pay-fixation calculation engine.
    var officialName by remember { mutableStateOf("") }
    var employeeCategory by remember { mutableStateOf(EmployeeCategory.ORDINARY) }
    var currentLevel by remember { mutableStateOf<String?>(null) }
    var currentPay by remember { mutableStateOf<Int?>(null) }
    var promotedLevel by remember { mutableStateOf<String?>(null) }
    var promotionDate by remember { mutableStateOf<Long?>(null) }
    var dniDate by remember { mutableStateOf<Long?>(null) }

    // Dropdown/dialog state for the input controls.
    var categoryMenu by remember { mutableStateOf(false) }
    var levelMenu by remember { mutableStateOf(false) }
    var payMenu by remember { mutableStateOf(false) }
    var promotedMenu by remember { mutableStateOf(false) }
    var dniMenu by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var selectedHistory by remember { mutableStateOf<CalculationHistory?>(null) }
    var showAdFreeDialog by remember { mutableStateOf(false) }

    // Controls visibility of the About dialog without affecting calculator state.
    var showAboutDialog by remember { mutableStateOf(false) }

    if (showHistory) {
        HistoryScreen(
            history = history,
            onBack = {
                // Going back from History is an explicit ad opportunity for free users.
                // Premium users are automatically allowed through by HistoryInterstitialAd.
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
                        // Clear only local history; this does not affect the calculator inputs.
                        HistoryStore.clear(context)
                        history = emptyList()
                        showClearHistoryDialog = false
                    }) { Text("Clear", color = Color(0xFFD64545), fontWeight = FontWeight.Bold) }
                },
                dismissButton = { TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") } }
            )
        }

        // Tapping a saved record opens its complete reconstructed calculation.
        selectedHistory?.let { entry -> HistoryDetailDialog(entry) { selectedHistory = null } }
        return
    }

    // Select the same data-provider used by the calculation engine so the input
    // levels and pay cells always match the chosen employee category.
    val matrix = PayMatrixSelection.forCategory(employeeCategory)

    // Pay stages are derived from the selected present level. Changing the level
    // also clears the previously selected basic pay in the input section.
    val payStages = currentLevel?.let { matrix.getPayStages(it) } ?: emptyList()

    // The two selectable DNI dates are generated by the same calculation utility
    // used by the existing app; the first one is selected automatically.
    val dniOptions = remember(promotionDate) { promotionDate?.let { getPayFixationDniOptions(it) } ?: emptyList() }
    LaunchedEffect(promotionDate) { dniDate = dniOptions.firstOrNull() }

    // IMPORTANT: all fixation formulas remain in calculatePayFixation().
    // The selected category changes only the pay-matrix provider.
    val result = if (currentLevel != null && currentPay != null && promotedLevel != null) {
        calculatePayFixation(currentLevel!!, currentPay!!, promotedLevel!!, promotionDate, dniDate, employeeCategory)
    } else null

    Column(Modifier.fillMaxSize().background(NiyamBackground)) {
        // A dedicated blue header gives the home screen a clear visual identity and
        // replaces the previous photo-like logo row with a compact, professional mark.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = NiyamHeaderBlue,
            shadowElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The simple NM mark remains crisp at every screen density and avoids
                // the clutter caused by the photographic RKCApps logo in the header.
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NM", color = NiyamHeaderBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text("NiyamMitra", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Pay Fixation Calculator", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                // Compact action controls keep History and About visible without competing
                // with the app title or consuming excessive horizontal space.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            // Refresh history before opening it so the newest save/delete state is shown.
                            history = HistoryStore.getAll(context)
                            val activity = context as? Activity
                            if (activity != null) {
                                // Free users may see the History-entry interstitial; premium users bypass it.
                                HistoryInterstitialAd.showIfDue(activity) { showHistory = true }
                            } else {
                                showHistory = true
                            }
                        }
                    ) {
                        Text("◷", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("History", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .height(34.dp)
                            .width(1.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            // About opens the app information/legal dialog and does not alter calculation state.
                            showAboutDialog = true
                        }
                    ) {
                        Text("ⓘ", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        Text("About", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            // This card is shown only to free users. Purchasing lifetime ad removal
            // makes this card disappear because BillingManager.isPremium becomes true.
            if (!BillingManager.isPremium) {
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(Color(0xFFEAF5FC))
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Go Ad-Free", color = NiyamBlue, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Remove all ads permanently for ${BillingManager.getPrice()} one time.", color = NiyamTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { showAdFreeDialog = true },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NiyamBlue),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Text("Remove Ads", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // The employee category is selected before pay level because it determines
            // which pay matrix the rest of the calculator will display and use.
            SelectionCard("Employee Category") {
                DropdownField(
                    label = "Employee Category",
                    value = employeeCategory.displayName,
                    options = EmployeeCategory.values().map { it.displayName },
                    expanded = categoryMenu,
                    onExpandedChange = { categoryMenu = it },
                    onSelected = { selected ->
                        // Switching category resets level/pay selections because the two
                        // matrices contain different levels and pay cells.
                        employeeCategory = EmployeeCategory.values().first { it.displayName == selected }
                        currentLevel = null
                        currentPay = null
                        promotedLevel = null
                    }
                )
            }

            // Current Status collects the employee's existing pay-level information.
            SelectionCard("Current Status") {
                OutlinedTextField(value = officialName, onValueChange = { officialName = it }, label = { Text("Name of Official (for History)") }, placeholder = { Text("Enter name, if required") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                DropdownField("Present Pay Level", currentLevel?.let { "Level $it" } ?: "Select your Present Pay Level", matrix.levels, levelMenu, { levelMenu = it }, { level -> currentLevel = level; currentPay = null; payMenu = false })
                DropdownField("Current Basic Pay", currentPay?.let { formatCurrency(it) } ?: "Select your Current Basic Pay", payStages.map { formatCurrency(it) }, payMenu, { payMenu = it }, { value -> currentPay = payStages.firstOrNull { formatCurrency(it) == value } }, currentLevel != null)
            }

            // Promotion Details collects the promoted level and the relevant dates.
            SelectionCard("Promotion Details") {
                DropdownField("Promoted Pay Level", promotedLevel?.let { "Level $it" } ?: "Select your Promoted Pay Level", matrix.levels, promotedMenu, { promotedMenu = it }, { level -> promotedLevel = level })
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
                // The result cards are display-only. The calculation itself remains
                // in PayFixationUtils.kt so changing the UI cannot alter the formulas.
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

                // Compare the actual pay available at the selected DNI, not just the
                // eventual final-pay values. This matters when the selected DNI is in
                // January: Option 1 can receive its increment months before Option 2.
                val selectedDni = dniDate
                val option1NextDni = result.option1.nextDni
                val option1PayAtSelectedDni = if (selectedDni != null && option1NextDni != null && option1NextDni <= selectedDni) {
                    result.option1.payAfterNextDni ?: result.option1.finalFixedPay
                } else {
                    result.option1.finalFixedPay
                }
                val option2PayAtSelectedDni = result.option2.finalFixedPay
                val dniPayDifference = option2PayAtSelectedDni - option1PayAtSelectedDni
                val preDniDifference = result.option2.payUntilDni - result.option1.finalFixedPay

                // If pay differs before the selected DNI, that difference is a real
                // financial advantage for the period leading up to the DNI. Otherwise,
                // compare the pay actually received on the selected DNI itself.
                val recommendationText = when {
                    preDniDifference > 0 -> "Recommended: Option 2. It gives ₹${preDniDifference} higher basic pay from the date of promotion until the selected DNI."
                    preDniDifference < 0 -> "Recommended: Option 1. It gives ₹${-preDniDifference} higher basic pay from the date of promotion until the selected DNI."
                    dniPayDifference > 0 -> "Recommended: Option 2. At the selected DNI, it gives ₹${dniPayDifference} higher basic pay than Option 1."
                    dniPayDifference < 0 -> "Recommended: Option 1. At the selected DNI, it gives ₹${-dniPayDifference} higher basic pay than Option 2."
                    else -> "Recommended: Option 1. Both options have the same basic pay at the selected DNI; Option 1 provides fixation from the date of promotion."
                }
                val recommendationColor = Color(0xFFE8F5E9)
                val recommendationTextColor = Color(0xFF2E7D32)

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(recommendationColor)) {
                    Text(recommendationText, Modifier.padding(16.dp), color = recommendationTextColor, fontWeight = FontWeight.Bold)
                }

                // Save stores the selected matrix category together with the calculation
                // so History can reconstruct the result using the same matrix later.
                Button(onClick = {
                    val now = System.currentTimeMillis()
                    HistoryStore.add(context, CalculationHistory(
                        id = now,
                        savedAt = now,
                        officialName = officialName.trim(),
                        employeeCategory = employeeCategory,
                        currentLevel = currentLevel!!,
                        currentPay = currentPay!!,
                        promotedLevel = promotedLevel!!,
                        promotionDate = promotionDate,
                        dniDate = dniDate,
                        option1FinalPay = result.option1.finalFixedPay,
                        option2FinalPay = result.option2.finalFixedPay
                    ))
                    history = HistoryStore.getAll(context)
                }, modifier = Modifier.fillMaxWidth()) { Text("Save Calculation to History") }
            }

            // This disclaimer is intentionally visible on the main calculator screen
            // so users see that results are assistive/reference calculations before relying
            // on them for any official service or financial purpose.
            PayFixationDisclaimer()

            Spacer(Modifier.height(30.dp))
        }

        // Banner ads are rendered only for free users. Premium users do not create
        // the AdView at all, which also prevents unnecessary ad requests.
        if (!BillingManager.isPremium) {
            BannerAd(Modifier.fillMaxWidth().navigationBarsPadding())
        }
    }

    if (showDatePicker) {
        // The selected promotion date is fed back into the DNI-option calculation.
        val state = rememberDatePickerState(initialSelectedDateMillis = promotionDate)
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = {
            TextButton(onClick = { promotionDate = state.selectedDateMillis; showDatePicker = false }) { Text("Confirm", fontWeight = FontWeight.Bold) }
        }) { DatePicker(state) }
    }

    // Shows app information and the privacy-policy link without changing calculation or billing behavior.
    if (showAboutDialog) {
        AboutDialog(onClose = { showAboutDialog = false })
    }

    // The purchase dialog is hidden after the entitlement is granted.
    if (showAdFreeDialog && !BillingManager.isPremium) {
        AdFreePurchaseDialog(context = context, onClose = { showAdFreeDialog = false })
    }
}

/**
 * Presents the legal-use disclaimer for the calculator without changing any
 * calculation formulas. It clarifies that results are indicative and should be
 * verified against applicable rules/orders and by the competent authority.
 */
@Composable
private fun PayFixationDisclaimer() {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(Color(0xFFFFF8E1))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Important Disclaimer", color = NiyamTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "This application is intended solely as an assistive and reference tool for working out indicative pay-fixation calculations based on the information and rules provided by the user. The results are not an official determination of pay, entitlement, or financial benefit and should not be treated as a substitute for applicable Government rules, regulations, orders, clarifications, or decisions of the competent authority.",
                color = NiyamTextPrimary,
                fontSize = 12.sp
            )
            Text(
                "Users should verify the results with the applicable rules/orders and the competent administrative or accounts authority before using them for any official, service, or financial purpose. The developer does not assume responsibility for any decision, claim, loss, liability, or consequence arising from reliance solely on the calculations provided by this application.",
                color = NiyamTextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

/** Dialog explaining and launching the one-time lifetime ad-removal purchase. */
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
                // BillingManager opens the official Google Play purchase flow.
                // If BillingClient is not ready yet, do not block the user; show a retry message.
                val activity = context as? Activity
                if (activity == null || BillingManager.launchPurchase(activity) == null) {
                    Toast.makeText(context, "Google Play purchase is not ready yet. Please try again in a moment.", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Buy Ad-Free") }
        },
        dismissButton = {
            Row {
                // Restore checks Google Play ownership again rather than trusting only local state.
                TextButton(onClick = { BillingManager.restorePurchases(context) }) { Text("Restore Purchase") }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        }
    )
}

/** Displays the locally saved calculations and provides delete/open actions. */
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
                                    Text(entry.employeeCategory.displayName, color = NiyamTextSecondary, fontSize = 12.sp)
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

/**
 * Reconstructs the complete saved calculation using the same calculation engine
 * and the same pay matrix category that was selected when it was saved.
 */
@Composable
private fun HistoryDetailDialog(entry: CalculationHistory, onClose: () -> Unit) {
    val result = remember(entry) {
        calculatePayFixation(entry.currentLevel, entry.currentPay, entry.promotedLevel, entry.promotionDate, entry.dniDate, entry.employeeCategory)
    }

    // Use the same date-aware recommendation as the live calculator so a saved
    // January-DNI calculation cannot display a different recommendation in History.
    val selectedDni = entry.dniDate
    val option1NextDni = result.option1.nextDni
    val option1PayAtSelectedDni = if (selectedDni != null && option1NextDni != null && option1NextDni <= selectedDni) {
        result.option1.payAfterNextDni ?: result.option1.finalFixedPay
    } else {
        result.option1.finalFixedPay
    }
    val option2PayAtSelectedDni = result.option2.finalFixedPay
    val dniPayDifference = option2PayAtSelectedDni - option1PayAtSelectedDni
    val preDniDifference = result.option2.payUntilDni - result.option1.finalFixedPay
    val recommendationText = when {
        preDniDifference > 0 -> "Recommended: Option 2. It gives ₹${preDniDifference} higher basic pay from the date of promotion until the selected DNI."
        preDniDifference < 0 -> "Recommended: Option 1. It gives ₹${-preDniDifference} higher basic pay from the date of promotion until the selected DNI."
        dniPayDifference > 0 -> "Recommended: Option 2. At the selected DNI, it gives ₹${dniPayDifference} higher basic pay than Option 1."
        dniPayDifference < 0 -> "Recommended: Option 1. At the selected DNI, it gives ₹${-dniPayDifference} higher basic pay than Option 2."
        else -> "Recommended: Option 1. Both options have the same basic pay at the selected DNI; Option 1 provides fixation from the date of promotion."
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (entry.officialName.isBlank()) "Saved Calculation" else entry.officialName) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Employee Category", color = NiyamBlue, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(entry.employeeCategory.displayName)
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
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFFE8F5E9))) {
                    Text(recommendationText, Modifier.padding(16.dp), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
    )
}

/** Renders the anchored adaptive banner used by free users. */
@Composable
private fun BannerAd(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (BillingManager.isPremium) return

    // Create one AdView for this composition and clean it up when the composable leaves it.
    val adView = remember(context) { AdView(context).apply {
        adUnitId = BANNER_AD_UNIT_ID
        val displayMetrics = context.resources.displayMetrics
        val adWidth = (displayMetrics.widthPixels / displayMetrics.density).toInt()
        setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidth))
        loadAd(AdRequest.Builder().build())
    } }
    DisposableEffect(adView) { onDispose { adView.destroy() } }
    AndroidView(modifier = modifier.wrapContentHeight(), factory = { adView })
}

/** Groups related input controls into a consistent white card. */
@Composable
private fun SelectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { Text(title, fontWeight = FontWeight.Bold, color = NiyamBlue); content() }
    }
}

/** Generic dropdown used for category, pay level, basic pay, and promoted level selections. */
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

/** Displays a date field that opens the Material date picker when tapped. */
@Composable
private fun DateField(label: String, millis: Long?, onClick: () -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).border(1.dp, Color.LightGray, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp)) {
            Text(millis?.let { formatDate(it) } ?: "Select", color = if (millis != null) NiyamTextPrimary else Color.Gray, fontSize = 14.sp)
        }
    }
}

/**
 * Displays one fixation option and its event-by-event pay values.
 * The date is intentionally shown with each event to retain the detailed
 * illustration format used by the earlier NiyamMitra implementation.
 */
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
