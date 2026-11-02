package com.niyammitra.payfixationcalculator

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExportPayJourneyPdf(
    lines: List<String>,
    modifier: Modifier = Modifier,
    buttonText: String = "Export to PDF",
    title: String = "Pay Fixation Calculation",
    fileNamePrefix: String = "Pay_Fixation"
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { PayJourneyPdf.write(it, lines, title) }
                    ?: throw IOException("Could not open the selected file.")
                Toast.makeText(context, "PDF saved successfully", Toast.LENGTH_SHORT).show()
                (context as? android.app.Activity)?.let { HistoryInterstitialAd.showIfDue(it) {} }
            } catch (error: Exception) {
                Toast.makeText(context, "Could not save PDF: ${error.localizedMessage ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        }
    }
    OutlinedButton(
        onClick = {
            val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val cleanPrefix = fileNamePrefix.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "Pay_Fixation" }
            launcher.launch("${cleanPrefix}_$dateStr.pdf")
        },
        modifier = modifier
    ) {
        Text(buttonText)
    }
}

private object PayJourneyPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 42f
    private const val RIGHT = 553f

    fun write(output: java.io.OutputStream, lines: List<String>, documentTitle: String = "Pay Fixation Calculation") {
        val document = PdfDocument()
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(35, 49, 66); textSize = 10f }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 105, 170); textSize = 18f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 43, 77); textSize = 12f; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD) }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.GRAY; textSize = 8f }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            val canvas = page!!.canvas
            canvas.drawText(documentTitle.take(65), LEFT, 38f, titlePaint)
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
