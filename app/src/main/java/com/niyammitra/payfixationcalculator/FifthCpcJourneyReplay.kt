package com.niyammitra.payfixationcalculator

/** Replays the accepted 5th CPC rows using the existing increment and event fixation rules. */
internal data class FifthCpcReplayResult(
    val increments: List<FifthCpcHistoricalIncrementStep>,
    val events: List<FifthCpcHistoricalEventStep>,
    val pay: Int,
    val scale: FifthCpcScale,
    val date: Long,
    val dni: Long?,
    val omAdjustment: FifthCpcOmAdjustmentSnapshot?,
    val normalDniForOm: Long?,
    val eventInputs: List<FifthCpcEventReplayInput>
)

internal data class FifthCpcEventReplayInput(
    val pay: Int,
    val scale: FifthCpcScale,
    val date: Long,
    val dni: Long?
)

/** Applies one accepted event payload, retaining its shared sequence when editing. */
internal fun upsertFifthCpcHistoricalEvent(
    existingEvents: List<FifthCpcHistoricalEventStep>,
    acceptedEvent: FifthCpcHistoricalEventStep,
    editingIdentity: String?,
    nextSequence: Int
): List<FifthCpcHistoricalEventStep> {
    if (editingIdentity != null) {
        val index = existingEvents.indexOfFirst { it.eventIdentity == editingIdentity }
        // A deleted/missing edit target must never silently turn into an add.
        if (index < 0) return existingEvents
        return existingEvents.toMutableList().also { rows ->
            val existing = existingEvents[index]
            rows[index] = acceptedEvent.copy(
                sequence = existing.sequence,
                omAppliedBeforeEvent = false,
                eventIdentity = existing.eventIdentity
            )
        }
    }
    return existingEvents + acceptedEvent.copy(sequence = nextSequence, omAppliedBeforeEvent = false)
}

internal fun deleteFifthCpcHistoricalEvent(
    existingEvents: List<FifthCpcHistoricalEventStep>,
    eventIdentity: String
): List<FifthCpcHistoricalEventStep> = existingEvents.filterNot { it.eventIdentity == eventIdentity }

internal data class FifthCpcContinuationState(
    val historySnapshot: SixthCpcJourneySnapshot?,
    val restoredSnapshot: SixthCpcJourneySnapshot?,
    val visible: Boolean
)

internal fun invalidateFifthCpcContinuation(state: FifthCpcContinuationState): FifthCpcContinuationState =
    state.copy(historySnapshot = null, restoredSnapshot = null, visible = false)

