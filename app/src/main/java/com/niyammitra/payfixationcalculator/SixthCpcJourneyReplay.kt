package com.niyammitra.payfixationcalculator

/** A source position used by the 6th CPC journey replay and its state mutations. */
data class SixthCpcJourneyStartingPosition(
    val dateMillis: Long,
    val payBand: String,
    val gradePay: Int,
    val payInPayBand: Int
)

data class SixthCpcJourneyState(
    val startingPosition: SixthCpcJourneyStartingPosition,
    val increments: List<SixthCpcHistoricalIncrement> = emptyList(),
    val events: List<SixthCpcEventChain> = emptyList(),
    val sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
)

data class SixthCpcJourneyPosition(
    val dateMillis: Long,
    val payBand: String,
    val gradePay: Int,
    val payInPayBand: Int,
    val sequence: Int,
    val nextDniMillis: Long
) {
    val basicPay: Int get() = payInPayBand + gradePay
}

data class SixthCpcJourneyReplay(
    val increments: List<SixthCpcHistoricalIncrement>,
    val events: List<SixthCpcEventChain>,
    val positions: List<SixthCpcJourneyPosition>,
    val finalPosition: SixthCpcJourneyPosition,
    val timeline: List<SixthCpcTimelineEntry>
)

enum class SixthCpcTimelineKind { STARTING_POSITION, ANNUAL_INCREMENT, EVENT, EVENT_INCREMENT }
data class SixthCpcTimelineEntry(
    val kind: SixthCpcTimelineKind,
    val dateMillis: Long,
    val sequence: Int,
    val position: SixthCpcJourneyPosition,
    val description: String,
    val dniMillis: Long? = null,
    val eventId: String? = null,
    val incrementIndex: Int? = null,
    val nestedIncrementIndex: Int? = null
)

data class SixthCpcMutationResult(
    val state: SixthCpcJourneyState,
    val replay: SixthCpcJourneyReplay?,
    val error: String? = null
) {
    val accepted: Boolean get() = error == null && replay != null
}

data class SixthCpcContinuationMutationState<T>(
    val sixthCpc: SixthCpcJourneyState,
    val seventhCpc: T?
) {
    fun withSixthCpc(updated: SixthCpcJourneyState): SixthCpcContinuationMutationState<T> =
        if (updated == sixthCpc) this else copy(sixthCpc = updated, seventhCpc = null)
}

data class SixthCpcNestedIncrementRef(val eventId: String, val incrementIndex: Int)

data class SixthCpcEventDeletionPlan(
    val targetEventId: String,
    val removedTopIncrementIndices: Set<Int>,
    val removedEventIds: Set<String>,
    val removedNestedIncrements: Set<SixthCpcNestedIncrementRef>
)

/** Plans the exact entries removed by the explicit event-and-later deletion path. */
fun planSixthCpcEventAndLaterDeletion(state: SixthCpcJourneyState, eventId: String): SixthCpcEventDeletionPlan? {
    val target = state.events.firstOrNull { it.localId == eventId } ?: return null
    val targetDate = target.result?.eventDate ?: target.scaleUpgrade?.eventDate ?: return null
    val targetSequence = target.sequence
    val targetIndex = state.events.indexOfFirst { it.localId == eventId }
    val removedEventIds = state.events.withIndex().filter { (index, event) ->
        if (state.sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) event.sequence > targetSequence
        else {
            val eventDate = event.result?.eventDate ?: event.scaleUpgrade?.eventDate ?: Long.MAX_VALUE
            eventDate > targetDate || (eventDate == targetDate && index > targetIndex)
        }
    }.map { it.value.localId }.toSet() + eventId
    val removedTopIncrementIndices = state.increments.withIndex().filter { (_, increment) ->
        if (state.sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) {
            increment.date > targetDate || increment.sequence > targetSequence
        } else increment.date >= targetDate
    }.map { it.index }.toSet()
    val removedNestedIncrements = buildSet {
        state.events.forEach { event ->
            event.increments.forEachIndexed { index, increment ->
                val remove = event.localId in removedEventIds ||
                    (state.sequenceIntegrity == CpcSequenceIntegrity.INFERRED && increment.date >= targetDate)
                if (remove) add(SixthCpcNestedIncrementRef(event.localId, index))
            }
        }
    }
    return SixthCpcEventDeletionPlan(eventId, removedTopIncrementIndices, removedEventIds, removedNestedIncrements)
}

