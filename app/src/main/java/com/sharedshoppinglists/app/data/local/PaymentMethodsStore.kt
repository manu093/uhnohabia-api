package com.sharedshoppinglists.app.data.local

import android.content.Context
import org.json.JSONObject

/**
 * Persists user's selected payment methods in SharedPreferences.
 */
object PaymentMethodsStore {
    private const val PREFS = "payment_methods"
    private const val KEY_IDS = "selected_medio_ids"
    private const val KEY_CARDS = "card_selections"

    fun getSelectedIds(context: Context): Set<Int> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_IDS, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    fun saveSelectedIds(context: Context, ids: Set<Int>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_IDS, ids.map { it.toString() }.toSet()).apply()
    }

    fun getCardSelections(context: Context): Map<Int, List<String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CARDS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associate { key ->
                val arr = obj.getJSONArray(key)
                key.toInt() to (0 until arr.length()).map { arr.getString(it) }
            }
        } catch (_: Exception) { emptyMap() }
    }

    fun saveCardSelections(context: Context, selections: Map<Int, List<String>>) {
        val json = JSONObject(selections.mapKeys { it.key.toString() }
            .mapValues { org.json.JSONArray(it.value) }).toString()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CARDS, json).apply()
    }
}
