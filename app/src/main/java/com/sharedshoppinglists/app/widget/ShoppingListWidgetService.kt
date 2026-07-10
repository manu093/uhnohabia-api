package com.sharedshoppinglists.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.sharedshoppinglists.app.R

class ShoppingListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        return ShoppingListRemoteViewsFactory(applicationContext, widgetId)
    }
}

class ShoppingListRemoteViewsFactory(
    private val context: Context,
    private val widgetId: Int
) : RemoteViewsService.RemoteViewsFactory {
    private var items = listOf<WidgetProduct>()

    override fun onCreate() { loadData() }
    override fun onDataSetChanged() { loadData() }
    override fun onDestroy() {}
    override fun getCount() = items.size
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount() = 1
    override fun getItemId(position: Int) = position.toLong()
    override fun hasStableIds() = false

    private fun loadData() {
        items = WidgetDataHelper.getProducts(context, widgetId)
    }

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= items.size) return RemoteViews(context.packageName, R.layout.widget_item)
        val item = items[position]
        val views = RemoteViews(context.packageName, R.layout.widget_item)

        val checkMark = if (item.isPurchased) "✅" else "⬜"
        val displayText = "$checkMark ${item.emoji} ${item.name}"
        views.setTextViewText(R.id.widget_item_name, displayText)
        views.setTextViewText(R.id.widget_item_qty, "${item.quantity.toInt()} ${item.unit}")

        // Strikethrough for purchased items
        if (item.isPurchased) {
            views.setInt(R.id.widget_item_name, "setPaintFlags", android.graphics.Paint.STRIKE_THRU_TEXT_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
            views.setTextColor(R.id.widget_item_name, 0x80FFFFFF.toInt())
            views.setTextColor(R.id.widget_item_qty, 0x50FFFFFF.toInt())
        } else {
            views.setInt(R.id.widget_item_name, "setPaintFlags", android.graphics.Paint.ANTI_ALIAS_FLAG)
            views.setTextColor(R.id.widget_item_name, 0xFFFFFFFF.toInt())
            views.setTextColor(R.id.widget_item_qty, 0x80FFFFFF.toInt())
        }

        // Fill in toggle intent
        val fillIntent = Intent().apply { putExtra("product_id", item.id) }
        views.setOnClickFillInIntent(R.id.widget_item_root, fillIntent)

        return views
    }
}