/** A changed effective 6th CPC source position invalidates every downstream 7th CPC entry. */
fun <T> seventhContinuationAfterSixthSourceChange(
    previous: SixthCpcJourneyState,
    updated: SixthCpcJourneyState,
    continuation: T?
): T? = if (previous == updated) continuation else null

/** Shared chronological rows for the live 6th CPC PDF and report consistency tests. */
internal fun sixthCpcTimelineExportLines(
    replay: SixthCpcJourneyReplay,
    dateLabel: (Long) -> String,
    amountLabel: (Int) -> String
): List<String> = replay.timeline.map { row ->
    val pay = "${dateLabel(row.dateMillis)} — ${row.description}: ${row.position.payBand}; " +
        "pay in pay band ${amountLabel(row.position.payInPayBand)} + Grade Pay ${amountLabel(row.position.gradePay)} = " +
        amountLabel(row.position.basicPay)
    if (row.dniMillis != null) "$pay; next DNI ${dateLabel(row.dniMillis)}" else pay
}

/** Production mutation path used by the 6th CPC screen callbacks and unit tests. */
fun addSixthCpcEvent(
    state: SixthCpcJourneyState,
    event: SixthCpcEventChain,
    initialSequence: Int = 0
): SixthCpcMutationResult {
    if (state.events.any { it.localId == event.localId }) {
        return SixthCpcMutationResult(state, null, "This event already exists in the journey.")
    }
    val sequence = nextSixthCpcApplicationSequence(state.increments, state.events, initialSequence)
    return replayMutation(state.copy(events = state.events + event.copy(sequence = sequence)), state)
}

/** Replays a restored journey after its authoritative starting position is edited. */
fun replaySixthCpcJourneyFromStartingPosition(
    state: SixthCpcJourneyState,
    startingPosition: SixthCpcJourneyStartingPosition
): SixthCpcMutationResult = replayMutation(state.copy(startingPosition = startingPosition), state)

fun replaceSixthCpcEvent(state: SixthCpcJourneyState, eventId: String, replacement: SixthCpcEventChain): SixthCpcMutationResult {
    val index = state.events.indexOfFirst { it.localId == eventId }
    if (index < 0) return SixthCpcMutationResult(state, null, "This event was removed while it was being edited.")
    val existing = state.events[index]
    val updated = replacement.copy(localId = existing.localId, sequence = existing.sequence)
    return replayMutation(state.copy(events = state.events.toMutableList().also { it[index] = updated }), state)
}

fun sixthCpcPositionBeforeEvent(state: SixthCpcJourneyState, eventId: String): SixthCpcJourneyPosition? {
    val targetIndex = state.events.indexOfFirst { it.localId == eventId }
    if (targetIndex < 0) return null
    val actions = orderedSixthCpcActions(state)
    val targetAction = actions.firstOrNull { it.kind == ActionKind.EVENT && it.eventIndex == targetIndex } ?: return null
    val prefix = actions.take(actions.indexOf(targetAction))
    return runCatching {
        replaySixthCpcJourneyWithActions(state, prefix).finalPosition
    }.getOrNull()
}

/** Latest replayed position belonging to an event and its nested increment chain. */
fun latestSixthCpcEventChainPosition(
    replay: SixthCpcJourneyReplay,
    eventId: String
): SixthCpcJourneyPosition? = replay.timeline.lastOrNull { it.eventId == eventId }?.position

