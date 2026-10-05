package com.niyammitra.payfixationcalculator

import org.json.JSONArray
import org.json.JSONObject

enum class CpcHistoryWorkflow { COMPLETE_JOURNEY, CPC_CONVERSION_ONLY }
enum class CpcSequenceIntegrity { ORIGINAL, INFERRED }
enum class CpcHistoryStage { FOURTH, FIFTH, SIXTH, SEVENTH }
enum class CpcJourneyEventKind {
    STARTING_POSITION, ANNUAL_INCREMENT, PROMOTION, ACP, MACP, FINANCIAL_UPGRADATION,
    PAY_SCALE_UPGRADATION, CPC_CONVERSION, OM_SPECIAL_INCREMENT, OTHER
}
enum class CpcFixationBasis { EVENT_DATE, DNI, NOT_APPLICABLE }
enum class StandaloneConversionKind { FOURTH_TO_FIFTH, FIFTH_TO_SIXTH, SIXTH_TO_SEVENTH }

/** CPC-specific pay position. Only fields used by this position's commission need values. */
data class CpcPayPosition(
    val cpc: CpcHistoryStage,
    val basicPay: Int,
    val scaleId: String? = null,
    val scaleTitle: String? = null,
    val payBandId: String? = null,
    val gradePay: Int? = null,
    val payInPayBand: Int? = null,
    val level: String? = null
)

data class CpcTimelineEntry(
    val order: Int,
    val dateMillis: Long?,
    val kind: CpcJourneyEventKind,
    val cpc: CpcHistoryStage,
    val description: String,
    val resultingPosition: CpcPayPosition,
    val sourcePosition: CpcPayPosition? = null,
    val dniMillis: Long? = null,
    val fixationBasis: CpcFixationBasis = CpcFixationBasis.NOT_APPLICABLE,
    val remarks: String? = null
)

data class CpcIncrementSnapshot(val order: Int, val pay: Int, val dateMillis: Long, val sequence: Int = order)

data class FourthCpcEventSnapshot(
    val order: Int,
    val eventDateMillis: Long,
    val eventType: CpcJourneyEventKind,
    val targetScaleId: String,
    val resultingPay: Int,
    val sequence: Int = order
)

data class FourthCpcJourneySnapshot(
    val scaleId: String,
    val scaleTitle: String,
    val startingBasicPay: Int,
    val payDateMillis: Long,
    val nextIncrementDateMillis: Long?,
    val increments: List<CpcIncrementSnapshot> = emptyList(),
    val events: List<FourthCpcEventSnapshot> = emptyList(),
    val conversionActivated: Boolean = false,
    val sequence: Int = 0
)

data class FifthCpcEventSnapshot(
    val order: Int,
    val eventType: CpcJourneyEventKind,
    val eventDateMillis: Long,
    val implementationDateMillis: Long,
    val targetScaleId: String,
    val resultingPay: Int,
    val resultingDniMillis: Long,
    val fixationBasis: CpcFixationBasis,
    val placementMethod: String,
    val implementationOption: String = "",
    val sequence: Int = order,
    val omAppliedBeforeEvent: Boolean = false
)

/** One-time pre-revised 5th CPC increment granted under the 19 March 2012 O.M. */
data class FifthCpcOmAdjustmentSnapshot(
    val normalDniMillis: Long,
    val incrementDateMillis: Long,
    val scaleTitle: String,
    val basicPayBefore: Int,
    val incrementAmount: Int,
    val adjustedBasicPay: Int,
    val nextRevisedIncrementDateMillis: Long
)

data class FifthCpcJourneySnapshot(
    val startingDateMillis: Long,
    val scaleId: String,
    val startingBasicPay: Int,
    val startingDniMillis: Long?,
    val increments: List<CpcIncrementSnapshot> = emptyList(),
    val events: List<FifthCpcEventSnapshot> = emptyList(),
    val sixthContinuation: SixthCpcJourneySnapshot? = null,
    val sequence: Int = 0,
    val omAdjustment: FifthCpcOmAdjustmentSnapshot? = null
)

data class SixthCpcIncrementSnapshot(
    val order: Int,
    val payInPayBand: Int,
    val gradePay: Int,
    val dateMillis: Long,
    val sequence: Int = order
)

data class SixthCpcJourneySnapshot(
    val startingDateMillis: Long,
    val payBandId: String,
    val gradePay: Int,
    val startingPayInPayBand: Int,
    val increments: List<SixthCpcIncrementSnapshot> = emptyList(),
    val eventChains: List<SixthCpcEventChain> = emptyList(),
    val seventhContinuation: SeventhCpcJourneySnapshot? = null,
    val sequence: Int = 0
)

data class SeventhCpcPromotionSnapshot(
    val currentLevel: String,
    val currentPay: Int,
    val knownDniMillis: Long,
    val promotedLevel: String? = null,
    val promotionDateMillis: Long? = null,
    val fixationBasis: CpcFixationBasis = CpcFixationBasis.EVENT_DATE,
    val postIncrements: List<CpcIncrementSnapshot> = emptyList(),
    val subsequentPromotion: SeventhCpcPromotionSnapshot? = null,
    val resultingPay: Int? = null,
    val resultingDniMillis: Long? = null,
    val eventKind: CpcJourneyEventKind = CpcJourneyEventKind.OTHER,
    val sequence: Int = 0
)

data class SeventhCpcJourneySnapshot(
    val startingLevel: String,
    val startingBasicPay: Int,
    val conversionDateMillis: Long,
    val increments: List<CpcIncrementSnapshot> = emptyList(),
    val promotions: List<SeventhCpcPromotionSnapshot> = emptyList(),
    val startingDniMillis: Long? = null,
    val sequence: Int = 0
)

data class CompleteJourneySnapshot(
    val startingCpc: CpcHistoryStage,
    val startingDateMillis: Long,
    val fourth: FourthCpcJourneySnapshot? = null,
    val fifth: FifthCpcJourneySnapshot? = null,
    val sixth: SixthCpcJourneySnapshot? = null,
    val seventh: SeventhCpcJourneySnapshot? = null,
    val sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
)

/** Pay position and complete prior journey carried into a later 7th CPC screen. */
data class SixthCpcContinuationContext(
    val payBandId: String,
    val gradePay: Int,
    val payInPayBand: Int,
    val effectiveDateMillis: Long,
    val journey: CompleteJourneySnapshot
)

/** Appends a later 6th CPC continuation while retaining its original journey owner. */
internal fun appendSixthContinuation(
    journey: CompleteJourneySnapshot,
    continuation: SixthCpcJourneySnapshot
): CompleteJourneySnapshot {
    val prior = journey.sixth ?: journey.fifth?.sixthContinuation
        ?: return journey.copy(sixth = continuation)
    val maxSequence = maxOf(
        prior.increments.maxOfOrNull { it.sequence } ?: 0,
        prior.eventChains.maxOfOrNull { chain -> maxOf(chain.sequence, chain.increments.maxOfOrNull { it.sequence } ?: 0) } ?: 0,
        prior.sequence,
        prior.seventhContinuation?.let { seventh -> maxOf(
            seventh.sequence,
            seventh.increments.maxOfOrNull { it.sequence } ?: 0,
            seventh.promotions.maxOfOrNull { it.maxApplicationSequence() } ?: 0
        ) } ?: 0
    )
    val offset = maxSequence
    val appended = continuation.copy(
        increments = continuation.increments.mapIndexed { index, item ->
            item.copy(order = prior.increments.size + index + 1, sequence = item.sequence + offset)
        },
        eventChains = continuation.eventChains.map { chain ->
            chain.copy(
                sequence = chain.sequence + offset,
                increments = chain.increments.map { it.copy(sequence = it.sequence + offset) }
            )
        }
    )
    val priorSeventh = prior.seventhContinuation
    val laterSeventh = continuation.seventhContinuation
    val mergedSeventh = when {
        priorSeventh == null -> laterSeventh
        laterSeventh == null -> priorSeventh
        else -> laterSeventh.copy(
            sequence = laterSeventh.sequence + offset,
            increments = priorSeventh.increments + laterSeventh.increments.map { it.copy(sequence = it.sequence + offset) },
            promotions = priorSeventh.promotions + laterSeventh.promotions.map { offsetPromotionSequence(it, offset) }
        )
    }
    val merged = prior.copy(
        increments = prior.increments + appended.increments,
        eventChains = prior.eventChains + appended.eventChains,
        seventhContinuation = mergedSeventh
    )
    return if (journey.sixth != null) journey.copy(sixth = merged)
    else journey.copy(fifth = journey.fifth?.copy(sixthContinuation = merged))
}

