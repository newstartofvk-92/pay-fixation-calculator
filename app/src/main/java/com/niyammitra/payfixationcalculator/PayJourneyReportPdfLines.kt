package com.niyammitra.payfixationcalculator

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Converts the authoritative report rows into an untruncated, chronological PDF outline. */
internal fun payJourneyReportPdfLines(report: PayJourneyReport): List<String> = buildList {
    fun date(value: Long?): String = value?.let {
        SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(Date(it))
    } ?: "Date not recorded"

    fun pay(value: Int): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN"))
        .format(value)

    fun position(value: CpcPayPosition): String = buildString {
        val cpcName = when (value.cpc) {
            CpcHistoryStage.FOURTH -> "4th"
            CpcHistoryStage.FIFTH -> "5th"
            CpcHistoryStage.SIXTH -> "6th"
            CpcHistoryStage.SEVENTH -> "7th"
        }
        append("$cpcName CPC")
        val structure = value.level?.let { "Level $it" }
            ?: value.scaleTitle ?: value.scaleId ?: value.payBandId
        if (!structure.isNullOrBlank()) append(" · $structure")
        value.payInPayBand?.let { append(" · Pay in Pay Band ${pay(it)}") }
        value.gradePay?.let { append(" · Grade Pay ${pay(it)}") }
        append(" · Basic Pay ${pay(value.basicPay)}")
    }

    add("## ${report.title}")
    add("Starting CPC: ${report.startingCpc} · Starting date: ${date(report.startingDateMillis)}")
    report.chronologyNote?.let(::add)
    report.replayWarnings.forEach(::add)

    report.sections.forEach { section ->
        add("## ${section.heading}")
        var precedingPosition: CpcPayPosition? = null
        section.rows.forEach { row ->
            val event = row.kind in setOf(
                CpcJourneyEventKind.PROMOTION,
                CpcJourneyEventKind.ACP,
                CpcJourneyEventKind.MACP,
                CpcJourneyEventKind.FINANCIAL_UPGRADATION,
                CpcJourneyEventKind.PAY_SCALE_UPGRADATION,
                CpcJourneyEventKind.OTHER
            )
            val before = row.sourcePosition ?: if (event) precedingPosition else null
            val summary = buildString {
                append(date(row.dateMillis))
                append(" · ${row.description}")
                if (row.eventDateMillis != null) append(" · Event date ${date(row.eventDateMillis)}")
                if (row.implementationDateMillis != null) append(" · Implementation date ${date(row.implementationDateMillis)}")
                append(" · ${position(row.position)}")
            }
            add(summary)
            when {
                row.kind == CpcJourneyEventKind.CPC_CONVERSION && row.sourcePosition != null ->
                    add("Converted from: ${position(row.sourcePosition)}")
                before != null -> add("Pay before: ${position(before)}")
            }
            row.dniMillis?.let { add("DNI / increment date: ${date(it)}") }
            if (row.fixationBasis != CpcFixationBasis.NOT_APPLICABLE) {
                add("Fixation basis: ${row.fixationBasis.name.replace('_', ' ')}")
            }
            row.remarks?.takeIf(String::isNotBlank)?.let { add("Details: $it") }
            precedingPosition = row.position
        }
    }

    report.finalPosition?.let { add("## Final Pay Position\n${position(it)}") }
    report.finalDniMillis?.let { add("Final DNI / next increment date: ${date(it)}") }
}

internal fun completeJourneyReport(snapshot: CompleteJourneySnapshot): PayJourneyReport? {
    val currentStage = when {
        snapshot.seventh != null || snapshot.sixth?.seventhContinuation != null || snapshot.fifth?.sixthContinuation?.seventhContinuation != null -> CpcHistoryStage.SEVENTH
        snapshot.sixth != null || snapshot.fifth?.sixthContinuation != null -> CpcHistoryStage.SIXTH
        snapshot.fifth != null -> CpcHistoryStage.FIFTH
        snapshot.fourth != null -> CpcHistoryStage.FOURTH
        else -> return null
    }
    val record = CpcHistoryRecord(
        uniqueId = "pdf-export",
        workflowType = CpcHistoryWorkflow.COMPLETE_JOURNEY,
        title = "Complete Pay Fixation Journey",
        savedAtMillis = System.currentTimeMillis(),
        startingCpc = snapshot.startingCpc,
        currentStage = currentStage,
        payload = CompleteJourneyPayload(snapshot)
    )
    return PayJourneyReportBuilder.build(record)
}