/** Adds the next eligible annual increment to the current final event chain. */
fun addNextSixthCpcEventChainIncrement(
    state: SixthCpcJourneyState,
    eventId: String,
    latestAllowedDateMillis: Long? = null
): SixthCpcMutationResult {
    val replay = runCatching { replaySixthCpcJourney(state) }.getOrElse {
        return SixthCpcMutationResult(state, null, it.message ?: "The journey could not be replayed.")
    }
    val latestTimelineEntry = replay.timeline.lastOrNull()
        ?: return SixthCpcMutationResult(state, null, "The journey has no current position.")
    if (latestTimelineEntry.eventId != eventId) {
        return SixthCpcMutationResult(state, null, "Continue from the latest 6th CPC journey entry.")
    }
    val position = latestSixthCpcEventChainPosition(replay, eventId)
        ?: return SixthCpcMutationResult(state, null, "This event has no replayed position.")
    val incrementDate = position.nextDniMillis
    if (!isSixthCpcJulyFirst(incrementDate) || incrementDate < position.dateMillis) {
        return SixthCpcMutationResult(state, null, "The next increment date is not a valid 6th CPC DNI.")
    }
    if (latestAllowedDateMillis != null && incrementDate > latestAllowedDateMillis) {
        return SixthCpcMutationResult(state, null, "The next 6th CPC increment is after the allowed journey period.")
    }
    val chainIndex = state.events.indexOfFirst { it.localId == eventId }
    if (chainIndex < 0) return SixthCpcMutationResult(state, null, "This event is no longer in the journey.")
    val band = runCatching { sixthCpcPayBandForPosition(position.payBand, position.gradePay) }.getOrElse {
        return SixthCpcMutationResult(state, null, it.message ?: "The active pay band is invalid.")
    }
    val nextBasic = calculateSixthCpcNextIncrement(position.payInPayBand, position.gradePay, band.payBandMaximum)
        ?: return SixthCpcMutationResult(state, null, "No further increment is available in the active pay band.")
    val increment = SixthCpcEventIncrement(
        payInPayBand = nextBasic - position.gradePay,
        gradePay = position.gradePay,
        date = incrementDate,
        sequence = nextSixthCpcApplicationSequence(state.increments, state.events)
    )
    val chain = state.events[chainIndex]
    val updated = chain.copy(increments = chain.increments + increment)
    val candidate = state.copy(events = state.events.toMutableList().also { it[chainIndex] = updated })
    return replayMutation(candidate, state)
}

fun deleteSixthCpcEvent(state: SixthCpcJourneyState, eventId: String): SixthCpcMutationResult {
    if (state.events.none { it.localId == eventId }) return SixthCpcMutationResult(state, null, "This event was already removed.")
    return replayMutation(state.copy(events = state.events.filterNot { it.localId == eventId }), state)
}

/** Explicitly destructive alternative shown only after replay reports dependent invalid entries. */
fun confirmDeleteSixthCpcEventAndLater(state: SixthCpcJourneyState, eventId: String): SixthCpcMutationResult {
    val plan = planSixthCpcEventAndLaterDeletion(state, eventId)
        ?: return SixthCpcMutationResult(state, null, "This event was already removed or has no saved date.")
    val candidate = state.copy(
        increments = state.increments.filterIndexed { index, _ -> index !in plan.removedTopIncrementIndices },
        events = state.events.filterNot { it.localId in plan.removedEventIds }
            .map { event ->
                event.copy(increments = event.increments.filterIndexed { index, _ ->
                    SixthCpcNestedIncrementRef(event.localId, index) !in plan.removedNestedIncrements
                })
            }
    )
    return replayMutation(candidate, state)
}

fun deleteSixthCpcIncrement(state: SixthCpcJourneyState, index: Int): SixthCpcMutationResult {
    if (index !in state.increments.indices) return SixthCpcMutationResult(state, null, "This increment no longer exists.")
    val candidate = state.copy(increments = state.increments.toMutableList().also { it.removeAt(index) })
    return replayMutation(candidate, state)
}

fun deleteSixthCpcEventIncrement(state: SixthCpcJourneyState, eventId: String, index: Int): SixthCpcMutationResult {
    val eventIndex = state.events.indexOfFirst { it.localId == eventId }
    if (eventIndex < 0) return SixthCpcMutationResult(state, null, "The event containing this increment was removed.")
    val event = state.events[eventIndex]
    if (index !in event.increments.indices) return SixthCpcMutationResult(state, null, "This increment no longer exists.")
    val updated = event.copy(increments = event.increments.toMutableList().also { it.removeAt(index) })
    return replayMutation(state.copy(events = state.events.toMutableList().also { it[eventIndex] = updated }), state)
}