internal fun replayFifthCpcJourney(
    initialPay: Int,
    initialScale: FifthCpcScale,
    initialDate: Long,
    initialDni: Long?,
    increments: List<FifthCpcHistoricalIncrementStep>,
    events: List<FifthCpcHistoricalEventStep>,
    sequenceIntegrity: CpcSequenceIntegrity
): FifthCpcReplayResult {
    data class Entry(val sequence: Int, val kind: Int, val index: Int)

    val pendingEntries = buildList {
        increments.forEachIndexed { index, row -> add(Entry(row.sequence, 1, index)) }
        events.forEachIndexed { index, row -> add(Entry(row.sequence, 0, index)) }
    }.toMutableList()

    var pay = initialPay
    var scale = initialScale
    var date = initialDate
    var dni = initialDni
    var om: FifthCpcOmAdjustmentSnapshot? = null
    var omApplied = false
    val replayedIncrements = increments.toMutableList()
    val replayedEvents = events.toMutableList()
    val eventInputs = MutableList<FifthCpcEventReplayInput?>(events.size) { null }
    var omDni = initialDni?.takeIf(::isEligibleForFifthCpcOmAdjustment)
    fun rememberEligibleDni(positionDate: Long) {
        if (positionDate < fifthCpcOmEffectiveDate() && omDni == null &&
            dni?.let(::isEligibleForFifthCpcOmAdjustment) == true) omDni = dni
    }

    fun applyOmBefore(effectiveDate: Long) {
        rememberEligibleDni(date)
        val eligibleDni = omDni
        if (!omApplied && eligibleDni != null && effectiveDate >= fifthCpcOmEffectiveDate() &&
            isEligibleForFifthCpcOmAdjustment(eligibleDni)) {
            om = calculateFifthCpcOmAdjustment(eligibleDni, pay, scale)
            om?.let {
                pay = it.adjustedBasicPay
                date = it.incrementDateMillis
                omApplied = true
            }
        }
    }

    fun effectiveDate(entry: Entry): Long = if (entry.kind == 1) {
        dni ?: increments[entry.index].date
    } else {
        val event = events[entry.index]
        if (event.implementationOption.equals("From DNI", ignoreCase = true)) dni ?: event.implementationDate
        else event.eventDate
    }

    fun compareTieBreak(left: Entry, right: Entry): Int {
        if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) {
            val sequenceOrder = left.sequence.compareTo(right.sequence)
            if (sequenceOrder != 0) return sequenceOrder
        } else {
            // Inferred records keep the pre-existing deterministic event-before-increment fallback.
            // Their original cross-type application order is not recoverable.
            val kindOrder = left.kind.compareTo(right.kind)
            if (kindOrder != 0) return kindOrder
        }
        val sequenceOrder = left.sequence.compareTo(right.sequence)
        return if (sequenceOrder != 0) sequenceOrder else left.index.compareTo(right.index)
    }

    while (pendingEntries.isNotEmpty()) {
        // Resolve dates against the current pay position once per pass. Earlier events can
        // change DNI, so downstream DNI-based entries are re-evaluated on the next pass;
        // the comparator itself sees immutable dates and is deterministic within this pass.
        val scheduledEntries = pendingEntries.map { it to effectiveDate(it) }
        val entry = scheduledEntries.minWithOrNull { left, right ->
            val dateOrder = left.second.compareTo(right.second)
            if (dateOrder != 0) dateOrder else compareTieBreak(left.first, right.first)
        }?.first ?: break
        pendingEntries.remove(entry)
        if (entry.kind == 1) {
            val step = increments[entry.index]
            val incrementDate = dni ?: step.date
            applyOmBefore(incrementDate)
            val nextPay = calculateNextFifthCpcStage(pay, scale.title) ?: pay
            pay = nextPay
            date = incrementDate
            dni = addFifthHistoricalYear(incrementDate)
            rememberEligibleDni(incrementDate)
            replayedIncrements[entry.index] = step.copy(pay = pay, date = incrementDate)
        } else {
            val event = events[entry.index]
            val targetScale = findFifthScaleForHistoricalJourney(event.scale) ?: continue
            val fromDni = event.implementationOption.equals("From DNI", ignoreCase = true)
            val implementationDate = if (fromDni) dni ?: event.implementationDate else event.eventDate
            applyOmBefore(implementationDate)
            eventInputs[entry.index] = FifthCpcEventReplayInput(pay, scale, date, dni)
            val fixation = calculateFifthCpcEventFixation(
                currentPay = pay,
                currentScale = scale,
                targetScale = targetScale,
                eventType = normalizeFifthCpcEventType(event.type),
                placementMethod = event.placementMethod,
                implementationOption = event.implementationOption,
                eventDate = event.eventDate,
                currentDni = dni,
                omAdjustment = null
            )
            val nextDni = if (fromDni) calculateNextFifthCpcDni(implementationDate)
            else calculateEventBasedFifthCpcDni(event.eventDate)
            pay = fixation.fixedPay ?: pay
            scale = targetScale
            date = implementationDate
            dni = nextDni
            rememberEligibleDni(implementationDate)
            replayedEvents[entry.index] = event.copy(
                pay = pay,
                implementationDate = implementationDate,
                dniDate = nextDni,
                omAppliedBeforeEvent = omApplied
            )
        }
    }

    val boundaryReached = date >= fifthCpcOmEffectiveDate() || (dni ?: Long.MIN_VALUE) > fifthCpcOmEffectiveDate()
    val eligibleDni = omDni
    if (!omApplied && boundaryReached && eligibleDni != null && isEligibleForFifthCpcOmAdjustment(eligibleDni)) {
        om = calculateFifthCpcOmAdjustment(eligibleDni, pay, scale)
        om?.let { pay = it.adjustedBasicPay; date = it.incrementDateMillis; omApplied = true }
    }

    return FifthCpcReplayResult(replayedIncrements, replayedEvents, pay, scale, date, dni, om, omDni,
        eventInputs.map { it ?: FifthCpcEventReplayInput(initialPay, initialScale, initialDate, initialDni) })
}

internal fun normalizeFifthCpcEventType(type: String): String = when (type.trim().uppercase().replace('_', ' ')) {
    "ACP" -> "ACP"
    "SCALE UPGRADATION", "PAY SCALE UPGRADATION" -> "Scale Upgradation"
    "FINANCIAL UPGRADATION" -> "Financial Upgradation"
    "MACP" -> "MACP"
    else -> type
}

internal fun fifthCpcOmEffectiveDate(): Long =
    java.util.Calendar.getInstance().apply {
        clear()
        set(2006, java.util.Calendar.JANUARY, 1, 0, 0, 0)
    }.timeInMillis

internal fun calculateEventBasedFifthCpcDni(eventDate: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = eventDate
        add(java.util.Calendar.YEAR, 1)
        set(java.util.Calendar.DAY_OF_MONTH, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

internal fun calculateNextFifthCpcDni(effectiveDate: Long): Long =
    java.util.Calendar.getInstance().apply {
        timeInMillis = effectiveDate
        add(java.util.Calendar.YEAR, 1)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
