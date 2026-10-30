package com.yg.xdl

import android.app.Service
import android.content.Intent
import android.os.IBinder

/** 只做一件事：拿到前台身份，让下载队列在后台不被回收 */
class DownloadService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        DownloadNotifier.ensureChannel(this)
        runCatching {
            startForeground(
                DownloadNotifier.NOTIFICATION_ID,
                DownloadNotifier.build(this, "XDL 正在下载", "准备中…", 0)
            )
        }.onFailure { Log.w("NOTI", "startForeground 失败: ${it.message}") }
        Log.i("NOTI", "下载前台服务已启动")
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.i("NOTI", "下载前台服务已停止")
        super.onDestroy()
    }
}
