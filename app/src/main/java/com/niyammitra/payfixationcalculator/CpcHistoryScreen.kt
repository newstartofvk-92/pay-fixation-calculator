package com.niyammitra.payfixationcalculator

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SaveCompleteJourneyButton(
    snapshot: CompleteJourneySnapshot,
    title: String = "Complete Pay Fixation Journey",
    sequenceIntegrity: CpcSequenceIntegrity = snapshot.sequenceIntegrity
) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val now = System.currentTimeMillis()
        val currentStage = when {
            snapshot.seventh != null || snapshot.sixth?.seventhContinuation != null || snapshot.fifth?.sixthContinuation?.seventhContinuation != null -> CpcHistoryStage.SEVENTH
            snapshot.sixth != null || snapshot.fifth?.sixthContinuation != null -> CpcHistoryStage.SIXTH
            snapshot.fifth != null -> CpcHistoryStage.FIFTH
            else -> CpcHistoryStage.FOURTH
        }
        CpcHistoryStore.save(context, CpcHistoryRecord(
            uniqueId = java.util.UUID.randomUUID().toString(),
            workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
            title = title,
            savedAtMillis = now,
            startingCpc = snapshot.startingCpc,
            currentStage = currentStage,
            payload = CompleteJourneyPayload(assignJourneyApplicationSequence(snapshot.copy(sequenceIntegrity = sequenceIntegrity)))
        ))
        android.widget.Toast.makeText(context, "Pay journey saved", android.widget.Toast.LENGTH_SHORT).show()
    }, modifier = Modifier.fillMaxWidth()) { Text("Save Journey to CPC History") }
}

@Composable
fun CpcHistoryScreen(
    records: List<CpcHistoryRecord>,
    onBack: () -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (CpcHistoryRecord) -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<CpcHistoryRecord?>(null) }
    var pendingDelete by remember { mutableStateOf<CpcHistoryRecord?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val report = selected?.let(PayJourneyReportBuilder::build)
        if (uri != null && report != null) runCatching {
            context.contentResolver.openOutputStream(uri)?.use { PayJourneyReportPdf.write(it, report) }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Surface(color = Color(0xFF1976B8), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) }
                Text("CPC Journey History", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Spacer(Modifier.padding(4.dp))
            }
        }
        if (records.isEmpty()) {
            Text("No CPC history saved yet.", Modifier.padding(24.dp), color = Color(0xFF5B6B7A))
        } else LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(records, key = { it.uniqueId }) { record ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color.White)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.title ?: record.workflowType.name.replace('_', ' '), fontWeight = FontWeight.Bold, color = Color(0xFF172B4D))
                        Text("${record.startingCpc.name} CPC • through ${record.currentStage.name} CPC • ${historyDate(record.savedAtMillis)}", color = Color(0xFF5B6B7A), fontSize = 12.sp)
                        val journey = (record.payload as? CompleteJourneyPayload)?.snapshot
                        if (journey?.sequenceIntegrity == CpcSequenceIntegrity.INFERRED) {
                            Text("Same-date event order inferred for this older record", color = Color(0xFF8A5A00), fontSize = 12.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { selected = record }) { Text("Report") }
                            Button(onClick = { onRestore(record) }) { Text("Restore") }
                            TextButton(onClick = { pendingDelete = record }) { Text("Delete", color = Color(0xFFC62828)) }
                        }
                    }
                }
            }
        }
    }
    selected?.let { record ->
        val report = PayJourneyReportBuilder.build(record)
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Pay Journey Report") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(report?.title ?: "Saved CPC conversion")
                    report?.chronologyNote?.let { Text(it, color = Color(0xFF8A5A00), fontSize = 12.sp) }
                    val allReportRows = report?.sections?.flatMap { it.rows }.orEmpty()
                    val previewRows = allReportRows.take(8)
                    val omRowsNotInPreview = allReportRows.filter { it.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT && it !in previewRows }
                    (previewRows + omRowsNotInPreview).forEach { row ->
                        val date = row.eventDateMillis?.let { "Effective ${historyDate(row.dateMillis)} • Event ${historyDate(it)}" }
                            ?: historyDate(row.dateMillis)
                        if (row.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT) {
                            Text("$date  ${row.description}: normal DNI ${historyDate(row.dniMillis)}, before ${row.sourcePosition?.basicPay}, increment ${row.position.basicPay - (row.sourcePosition?.basicPay ?: 0)}, adjusted ${row.position.basicPay}. ${row.remarks}", fontSize = 12.sp)
                        } else Text("$date  ${row.description}: ${row.position.basicPay}", fontSize = 12.sp)
                    }
                    HorizontalDivider()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { exportLauncher.launch("Pay_Journey_${record.uniqueId.take(8)}.pdf") }) { Text("Export PDF") }
                        OutlinedButton(onClick = { report?.let { printPayJourney(context, it) } }) { Text("Print") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
    pendingDelete?.let { record ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Delete saved history?") },
            text = { Text("This saved record will be removed from this device.") },
            confirmButton = { TextButton(onClick = { onDelete(record.uniqueId); pendingDelete = null; if (selected?.uniqueId == record.uniqueId) selected = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } })
    }
}

