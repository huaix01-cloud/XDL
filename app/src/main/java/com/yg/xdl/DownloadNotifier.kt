package com.yg.xdl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/** 下载通知 + 前台服务，用来在后台保活[下载队列] */
object DownloadNotifier {

    private const val CHANNEL_ID = "xdl_download"
    const val NOTIFICATION_ID = 1001

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(CHANNEL_ID, "下载", NotificationManager.IMPORTANCE_LOW).apply {
            description = "显示下载进度"
            setShowBadge(false)
        }
        nm.createNotificationChannel(ch)
    }

    fun build(ctx: Context, title: String, text: String, percent: Int): Notification {
        val pi = PendingIntent.getActivity(
            ctx,
            0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .build()
    }

    fun startService(ctx: Context) {
        if (!Settings.notifyDownload) return
        ensureChannel(ctx)
        runCatching { ctx.startForegroundService(Intent(ctx, DownloadService::class.java)) }
            .onFailure { Log.w("NOTI", "启动下载服务失败: ${it.message}") }
    }

    fun tick(ctx: Context, tasks: List<DownloadTask>) {
        if (!Settings.notifyDownload) return
        val done = tasks.count { it.status == TaskStatus.DONE }
        val total = tasks.size
        val pct = if (total > 0) done * 100 / total else 0
        push(ctx, "正在下载 $done/$total", "XDL", pct)
    }

    fun finish(ctx: Context, tasks: List<DownloadTask>) {
        if (!Settings.notifyDownload) return
        val done = tasks.count { it.status == TaskStatus.DONE }
        val failed = tasks.count { it.status == TaskStatus.FAILED }
        val text = if (failed > 0) "成功 $done · 失败 $failed" else "全部完成，共 $done 个"
        push(ctx, "下载结束", text, 100)
        runCatching { ctx.stopService(Intent(ctx, DownloadService::class.java)) }
    }

    private fun push(ctx: Context, title: String, text: String, percent: Int) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIFICATION_ID, build(ctx, title, text, percent)) }
    }

    fun cancel(ctx: Context) {
        runCatching {
            ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
            ctx.stopService(Intent(ctx, DownloadService::class.java))
        }
    }
}
