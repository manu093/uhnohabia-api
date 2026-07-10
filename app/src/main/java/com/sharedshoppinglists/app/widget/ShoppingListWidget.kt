package com.sharedshoppinglists.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.sharedshoppinglists.app.R
import com.sharedshoppinglists.app.presentation.MainActivity

class ShoppingListWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TOGGLE) {
            val productId = intent.getStringExtra("product_id") ?: return
            WidgetDataHelper.toggleProduct(context, productId)
            // Refresh all widgets
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(android.content.ComponentName(context, ShoppingListWidget::class.java))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Clean up config when widget is removed
        for (id in appWidgetIds) {
            WidgetConfigActivity.deleteWidgetConfig(context, id)
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.sharedshoppinglists.app.TOGGLE_PRODUCT"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_shopping_list)

            // Set title with list name
            val listName = WidgetDataHelper.getListName(context, appWidgetId)
            val (pending, total) = WidgetDataHelper.getProductCount(context, appWidgetId)
            val countText = if (total > 0) " ($pending/$total)" else ""
            views.setTextViewText(R.id.widget_title, "🛒 $listName")

            // Title click opens app
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(context, appWidgetId, openAppIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_title, openAppPending)

            // Set up list adapter with widgetId
            val serviceIntent = Intent(context, ShoppingListWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Set up toggle click template
            val toggleIntent = Intent(context, ShoppingListWidget::class.java).apply { action = ACTION_TOGGLE }
            val togglePending = PendingIntent.getBroadcast(context, appWidgetId, toggleIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            views.setPendingIntentTemplate(R.id.widget_list, togglePending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_list)
        }
    }
}
