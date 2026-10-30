package com.yg.xdl

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量运行日志。
 * - 落盘：Android/data/<pkg>/files/logs/xdl.log，超过 512KB 自动轮转，留 2 份历史
 * - 导出：写一份到系统「下载/XDL/」目录，卸载 App 也还在，adb 也能取
 */
object Log {

    private const val FILE_NAME = "xdl.log"
    private const val MAX_BYTES = 512 * 1024L
    private const val KEEP = 2
    private const val DUMP_LINES = 1200

    private val lock = Any()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val stampFile = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)

    @Volatile
    private var dir: File? = null

    fun init(ctx: Context) {
        synchronized(lock) {
            dir = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "logs").also { it.mkdirs() }
        }
        val ver = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()
        i(
            "App",
            "启动 version=$ver sdk=${Build.VERSION.SDK_INT} 设备=${Build.MANUFACTURER} ${Build.MODEL}"
        )
    }

    fun i(tag: String, msg: String) = write("I", tag, msg, null)

    fun w(tag: String, msg: String) = write("W", tag, msg, null)

    fun e(tag: String, msg: String, t: Throwable? = null) = write("E", tag, msg, t)

    private fun write(level: String, tag: String, msg: String, t: Throwable?) {
        val line = buildString {
            append(stamp.format(Date()))
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(": ")
            append(msg)
            if (t != null) {
                append('\n')
                append(t.stackTraceToString().trimEnd())
            }
        }
        when (level) {
            "E" -> android.util.Log.e("XDL", "[$tag] $msg", t)
            "W" -> android.util.Log.w("XDL", "[$tag] $msg")
            else -> android.util.Log.i("XDL", "[$tag] $msg")
        }
        synchronized(lock) {
            val d = dir ?: return
            val f = File(d, FILE_NAME)
            if (f.length() > MAX_BYTES) rotate(d)
            runCatching { f.appendText(line + "\n") }
        }
    }

    private fun rotate(d: File) {
        runCatching {
            for (i in KEEP - 1 downTo 1) {
                val from = File(d, "xdl.$i.log")
                if (from.exists()) from.renameTo(File(d, "xdl.${i + 1}.log"))
            }
            File(d, FILE_NAME).renameTo(File(d, "xdl.1.log"))
        }
    }

    /** 取最近若干行用于界面展示 */
    fun dump(maxLines: Int = DUMP_LINES): String {
        val d = dir
        if (d == null) return "日志系统尚未初始化"
        val f = File(d, FILE_NAME)
        if (!f.exists() || f.length() == 0L) return "（还没有日志）"
        val lines = runCatching { f.readLines() }.getOrElse { return "读取日志失败：${it.message}" }
        val tail = if (lines.size > maxLines) lines.subList(lines.size - maxLines, lines.size) else lines
        return "共 ${lines.size} 行，显示最近 ${tail.size} 行\n\n" + tail.joinToString("\n")
    }

    fun logPath(ctx: Context): String {
        val d = dir ?: return "?"
        return File(d, FILE_NAME).absolutePath
    }

    fun clear() {
        synchronized(lock) {
            val d = dir ?: return
            runCatching {
                File(d, FILE_NAME).delete()
                for (i in 1..KEEP + 1) File(d, "xdl.$i.log").delete()
            }
        }
        i("App", "日志已清空")
    }

    /** 导出到 系统下载/XDL/xdl-log-时间.txt，返回可读路径，失败返回 null */
    fun export(ctx: Context): String? {
        val d = dir ?: return null
        val src = File(d, FILE_NAME)
        if (!src.exists()) return null

        val header = buildString {
            appendLine("=== XDL 运行日志 ===")
            appendLine("导出时间: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
            appendLine("代理    : " + NetConfig.current.ifBlank { "未设置（直连 / 系统 VPN）" })
            appendLine("设备    : ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("日志文件: ${src.absolutePath} (${src.length()} 字节)")
            appendLine("=".repeat(40))
            appendLine()
        }
        val name = "xdl-log-${stampFile.format(Date())}.txt"
        val bytes = (header + src.readText()).toByteArray()

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/XDL")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri = ctx.contentResolver.insert(collection, values) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, done, null, null)
            i("App", "日志已导出: Download/XDL/$name (${bytes.size} 字节)")
            "Download/XDL/$name"
        } catch (e: Exception) {
            android.util.Log.e("XDL", "导出日志失败", e)
            null
        }
    }
}
