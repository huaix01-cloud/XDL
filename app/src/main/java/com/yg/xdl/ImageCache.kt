package com.yg.xdl

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.net.HttpURLConnection

/** 极简缩略图缓存（够用就行，不引第三方图片库） */
object ImageCache {

    private val cache = LruCache<String, ImageBitmap>(60)

    fun load(url: String): ImageBitmap? {
        cache.get(url)?.let { return it }
        val r = loadOnce(url)
        return r
    }

    private fun loadOnce(url: String): ImageBitmap? {
        return try {
            val conn = NetConfig.open(url) as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 25000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", XParser.UA)
            conn.setRequestProperty("Referer", "https://x.com/")
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                return null
            }
            val bytes = conn.inputStream.use { it.readBytes() }
            conn.disconnect()
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val ib = bmp.asImageBitmap()
            cache.put(url, ib)
            ib
        } catch (e: java.io.IOException) {
                        null
        } catch (e: Exception) {
            null
        }
    }
}