private fun replayMutation(candidate: SixthCpcJourneyState, original: SixthCpcJourneyState = candidate): SixthCpcMutationResult = try {
    val replay = replaySixthCpcJourney(candidate)
    SixthCpcMutationResult(candidate.copy(increments = replay.increments, events = replay.events), replay)
} catch (failure: IllegalArgumentException) {
    SixthCpcMutationResult(state = original, replay = null, error = failure.message ?: "The journey could not be recalculated.")
}

/**
 * Recalculates stored 6th CPC actions from their preserved dates, target Grade Pays,
 * fixation options and route selections. Calculation formulas remain in the existing
 * utilities; this class only feeds each saved action the preceding recalculated pay.
 */
fun replaySixthCpcJourney(state: SixthCpcJourneyState): SixthCpcJourneyReplay {
    return replaySixthCpcJourneyWithActions(state, orderedSixthCpcActions(state))
}

private fun orderedSixthCpcActions(state: SixthCpcJourneyState): List<Action> {
    val actions = buildList {
        state.increments.forEachIndexed { index, increment ->
            add(Action(increment.date, increment.sequence, index, ActionKind.INCREMENT, incrementIndex = index))
        }
        state.events.forEachIndexed { eventIndex, chain ->
            val eventDate = chain.result?.eventDate ?: chain.scaleUpgrade?.eventDate
                ?: throw IllegalArgumentException("Event has no saved result/date.")
            add(Action(eventDate, chain.sequence, state.increments.size + eventIndex * 1001, ActionKind.EVENT, eventIndex = eventIndex))
            chain.increments.forEachIndexed { incrementIndex, increment ->
                add(Action(increment.date, increment.sequence, state.increments.size + eventIndex * 1001 + incrementIndex + 1,
                    ActionKind.EVENT_INCREMENT, eventIndex = eventIndex, eventIncrementIndex = incrementIndex))
            }
        }
    }.sortedWith { left, right ->
        if (state.sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) {
            compareValuesBy(left, right, Action::sequence, Action::order)
        } else {
            compareValuesBy(left, right, Action::dateMillis, Action::order)
        }
    }
    return actions
}

fun sixthCpcPayBandForPosition(payBand: String, gradePay: Int): SixthCpcPayBand {
    val band = SixthToSeventhCpcData.payBands.firstOrNull {
    it.title.substringBefore(":").trim() == payBand.substringBefore(":").trim()
    } ?: throw IllegalArgumentException("Unknown 6th CPC Pay Band: $payBand")
    require(gradePay in band.gradePays) { "Grade Pay does not belong to the active pay band." }
    return band
}

