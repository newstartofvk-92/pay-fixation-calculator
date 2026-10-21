package com.niyammitra.payfixationcalculator

enum class FourthCpcEventType { PROMOTION, ACP }

data class FourthCpcEventUiState(
    val showEventTypes: Boolean = false,
    val draftType: FourthCpcEventType? = null,
    val acceptedEvents: List<FourthCpcEventSnapshot> = emptyList()
)

sealed interface FourthCpcEventAction {
    data object OpenEventTypes : FourthCpcEventAction
    data class SelectType(val type: FourthCpcEventType) : FourthCpcEventAction
    data object CancelDraft : FourthCpcEventAction
    data class Accept(val event: FourthCpcEventSnapshot) : FourthCpcEventAction
    data class Delete(val index: Int) : FourthCpcEventAction
}

fun reduceFourthCpcEventState(
    state: FourthCpcEventUiState,
    action: FourthCpcEventAction
): FourthCpcEventUiState = when (action) {
    FourthCpcEventAction.OpenEventTypes -> state.copy(showEventTypes = true, draftType = null)
    is FourthCpcEventAction.SelectType -> state.copy(showEventTypes = false, draftType = action.type)
    FourthCpcEventAction.CancelDraft -> state.copy(showEventTypes = false, draftType = null)
    is FourthCpcEventAction.Accept -> if (state.draftType == null) state else state.copy(
        draftType = null,
        acceptedEvents = state.acceptedEvents + action.event
    )
    is FourthCpcEventAction.Delete -> if (action.index !in state.acceptedEvents.indices) state else {
        val events = state.acceptedEvents.toMutableList().also { it.removeAt(action.index) }
        state.copy(acceptedEvents = events.mapIndexed { index, event -> event.copy(order = index + 1) })
    }
}

data class FourthCpcTimelineResult(
    val increments: List<FourthToFifthIncrementStep>,
    val events: List<FourthCpcEventSnapshot>,
    val pay: Int,
    val scale: FourthCpcScale,
    val lastEffectiveDate: Long?,
    val failure: FourthCpcTimelineFailure? = null
)

data class FourthCpcTimelineFailure(val dateMillis: Long, val action: String, val message: String)

sealed interface FourthCpcEventPositionResolution {
    data class Available(val position: FourthCpcEventPosition) : FourthCpcEventPositionResolution
    data class Unresolved(val message: String) : FourthCpcEventPositionResolution
}

fun calculateFourthCpcEventPosition(
    currentPay: Int,
    currentScale: FourthCpcScale,
    targetScale: FourthCpcScale
): FourthCpcEventPositionResolution {
    if (!isKnownFourthCpcStage(currentPay, currentScale)) {
        return FourthCpcEventPositionResolution.Unresolved(fourthCpcPayValidationMessage(currentPay, currentScale))
    }
    if (targetScale.existingStages.isEmpty()) {
        return FourthCpcEventPositionResolution.Unresolved(
            "${targetScale.grade} has no verified 4th CPC increment stages, so an event position cannot be calculated."
        )
    }
    val oneIncrement = when (val next = resolveFourthCpcNextIncrement(currentPay, currentScale)) {
        is FourthCpcNextIncrementResolution.Available -> next.pay
        FourthCpcNextIncrementResolution.MaximumReached -> currentPay
        is FourthCpcNextIncrementResolution.ProgressionUnknown -> return FourthCpcEventPositionResolution.Unresolved(next.message)
        is FourthCpcNextIncrementResolution.InvalidPosition -> return FourthCpcEventPositionResolution.Unresolved(next.message)
    }
    val fixedPay = targetScale.existingStages.firstOrNull { it >= oneIncrement }
        ?: targetScale.existingStages.lastOrNull()
        ?: return FourthCpcEventPositionResolution.Unresolved("The target scale has no verified pay stage for this event.")
    return FourthCpcEventPositionResolution.Available(FourthCpcEventPosition(targetScale, fixedPay))
}

/** Replays the saved 4th CPC timeline so removing an event recalculates every later position. */
fun recalculateFourthCpcTimeline(
    startingPay: Int,
    startingScale: FourthCpcScale,
    increments: List<FourthToFifthIncrementStep>,
    events: List<FourthCpcEventSnapshot>,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
): FourthCpcTimelineResult {
    var pay = startingPay
    var scale = startingScale
    var lastDate: Long? = null
    val replayedIncrements = increments.toMutableList()
    val replayedEvents = events.toMutableList()
    val entries = buildList {
        increments.forEachIndexed { index, step ->
            add(TimelineEntry(step.date, step.sequence, index + 1, 0, index))
        }
        events.forEachIndexed { index, event ->
            add(TimelineEntry(event.eventDateMillis, event.sequence, event.order, 1, index))
        }
    }.sortedWith(compareBy<TimelineEntry> { it.date }
        .thenBy { if (sequenceIntegrity == CpcSequenceIntegrity.ORIGINAL) it.sequence else it.listOrder }
        .thenBy { it.kind }
        .thenBy { it.index })

    var failure: FourthCpcTimelineFailure? = null
    for (entry in entries) {
        if (entry.kind == 0) {
            when (val resolution = resolveFourthCpcNextIncrement(pay, scale)) {
                is FourthCpcNextIncrementResolution.Available -> pay = resolution.pay
                FourthCpcNextIncrementResolution.MaximumReached -> {
                    failure = FourthCpcTimelineFailure(entry.date, "increment", "The current pay is already at the maximum verified stage of ${scale.grade}.")
                }
                is FourthCpcNextIncrementResolution.ProgressionUnknown -> {
                    failure = FourthCpcTimelineFailure(entry.date, "increment", resolution.message)
                }
                is FourthCpcNextIncrementResolution.InvalidPosition -> {
                    failure = FourthCpcTimelineFailure(entry.date, "increment", resolution.message)
                }
            }
            if (failure != null) break
            replayedIncrements[entry.index] = replayedIncrements[entry.index].copy(pay = pay)
        } else {
            val event = replayedEvents[entry.index]
            val target = FourthToFifthCpcData.scales.firstOrNull { it.existingScale == event.targetScaleId }
            if (target == null) {
                failure = FourthCpcTimelineFailure(entry.date, "event", "The saved target scale is not available in the current 4th CPC scale data.")
                break
            }
            val resolution = calculateFourthCpcEventPosition(pay, scale, target)
            if (resolution is FourthCpcEventPositionResolution.Unresolved) {
                failure = FourthCpcTimelineFailure(entry.date, "event", resolution.message)
                break
            }
            val fixed = (resolution as FourthCpcEventPositionResolution.Available).position
            pay = fixed.pay
            scale = fixed.scale
            replayedEvents[entry.index] = event.copy(resultingPay = pay)
        }
        lastDate = entry.date
    }
    return FourthCpcTimelineResult(replayedIncrements, replayedEvents, pay, scale, lastDate, failure)
}

data class FourthCpcEventPosition(val scale: FourthCpcScale, val pay: Int)

private data class TimelineEntry(val date: Long, val sequence: Int, val listOrder: Int, val kind: Int, val index: Int)

data class FourthCpcContinuationState(
    val conversionActivated: Boolean,
    val fifthSnapshot: FifthCpcJourneySnapshot?
)

fun invalidateFourthCpcContinuation(state: FourthCpcContinuationState) =
    state.copy(conversionActivated = false, fifthSnapshot = null)