private fun historyDate(millis: Long?): String = millis?.let { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it)) } ?: "Date not recorded"

private object PayJourneyReportPdf {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    fun write(output: java.io.OutputStream, report: PayJourneyReport) {
        val doc = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 105, 170); textSize = 17f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val heading = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(23, 43, 77); textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.DKGRAY; textSize = 9f }
        var pageNum = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        var currentSection: String? = null
        fun finishPage() {
            page?.let {
                it.canvas.drawText("Page $pageNum", 510f, PAGE_HEIGHT - 24f, body)
                doc.finishPage(it)
            }
            page = null
        }
        fun newPage() {
            finishPage()
            pageNum++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            page!!.canvas.drawText(report.title.take(68), 40f, 40f, title)
            y = 70f
            currentSection?.let { page!!.canvas.drawText(it.take(82), 40f, y, heading); y += 16f }
        }
        fun draw(text: String, paint: Paint, keepTogether: Boolean = false) {
            val words = text.split(Regex("\\s+")); var line = ""; val lines = mutableListOf<String>()
            for (word in words) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(next) > 510f && line.isNotEmpty()) {
                    lines += line; line = word
                } else line = next
            }
            if (line.isNotEmpty()) lines += line
            if (keepTogether && y + lines.size * 14f > PAGE_HEIGHT - 48) newPage()
            lines.forEach { wrapped ->
                if (y > PAGE_HEIGHT - 48) newPage()
                page!!.canvas.drawText(wrapped, 40f, y, paint); y += 14f
            }
        }
        try {
            newPage()
            draw("Starting CPC: ${report.startingCpc}    Starting date: ${historyDate(report.startingDateMillis)}", body)
            draw("Saved: ${historyDate(report.savedAtMillis)}", body)
            report.chronologyNote?.let { draw(it, body) }
            report.sections.forEach { section ->
                y += 8f; currentSection = section.heading; draw(section.heading, heading)
                section.rows.forEach { row ->
                    val pos = row.position
                    val details = buildString {
                        if (row.kind != CpcJourneyEventKind.OM_SPECIAL_INCREMENT) append("#${row.sequence} | ")
                        if (row.eventDateMillis != null) {
                            append("Effective date ${historyDate(row.dateMillis)} | Event date ${historyDate(row.eventDateMillis)}")
                        } else append(historyDate(row.dateMillis))
                        append(" | ${row.description} | ${row.cpc} CPC | ")
                        append(pos.level?.let { "Level $it" } ?: pos.scaleTitle ?: pos.scaleId ?: pos.payBandId ?: "")
                        pos.gradePay?.let { append(" | GP $it") }; pos.payInPayBand?.let { append(" | Pay in band $it") }
                        append(" | Pay ${pos.basicPay}"); row.dniMillis?.let { append(" | DNI ${historyDate(it)}") }
                        if (row.fixationBasis != CpcFixationBasis.NOT_APPLICABLE) append(" | Fixation ${row.fixationBasis.name.replace('_', ' ')}")
                        row.sourcePosition?.let { source -> append(" | From ${source.cpc} CPC pay ${source.basicPay}") }
                        row.implementationDateMillis?.let { append(" | Implementation date ${historyDate(it)}") }
                        row.remarks?.let { append(" | $it") }
                    }
                    draw(details, body, keepTogether = true)
                }
            }
                    report.finalPosition?.let { position ->
                        val details = buildString {
                            append("Final pay position: ${position.cpc} CPC | ")
                            append(position.level?.let { "Level $it" } ?: position.scaleTitle ?: position.scaleId ?: position.payBandId.orEmpty())
                            position.gradePay?.let { append(" | GP $it") }
                            position.payInPayBand?.let { append(" | Pay in band $it") }
                            append(" | Pay ${position.basicPay}")
                            report.finalDniMillis?.let { append(" | DNI ${historyDate(it)}") }
                        }
                        draw(details, heading)
                    }
            finishPage(); doc.writeTo(output)
        } finally { page?.let(doc::finishPage); doc.close() }
    }
}

private fun printPayJourney(context: Context, report: PayJourneyReport) {
    val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?, cancellationSignal: CancellationSignal, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancellationSignal.isCanceled) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(PrintDocumentInfo.Builder("pay_journey.pdf").setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN).build(), true)
        }
        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
            if (cancellationSignal.isCanceled) { callback.onWriteCancelled(); return }
            runCatching { FileOutputStream(destination.fileDescriptor).let { stream -> PayJourneyReportPdf.write(stream, report); stream.flush() } }
                .onSuccess { callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES)) }
                .onFailure { callback.onWriteFailed(it.localizedMessage) }
        }
    }
    manager.print(report.title.take(40), adapter, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins(30, 30, 30, 30)).build())
}