private fun replaySixthCpcJourneyWithActions(state: SixthCpcJourneyState, actions: List<Action>): SixthCpcJourneyReplay {
    val start = state.startingPosition
    require(start.payInPayBand > 0) { "Pay in Pay Band must be positive." }
    val startingBand = sixthCpcPayBandForPosition(start.payBand, start.gradePay)
    require(start.payInPayBand in sixthCpcPayBandMinimum(startingBand)..sixthCpcPayBandMaximum(startingBand)) {
        "Pay in Pay Band is outside the selected pay band's range."
    }
    var position = SixthCpcJourneyPosition(start.dateMillis, startingBand.title, start.gradePay, start.payInPayBand, 0,
        sixthCpcNextAnnualIncrementDate(start.dateMillis))
    val positions = mutableListOf(position)
    val timeline = mutableListOf(SixthCpcTimelineEntry(
        SixthCpcTimelineKind.STARTING_POSITION, start.dateMillis, 0, position, "Starting position"
    ))
    val increments = state.increments.toMutableList()
    val events = state.events.toMutableList()
    actions.forEach { action ->
        require(action.dateMillis >= position.dateMillis) { "Saved 6th CPC actions are not chronological." }
        when (action.kind) {
            ActionKind.INCREMENT -> {
                val old = increments[action.incrementIndex!!]
                require(old.date >= position.nextDniMillis && isSixthCpcJulyFirst(old.date)) {
                    "A 6th CPC annual increment must be on or after the current DNI and dated 1 July."
                }
                val band = sixthCpcPayBandForPosition(position.payBand, position.gradePay)
                val newBasic = calculateSixthCpcNextIncrement(position.payInPayBand, position.gradePay, band.payBandMaximum)
                    ?: throw IllegalArgumentException("The next increment cannot be calculated for this pay position.")
                val updated = old.copy(payInPayBand = newBasic - position.gradePay, gradePay = position.gradePay)
                increments[action.incrementIndex] = updated
                position = SixthCpcJourneyPosition(updated.date, position.payBand, updated.gradePay, updated.payInPayBand, updated.sequence,
                    sixthCpcNextAnnualIncrementDate(updated.date))
                timeline += SixthCpcTimelineEntry(SixthCpcTimelineKind.ANNUAL_INCREMENT, updated.date, updated.sequence, position,
                    "Annual increment", sixthCpcNextAnnualIncrementDate(updated.date), eventId = null, incrementIndex = action.incrementIndex)
            }
            ActionKind.EVENT -> {
                require(action.dateMillis <= position.nextDniMillis) {
                    "A due 6th CPC increment must be recorded before this later event."
                }
                val index = action.eventIndex!!
                val chain = events[index]
                val oldResult = chain.result
                val oldUpgrade = chain.scaleUpgrade
                val updated = when {
                    oldResult != null -> {
                        val target = oldResult.newGradePay
                        val recalculated = calculateSixthCpcPromotionOrMacp(
                            position.payInPayBand, position.gradePay, target, oldResult.eventDate,
                            oldResult.eventType, oldResult.fixationOption, oldResult.financialUpgradation
                        )
                        chain.copy(result = recalculated)
                    }
                    oldUpgrade != null -> chain.copy(scaleUpgrade = recalculateScaleUpgrade(position, oldUpgrade))
                    else -> throw IllegalArgumentException("Event has no saved calculation inputs.")
                }
                events[index] = updated
                val result = updated.result
                val upgrade = updated.scaleUpgrade
                position = if (result != null) {
                    SixthCpcJourneyPosition(result.eventDate, result.newPayBand, result.newGradePay, result.newPayInPayBand, updated.sequence, result.nextIncrementDate)
                } else {
                    SixthCpcJourneyPosition(upgrade!!.eventDate, upgrade.newPayBand, upgrade.newGradePay, upgrade.newPayInPayBand, updated.sequence, upgrade.nextIncrementDate)
                }
                timeline += SixthCpcTimelineEntry(SixthCpcTimelineKind.EVENT, action.dateMillis, updated.sequence, position,
                    when (updated.kind) {
                        SixthCpcEventKind.PROMOTION -> "Promotion"
                        SixthCpcEventKind.FINANCIAL_UPGRADATION -> "Financial upgradation"
                        SixthCpcEventKind.PAY_SCALE_UPGRADATION -> "Pay-scale upgradation / revision"
                    }, result?.nextIncrementDate ?: upgrade?.nextIncrementDate, updated.localId)
            }
            ActionKind.EVENT_INCREMENT -> {
                val eventIndex = action.eventIndex!!
                val incrementIndex = action.eventIncrementIndex!!
                val chain = events[eventIndex]
                val old = chain.increments[incrementIndex]
                require(old.date >= position.nextDniMillis && isSixthCpcJulyFirst(old.date)) {
                    "A 6th CPC event-chain increment must be on or after the current DNI and dated 1 July."
                }
                val band = sixthCpcPayBandForPosition(position.payBand, position.gradePay)
                val newBasic = calculateSixthCpcNextIncrement(position.payInPayBand, position.gradePay, band.payBandMaximum)
                    ?: throw IllegalArgumentException("The next event-chain increment cannot be calculated.")
                val updatedIncrement = old.copy(payInPayBand = newBasic - position.gradePay, gradePay = position.gradePay)
                val updatedIncrements = chain.increments.toMutableList().also { it[incrementIndex] = updatedIncrement }
                events[eventIndex] = chain.copy(increments = updatedIncrements)
                position = SixthCpcJourneyPosition(updatedIncrement.date, position.payBand, updatedIncrement.gradePay, updatedIncrement.payInPayBand,
                    updatedIncrement.sequence, sixthCpcNextAnnualIncrementDate(updatedIncrement.date))
                timeline += SixthCpcTimelineEntry(SixthCpcTimelineKind.EVENT_INCREMENT, updatedIncrement.date, updatedIncrement.sequence, position,
                    "Event-chain increment", sixthCpcNextAnnualIncrementDate(updatedIncrement.date), eventId = chain.localId, nestedIncrementIndex = incrementIndex)
            }
        }
        positions += position
    }
    return SixthCpcJourneyReplay(increments, events, positions, position, timeline)
}

