package com.nimeko.stream

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Update(val tag: String, val url: String)

object Updater {
    private val http = OkHttpClient()

    private fun newer(a: String, b: String): Boolean {
        val x = a.split(".").map { it.toIntOrNull() ?: 0 }
        val y = b.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0..2) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val j = JSONObject(r.body?.string().orEmpty())
                val tag = j.optString("tag_name").removePrefix("v")
                if (tag.isBlank() || !newer(tag, BuildConfig.VERSION_NAME)) return@use null
                val apk = j.optJSONArray("assets").objs().firstOrNull { it.optString("name").endsWith(".apk") }?.optString("browser_download_url")
                Update(tag, apk ?: j.optString("html_url"))
            }
        } catch (e: Exception) { null }
    }

    fun notify(c: Context, u: Update) {
        val prefs = Prefs(c)
        if (prefs.notified == u.tag) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(c, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("updates", "App updates", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(c, 0, Intent(Intent.ACTION_VIEW, Uri.parse(u.url)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(c, "updates")
            .setSmallIcon(R.drawable.ic_fg)
            .setContentTitle("Nimeko ${u.tag} is available")
            .setContentText("Tap to download the update.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(7, n)
        prefs.notified = u.tag
    }
}

class UpdateWorker(c: Context, p: WorkerParameters) : CoroutineWorker(c, p) {
    override suspend fun doWork(): Result {
        Updater.check()?.let { Updater.notify(applicationContext, it) }
        return Result.success()
    }
}

class NimekoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("updates", ExistingPeriodicWorkPolicy.KEEP, req)
    }
}
