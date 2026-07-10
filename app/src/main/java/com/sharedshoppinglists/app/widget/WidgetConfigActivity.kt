package com.sharedshoppinglists.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sharedshoppinglists.app.data.local.AppDatabase

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set result to CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                WidgetConfigScreen(
                    onListSelected = { listId, listName ->
                        saveWidgetConfig(this, appWidgetId, listId, listName)
                        // Update the widget
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        ShoppingListWidget.updateAppWidget(this, appWidgetManager, appWidgetId)
                        // Return OK
                        val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        setResult(Activity.RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = { finish() }
                )
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "widget_config"
        private const val KEY_LIST_ID = "widget_list_id_"
        private const val KEY_LIST_NAME = "widget_list_name_"

        fun saveWidgetConfig(context: Context, widgetId: Int, listId: String, listName: String) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LIST_ID + widgetId, listId)
                .putString(KEY_LIST_NAME + widgetId, listName)
                .apply()
        }

        fun getWidgetListId(context: Context, widgetId: Int): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LIST_ID + widgetId, null)
        }

        fun getWidgetListName(context: Context, widgetId: Int): String? {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LIST_NAME + widgetId, null)
        }

        fun deleteWidgetConfig(context: Context, widgetId: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(KEY_LIST_ID + widgetId)
                .remove(KEY_LIST_NAME + widgetId)
                .apply()
        }
    }
}


@Composable
private fun WidgetConfigScreen(
    onListSelected: (String, String) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lists = remember {
        try {
            val db = AppDatabase.getInstance(context)
            db.shoppingListDao().getAllSync()
        } catch (_: Exception) { emptyList() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp)
        ) {
            Spacer(Modifier.height(48.dp))
            Text(
                "🛒 Configurar Widget",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Elegí qué lista mostrar en el widget",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            if (lists.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📋", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("No hay listas disponibles", style = MaterialTheme.typography.titleMedium)
                        Text("Creá una lista primero en la app", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(lists) { list ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onListSelected(list.id, list.name)
                            },
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (list.isShared) "👥" else "🛒",
                                    fontSize = 28.sp
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        list.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (list.isShared) {
                                        Text(
                                            "Lista compartida",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar")
            }
        }
    }
}