private fun offsetPromotionSequence(promotion: SeventhCpcPromotionSnapshot, offset: Int): SeventhCpcPromotionSnapshot =
    promotion.copy(
        sequence = promotion.sequence + offset,
        postIncrements = promotion.postIncrements.map { it.copy(sequence = it.sequence + offset) },
        subsequentPromotion = promotion.subsequentPromotion?.let { offsetPromotionSequence(it, offset) }
    )

sealed interface StandaloneCpcConversionSnapshot {
    val kind: StandaloneConversionKind

    data class FourthToFifth(val scaleId: String, val existingBasicPay: Int) : StandaloneCpcConversionSnapshot {
        override val kind = StandaloneConversionKind.FOURTH_TO_FIFTH
    }
    data class FifthToSixth(
        val scaleId: String,
        val existingBasicPay: Int,
        val normalDniMillis: Long? = null,
        val omAdjustment: FifthCpcOmAdjustmentSnapshot? = null
    ) : StandaloneCpcConversionSnapshot {
        override val kind = StandaloneConversionKind.FIFTH_TO_SIXTH
    }
    data class SixthToSeventh(val payBandId: String, val payInPayBand: Int, val gradePay: Int) : StandaloneCpcConversionSnapshot {
        override val kind = StandaloneConversionKind.SIXTH_TO_SEVENTH
    }
}

sealed interface CpcHistoryPayload
data class CompleteJourneyPayload(val snapshot: CompleteJourneySnapshot) : CpcHistoryPayload
data class StandaloneConversionPayload(val snapshot: StandaloneCpcConversionSnapshot) : CpcHistoryPayload

data class CpcHistoryRecord(
    val uniqueId: String,
    val schemaVersion: Int = CPC_HISTORY_SCHEMA_VERSION,
    val workflowType: CpcHistoryWorkflow,
    val title: String? = null,
    val savedAtMillis: Long,
    val startingCpc: CpcHistoryStage,
    val currentStage: CpcHistoryStage,
    val payload: CpcHistoryPayload,
    val officialName: String? = null,
    val designation: String? = null
)

const val CPC_HISTORY_SCHEMA_VERSION = 1

enum class CpcHistoryRestoreDestination { FOURTH_JOURNEY, FIFTH_JOURNEY, SIXTH_JOURNEY, SEVENTH_JOURNEY, CPC_CONVERSION_ONLY }
fun parseCpcConversionTitle(title: String?): Pair<String, String>? {
    val parts = title?.split(" — ") ?: return null
    if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank()) return null
    val knownConversionNames = StandaloneConversionKind.values().map { it.name.replace('_', ' ') }
    if (parts[2] !in knownConversionNames) return null
    return parts[0].trim() to parts[1].trim()
}

fun cpcHistoryRestoreDestination(record: CpcHistoryRecord): CpcHistoryRestoreDestination = when {
    record.workflowType == CpcHistoryWorkflow.CPC_CONVERSION_ONLY -> CpcHistoryRestoreDestination.CPC_CONVERSION_ONLY
    record.startingCpc == CpcHistoryStage.FOURTH -> CpcHistoryRestoreDestination.FOURTH_JOURNEY
    record.startingCpc == CpcHistoryStage.FIFTH -> CpcHistoryRestoreDestination.FIFTH_JOURNEY
    record.startingCpc == CpcHistoryStage.SIXTH -> CpcHistoryRestoreDestination.SIXTH_JOURNEY
    else -> CpcHistoryRestoreDestination.SEVENTH_JOURNEY
}

/** Makes a saved 6th CPC continuation visible to the embedded 5th CPC restore flow. */
internal fun normalizeJourneyForCpcRestore(snapshot: CompleteJourneySnapshot): CompleteJourneySnapshot {
    if (snapshot.startingCpc > CpcHistoryStage.FIFTH) return snapshot
    val fifth = snapshot.fifth ?: return snapshot
    val sixth = snapshot.sixth ?: fifth.sixthContinuation ?: return snapshot
    return snapshot.copy(
        fifth = fifth.copy(sixthContinuation = sixth),
        sixth = null
    )
}

fun SeventhCpcPromotionSnapshot.maxApplicationSequence(): Int = maxOf(
    sequence,
    postIncrements.maxOfOrNull { it.sequence } ?: 0,
    subsequentPromotion?.maxApplicationSequence() ?: 0
)

data class PayJourneyReportRow(
    val dateMillis: Long?,
    val order: Int,
    val cpc: CpcHistoryStage,
    val kind: CpcJourneyEventKind,
    val description: String,
    val position: CpcPayPosition,
    val dniMillis: Long? = null,
    val fixationBasis: CpcFixationBasis = CpcFixationBasis.NOT_APPLICABLE,
    val remarks: String? = null,
    val sourcePosition: CpcPayPosition? = null,
    val sequence: Int = order,
    val implementationDateMillis: Long? = null,
    val eventDateMillis: Long? = null
)

data class PayJourneyReportSection(val heading: String, val rows: List<PayJourneyReportRow>)
data class PayJourneyReport(
    val title: String,
    val savedAtMillis: Long,
    val startingCpc: CpcHistoryStage,
    val startingDateMillis: Long,
    val sections: List<PayJourneyReportSection>,
    val finalPosition: CpcPayPosition?,
    val finalDniMillis: Long? = null,
    val chronologyNote: String? = null
)

