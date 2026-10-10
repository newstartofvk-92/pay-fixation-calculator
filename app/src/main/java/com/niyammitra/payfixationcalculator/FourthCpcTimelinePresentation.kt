package com.niyammitra.payfixationcalculator

internal enum class FourthCpcTimelineEntryType {
    STARTING_POSITION,
    INCREMENT,
    EVENT
}

internal data class FourthCpcTimelineEntry(
    val type: FourthCpcTimelineEntryType,
    val effectiveDateMillis: Long,
    val basicPay: Int,
    val sequence: Int = 0,
    val sourceIndex: Int? = null,
    val eventType: CpcJourneyEventKind? = null,
    val targetScaleId: String? = null
)

/** Projects the existing replay result into display rows without recalculating or changing journey data. */
internal fun buildFourthCpcTimelineEntries(
    startingPay: Int,
    startingDateMillis: Long,
    timeline: FourthCpcTimelineResult,
    sequenceIntegrity: CpcSequenceIntegrity
): List<FourthCpcTimelineEntry> {
    data class OrderedEntry(
        val entry: FourthCpcTimelineEntry,
        val listOrder: Int,
        val kindOrder: Int,
        val index: Int
    )

    val progression = buildList {
        timeline.increments.forEachIndexed { index, increment ->
            add(
                OrderedEntry(
                    entry = FourthCpcTimelineEntry(
                        type = FourthCpcTimelineEntryType.INCREMENT,
                        effectiveDateMillis = increment.date,
                        basicPay = increment.pay,
                        sequence = increment.sequence,
                        sourceIndex = index
                    ),
                    listOrder = index + 1,
                    kindOrder = 0,
                    index = index
                )
            )
        }
        timeline.events.forEachIndexed { index, event ->
            add(
                OrderedEntry(
                    entry = FourthCpcTimelineEntry(
                        type = FourthCpcTimelineEntryType.EVENT,
                        effectiveDateMillis = event.eventDateMillis,
                        basicPay = event.resultingPay,
                        sequence = event.sequence,
                        sourceIndex = index,
                        eventType = event.eventType,
                        targetScaleId = event.targetScaleId
                    ),
                    listOrder = event.order,
                    kindOrder = 1,
                    index = index
                )
            )
        }
    }.sortedWith(
        compareBy<OrderedEntry> { it.entry.effectiveDateMillis }
            .thenBy { if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) it.entry.sequence else it.listOrder }
            .thenBy { it.kindOrder }
            .thenBy { it.index }
    )

    return buildList {
        add(
            FourthCpcTimelineEntry(
                type = FourthCpcTimelineEntryType.STARTING_POSITION,
                effectiveDateMillis = startingDateMillis,
                basicPay = startingPay
            )
        )
        addAll(progression.map(OrderedEntry::entry))
    }
}
