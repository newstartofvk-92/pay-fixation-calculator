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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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

/** V2 entry screen for selecting the pay-fixation workflow. */
@Composable
fun V2AppScreen() {
    var selectedFixationType by remember { mutableStateOf<FixationType?>(null) }
    var selectedSeventhCpcType by remember { mutableStateOf<SeventhCpcFixationType?>(null) }
    var showCalculator by remember { mutableStateOf(false) }

    if (showCalculator) {
        BackHandler { showCalculator = false }
        PayFixationCalculatorScreen()
        return
    }

    if (selectedFixationType == FixationType.SEVENTH_CPC && selectedSeventhCpcType == null) {
        SeventhCpcFixationSelectionScreen(
            onBack = { selectedFixationType = null },
            onSelected = { type ->
                selectedSeventhCpcType = type
                if (type == SeventhCpcFixationType.PROMOTION) showCalculator = true
            }
        )
        return
    }

    HomeFixationSelectionScreen { type ->
        selectedFixationType = type
        if (type != FixationType.SEVENTH_CPC) selectedSeventhCpcType = null
    }
}

@Composable
private fun HomeFixationSelectionScreen(onSelected: (FixationType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(NiyamBackground),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HomeHeader()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Select Fixation Type", color = NiyamTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Choose the pay-revision or pay-fixation workflow you want to work out.", color = NiyamTextSecondary, fontSize = 14.sp)
            FixationType.values().forEach { type ->
                FixationTypeCard(
                    type = type,
                    enabled = type == FixationType.SEVENTH_CPC,
                    onClick = { onSelected(type) }
                )
            }
        }
    }
}

@Composable
private fun SeventhCpcFixationSelectionScreen(
    onBack: () -> Unit,
    onSelected: (SeventhCpcFixationType) -> Unit
) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().background(NiyamBackground)) {
        HomeHeader()
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("7th CPC Pay Fixation", color = NiyamTextPrimary, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("Select the reason for fixation.", color = NiyamTextSecondary, fontSize = 14.sp)
            SeventhCpcFixationType.values().forEach { type ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelected(type) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text(type.title, color = NiyamBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(6.dp))
                        Text(type.description, color = NiyamTextSecondary, fontSize = 13.sp)
                    }
                }
            }
            Text(
                "‹  Back",
                modifier = Modifier.clickable(onClick = onBack).padding(vertical = 8.dp),
                color = NiyamBlue,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FixationTypeCard(type: FixationType, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color.White else Color(0xFFF0F2F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (enabled) 2.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(46.dp).background(
                    if (enabled) NiyamBlue.copy(alpha = 0.10f) else Color(0xFFE1E4E8),
                    RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    when (type) {
                        FixationType.FIFTH_TO_SIXTH -> "5→6"
                        FixationType.SIXTH_TO_SEVENTH -> "6→7"
                        FixationType.SEVENTH_CPC -> "7th"
                    },
                    color = if (enabled) NiyamBlue else NiyamTextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(type.title, color = if (enabled) NiyamTextPrimary else NiyamTextSecondary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(if (enabled) type.description else "Coming soon", color = NiyamTextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HomeHeader() {
    Surface(modifier = Modifier.fillMaxWidth(), color = NiyamHeaderBlue, shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(56.dp).background(Color.White, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("NM", color = NiyamHeaderBlue, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Pay Fixation Calculator", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text("NiyamMitra", color = Color.White.copy(alpha = 0.88f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
