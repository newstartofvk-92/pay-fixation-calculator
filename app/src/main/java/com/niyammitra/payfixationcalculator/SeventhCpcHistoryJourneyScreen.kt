package com.niyammitra.payfixationcalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SeventhCpcHistoryJourneyScreen(
    snapshot: SeventhCpcJourneySnapshot?,
    onBack: () -> Unit,
    sequenceIntegrity: CpcSequenceIntegrity = CpcSequenceIntegrity.ORIGINAL
) {
    BackHandler(onBack = onBack)
    if (snapshot == null) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("This history record has no 7th CPC journey data.")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }
    var increments by remember(snapshot) {
        mutableStateOf(snapshot.increments.map { SeventhCpcIncrementStep(it.pay, it.dateMillis, it.sequence) })
    }
    var promotion by remember(snapshot) { mutableStateOf(snapshot.promotions.firstOrNull()) }
    val current = currentSeventhPosition(snapshot, increments, listOfNotNull(promotion), sequenceIntegrity)
    val sequence = maxOf(increments.maxOfOrNull { it.sequence } ?: 0, promotion?.sequence ?: 0)
    val date = increments.lastOrNull()?.date?.let { addSeventhHistoryYears(it, 1) }
        ?: promotion?.resultingDniMillis ?: snapshot.startingDniMillis
        ?: SeventhCpcHistoryStartDni

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("7th CPC Pay Journey")
        Text("Starting position: Level ${snapshot.startingLevel}, ₹${snapshot.startingBasicPay}")
        Text("Conversion date: ${seventhHistoryDate(snapshot.conversionDateMillis)}")
        Text("Known DNI: ${snapshot.startingDniMillis?.let(::seventhHistoryDate) ?: "Not available"}")
        increments.forEachIndexed { index, item ->
            Text("Increment ${index + 1}: ${seventhHistoryDate(item.date)} — Level ${current.level}, ₹${item.pay}")
        }
        Button(
            enabled = getSixthToSeventhNextCell(current.level, current.pay) != null,
            onClick = {
                getSixthToSeventhNextCell(current.level, current.pay)?.let { nextPay ->
                    increments = increments + SeventhCpcIncrementStep(nextPay, date, sequence + 1)
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { Text("Next 7th CPC Increment") }
        PromotionMacpFromConversion(
            currentLevel = current.level,
            currentPay = current.pay,
            knownDni = current.dni,
            restoredSnapshot = promotion,
            onHistorySnapshot = { accepted ->
                promotion = accepted?.copy(sequence = accepted.sequence.takeIf { it > 0 } ?: sequence + 1)
            }
        )
        SaveCompleteJourneyButton(
            CompleteJourneySnapshot(
                startingCpc = CpcHistoryStage.SEVENTH,
                startingDateMillis = snapshot.conversionDateMillis,
                seventh = snapshot.copy(
                    increments = increments.mapIndexed { index, step ->
                        CpcIncrementSnapshot(index + 1, step.pay, step.date, step.sequence.takeIf { it > 0 } ?: index + 1)
                    },
                    promotions = listOfNotNull(promotion)
                )
            ), sequenceIntegrity = sequenceIntegrity
        )
    }
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

private const val SeventhCpcHistoryStartDni = 1467331200000L // 01 July 2016
private fun addSeventhHistoryYears(date: Long, years: Int) = java.util.Calendar.getInstance().apply { timeInMillis = date; add(java.util.Calendar.YEAR, years) }.timeInMillis
private fun seventhHistoryDate(value: Long) = SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH).format(Date(value))
