package com.niyammitra.payfixationcalculator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Presents the app identity, purpose, key features, version and legal links
 * without changing any calculation or billing behaviour.
 */
@Composable
fun AboutDialog(onClose: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("About NiyamMitra", fontWeight = FontWeight.Bold)
                Text("Pay Fixation Calculator", style = MaterialTheme.typography.labelLarge)
            }
        },
        text = {
            // Keep the full About content accessible on smaller phones by making
            // the dialog body vertically scrollable instead of allowing its lower
            // content, including the version and Privacy Policy button, to be clipped.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("RKCApps", fontWeight = FontWeight.Bold)
                Text("Apps • Tools • Solutions", style = MaterialTheme.typography.bodySmall)

                Text("About the App", fontWeight = FontWeight.Bold)
                Text(
                    "NiyamMitra Pay Fixation Calculator is designed as an assistive reference tool " +
                        "for working out indicative pay-fixation calculations based on the information " +
                        "and rules provided by the user."
                )

                Text("Key Features", fontWeight = FontWeight.Bold)
                Text(
                    "• Pay fixation illustrations using the available pay matrices\n" +
                        "• Comparison of the two fixation options\n" +
                        "• Local calculation history\n" +
                        "• Optional lifetime ad-free access"
                )

                Text("Important", fontWeight = FontWeight.Bold)
                Text(
                    "The results are indicative and are not an official determination of pay, " +
                        "entitlement or financial benefit. Users should verify calculations against " +
                        "applicable rules/orders and with the competent authority before official use."
                )

                // Keep the About screen synchronized with the Gradle app version so
                // the version shown to users matches the release being tested.
                Text("Version 1.1", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                // Opens the already-published privacy policy in the user's default browser.
                Button(onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://newstartofvk-92.github.io/pay-fixation-calculator/")
                        )
                    )
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Privacy Policy")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text("Close") }
        }
    )
}
