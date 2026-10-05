package com.niyammitra.payfixationcalculator

internal fun replaceSeventhPromotion(
    promotions: List<SeventhCpcPromotionSnapshot>,
    editedIndex: Int?,
    accepted: SeventhCpcPromotionSnapshot?,
    fallbackSequence: Int
): List<SeventhCpcPromotionSnapshot> {
    if (accepted == null) return promotions
    val index = editedIndex?.takeIf { it in promotions.indices } ?: -1
    val saved = accepted.copy(sequence = accepted.sequence.takeIf { it > 0 } ?: if (index >= 0) promotions[index].sequence else fallbackSequence)
    return if (index < 0) promotions + saved else promotions.toMutableList().also { it[index] = saved }
}

internal fun appendSeventhPostIncrement(
    promotion: SeventhCpcPromotionSnapshot,
    increment: CpcIncrementSnapshot
): SeventhCpcPromotionSnapshot = promotion.subsequentPromotion?.let { nested ->
    promotion.copy(subsequentPromotion = appendSeventhPostIncrement(nested, increment))
} ?: promotion.copy(postIncrements = promotion.postIncrements + increment)

internal fun flattenSeventhPromotions(promotions: List<SeventhCpcPromotionSnapshot>): List<SeventhCpcPromotionSnapshot> =
    promotions.flatMap { promotion -> listOf(promotion) + promotion.subsequentPromotion?.let { flattenSeventhPromotions(listOf(it)) }.orEmpty() }

internal fun latestSeventhJourneyEventDate(
    startDateMillis: Long,
    increments: List<SeventhCpcIncrementStep>,
    promotions: List<SeventhCpcPromotionSnapshot>
): Long {
    val dates = buildList {
        add(startDateMillis)
        increments.forEach { add(it.date) }
        fun addPromotion(promotion: SeventhCpcPromotionSnapshot) {
            promotion.promotionDateMillis?.let(::add)
            promotion.postIncrements.forEach { add(it.dateMillis) }
            promotion.subsequentPromotion?.let(::addPromotion)
        }
        promotions.forEach(::addPromotion)
    }
    return dates.maxOrNull() ?: startDateMillis
}

internal data class SeventhHistoryPosition(val level: String, val pay: Int, val dni: Long)

internal fun currentSeventhPosition(
    snapshot: SeventhCpcJourneySnapshot,
    increments: List<SeventhCpcIncrementStep>,
    promotions: List<SeventhCpcPromotionSnapshot>,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
): SeventhHistoryPosition {
    var result = SeventhHistoryPosition(snapshot.startingLevel, snapshot.startingBasicPay,
        snapshot.startingDniMillis ?: SeventhCpcHistoryStartDni)
    fun finalPromotion(p: SeventhCpcPromotionSnapshot): SeventhHistoryPosition {
        val level = p.promotedLevel ?: p.currentLevel
        val lastPostIncrement = if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) p.postIncrements.maxByOrNull { it.sequence }
            else p.postIncrements.withIndex().maxWithOrNull(compareBy<IndexedValue<CpcIncrementSnapshot>> { it.value.dateMillis }.thenBy { it.index })?.value
        val pay = lastPostIncrement?.pay ?: p.resultingPay ?: p.currentPay
        val dni = lastPostIncrement?.let { addSeventhHistoryYears(it.dateMillis, 1) }
            ?: p.resultingDniMillis ?: p.knownDniMillis
        return p.subsequentPromotion?.let(::finalPromotion) ?: SeventhHistoryPosition(level, pay, dni)
    }
    val latestIncrement = if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) increments.maxByOrNull { it.sequence }
        else increments.withIndex().maxWithOrNull(compareBy<IndexedValue<SeventhCpcIncrementStep>> { it.value.date }.thenBy { it.index })?.value
    fun promotionDate(p: SeventhCpcPromotionSnapshot): Long? = p.subsequentPromotion?.let(::promotionDate)
        ?: p.postIncrements.withIndex().maxWithOrNull(compareBy<IndexedValue<CpcIncrementSnapshot>> { it.value.dateMillis }.thenBy { it.index })?.value?.dateMillis
        ?: p.promotionDateMillis
    val latestPromotion = if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) {
        promotions.withIndex().maxWithOrNull(compareBy<IndexedValue<SeventhCpcPromotionSnapshot>> { it.value.maxApplicationSequence() }
            .thenBy { promotionDate(it.value) ?: Long.MIN_VALUE }.thenBy { it.index })
    } else {
        promotions.withIndex().maxWithOrNull(compareBy<IndexedValue<SeventhCpcPromotionSnapshot>> { promotionDate(it.value) ?: Long.MIN_VALUE }
            .thenBy { it.index })
    }
    val promotionPosition = latestPromotion?.value?.let(::finalPromotion)
    val promotionComesLast = latestPromotion != null && when (sequenceIntegrity) {
        CpcSequenceIntegrity.ORIGINAL -> latestIncrement == null || latestPromotion.value.maxApplicationSequence() > latestIncrement.sequence
        CpcSequenceIntegrity.INFERRED -> latestIncrement == null || (promotionDate(latestPromotion.value) ?: Long.MIN_VALUE) >= latestIncrement.date
    }
    when {
        promotionPosition != null && promotionComesLast -> result = promotionPosition
        latestIncrement != null -> result = SeventhHistoryPosition(promotionPosition?.level ?: result.level, latestIncrement.pay, addSeventhHistoryYears(latestIncrement.date, 1))
        promotionPosition != null -> result = promotionPosition
    }
    return result
}

internal fun addSeventhHistoryYears(date: Long, years: Int) = java.util.Calendar.getInstance().apply { timeInMillis = date; add(java.util.Calendar.YEAR, years) }.timeInMillis

private const val SeventhCpcHistoryStartDni = 1467331200000L // 01 July 2016