/** Reusable text/report model; renderers never recalculate pay. */
object PayJourneyReportBuilder {
    fun build(record: CpcHistoryRecord): PayJourneyReport? {
        val payload = record.payload as? CompleteJourneyPayload
        if (payload == null) return buildStandaloneReport(record)
        val snapshot = payload.snapshot
        // A journey beginning in the 4th CPC keeps later stages nested in the prior snapshot.
        // Prefer the dedicated top-level stage when both representations are present.
        val effectiveSixth = snapshot.sixth ?: snapshot.fifth?.sixthContinuation
        val effectiveSeventh = snapshot.seventh ?: effectiveSixth?.seventhContinuation
        val allRows = mutableListOf<PayJourneyReportRow>()
        val sections = mutableListOf<PayJourneyReportSection>()
        fun finalFourth(s: FourthCpcJourneySnapshot): CpcPayPosition {
            val lastIncrement = s.increments.maxWithOrNull(compareBy<CpcIncrementSnapshot> { it.dateMillis }.thenBy { it.order })
            val lastEvent = s.events.maxWithOrNull(compareBy<FourthCpcEventSnapshot> { it.eventDateMillis }.thenBy { it.order })
            return if (lastEvent != null && (lastIncrement == null || lastEvent.eventDateMillis > lastIncrement.dateMillis ||
                    lastEvent.eventDateMillis == lastIncrement.dateMillis && lastEvent.sequence >= lastIncrement.sequence))
                CpcPayPosition(CpcHistoryStage.FOURTH, lastEvent.resultingPay, lastEvent.targetScaleId, lastEvent.targetScaleId)
            else CpcPayPosition(CpcHistoryStage.FOURTH, lastIncrement?.pay ?: s.startingBasicPay, s.scaleId, s.scaleTitle)
        }
        fun finalFifth(s: FifthCpcJourneySnapshot): CpcPayPosition {
            val lastIncrement = s.increments.maxWithOrNull(compareBy<CpcIncrementSnapshot> { it.dateMillis }.thenBy { it.order })
            val lastEvent = s.events.maxWithOrNull(compareBy<FifthCpcEventSnapshot> { it.implementationDateMillis }.thenBy { it.order })
            val ordinaryPosition = if (lastEvent != null && (lastIncrement == null || lastEvent.implementationDateMillis > lastIncrement.dateMillis ||
                    lastEvent.implementationDateMillis == lastIncrement.dateMillis && lastEvent.sequence >= lastIncrement.sequence))
                CpcPayPosition(CpcHistoryStage.FIFTH, lastEvent.resultingPay, lastEvent.targetScaleId, lastEvent.targetScaleId)
            else CpcPayPosition(CpcHistoryStage.FIFTH, lastIncrement?.pay ?: s.startingBasicPay, s.scaleId, s.scaleId)
            val ordinaryDate = maxOf(lastIncrement?.dateMillis ?: Long.MIN_VALUE, lastEvent?.implementationDateMillis ?: Long.MIN_VALUE)
            val adjustment = s.omAdjustment
            return if (adjustment != null && adjustment.incrementDateMillis >= ordinaryDate) {
                CpcPayPosition(CpcHistoryStage.FIFTH, adjustment.adjustedBasicPay, scaleId = adjustment.scaleTitle, scaleTitle = adjustment.scaleTitle)
            } else ordinaryPosition
        }
        fun finalSixth(s: SixthCpcJourneySnapshot): CpcPayPosition {
            val base = CpcPayPosition(CpcHistoryStage.SIXTH, s.startingPayInPayBand + s.gradePay, payBandId = s.payBandId, gradePay = s.gradePay, payInPayBand = s.startingPayInPayBand)
            var latestDate = s.startingDateMillis
            var latestSequence = s.sequence
            var latest = base
            s.increments.forEach { if (it.dateMillis > latestDate || it.dateMillis == latestDate && it.sequence >= latestSequence) { latestDate = it.dateMillis; latestSequence = it.sequence; latest = sixthPosition(s.payBandId, it.gradePay, it.payInPayBand) } }
            s.eventChains.forEach { chain ->
                val result = chain.result
                val upgrade = chain.scaleUpgrade
                val date = result?.eventDate ?: upgrade?.eventDate
                if (date != null && (date > latestDate || date == latestDate && chain.sequence >= latestSequence)) { latestDate = date; latestSequence = chain.sequence; latest = if (result != null) sixthPosition(result.newPayBand, result.newGradePay, result.newPayInPayBand) else sixthPosition(upgrade!!.newPayBand, upgrade.newGradePay, upgrade.newPayInPayBand) }
                val eventBand = result?.newPayBand ?: upgrade?.newPayBand ?: s.payBandId
                chain.increments.forEach { inc -> if (inc.date > latestDate || inc.date == latestDate && inc.sequence >= latestSequence) { latestDate = inc.date; latestSequence = inc.sequence; latest = sixthPosition(eventBand, inc.gradePay, inc.payInPayBand) } }
            }
            return latest
        }
        fun section(name: String, stage: CpcHistoryStage, rows: List<PayJourneyReportRow>) {
            if (rows.isNotEmpty()) sections += PayJourneyReportSection(name, rows.sortedWith { left, right ->
                val dateOrder = (left.dateMillis ?: Long.MIN_VALUE).compareTo(right.dateMillis ?: Long.MIN_VALUE)
                if (dateOrder != 0) dateOrder
                else when {
                    left.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT && right.kind != CpcJourneyEventKind.OM_SPECIAL_INCREMENT -> 1
                    right.kind == CpcJourneyEventKind.OM_SPECIAL_INCREMENT && left.kind != CpcJourneyEventKind.OM_SPECIAL_INCREMENT -> -1
                    else -> left.sequence.compareTo(right.sequence)
                }
            })
        }
        snapshot.fourth?.let { s ->
            val rows = buildList {
                add(PayJourneyReportRow(s.payDateMillis, 0, CpcHistoryStage.FOURTH, CpcJourneyEventKind.STARTING_POSITION,
                    "Starting position", CpcPayPosition(CpcHistoryStage.FOURTH, s.startingBasicPay, s.scaleId, s.scaleTitle), sequence = s.sequence))
                s.increments.forEach { add(PayJourneyReportRow(it.dateMillis, it.order, CpcHistoryStage.FOURTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                    "Annual increment", CpcPayPosition(CpcHistoryStage.FOURTH, it.pay, s.scaleId, s.scaleTitle), sequence = it.sequence)) }
                s.events.forEach { e -> add(PayJourneyReportRow(e.eventDateMillis, e.order, CpcHistoryStage.FOURTH, e.eventType,
                    e.eventType.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), CpcPayPosition(CpcHistoryStage.FOURTH, e.resultingPay, e.targetScaleId, e.targetScaleId), sequence = e.sequence)) }
            }
            section("4th CPC Pay Journey", CpcHistoryStage.FOURTH, rows); allRows += rows
        }
        snapshot.fifth?.let { s ->
            val rows = buildList {
                val isConversion = snapshot.startingCpc < CpcHistoryStage.FIFTH
                add(PayJourneyReportRow(s.startingDateMillis, 0, CpcHistoryStage.FIFTH, if (isConversion) CpcJourneyEventKind.CPC_CONVERSION else CpcJourneyEventKind.STARTING_POSITION,
                    if (isConversion) "4th to 5th CPC conversion" else "Starting position", CpcPayPosition(CpcHistoryStage.FIFTH, s.startingBasicPay, s.scaleId, s.scaleId), s.startingDniMillis,
                    sourcePosition = snapshot.fourth?.let(::finalFourth), sequence = s.sequence))
                s.increments.forEach { add(PayJourneyReportRow(it.dateMillis, it.order, CpcHistoryStage.FIFTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                    "Annual increment", CpcPayPosition(CpcHistoryStage.FIFTH, it.pay, s.scaleId, s.scaleId), it.dateMillis, sequence = it.sequence)) }
                s.events.forEach { e -> add(PayJourneyReportRow(e.implementationDateMillis, e.order, CpcHistoryStage.FIFTH, e.eventType,
                    e.eventType.name.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase), CpcPayPosition(CpcHistoryStage.FIFTH, e.resultingPay, e.targetScaleId, e.targetScaleId), e.resultingDniMillis, e.fixationBasis,
                    "placement ${e.placementMethod}; option ${e.implementationOption}", sequence = e.sequence, implementationDateMillis = e.implementationDateMillis, eventDateMillis = e.eventDateMillis)) }
                s.omAdjustment?.let { om -> add(omReportRow(om)) }
            }
            section("5th CPC Pay Journey", CpcHistoryStage.FIFTH, rows); allRows += rows
        }
        effectiveSixth?.let { s ->
            val rows = buildList {
                val isConversion = snapshot.startingCpc < CpcHistoryStage.SIXTH
                add(PayJourneyReportRow(s.startingDateMillis, 0, CpcHistoryStage.SIXTH, if (isConversion) CpcJourneyEventKind.CPC_CONVERSION else CpcJourneyEventKind.STARTING_POSITION,
                    if (isConversion) "5th to 6th CPC conversion" else "Starting position", sixthPosition(s.payBandId, s.gradePay, s.startingPayInPayBand),
                    sourcePosition = snapshot.fifth?.let(::finalFifth), sequence = s.sequence))
                s.increments.forEach { add(PayJourneyReportRow(it.dateMillis, it.order, CpcHistoryStage.SIXTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                    "Annual increment", sixthPosition(s.payBandId, it.gradePay, it.payInPayBand), it.dateMillis, sequence = it.sequence)) }
                s.eventChains.forEachIndexed { index, chain ->
                    chain.result?.let { r -> add(PayJourneyReportRow(r.eventDate, index + 1, CpcHistoryStage.SIXTH, eventKind(r),
                        r.eventType, sixthPosition(r.newPayBand, r.newGradePay, r.newPayInPayBand), r.nextIncrementDate, when (r.fixationOption) { SixthCpcFixationOption.FROM_EVENT_DATE -> CpcFixationBasis.EVENT_DATE; SixthCpcFixationOption.FROM_DNI -> CpcFixationBasis.DNI }, r.ruleBasis.joinToString(" "), sequence = chain.sequence)) }
                    chain.scaleUpgrade?.let { r -> add(PayJourneyReportRow(r.eventDate, index + 1, CpcHistoryStage.SIXTH, CpcJourneyEventKind.PAY_SCALE_UPGRADATION,
                        "Pay-scale upgradation", sixthPosition(r.newPayBand, r.newGradePay, r.newPayInPayBand), r.nextIncrementDate,
                        remarks = listOfNotNull(r.historicalRoute?.name, r.sourcePreRevisedBasicPay?.let { "Source pre-revised pay $it" }, r.fixationIncrement?.let { "Fixation increment $it" }).joinToString("; "), sequence = chain.sequence)) }
                    val eventPayBand = chain.result?.newPayBand ?: chain.scaleUpgrade?.newPayBand ?: s.payBandId
                    chain.increments.forEachIndexed { ix, inc -> add(PayJourneyReportRow(inc.date, index * 1000 + ix + 2, CpcHistoryStage.SIXTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                        "Event-chain increment", sixthPosition(eventPayBand, inc.gradePay, inc.payInPayBand), sequence = chain.sequence + ix + 1)) }
                }
            }
            section("6th CPC Pay Journey", CpcHistoryStage.SIXTH, rows); allRows += rows
        }
        effectiveSeventh?.let { s ->
            fun promotionRows(promo: SeventhCpcPromotionSnapshot, order: Int, depth: Int): List<PayJourneyReportRow> = buildList {
                promo.promotionDateMillis?.let { date -> add(PayJourneyReportRow(date, order, CpcHistoryStage.SEVENTH, promo.eventKind,
                    "${"  ".repeat(depth)}${promo.eventKind.name.replace('_', ' ')} to Level ${promo.promotedLevel ?: "—"}", CpcPayPosition(CpcHistoryStage.SEVENTH, promo.resultingPay ?: promo.currentPay, level = promo.promotedLevel), promo.resultingDniMillis, promo.fixationBasis, remarks = "Known DNI ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(promo.knownDniMillis))}", sequence = promo.sequence)) }
                promo.postIncrements.forEach { add(PayJourneyReportRow(it.dateMillis, order * 100 + it.order, CpcHistoryStage.SEVENTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                    "${"  ".repeat(depth)}Post-event increment", CpcPayPosition(CpcHistoryStage.SEVENTH, it.pay, level = promo.promotedLevel), sequence = it.sequence)) }
                promo.subsequentPromotion?.let { addAll(promotionRows(it, order + 1, depth + 1)) }
            }
            val rows = buildList {
                add(PayJourneyReportRow(s.conversionDateMillis, 0, CpcHistoryStage.SEVENTH, CpcJourneyEventKind.CPC_CONVERSION,
                    "6th to 7th CPC conversion", CpcPayPosition(CpcHistoryStage.SEVENTH, s.startingBasicPay, level = s.startingLevel), s.startingDniMillis,
                    sourcePosition = effectiveSixth?.let(::finalSixth), sequence = s.sequence))
                s.increments.forEach { add(PayJourneyReportRow(it.dateMillis, it.order, CpcHistoryStage.SEVENTH, CpcJourneyEventKind.ANNUAL_INCREMENT,
                    "Annual increment", CpcPayPosition(CpcHistoryStage.SEVENTH, it.pay, level = s.startingLevel), it.dateMillis, sequence = it.sequence)) }
                s.promotions.forEachIndexed { ix, promo -> addAll(promotionRows(promo, ix + 1, 0)) }
            }
            section("7th CPC Pay Journey", CpcHistoryStage.SEVENTH, rows); allRows += rows
        }
        val seventhSnapshot = effectiveSeventh
        // Use the same sequence/date resolver as the 7th CPC journey screen so report
        // pay, level, and DNI cannot disagree for inferred legacy records.
        val resolvedSeventh = seventhSnapshot?.let { stage ->
            currentSeventhPosition(
                stage,
                stage.increments.map { SeventhCpcIncrementStep(it.pay, it.dateMillis, it.sequence) },
                stage.promotions,
                snapshot.sequenceIntegrity
            )
        }
        val finalSeventh = resolvedSeventh?.let {
            CpcPayPosition(CpcHistoryStage.SEVENTH, it.pay, level = it.level)
        }
        val final = finalSeventh
            ?: effectiveSixth?.let(::finalSixth)
            ?: snapshot.fifth?.let(::finalFifth)
            ?: snapshot.fourth?.let(::finalFourth)
        val hasSeventhPositionDate = seventhSnapshot != null &&
            (seventhSnapshot.startingDniMillis != null || seventhSnapshot.increments.isNotEmpty() || seventhSnapshot.promotions.isNotEmpty())
        val finalDni = resolvedSeventh?.dni?.takeIf { hasSeventhPositionDate }
            ?: seventhSnapshot?.increments?.maxByOrNull { it.sequence }?.let { addOneYear(it.dateMillis) }
            ?: seventhSnapshot?.startingDniMillis
            ?: effectiveSixth?.eventChains?.maxByOrNull { it.sequence }?.let { chain ->
                chain.increments.maxByOrNull { it.sequence }?.date ?: chain.result?.nextIncrementDate ?: chain.scaleUpgrade?.nextIncrementDate
            }
            ?: effectiveSixth?.increments?.maxByOrNull { it.sequence }?.dateMillis
            ?: snapshot.fifth?.events?.maxByOrNull { it.sequence }?.resultingDniMillis
            ?: snapshot.fifth?.increments?.maxByOrNull { it.sequence }?.let { addOneYear(it.dateMillis) }
            ?: snapshot.fifth?.startingDniMillis
        return PayJourneyReport(record.title ?: "Pay Fixation / Pay Journey Report", record.savedAtMillis, snapshot.startingCpc,
            snapshot.startingDateMillis, sections, final, finalDni,
            chronologyNote = if (snapshot.sequenceIntegrity == CpcSequenceIntegrity.INFERRED)
                "Note: This saved calculation predates chronological event sequencing. Where multiple events share the same date, their original application order could not be determined."
            else null)
    }

    private fun addOneYear(date: Long): Long = java.util.Calendar.getInstance().apply { timeInMillis = date; add(java.util.Calendar.YEAR, 1) }.timeInMillis

    private fun omReportRow(om: FifthCpcOmAdjustmentSnapshot) = PayJourneyReportRow(
        dateMillis = om.incrementDateMillis,
        order = 0,
        cpc = CpcHistoryStage.FIFTH,
        kind = CpcJourneyEventKind.OM_SPECIAL_INCREMENT,
        description = "Special 5th CPC increment under O.M. dated 19 March 2012",
        position = CpcPayPosition(CpcHistoryStage.FIFTH, om.adjustedBasicPay, scaleId = om.scaleTitle, scaleTitle = om.scaleTitle),
        dniMillis = om.normalDniMillis,
        remarks = "Basic pay before OM ${om.basicPayBefore}; special increment ${om.incrementAmount}; adjusted basic pay ${om.adjustedBasicPay}; next revised-pay increment ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(om.nextRevisedIncrementDateMillis))}",
        sourcePosition = CpcPayPosition(CpcHistoryStage.FIFTH, om.basicPayBefore, scaleId = om.scaleTitle, scaleTitle = om.scaleTitle),
        sequence = 0
    )

    private fun buildStandaloneReport(record: CpcHistoryRecord): PayJourneyReport? {
        val snapshot = (record.payload as? StandaloneConversionPayload)?.snapshot ?: return null
        val conversionDate = fun(value: String): Long? = listOf("dd MMMM yyyy", "dd/MM/yyyy", "dd MMM yyyy").firstNotNullOfOrNull { pattern ->
            runCatching { java.text.SimpleDateFormat(pattern, java.util.Locale.US).apply { isLenient = false }.parse(value)?.time }.getOrNull()
        }
        var specialOmRow: PayJourneyReportRow? = null
        var nextIncrementDateMillis: Long? = null
        val row = when (snapshot) {
            is StandaloneCpcConversionSnapshot.FourthToFifth -> {
                val scale = FourthToFifthCpcData.scales.firstOrNull { it.existingScale == snapshot.scaleId } ?: return null
                val result = runCatching { calculateFourthToFifthCpc(snapshot.existingBasicPay, scale) }.getOrNull() ?: return null
                PayJourneyReportRow(conversionDate(result.conversionDate), 0, CpcHistoryStage.FIFTH, CpcJourneyEventKind.CPC_CONVERSION,
                    "4th to 5th CPC conversion", CpcPayPosition(CpcHistoryStage.FIFTH, result.revisedBasicPay, scaleTitle = scale.revisedScale),
                    remarks = "Conversion result ${result.revisedBasicPay}", sourcePosition = CpcPayPosition(CpcHistoryStage.FOURTH, snapshot.existingBasicPay, scaleId = scale.existingScale, scaleTitle = scale.grade))
            }
            is StandaloneCpcConversionSnapshot.FifthToSixth -> {
                val scale = FifthToSixthCpcData.scales.firstOrNull { it.title == snapshot.scaleId } ?: return null
                val resultPay = snapshot.omAdjustment?.adjustedBasicPay ?: snapshot.existingBasicPay
                val result = runCatching { calculateFifthToSixthCpc(resultPay, scale) }.getOrNull() ?: return null
                val hasDniSupport = snapshot.normalDniMillis != null || snapshot.omAdjustment != null
                nextIncrementDateMillis = if (hasDniSupport) snapshot.omAdjustment?.nextRevisedIncrementDateMillis ?: conversionDate(result.nextIncrementDate) else null
                specialOmRow = snapshot.omAdjustment?.let(::omReportRow)
                PayJourneyReportRow(conversionDate(result.conversionDate), if (specialOmRow != null) 1 else 0, CpcHistoryStage.SIXTH, CpcJourneyEventKind.CPC_CONVERSION,
                    "5th to 6th CPC conversion", sixthPosition(result.scale.payBand, result.gradePay, result.payInPayBand),
                    remarks = if (hasDniSupport) "Conversion result ${result.revisedBasicPay}; next increment ${result.nextIncrementDate}" else "Conversion result ${result.revisedBasicPay}",
                    sourcePosition = CpcPayPosition(CpcHistoryStage.FIFTH, resultPay, scaleId = scale.title, scaleTitle = scale.title))
            }
            is StandaloneCpcConversionSnapshot.SixthToSeventh -> {
                val band = SixthToSeventhCpcData.payBands.firstOrNull { it.title.substringBefore(":").trim() == snapshot.payBandId } ?: return null
                val result = calculateSixthToSeventhCpc(snapshot.payInPayBand, snapshot.gradePay, band) ?: return null
                PayJourneyReportRow(java.util.Calendar.getInstance().apply { clear(); set(2016, java.util.Calendar.JANUARY, 1) }.timeInMillis, 0, CpcHistoryStage.SEVENTH, CpcJourneyEventKind.CPC_CONVERSION,
                    "6th to 7th CPC conversion", CpcPayPosition(CpcHistoryStage.SEVENTH, result.revisedBasicPay, level = result.level),
                    remarks = "Conversion result ${result.revisedBasicPay}", sourcePosition = sixthPosition(snapshot.payBandId, snapshot.gradePay, snapshot.payInPayBand))
            }
        }
        return PayJourneyReport(record.title ?: "CPC Conversion Report", record.savedAtMillis, record.startingCpc,
            row.dateMillis ?: record.savedAtMillis,
            listOf(PayJourneyReportSection("CPC Conversion", listOfNotNull(specialOmRow, row))), row.position,
            nextIncrementDateMillis)
    }

    private fun sixthPosition(band: String, gp: Int, pb: Int) = CpcPayPosition(CpcHistoryStage.SIXTH, pb + gp, payBandId = band, gradePay = gp, payInPayBand = pb)
    private fun eventKind(result: SixthCpcEventResult) = when {
        result.financialUpgradation == SixthCpcFinancialUpgradation.ACP -> CpcJourneyEventKind.ACP
        result.financialUpgradation == SixthCpcFinancialUpgradation.MACP -> CpcJourneyEventKind.MACP
        result.eventType.equals("promotion", true) -> CpcJourneyEventKind.PROMOTION
        else -> CpcJourneyEventKind.FINANCIAL_UPGRADATION
    }
}