/** Sequence allocator shared by top-level increments, event chains, and nested increments. */
fun nextSixthCpcApplicationSequence(
    increments: List<SixthCpcHistoricalIncrement>,
    events: List<SixthCpcEventChain>,
    initialSequence: Int = 0
): Int = maxOf(
    initialSequence,
    increments.maxOfOrNull { it.sequence } ?: 0,
    events.maxOfOrNull { chain -> maxOf(chain.sequence, chain.increments.maxOfOrNull { it.sequence } ?: 0) } ?: 0
) + 1

fun sixthCpcNextAnnualIncrementDate(dateMillis: Long): Long {
    val date = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
    val year = date.get(java.util.Calendar.YEAR)
    val julyThisYear = java.util.Calendar.getInstance().apply { clear(); set(year, java.util.Calendar.JULY, 1, 0, 0, 0) }.timeInMillis
    return if (dateMillis < julyThisYear) julyThisYear else java.util.Calendar.getInstance().apply {
        clear(); set(year + 1, java.util.Calendar.JULY, 1, 0, 0, 0)
    }.timeInMillis
}

private fun isSixthCpcJulyFirst(dateMillis: Long): Boolean {
    val date = java.util.Calendar.getInstance().apply { timeInMillis = dateMillis }
    return date.get(java.util.Calendar.MONTH) == java.util.Calendar.JULY &&
        date.get(java.util.Calendar.DAY_OF_MONTH) == 1
}

private fun sixthCpcDefaultStartingDate(): Long = java.util.Calendar.getInstance().apply {
    clear(); set(2006, java.util.Calendar.JANUARY, 1, 0, 0, 0)
}.timeInMillis

private fun recalculateScaleUpgrade(
    position: SixthCpcJourneyPosition,
    original: SixthCpcScaleUpgradeResult
): SixthCpcScaleUpgradeResult {
    val result = when (original.historicalRoute) {
        HistoricalSixthCpcRoute.SCALE_6500_10500_FROM_5500_9000 -> {
            require(position.gradePay == 4200) { "The saved 6500–10500 route no longer matches the current Grade Pay." }
            maxOf(position.payInPayBand, 12090) to 4200
        }
        HistoricalSixthCpcRoute.SCALE_7450_11500_FROM_6500_10500 -> {
            require(position.gradePay == 4200) { "The saved 7450–11500 route no longer matches the current Grade Pay." }
            maxOf(position.payInPayBand, 13860) to 4600
        }
        HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC -> {
            val wholeRupees = kotlin.math.floor((position.payInPayBand + position.gradePay) * 0.03).toInt()
            val increment = (kotlin.math.ceil(wholeRupees / 10.0) * 10.0).toInt()
            (position.payInPayBand + increment) to original.newGradePay
        }
        null -> throw IllegalArgumentException("This saved scale-upgrade route lacks replayable inputs; later items need an explicit removal decision.")
    }
    val targetBand = if (result.second == position.gradePay) sixthCpcPayBandForPosition(position.payBand, position.gradePay)
        else bandForGradePay(result.second)
    val payInBand = result.first
    return original.copy(
        oldPayInPayBand = position.payInPayBand,
        oldGradePay = position.gradePay,
        oldPayBand = position.payBand,
        newPayInPayBand = payInBand,
        newGradePay = result.second,
        newPayBand = targetBand.title,
        revisedBasicPay = payInBand + result.second,
        fixationIncrement = if (original.historicalRoute == HistoricalSixthCpcRoute.RULE_13_IN_SIXTH_CPC) result.first - position.payInPayBand else original.fixationIncrement
    )
}

private enum class ActionKind { INCREMENT, EVENT, EVENT_INCREMENT }
private data class Action(
    val dateMillis: Long,
    val sequence: Int,
    val order: Int,
    val kind: ActionKind,
    val incrementIndex: Int? = null,
    val eventIndex: Int? = null,
    val eventIncrementIndex: Int? = null
)
