package com.yg.xdl

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.net.HttpURLConnection

/** 单个文件的落盘结果 */
data class Saved(val uri: Uri, val name: String, val bytes: Long, val mime: String, val where: String)

object Downloader {

    /**
     * 把一个媒体存到系统相册。
     * 只做「一个 URL → 一个文件」，队列/并发/去重策略在 DownloadCenter 里。
     */
    fun saveRaw(
        ctx: Context,
        platform: Platform,
        handle: String,
        tweetId: String,
        index: Int,
        url: String,
        isVideo: Boolean,
        referer: String? = null,
        onProgress: (done: Long, total: Long) -> Unit
    ): Saved {
        val ext = if (isVideo) "mp4" else guessExt(url)
        val mime = if (isVideo) "video/mp4" else mimeFor(ext)

        val safeHandle = handle.ifBlank { "x" }
            .replace(Regex("[^A-Za-z0-9_]"), "")
            .ifBlank { "x" }
        val prefix = if (platform == Platform.X) "" else platform.short.lowercase() + "_"
        val idPart = tweetId.ifBlank { System.currentTimeMillis().toString() }
        val base = "$prefix${safeHandle}_${idPart}_${index + 1}"

        val rel = if (isVideo) "Movies/XDL" else "Pictures/XDL"
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }

        var name = "$base.$ext"
        var n = 1
        while (alreadyExists(ctx, collection, rel, name)) {
            name = "${base}_$n.$ext"
            n++
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val uri = ctx.contentResolver.insert(collection, values)
            ?: throw ParseException("无法创建媒体条目")

        val t0 = System.currentTimeMillis()
        Log.i("DL", "开始 [$mime] $rel/$name <- $url")

        try {
            val conn = openHttp(url, referer)
            val code = conn.responseCode
            if (code !in 200..299) {
                conn.disconnect()
                throw ParseException("下载失败 HTTP $code")
            }
            val total = conn.contentLengthLong

            var written = 0L
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                conn.inputStream.use { input ->
                    val buf = ByteArray(128 * 1024)
                    var lastNotify = 0L
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastNotify > 150) {
                            lastNotify = now
                            onProgress(written, total)
                        }
                    }
                    onProgress(written, total)
                }
                out.flush()
            } ?: throw ParseException("无法写入文件")

            conn.disconnect()

            val finish = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            ctx.contentResolver.update(uri, finish, null, null)
            Log.i(
                "DL",
                "完成 $name 写入=$written" +
                    (if (total > 0) " 服务器=$total ${if (written == total) "完全一致" else "不一致!"}" else " 服务器长度未知") +
                    " 用时=${System.currentTimeMillis() - t0}ms"
            )
            return Saved(uri, name, written, mime, rel)
        } catch (e: Exception) {
            Log.e("DL", "失败 $name <- $url", e)
            runCatching { ctx.contentResolver.delete(uri, null, null) }
            throw when {
                e is java.io.IOException -> e
                e is ParseException -> e
                else -> ParseException(e.message ?: "下载出错")
            }
        }
    }

    private fun openHttp(url: String, referer: String?): HttpURLConnection =
        (NetConfig.open(url) as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 60000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", XParser.UA)
            setRequestProperty("Referer", referer ?: "https://x.com/")
            setRequestProperty("Accept", "*/*")
        }

    private fun alreadyExists(ctx: Context, collection: Uri, rel: String, name: String): Boolean {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection =
            "${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME}=?"
        return try {
            ctx.contentResolver.query(collection, projection, selection, arrayOf("$rel/", name), null)
                ?.use { it.count > 0 } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun guessExt(url: String): String {
        val path = url.substringBefore("?").lowercase()
        return when {
            path.endsWith(".png") -> "png"
            path.endsWith(".webp") -> "webp"
            path.endsWith(".gif") -> "gif"
            else -> "jpg"
        }
    }

    private fun mimeFor(ext: String): String = when (ext) {
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        else -> "image/jpeg"
    }
}
