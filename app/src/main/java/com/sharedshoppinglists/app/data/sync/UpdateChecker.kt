package com.sharedshoppinglists.app.data.sync

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

data class AppUpdate(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String,
    val forceUpdate: Boolean
)

object UpdateChecker {
    private const val BASE_URL = "https://colonial-albertine-pepin-5207cd9b.koyeb.app"

    suspend fun checkForUpdate(context: Context): AppUpdate? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/app/version")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "UhNoHabia-Android")
            val responseCode = conn.responseCode
            if (responseCode != 200) { conn.disconnect(); return@withContext null }
            val json = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val obj = JSONObject(json)
            val remoteVersion = obj.getInt("versionCode")
            val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
            }
            if (remoteVersion > currentVersion) {
                AppUpdate(
                    versionCode = remoteVersion,
                    versionName = obj.getString("versionName"),
                    apkUrl = obj.getString("apkUrl"),
                    releaseNotes = obj.optString("releaseNotes", ""),
                    forceUpdate = obj.optBoolean("forceUpdate", false)
                )
            } else null
        } catch (e: Exception) {
            // Retry once after 3 seconds
            try {
                kotlinx.coroutines.delay(3000)
                val url = URL("$BASE_URL/app/version")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "UhNoHabia-Android")
                val json = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val obj = JSONObject(json)
                val remoteVersion = obj.getInt("versionCode")
                val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt() else @Suppress("DEPRECATION") it.versionCode
                }
                if (remoteVersion > currentVersion) {
                    AppUpdate(
                        versionCode = remoteVersion,
                        versionName = obj.getString("versionName"),
                        apkUrl = obj.getString("apkUrl"),
                        releaseNotes = obj.optString("releaseNotes", ""),
                        forceUpdate = obj.optBoolean("forceUpdate", false)
                    )
                } else null
            } catch (_: Exception) { null }
        }
    }

    fun downloadAndInstall(context: Context, update: AppUpdate) {
        val request = DownloadManager.Request(Uri.parse(update.apkUrl))
            .setTitle("Uh No Habia v${update.versionName}")
            .setDescription("Descargando actualizacion...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "UhNoHabia-${update.versionName}.apk")
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = dm.enqueue(request)

        // Register receiver to install when download completes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    installApk(ctx, update.versionName)
                    ctx.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_NOT_EXPORTED)
        Toast.makeText(context, "Descargando actualizacion...", Toast.LENGTH_SHORT).show()
    }

    private fun installApk(context: Context, versionName: String) {
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "UhNoHabia-${versionName}.apk")
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }
}