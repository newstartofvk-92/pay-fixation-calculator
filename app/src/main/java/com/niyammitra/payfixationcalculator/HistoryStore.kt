package com.niyammitra.payfixationcalculator

import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local-only history for completed pay-fixation calculations.
 *
 * History is stored on the user's device with SharedPreferences. No account,
 * server, or cloud synchronization is involved in this implementation.
 */
data class CalculationHistory(
    val id: Long,
    val savedAt: Long,
    val officialName: String,
    val employeeCategory: EmployeeCategory,
    val currentLevel: String,
    val currentPay: Int,
    val promotedLevel: String,
    val promotionDate: Long?,
    val dniDate: Long?,
    val option1FinalPay: Int,
    val option2FinalPay: Int
)

object HistoryStore {
    private const val PREFS = "pay_fixation_history"
    private const val KEY_HISTORY = "entries"

    /** Reads all saved calculations and returns the newest entries first. */
    fun getAll(context: Context): List<CalculationHistory> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, "[]") ?: "[]"

        // Invalid/corrupt stored JSON should not crash the calculator.
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }

        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                add(
                    CalculationHistory(
                        id = item.optLong("id"),
                        savedAt = item.optLong("savedAt"),
                        // Older history records predate employee-category selection,
                        // so they are treated as ordinary/general employee records.
                        employeeCategory = runCatching {
                            EmployeeCategory.valueOf(item.optString("employeeCategory"))
                        }.getOrDefault(EmployeeCategory.ORDINARY),
                        // optString keeps older saved records compatible when
                        // the official-name field did not exist yet.
                        officialName = item.optString("officialName"),
                        currentLevel = item.optString("currentLevel"),
                        currentPay = item.optInt("currentPay"),
                        promotedLevel = item.optString("promotedLevel"),
                        promotionDate = if (item.isNull("promotionDate")) null else item.optLong("promotionDate"),
                        dniDate = if (item.isNull("dniDate")) null else item.optLong("dniDate"),
                        option1FinalPay = item.optInt("option1FinalPay"),
                        option2FinalPay = item.optInt("option2FinalPay")
                    )
                )
            }
        }.sortedByDescending { it.savedAt }
    }

    /**
     * Saves a calculation unless the same calculation is already present.
     * The duplicate check deliberately ignores the generated id/timestamp.
     */
    fun add(context: Context, entry: CalculationHistory) {
        val entries = getAll(context).toMutableList()

        // A calculation is uniquely identified by category, official name, and
        // all calculation inputs. Category is included so ordinary and faculty
        // calculations with otherwise identical inputs remain separate records.
        val alreadySaved = entries.any { existing ->
            existing.employeeCategory == entry.employeeCategory &&
                existing.officialName.trim() == entry.officialName.trim() &&
                existing.currentLevel == entry.currentLevel &&
                existing.currentPay == entry.currentPay &&
                existing.promotedLevel == entry.promotedLevel &&
                existing.promotionDate == entry.promotionDate &&
                existing.dniDate == entry.dniDate
        }

        if (alreadySaved) {
            Toast.makeText(context, "History already saved", Toast.LENGTH_SHORT).show()
            return
        }

        // New entries are placed first, while the history is capped at 100.
        entries.removeAll { it.id == entry.id }
        entries.add(0, entry)
        save(context, entries.take(100))
        Toast.makeText(context, "Fixation saved", Toast.LENGTH_SHORT).show()
    }

    /** Deletes one saved calculation by its generated id. */
    fun delete(context: Context, id: Long) {
        save(context, getAll(context).filterNot { it.id == id })
    }

    /** Removes the entire local history from the device. */
    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }

    /** Serializes the history list back into SharedPreferences as JSON. */
    private fun save(context: Context, entries: List<CalculationHistory>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().apply {
                put("id", entry.id)
                put("savedAt", entry.savedAt)
                put("officialName", entry.officialName)
                put("employeeCategory", entry.employeeCategory.name)
                put("currentLevel", entry.currentLevel)
                put("currentPay", entry.currentPay)
                put("promotedLevel", entry.promotedLevel)
                put("promotionDate", entry.promotionDate ?: JSONObject.NULL)
                put("dniDate", entry.dniDate ?: JSONObject.NULL)
                put("option1FinalPay", entry.option1FinalPay)
                put("option2FinalPay", entry.option2FinalPay)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }
}
