package com.niyammitra.payfixationcalculator

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportPayJourneyHeaderAction(
    snapshot: CompleteJourneySnapshot?,
    title: String = "Complete Pay Fixation Journey Report"
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && snapshot != null) {
            try {
                val record = CpcHistoryRecord(
                    uniqueId = "live_export_${System.currentTimeMillis()}",
                    workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
                    title = title,
                    savedAtMillis = System.currentTimeMillis(),
                    startingCpc = snapshot.startingCpc,
                    currentStage = CpcHistoryStage.SEVENTH,
                    payload = CompleteJourneyPayload(snapshot)
                )
                val report = PayJourneyReportBuilder.build(record) ?: throw IllegalStateException("Could not generate report payload.")
                context.contentResolver.openOutputStream(uri)?.use { PayJourneyReportPdf.write(it, report) }
                    ?: throw IOException("Could not open the selected file.")
                Toast.makeText(context, "Pay journey PDF saved", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Could not save PDF: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    if (snapshot != null) {
        TextButton(onClick = {
            launcher.launch("Pay_Fixation_Journey_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.pdf")
        }) {
            Text("Export PDF", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ExportPayJourneyPdf(
    snapshot: CompleteJourneySnapshot?,
    title: String = "Complete Pay Fixation Journey Report",
    modifier: Modifier = Modifier,
    label: String = "Export Complete Journey PDF"
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null && snapshot != null) {
            try {
                val record = CpcHistoryRecord(
                    uniqueId = "live_export_${System.currentTimeMillis()}",
                    workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
                    title = title,
                    savedAtMillis = System.currentTimeMillis(),
                    startingCpc = snapshot.startingCpc,
                    currentStage = CpcHistoryStage.SEVENTH,
                    payload = CompleteJourneyPayload(snapshot)
                )
                val report = PayJourneyReportBuilder.build(record) ?: throw IllegalStateException("Could not generate report payload.")
                context.contentResolver.openOutputStream(uri)?.use { PayJourneyReportPdf.write(it, report) }
                    ?: throw IOException("Could not open the selected file.")
                Toast.makeText(context, "Pay journey PDF saved", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Could not save PDF: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    OutlinedButton(
        onClick = {
            if (snapshot != null) {
                launcher.launch("Pay_Fixation_Journey_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.pdf")
            } else {
                Toast.makeText(context, "Complete journey data not yet available for export.", Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier,
        enabled = snapshot != null
    ) {
        Text(label)
    }
}

@Composable
fun ExportPayJourneyPdf(
    lines: List<String>,
    modifier: Modifier = Modifier,
    label: String = "Export Complete Journey PDF"
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { PayJourneyPdf.write(it, lines) }
                    ?: throw IOException("Could not open the selected file.")
                Toast.makeText(context, "Pay journey PDF saved", Toast.LENGTH_LONG).show()
            } catch (error: Exception) {
                Toast.makeText(context, "Could not save PDF: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    OutlinedButton(onClick = { launcher.launch("Pay_Fixation_Journey_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())}.pdf") }, modifier = modifier) {
        Text(label)
    }
}

@Composable
fun ExportCompleteJourneyPdf(snapshot: CompleteJourneySnapshot?, modifier: Modifier = Modifier) {
    val report = remember(snapshot) { snapshot?.let(::completeJourneyReport) }
    if (report != null) ExportPayJourneyPdf(payJourneyReportPdfLines(report), modifier)
}

private object PayJourneyPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 42f
    private const val RIGHT = 553f

    fun write(output: OutputStream, lines: List<String>) {
        val document = PdfDocument()
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 49, 66); textSize = 10f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 105, 170); textSize = 18f; typeface = Typeface.create(
            Typeface.DEFAULT, Typeface.BOLD) }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 43, 77); textSize = 12f; typeface = Typeface.create(
            Typeface.DEFAULT, Typeface.BOLD) }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; textSize = 8f }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            val canvas = page!!.canvas
            canvas.drawText("Complete Pay Fixation Journey", LEFT, 38f, titlePaint)
            canvas.drawText("Generated ${SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ENGLISH).format(Date())}", LEFT, 55f, footerPaint)
            y = 82f
        }

        fun drawLine(text: String) {
            val isHeading = text.startsWith("## ")
            val paint = if (isHeading) headingPaint else bodyPaint
            val value = text.removePrefix("## ")
            val words = value.split(Regex("\\s+"))
            var current = ""
            val wrapped = mutableListOf<String>()
            words.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) > RIGHT - LEFT && current.isNotEmpty()) {
                    wrapped += current
                    current = word
                } else current = candidate
            }
            if (current.isNotEmpty()) wrapped += current
            if (wrapped.isEmpty()) wrapped += " "
            wrapped.forEachIndexed { index, line ->
                val lineHeight = if (isHeading) 19f else 15f
                if (y + lineHeight > PAGE_HEIGHT - 42f) newPage()
                page!!.canvas.drawText(line, LEFT, y, paint)
                y += lineHeight
                if (isHeading && index == wrapped.lastIndex) y += 3f
            }
        }

        try {
            newPage()
            lines.forEach(::drawLine)
            page!!.canvas.drawText("Indicative calculation. Verify against applicable rules, orders and service records.", LEFT, PAGE_HEIGHT - 20f, footerPaint)
            document.finishPage(page!!)
            page = null
            document.writeTo(output)
        } finally {
            page?.let(document::finishPage)
            document.close()
        }
    }
}
