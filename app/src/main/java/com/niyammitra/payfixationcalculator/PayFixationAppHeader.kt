package com.niyammitra.payfixationcalculator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val HeaderBlue = Color(0xFF1976B8)

/** Shared top bar for the app's full-screen destinations. */
@Composable
fun PayFixationAppHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    onHome: (() -> Unit)? = null,
    branded: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = HeaderBlue, shadowElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
            if (branded) {
                Box(
                    Modifier.size(44.dp).background(Color.White, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("NM", color = HeaderBlue, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(Modifier.weight(1f).padding(start = if (onBack == null && !branded) 4.dp else 0.dp)) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                subtitle?.let {
                    Text(it, color = Color.White.copy(alpha = .88f), fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            onHome?.let { TextButton(onClick = it) { Text("Home", color = Color.White, fontWeight = FontWeight.Bold) } }
            actions()
        }
    }
}

@Composable
fun PayFixationHeaderIconAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick).padding(horizontal = 3.dp, vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(24.dp), tint = Color.White)
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