/** True only when the canonical journey carries positive, unique shared sequence values. */
internal fun hasValidSharedApplicationSequence(snapshot: CompleteJourneySnapshot): Boolean {
    val values = buildList {
        snapshot.fourth?.let { add(it.sequence); addAll(it.increments.map { item -> item.sequence }); addAll(it.events.map { item -> item.sequence }) }
        snapshot.fifth?.let { add(it.sequence); addAll(it.increments.map { item -> item.sequence }); addAll(it.events.map { item -> item.sequence }) }
        val sixth = snapshot.sixth ?: snapshot.fifth?.sixthContinuation
        sixth?.let { stage ->
            add(stage.sequence); addAll(stage.increments.map { it.sequence })
            stage.eventChains.forEach { chain -> add(chain.sequence); addAll(chain.increments.map { it.sequence }) }
        }
        val seventh = snapshot.seventh ?: sixth?.seventhContinuation
        fun addPromotion(promotion: SeventhCpcPromotionSnapshot, output: MutableList<Int>) {
            output.add(promotion.sequence); output.addAll(promotion.postIncrements.map { it.sequence })
            promotion.subsequentPromotion?.let { addPromotion(it, output) }
        }
        seventh?.let { stage ->
            add(stage.sequence); addAll(stage.increments.map { it.sequence })
            stage.promotions.forEach { addPromotion(it, this) }
        }
    }
    return values.isNotEmpty() && values.all { it > 0 } && values.distinct().size == values.size
}

