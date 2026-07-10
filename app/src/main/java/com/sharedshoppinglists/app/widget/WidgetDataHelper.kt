package com.sharedshoppinglists.app.widget

import android.content.Context
import com.sharedshoppinglists.app.data.local.AppDatabase

data class WidgetProduct(val id: String, val name: String, val emoji: String, val quantity: Double, val unit: String, val isPurchased: Boolean)

object WidgetDataHelper {

    fun getProducts(context: Context, widgetId: Int = -1): List<WidgetProduct> {
        return try {
            val db = AppDatabase.getInstance(context)

            // Try to get configured list for this widget
            val configuredListId = if (widgetId >= 0) {
                WidgetConfigActivity.getWidgetListId(context, widgetId)
            } else null

            val listId = if (configuredListId != null) {
                // Verify the list still exists
                val list = db.shoppingListDao().getAllSync().find { it.id == configuredListId }
                list?.id
            } else {
                // Fallback: use first list
                db.shoppingListDao().getAllSync().firstOrNull()?.id
            }

            if (listId == null) return emptyList()

            val products = db.productDao().getByListIdSync(listId)
            products.map { p ->
                WidgetProduct(p.id, p.name, p.emoji.ifBlank { p.categoryEmoji }, p.quantity, p.unit, p.isPurchased)
            }.sortedBy { it.isPurchased }
        } catch (_: Exception) { emptyList() }
    }

    fun getListName(context: Context, widgetId: Int): String {
        return try {
            val configuredName = WidgetConfigActivity.getWidgetListName(context, widgetId)
            if (configuredName != null) return configuredName

            val db = AppDatabase.getInstance(context)
            db.shoppingListDao().getAllSync().firstOrNull()?.name ?: "Lista de Compras"
        } catch (_: Exception) { "Lista de Compras" }
    }

    fun getProductCount(context: Context, widgetId: Int): Pair<Int, Int> {
        return try {
            val db = AppDatabase.getInstance(context)
            val configuredListId = if (widgetId >= 0) WidgetConfigActivity.getWidgetListId(context, widgetId) else null
            val listId = configuredListId ?: db.shoppingListDao().getAllSync().firstOrNull()?.id ?: return 0 to 0
            val products = db.productDao().getByListIdSync(listId)
            val pending = products.count { !it.isPurchased }
            val total = products.size
            pending to total
        } catch (_: Exception) { 0 to 0 }
    }

    fun toggleProduct(context: Context, productId: String) {
        try {
            val db = AppDatabase.getInstance(context)
            val product = db.productDao().getByIdSync(productId) ?: return
            db.productDao().markAsPurchasedSync(productId, !product.isPurchased, System.currentTimeMillis())
        } catch (_: Exception) { }
    }
}
