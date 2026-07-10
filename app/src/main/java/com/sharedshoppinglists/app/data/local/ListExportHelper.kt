package com.sharedshoppinglists.app.data.local

import android.content.Context
import android.content.Intent

object ListExportHelper {

    fun exportAsText(context: Context, listName: String, products: List<ExportProduct>) {
        val sb = StringBuilder()
        sb.appendLine("Lista: $listName")
        sb.appendLine("---")
        products.forEach { p ->
            val check = if (p.isPurchased) "[x]" else "[ ]"
            sb.appendLine("$check ${p.emoji} ${p.name} - ${p.quantity.toInt()} ${p.unit}")
        }
        shareText(context, sb.toString(), "Compartir lista")
    }

    fun exportAsCsv(context: Context, listName: String, products: List<ExportProduct>) {
        val sb = StringBuilder()
        sb.appendLine("Producto,Cantidad,Unidad,Categoria,Comprado")
        products.forEach { p ->
            sb.appendLine("\"${p.name}\",${p.quantity},\"${p.unit}\",\"${p.category}\",${p.isPurchased}")
        }
        shareText(context, sb.toString(), "Exportar CSV - $listName")
    }

    private fun shareText(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    data class ExportProduct(
        val name: String,
        val emoji: String,
        val quantity: Double,
        val unit: String,
        val category: String,
        val isPurchased: Boolean
    )
}