/** Assign one monotonically increasing application sequence across the saved CPC stages. */
fun assignJourneyApplicationSequence(snapshot: CompleteJourneySnapshot): CompleteJourneySnapshot {
    if (snapshot.sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL && hasValidSharedApplicationSequence(snapshot)) return snapshot
    var next = 0
    fun <I, E> merge(
        increments: List<I>, incrementSeq: (I) -> Int, setIncrement: (I, Int) -> I,
        events: List<E>, eventSeq: (E) -> Int, setEvent: (E, Int) -> E
    ): Pair<List<I>, List<E>> {
        val refs = buildList {
            increments.forEachIndexed { index, item -> add(Triple(incrementSeq(item), 0, index)) }
            events.forEachIndexed { index, item -> add(Triple(eventSeq(item), 1, index)) }
        }.sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third })
        val updatedIncrements = increments.toMutableList()
        val updatedEvents = events.toMutableList()
        refs.forEach { (_, type, index) ->
            next++
            if (type == 0) updatedIncrements[index] = setIncrement(updatedIncrements[index], next)
            else updatedEvents[index] = setEvent(updatedEvents[index], next)
        }
        return updatedIncrements to updatedEvents
    }
    var fourth = snapshot.fourth
    var fifth = snapshot.fifth
    var sixth = snapshot.sixth ?: snapshot.fifth?.sixthContinuation
    var seventh = snapshot.seventh ?: sixth?.seventhContinuation
    if (snapshot.startingCpc <= CpcHistoryStage.FOURTH) fourth = fourth?.let { stage ->
        val startSequence = ++next
        val (increments, events) = merge(stage.increments, { it.sequence }, { item, seq -> item.copy(sequence = seq) }, stage.events, { it.sequence }, { item, seq -> item.copy(sequence = seq) })
        stage.copy(increments = increments, events = events, sequence = startSequence)
    }
    if (snapshot.startingCpc <= CpcHistoryStage.FIFTH) fifth = fifth?.let { stage ->
        val startSequence = ++next
        val (increments, events) = merge(stage.increments, { it.sequence }, { item, seq -> item.copy(sequence = seq) }, stage.events, { it.sequence }, { item, seq -> item.copy(sequence = seq) })
        stage.copy(increments = increments, events = events, sequence = startSequence)
    }
    if (snapshot.startingCpc <= CpcHistoryStage.SIXTH) sixth = sixth?.let { stage ->
        val startSequence = ++next
        val increments = stage.increments.toMutableList()
        val chains = stage.eventChains.toMutableList()
        val refs = buildList {
            stage.increments.forEachIndexed { index, value -> add(Triple(value.sequence, 0, index)) }
            stage.eventChains.forEachIndexed { index, value -> add(Triple(value.sequence, 1, index)) }
        }.sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third })
        refs.forEach { (_, type, index) ->
            if (type == 0) increments[index] = increments[index].copy(sequence = ++next)
            else {
                val chain = chains[index]
                val eventSequence = ++next
                chains[index] = chain.copy(sequence = eventSequence, increments = chain.increments.sortedBy { it.sequence }.map { it.copy(sequence = ++next) })
            }
        }
        stage.copy(increments = increments, eventChains = chains, sequence = startSequence)
    }
    if (snapshot.startingCpc <= CpcHistoryStage.SEVENTH) seventh = seventh?.let { stage ->
        val startSequence = ++next
        val increments = stage.increments.toMutableList()
        val promotions = stage.promotions.toMutableList()
        fun renumberPromotion(promotion: SeventhCpcPromotionSnapshot): SeventhCpcPromotionSnapshot {
            val eventSequence = ++next
            val post = promotion.postIncrements.sortedBy { it.sequence }.map { it.copy(sequence = ++next) }
            val nested = promotion.subsequentPromotion?.let(::renumberPromotion)
            return promotion.copy(sequence = eventSequence, postIncrements = post, subsequentPromotion = nested)
        }
        val refs = buildList {
            stage.increments.forEachIndexed { index, value -> add(Triple(value.sequence, 0, index)) }
            stage.promotions.forEachIndexed { index, value -> add(Triple(value.sequence, 1, index)) }
        }.sortedWith(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third })
        refs.forEach { (_, type, index) ->
            if (type == 0) increments[index] = increments[index].copy(sequence = ++next)
            else promotions[index] = renumberPromotion(promotions[index])
        }
        stage.copy(increments = increments, promotions = promotions, sequence = startSequence)
    }
    val normalizedSixth = sixth?.copy(seventhContinuation = seventh)
    val normalizedFifth = fifth?.copy(sixthContinuation = if (fifth.sixthContinuation != null) normalizedSixth else fifth.sixthContinuation)
    return snapshot.copy(
        fourth = fourth,
        fifth = normalizedFifth,
        sixth = if (snapshot.sixth != null) normalizedSixth else null,
        seventh = if (snapshot.seventh != null) seventh else null
    )
}

