package com.yg.xdl

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

enum class TaskStatus(val label: String) {
    QUEUED("排队中"), RUNNING("下载中"), PAUSED("已暂停"), DONE("已完成"), FAILED("失败")
}

class DownloadTask(
    val key: String,
    val platform: Platform,
    val handle: String,
    val tweetId: String,
    val index: Int,
    val kind: String,
    val title: String,
    val qualityLabel: String,
    val url: String,
    val isVideo: Boolean,
    val referer: String? = null
) {
    val kindLabel: String
        get() = when {
            kind == "gif" -> "GIF"
            isVideo -> "视频"
            else -> "图片"
        }

    var status by mutableStateOf(TaskStatus.QUEUED)
    var progress by mutableStateOf(0f)
    var bytes by mutableStateOf(0L)
    var total by mutableStateOf(0L)
    var speed by mutableStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var savedName by mutableStateOf<String?>(null)
    var savedUri by mutableStateOf<Uri?>(null)
    var mime by mutableStateOf(if (isVideo) "video/mp4" else "image/jpeg")
    var cancelled by mutableStateOf(false)
}

/** 下载队列：入队 → 并发执行（数量来自设置）→ 状态回流给 UI */
object DownloadCenter {

    val tasks = mutableStateListOf<DownloadTask>()
    var paused by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set

    private var appCtx: Context? = null
    private var scope: CoroutineScope? = null
    private var job: Job? = null

    fun init(ctx: Context) {
        appCtx = ctx.applicationContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    val doneCount: Int get() = tasks.count { it.status == TaskStatus.DONE }
    val failedCount: Int get() = tasks.count { it.status == TaskStatus.FAILED }
    val activeCount: Int get() = tasks.count { it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING }

    /** 入队一个媒体；同 key 已在队列/已完成则直接返回已有的 */
    fun enqueue(
        platform: Platform,
        handle: String,
        tweetId: String,
        media: MediaItem,
        index: Int,
        qualityLabel: String
    ): DownloadTask {
        val key = "${platform.name}_${tweetId}_$index"
        val existing = tasks.firstOrNull { it.key == key }
        if (existing != null && existing.status != TaskStatus.FAILED) {
            Log.i("Q", "跳过（已存在 ${existing.status.label}）$key")
            return existing
        }
        if (existing != null) {
            existing.cancelled = false
            existing.status = TaskStatus.QUEUED
            existing.error = null
            existing.progress = 0f
            startIfIdle()
            return existing
        }
        val t = DownloadTask(
            key = key,
            platform = platform,
            handle = handle,
            tweetId = tweetId,
            index = index,
            kind = media.kind,
            title = "@$handle",
            qualityLabel = qualityLabel,
            url = media.url,
            isVideo = media.isVideo,
            referer = media.referer
        )
        tasks.add(t)
        Log.i("Q", "入队 $key ${t.kindLabel} ${media.width}x${media.height} ${media.url.take(90)}")
        startIfIdle()
        return t
    }

    private fun startIfIdle() {
        if (job?.isActive == true) return
        startQueue()
    }

    fun startQueue() {
        val ctx = appCtx ?: return
        val s = scope ?: return
        if (job?.isActive == true) return
        paused = false
        running = true
        DownloadNotifier.startService(ctx)
        job = s.launch {
            while (true) {
                val batch = tasks.filter { it.status == TaskStatus.QUEUED }
                if (batch.isEmpty()) break
                val sem = Semaphore(Settings.maxConcurrent.coerceIn(1, 4))
                batch.map { t -> async { sem.withPermit { runOne(ctx, t) } } }.awaitAll()
            }
            running = false
            DownloadNotifier.finish(ctx, tasks)
            Log.i("Q", "队列结束 成功=$doneCount 失败=$failedCount")
        }
    }

    private suspend fun runOne(ctx: Context, t: DownloadTask) {
        if (t.status != TaskStatus.QUEUED) return
        t.status = TaskStatus.RUNNING
        t.error = null
        t.progress = 0f
        t.speed = 0
        var lastBytes = 0L
        var lastAt = System.currentTimeMillis()

        try {
            val saved = withContext(Dispatchers.IO) {
                Downloader.saveRaw(
                    ctx = ctx,
                    platform = t.platform,
                    handle = t.handle,
                    tweetId = t.tweetId,
                    index = t.index,
                    url = t.url,
                    isVideo = t.isVideo,
                    referer = t.referer
                ) { done, total ->
                    t.bytes = done
                    t.total = total
                    if (total > 0) t.progress = (done.toFloat() / total).coerceIn(0f, 1f)
                    val now = System.currentTimeMillis()
                    val dt = now - lastAt
                    if (dt >= 400) {
                        t.speed = (done - lastBytes) * 1000 / dt
                        lastBytes = done
                        lastAt = now
                    }
                }
            }
            if (t.cancelled) {
                runCatching { ctx.contentResolver.delete(saved.uri, null, null) }
                tasks.remove(t)
                Log.i("Q", "已取消并删除 ${saved.name}")
                return
            }
            t.savedUri = saved.uri
            t.savedName = saved.name
            t.mime = saved.mime
            t.bytes = saved.bytes
            t.progress = 1f
            t.speed = 0
            t.status = TaskStatus.DONE
            DownloadNotifier.tick(ctx, tasks)
        } catch (e: Exception) {
            if (t.cancelled) {
                tasks.remove(t)
                return
            }
            t.status = TaskStatus.FAILED
            t.error = e.message ?: "下载失败"
            t.speed = 0
            Log.e("Q", "任务失败 ${t.key}", e)
            DownloadNotifier.tick(ctx, tasks)
        }
    }

    fun pauseAll() {
        paused = true
        tasks.filter { it.status == TaskStatus.QUEUED }.forEach { it.status = TaskStatus.PAUSED }
        Log.i("Q", "批量暂停，未开始的任务已挂起")
    }

    fun resumeAll() {
        paused = false
        tasks.filter { it.status == TaskStatus.PAUSED }.forEach { it.status = TaskStatus.QUEUED }
        startIfIdle()
    }

    fun retry(t: DownloadTask) {
        if (t.status != TaskStatus.FAILED) return
        t.cancelled = false
        t.status = TaskStatus.QUEUED
        t.error = null
        t.progress = 0f
        startIfIdle()
    }

    fun retryAllFailed() {
        tasks.filter { it.status == TaskStatus.FAILED }.forEach {
            it.cancelled = false
            it.status = TaskStatus.QUEUED
            it.error = null
            it.progress = 0f
        }
        startIfIdle()
    }

    fun remove(t: DownloadTask) {
        t.cancelled = true
        if (t.status != TaskStatus.RUNNING) tasks.remove(t)
    }

    fun clearDone() {
        tasks.removeAll { it.status == TaskStatus.DONE }
    }

    fun clearFailed() {
        tasks.removeAll { it.status == TaskStatus.FAILED }
    }

    fun openFile(ctx: Context, t: DownloadTask) {
        val uri = t.savedUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, t.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { ctx.startActivity(intent) }
            .onFailure { Log.w("Q", "打开文件失败: ${it.message}") }
    }
}
