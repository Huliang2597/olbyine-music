package com.olbyine.music.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

class DownloadNotifier(private val context: Context) {
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL, "下载", NotificationManager.IMPORTANCE_LOW).apply {
            description = "网易云歌曲下载进度"
            setShowBadge(false)
        }
        manager?.createNotificationChannel(channel)
    }

    fun progress(title: String, written: Long, total: Long) {
        ensureChannel()
        val percent = if (total > 0L) ((written * 100) / total).toInt().coerceIn(0, 100) else -1
        val text = buildString {
            append(if (percent >= 0) "$percent%" else formatSize(written))
            if (total > 0L) {
                append(" · ")
                append(formatSize(written))
                append(" / ")
                append(formatSize(total))
            }
        }
        val notification = base()
            .setContentTitle("正在下载 · $title")
            .setContentText(text)
            .setProgress(if (total > 0L) 100 else 0, if (percent >= 0) percent else 0, total <= 0L)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
    }

    fun done(title: String, summary: String) {
        ensureChannel()
        val notification = base()
            .setContentTitle("下载完成 · $title")
            .setContentText(summary)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
    }

    fun failed(title: String, reason: String) {
        ensureChannel()
        val notification = base()
            .setContentTitle("下载失败 · $title")
            .setContentText(reason)
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { manager?.notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        runCatching { manager?.cancel(NOTIFICATION_ID) }
    }

    private fun base(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setCategory(Notification.CATEGORY_PROGRESS)

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = listOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) {
            value /= 1024
            unit++
        }
        return if (unit == 0) "$bytes B" else String.format(java.util.Locale.US, "%.1f %s", value, units[unit])
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 4211
    }
}