/** Private preference namespace. It deliberately does not touch the legacy HistoryStore keys. */
object CpcHistoryStore {
    private const val PREFS = "cpc_journey_history_v1"
    private const val KEY_RECORDS = "records"

    fun getAll(context: android.content.Context): List<CpcHistoryRecord> {
        val raw = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(KEY_RECORDS, "[]") ?: "[]"
        return CpcHistoryCodec.decodeAll(raw)
    }

    fun getById(context: android.content.Context, id: String): CpcHistoryRecord? = getAll(context).firstOrNull { it.uniqueId == id }

    fun save(context: android.content.Context, record: CpcHistoryRecord) {
        require(record.schemaVersion == CPC_HISTORY_SCHEMA_VERSION)
        write(context, CpcHistoryCollection.save(getAll(context), record))
    }

    fun delete(context: android.content.Context, id: String) = write(context, CpcHistoryCollection.delete(getAll(context), id))
    fun clear(context: android.content.Context) = write(context, CpcHistoryCollection.clear())
    fun encode(record: CpcHistoryRecord): String = encodeRecord(record).toString()
    fun encodeAll(records: List<CpcHistoryRecord>): String = JSONArray().apply { records.forEach { put(encodeRecord(it)) } }.toString()
    fun decodeAll(json: String): List<CpcHistoryRecord> {
        val array = runCatching { JSONArray(json) }.getOrElse { return emptyList() }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching { decodeRecord(item) }.getOrNull()?.let(::add)
            }
        }.sortedByDescending { it.savedAtMillis }
    }
    fun decode(json: String): CpcHistoryRecord? = runCatching { decodeRecord(JSONObject(json)) }.getOrNull()

    private fun write(context: android.content.Context, records: List<CpcHistoryRecord>) {
        val array = JSONArray()
        records.forEach { array.put(encodeRecord(it)) }
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun encodeRecord(r: CpcHistoryRecord): JSONObject {
        require((r.workflowType == CpcHistoryWorkflow.COMPLETE_JOURNEY) == (r.payload is CompleteJourneyPayload)) {
            "History workflow and payload type do not match"
        }
        return JSONObject().apply {         put("schemaVersion", r.schemaVersion); put("uniqueId", r.uniqueId); put("workflowType", r.workflowType.name)
        put("schemaVersion", r.schemaVersion); put("uniqueId", r.uniqueId); put("workflowType", r.workflowType.name)
        put("title", r.title ?: JSONObject.NULL); put("savedAtMillis", r.savedAtMillis); put("startingCpc", r.startingCpc.name); put("currentStage", r.currentStage.name)
        put("officialName", r.officialName ?: JSONObject.NULL); put("designation", r.designation ?: JSONObject.NULL)
        put("payload", when (val p = r.payload) {
            is CompleteJourneyPayload -> encodeJourney(p.snapshot)
            is StandaloneConversionPayload -> encodeStandalone(p.snapshot)
        })
        }
    }

    private fun decodeRecord(o: JSONObject): CpcHistoryRecord {
        val version = o.optInt("schemaVersion", -1)
        require(version == CPC_HISTORY_SCHEMA_VERSION) { "Unsupported CPC history schema version: $version" }
        val workflow = CpcHistoryWorkflow.valueOf(o.getString("workflowType"))
        val payloadObject = o.getJSONObject("payload")
        val payload = when (workflow) {
            CpcHistoryWorkflow.COMPLETE_JOURNEY -> CompleteJourneyPayload(decodeJourney(payloadObject))
            CpcHistoryWorkflow.CPC_CONVERSION_ONLY -> StandaloneConversionPayload(decodeStandalone(payloadObject))
        }
        return CpcHistoryRecord(o.getString("uniqueId"), version, workflow,
            o.optString("title").takeIf { it.isNotBlank() && it != "null" }, o.getLong("savedAtMillis"),
            CpcHistoryStage.valueOf(o.getString("startingCpc")), CpcHistoryStage.valueOf(o.getString("currentStage")), payload,
            o.optString("officialName").takeIf { it.isNotBlank() && it != "null" },
            o.optString("designation").takeIf { it.isNotBlank() && it != "null" })
    }

    private fun encodeJourney(s: CompleteJourneySnapshot) = JSONObject().apply {
        put("kind", "completeJourney"); put("startingCpc", s.startingCpc.name); put("startingDateMillis", s.startingDateMillis)
        put("sequenceIntegrity", s.sequenceIntegrity.name)
        if (s.sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL && hasValidSharedApplicationSequence(s)) {
            put("sharedApplicationSequenceVersion", SHARED_APPLICATION_SEQUENCE_VERSION)
        }
        put("fourth", s.fourth?.let(::encodeFourth) ?: JSONObject.NULL); put("fifth", s.fifth?.let(::encodeFifth) ?: JSONObject.NULL)
        put("sixth", s.sixth?.let(::encodeSixth) ?: JSONObject.NULL); put("seventh", s.seventh?.let(::encodeSeventh) ?: JSONObject.NULL)
    }
    private fun decodeJourney(o: JSONObject) = CompleteJourneySnapshot(CpcHistoryStage.valueOf(o.getString("startingCpc")), o.getLong("startingDateMillis"),
        nullableObject(o, "fourth")?.let(::decodeFourth), nullableObject(o, "fifth")?.let(::decodeFifth),
        nullableObject(o, "sixth")?.let(::decodeSixth), nullableObject(o, "seventh")?.let(::decodeSeventh),
        if (o.optString("sequenceIntegrity") == CpcSequenceIntegrity.INFERRED.name) CpcSequenceIntegrity.INFERRED
        else if (o.optInt("sharedApplicationSequenceVersion", 0) == SHARED_APPLICATION_SEQUENCE_VERSION &&
            hasValidSharedApplicationSequence(o)) CpcSequenceIntegrity.ORIGINAL else CpcSequenceIntegrity.INFERRED)

    private fun hasValidSharedApplicationSequence(snapshot: CompleteJourneySnapshot): Boolean =
        com.niyammitra.payfixationcalculator.hasValidSharedApplicationSequence(snapshot)

    private fun hasValidSharedApplicationSequence(o: JSONObject): Boolean = runCatching {
        val decoded = CompleteJourneySnapshot(CpcHistoryStage.valueOf(o.getString("startingCpc")), o.getLong("startingDateMillis"),
            nullableObject(o, "fourth")?.let(::decodeFourth), nullableObject(o, "fifth")?.let(::decodeFifth),
            nullableObject(o, "sixth")?.let(::decodeSixth), nullableObject(o, "seventh")?.let(::decodeSeventh), CpcSequenceIntegrity.INFERRED)
        hasValidSharedApplicationSequence(decoded)
    }.getOrDefault(false)

    private const val SHARED_APPLICATION_SEQUENCE_VERSION = 1

    private fun encodeFourth(s: FourthCpcJourneySnapshot) = JSONObject().apply {
        put("scaleId", s.scaleId); put("scaleTitle", s.scaleTitle); put("startingBasicPay", s.startingBasicPay); put("payDateMillis", s.payDateMillis)
        put("nextIncrementDateMillis", s.nextIncrementDateMillis ?: JSONObject.NULL); put("increments", JSONArray().apply { s.increments.forEach { put(JSONObject().put("order", it.order).put("sequence", it.sequence).put("pay", it.pay).put("date", it.dateMillis)) } })
        put("events", JSONArray().apply { s.events.forEach { put(JSONObject().put("order", it.order).put("sequence", it.sequence).put("date", it.eventDateMillis).put("type", it.eventType.name).put("scale", it.targetScaleId).put("pay", it.resultingPay)) } })
        put("conversionActivated", s.conversionActivated); put("sequence", s.sequence)
    }
    private fun decodeFourth(o: JSONObject) = FourthCpcJourneySnapshot(o.getString("scaleId"), o.getString("scaleTitle"), o.getInt("startingBasicPay"), o.getLong("payDateMillis"),
        nullableLong(o, "nextIncrementDateMillis"), decodeIncrements(o.optJSONArray("increments")), buildList { val a = o.optJSONArray("events") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(FourthCpcEventSnapshot(it.getInt("order"), it.getLong("date"), CpcJourneyEventKind.valueOf(it.getString("type")), it.getString("scale"), it.getInt("pay"), it.optInt("sequence", it.optInt("order", i)))) } }, o.optBoolean("conversionActivated"), o.optInt("sequence", 0))

    private fun encodeFifth(s: FifthCpcJourneySnapshot) = JSONObject().apply {
        put("startingDateMillis", s.startingDateMillis); put("scaleId", s.scaleId); put("startingBasicPay", s.startingBasicPay); put("startingDniMillis", s.startingDniMillis ?: JSONObject.NULL)
        put("increments", encodeIncrements(s.increments)); put("events", JSONArray().apply { s.events.forEach { put(JSONObject().put("order", it.order).put("sequence", it.sequence).put("type", it.eventType.name).put("eventDate", it.eventDateMillis).put("implementationDate", it.implementationDateMillis).put("targetScale", it.targetScaleId).put("pay", it.resultingPay).put("dni", it.resultingDniMillis).put("basis", it.fixationBasis.name).put("placement", it.placementMethod).put("implementationOption", it.implementationOption).put("omAppliedBeforeEvent", it.omAppliedBeforeEvent)) } })
        put("sixthContinuation", s.sixthContinuation?.let(::encodeSixth) ?: JSONObject.NULL); put("sequence", s.sequence)
        put("omAdjustment", s.omAdjustment?.let(::encodeOmAdjustment) ?: JSONObject.NULL)
    }
    private fun decodeFifth(o: JSONObject) = FifthCpcJourneySnapshot(o.getLong("startingDateMillis"), o.getString("scaleId"), o.getInt("startingBasicPay"), nullableLong(o, "startingDniMillis"),
        decodeIncrements(o.optJSONArray("increments")), buildList { val a = o.optJSONArray("events") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(FifthCpcEventSnapshot(it.getInt("order"), CpcJourneyEventKind.valueOf(it.getString("type")), it.getLong("eventDate"), it.getLong("implementationDate"), it.getString("targetScale"), it.getInt("pay"), it.getLong("dni"), CpcFixationBasis.valueOf(it.getString("basis")), it.optString("placement"), it.optString("implementationOption"), it.optInt("sequence", it.optInt("order", i)), it.optBoolean("omAppliedBeforeEvent", false))) } }, nullableObject(o, "sixthContinuation")?.let(::decodeSixth), o.optInt("sequence", 0), nullableObject(o, "omAdjustment")?.let(::decodeOmAdjustment))

    private fun encodeOmAdjustment(s: FifthCpcOmAdjustmentSnapshot) = JSONObject().apply {
        put("normalDniMillis", s.normalDniMillis); put("incrementDateMillis", s.incrementDateMillis); put("scaleTitle", s.scaleTitle)
        put("basicPayBefore", s.basicPayBefore); put("incrementAmount", s.incrementAmount); put("adjustedBasicPay", s.adjustedBasicPay)
        put("nextRevisedIncrementDateMillis", s.nextRevisedIncrementDateMillis)
    }

    private fun decodeOmAdjustment(o: JSONObject) = FifthCpcOmAdjustmentSnapshot(
        o.getLong("normalDniMillis"), o.getLong("incrementDateMillis"), o.getString("scaleTitle"),
        o.getInt("basicPayBefore"), o.getInt("incrementAmount"), o.getInt("adjustedBasicPay"),
        o.getLong("nextRevisedIncrementDateMillis")
    )

    private fun encodeSixth(s: SixthCpcJourneySnapshot) = JSONObject().apply {
        put("startingDateMillis", s.startingDateMillis); put("payBandId", s.payBandId); put("gradePay", s.gradePay); put("startingPayInPayBand", s.startingPayInPayBand)
        put("increments", JSONArray().apply { s.increments.forEach { put(JSONObject().put("order", it.order).put("sequence", it.sequence).put("payInPayBand", it.payInPayBand).put("gradePay", it.gradePay).put("date", it.dateMillis)) } })
        put("eventChains", encodeChains(s.eventChains)); put("seventhContinuation", s.seventhContinuation?.let(::encodeSeventh) ?: JSONObject.NULL); put("sequence", s.sequence)
    }
    private fun decodeSixth(o: JSONObject) = SixthCpcJourneySnapshot(o.getLong("startingDateMillis"), o.getString("payBandId"), o.getInt("gradePay"), o.getInt("startingPayInPayBand"),
        buildList { val a = o.optJSONArray("increments") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(SixthCpcIncrementSnapshot(it.getInt("order"), it.getInt("payInPayBand"), it.getInt("gradePay"), it.getLong("date"), it.optInt("sequence", it.optInt("order", i)))) } }, decodeChains(o.optJSONArray("eventChains")), nullableObject(o, "seventhContinuation")?.let(::decodeSeventh), o.optInt("sequence", 0))

    private fun encodeSeventh(s: SeventhCpcJourneySnapshot) = JSONObject().apply {
        put("startingLevel", s.startingLevel); put("startingBasicPay", s.startingBasicPay); put("conversionDateMillis", s.conversionDateMillis); put("startingDniMillis", s.startingDniMillis ?: JSONObject.NULL); put("sequence", s.sequence); put("increments", encodeIncrements(s.increments))
        put("promotions", JSONArray().apply { s.promotions.forEach { put(encodePromotion(it)) } })
    }
    private fun decodeSeventh(o: JSONObject) = SeventhCpcJourneySnapshot(o.getString("startingLevel"), o.getInt("startingBasicPay"), o.getLong("conversionDateMillis"), decodeIncrements(o.optJSONArray("increments")),
        buildList { val a = o.optJSONArray("promotions") ?: JSONArray(); for (i in 0 until a.length()) a.optJSONObject(i)?.let { add(decodePromotion(it)) } }, nullableLong(o, "startingDniMillis"), o.optInt("sequence", 0))
    private fun encodePromotion(s: SeventhCpcPromotionSnapshot): JSONObject = JSONObject().apply { put("currentLevel", s.currentLevel); put("currentPay", s.currentPay); put("knownDni", s.knownDniMillis); put("promotedLevel", s.promotedLevel ?: JSONObject.NULL); put("promotionDate", s.promotionDateMillis ?: JSONObject.NULL); put("basis", s.fixationBasis.name); put("postIncrements", encodeIncrements(s.postIncrements)); put("next", s.subsequentPromotion?.let { encodePromotion(it) } ?: JSONObject.NULL); put("resultingPay", s.resultingPay ?: JSONObject.NULL); put("resultingDni", s.resultingDniMillis ?: JSONObject.NULL); put("eventKind", s.eventKind.name); put("sequence", s.sequence) }
    private fun decodePromotion(o: JSONObject): SeventhCpcPromotionSnapshot = SeventhCpcPromotionSnapshot(o.getString("currentLevel"), o.getInt("currentPay"), o.getLong("knownDni"), o.optString("promotedLevel").takeIf { it.isNotBlank() && it != "null" }, nullableLong(o, "promotionDate"), CpcFixationBasis.valueOf(o.optString("basis", CpcFixationBasis.EVENT_DATE.name)), decodeIncrements(o.optJSONArray("postIncrements")), nullableObject(o, "next")?.let(::decodePromotion), nullableInt(o, "resultingPay"), nullableLong(o, "resultingDni"), CpcJourneyEventKind.valueOf(o.optString("eventKind", CpcJourneyEventKind.OTHER.name)), o.optInt("sequence", 0))

    private fun encodeStandalone(s: StandaloneCpcConversionSnapshot) = JSONObject().apply { put("kind", s.kind.name); when (s) {
        is StandaloneCpcConversionSnapshot.FourthToFifth -> { put("scaleId", s.scaleId); put("existingBasicPay", s.existingBasicPay) }
        is StandaloneCpcConversionSnapshot.FifthToSixth -> {
            put("scaleId", s.scaleId); put("existingBasicPay", s.existingBasicPay)
            put("normalDniMillis", s.normalDniMillis ?: JSONObject.NULL)
            put("omAdjustment", s.omAdjustment?.let(::encodeOmAdjustment) ?: JSONObject.NULL)
        }
        is StandaloneCpcConversionSnapshot.SixthToSeventh -> { put("payBandId", s.payBandId); put("payInPayBand", s.payInPayBand); put("gradePay", s.gradePay) }
    } }
    private fun decodeStandalone(o: JSONObject): StandaloneCpcConversionSnapshot = when (StandaloneConversionKind.valueOf(o.getString("kind"))) {
        StandaloneConversionKind.FOURTH_TO_FIFTH -> StandaloneCpcConversionSnapshot.FourthToFifth(o.getString("scaleId"), o.getInt("existingBasicPay"))
        StandaloneConversionKind.FIFTH_TO_SIXTH -> StandaloneCpcConversionSnapshot.FifthToSixth(
            o.getString("scaleId"), o.getInt("existingBasicPay"), nullableLong(o, "normalDniMillis"),
            nullableObject(o, "omAdjustment")?.let(::decodeOmAdjustment)
        )
        StandaloneConversionKind.SIXTH_TO_SEVENTH -> StandaloneCpcConversionSnapshot.SixthToSeventh(o.getString("payBandId"), o.getInt("payInPayBand"), o.getInt("gradePay"))
    }

    private fun encodeIncrements(xs: List<CpcIncrementSnapshot>) = JSONArray().apply { xs.forEach { put(JSONObject().put("order", it.order).put("sequence", it.sequence).put("pay", it.pay).put("date", it.dateMillis)) } }
    private fun decodeIncrements(a: JSONArray?) = buildList { val array = a ?: JSONArray(); for (i in 0 until array.length()) array.optJSONObject(i)?.let { val order = it.optInt("order", i); add(CpcIncrementSnapshot(order, it.getInt("pay"), it.getLong("date"), it.optInt("sequence", order))) } }

    private fun encodeChains(chains: List<SixthCpcEventChain>) = JSONArray().apply { chains.forEach { chain -> put(JSONObject().apply {
        put("kind", chain.kind.name); put("sequence", chain.sequence); put("result", chain.result?.let { r -> JSONObject().apply { put("eventType", r.eventType); put("financialUpgradation", r.financialUpgradation?.name ?: JSONObject.NULL); put("fixationOption", r.fixationOption.name); put("eventDate", r.eventDate); put("oldPayInPayBand", r.oldPayInPayBand); put("oldGradePay", r.oldGradePay); put("increment", r.increment); put("newPayInPayBand", r.newPayInPayBand); put("newGradePay", r.newGradePay); put("newPayBand", r.newPayBand); put("revisedBasicPay", r.revisedBasicPay); put("nextIncrementDate", r.nextIncrementDate); put("ruleBasis", JSONArray(r.ruleBasis)) } } ?: JSONObject.NULL)
        put("scaleUpgrade", chain.scaleUpgrade?.let { r -> JSONObject().apply { put("eventDate", r.eventDate); put("oldPayInPayBand", r.oldPayInPayBand); put("oldGradePay", r.oldGradePay); put("oldPayBand", r.oldPayBand); put("newPayInPayBand", r.newPayInPayBand); put("newGradePay", r.newGradePay); put("newPayBand", r.newPayBand); put("revisedBasicPay", r.revisedBasicPay); put("nextIncrementDate", r.nextIncrementDate); put("historicalRoute", r.historicalRoute?.name ?: JSONObject.NULL); put("sourcePreRevisedBasicPay", r.sourcePreRevisedBasicPay ?: JSONObject.NULL); put("fixationIncrement", r.fixationIncrement ?: JSONObject.NULL) } } ?: JSONObject.NULL)
        put("increments", JSONArray().apply { chain.increments.forEach { put(JSONObject().put("sequence", it.sequence).put("payInPayBand", it.payInPayBand).put("gradePay", it.gradePay).put("date", it.date)) } })
    }) } }
    private fun decodeChains(a: JSONArray?) = buildList { val array = a ?: JSONArray(); for (i in 0 until array.length()) array.optJSONObject(i)?.let { o ->
        val ro = nullableObject(o, "result"); val so = nullableObject(o, "scaleUpgrade")
        val result = ro?.let { SixthCpcEventResult(it.getString("eventType"), it.optString("financialUpgradation").takeIf(String::isNotBlank)?.let(SixthCpcFinancialUpgradation::valueOf), SixthCpcFixationOption.valueOf(it.getString("fixationOption")), it.getLong("eventDate"), it.getInt("oldPayInPayBand"), it.getInt("oldGradePay"), it.getInt("increment"), it.getInt("newPayInPayBand"), it.getInt("newGradePay"), it.getString("newPayBand"), it.getInt("revisedBasicPay"), it.getLong("nextIncrementDate"), jsonStrings(it.optJSONArray("ruleBasis"))) }
        val upgrade = so?.let { SixthCpcScaleUpgradeResult(it.getLong("eventDate"), it.getInt("oldPayInPayBand"), it.getInt("oldGradePay"), it.getString("oldPayBand"), it.getInt("newPayInPayBand"), it.getInt("newGradePay"), it.getString("newPayBand"), it.getInt("revisedBasicPay"), it.getLong("nextIncrementDate"), it.optString("historicalRoute").takeIf(String::isNotBlank)?.let(HistoricalSixthCpcRoute::valueOf), nullableInt(it, "sourcePreRevisedBasicPay"), nullableInt(it, "fixationIncrement")) }
        val sequence = o.optInt("sequence", 0)
        val inc = buildList { val xs = o.optJSONArray("increments") ?: JSONArray(); for (j in 0 until xs.length()) xs.optJSONObject(j)?.let { add(SixthCpcEventIncrement(it.getInt("payInPayBand"), it.getInt("gradePay"), it.getLong("date"), it.optInt("sequence", sequence + j + 1))) } }
        add(SixthCpcEventChain(SixthCpcEventKind.valueOf(o.getString("kind")), result, upgrade, inc, sequence))
    } }

    private fun nullableObject(o: JSONObject, key: String): JSONObject? = if (o.isNull(key)) null else o.optJSONObject(key)
    private fun nullableLong(o: JSONObject, key: String): Long? = if (o.isNull(key)) null else o.optLong(key)
    private fun nullableInt(o: JSONObject, key: String): Int? = if (o.isNull(key)) null else o.optInt(key)
    private fun jsonStrings(a: JSONArray?) = buildList { val array = a ?: JSONArray(); for (i in 0 until array.length()) array.optString(i)?.let(::add) }
}

object CpcHistoryCodec {
    fun encodeAll(records: List<CpcHistoryRecord>): String = CpcHistoryStore.encodeAll(records)
    fun decodeAll(json: String): List<CpcHistoryRecord> = CpcHistoryStore.decodeAll(json)
}

object CpcHistoryCollection {
    fun getById(records: List<CpcHistoryRecord>, id: String): CpcHistoryRecord? = records.firstOrNull { it.uniqueId == id }
    fun save(records: List<CpcHistoryRecord>, record: CpcHistoryRecord): List<CpcHistoryRecord> =
        (listOf(record) + records.filterNot { it.uniqueId == record.uniqueId }).take(100)
    fun delete(records: List<CpcHistoryRecord>, id: String): List<CpcHistoryRecord> = records.filterNot { it.uniqueId == id }
    fun clear(): List<CpcHistoryRecord> = emptyList()
}
