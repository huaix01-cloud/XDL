package com.yg.xdl

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一条媒体（图片 / 视频 / GIF） */
data class MediaItem(
    val kind: String,          // photo | video | gif
    val url: String,           // 直链（图片已是 name=orig）
    val thumb: String?,        // 缩略图
    val width: Int,
    val height: Int,
    val durationMs: Long,
    /** 下载时需要的 Referer（B站有防盗链，X 不需要） */
    val referer: String? = null
) {
    val isVideo: Boolean get() = kind == "video" || kind == "gif"
}

/** 解析后的推文 */
data class TweetResult(
    val id: String,
    val authorName: String,
    val handle: String,
    val avatar: String?,
    val text: String,
    val date: String,
    val media: List<MediaItem>,
    /** 这条内容来自哪个平台（下载时用于文件名前缀与记录） */
    val platform: Platform = Platform.X
)

class ParseException(message: String) : Exception(message)

object XParser {

    internal const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0.0.0 Mobile Safari/537.36"

    private const val FX_STATUS = "https://api.fxtwitter.com/2/status/"
    private const val VX_STATUS = "https://api.vxtwitter.com/i/status/"

    private val TWEET_URL = Regex(
        """https?://(?:www\.|mobile\.)?(?:x|twitter)\.com/([A-Za-z0-9_]{1,20})/status(?:es)?/(\d+)""",
        RegexOption.IGNORE_CASE
    )
    private val ONLY_ID = Regex("""^\d{15,25}$""")
    private val TCO = Regex("""https?://t\.co/\w+""")
    private val OG_URL = Regex("""og:url"\s+content="([^"]+)"""")
    private val CANONICAL = Regex("""rel="canonical"\s+href="([^"]+)"""")

    // ---------------------------------------------------------------- 链接处理

    /** 从任意分享文本里找出 X 推文链接 */
    fun findTweetUrl(text: String): String? {
        val m = TWEET_URL.find(text) ?: return null
        return "https://x.com/${m.groupValues[1]}/status/${m.groupValues[2]}"
    }

    /** 抽出推文 ID：支持完整链接、纯 ID、t.co 短链 */
    fun extractId(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) throw ParseException("先粘贴一条推文链接")

        findTweetUrl(text)?.let { u ->
            TWEET_URL.find(u)?.let { return it.groupValues[2] }
        }
        if (ONLY_ID.matches(text)) return text

        TCO.find(text)?.let { tco ->
            val (finalUrl, body) = httpGetFollow(tco.value)
            TWEET_URL.find(finalUrl)?.let { return it.groupValues[2] }
            TWEET_URL.find(body)?.let { return it.groupValues[2] }
            OG_URL.find(body)?.let { m ->
                TWEET_URL.find(m.groupValues[1])?.let { return it.groupValues[2] }
            }
            CANONICAL.find(body)?.let { m ->
                TWEET_URL.find(m.groupValues[1])?.let { return it.groupValues[2] }
            }
        }
        throw ParseException("没找到推文链接（要形如 x.com/xxx/status/123456）")
    }

    // ---------------------------------------------------------------- 网络

    /** 发 GET，自动跟随重定向 */
    private fun httpGetFollow(urlStr: String, timeoutMs: Int = 20000): Pair<String, String> =
        getOnce(urlStr, timeoutMs, useProxy = false)

    private fun getOnce(urlStr: String, timeoutMs: Int, useProxy: Boolean): Pair<String, String> {
        val t0 = System.currentTimeMillis()
        var current = urlStr
        var hops = 0
        while (true) {
            val conn = NetConfig.open(current, useProxy) as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8")

            val code = conn.responseCode
            if (code in 300..399 && hops < 6) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw ParseException("HTTP $code 无跳转地址")
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
                hops++
                continue
            }
            if (code !in 200..299) {
                val err = try {
                    conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                } catch (e: Exception) {
                    ""
                }
                conn.disconnect()
                Log.w("HTTP", "GET $current -> $code (跳转${hops}次) ${err.take(120)}")
                throw ParseException("HTTP $code ${err.take(120)}")
            }
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            conn.disconnect()
            Log.i(
                "HTTP",
                "GET $current -> 200 ${body.length}B ${System.currentTimeMillis() - t0}ms 跳转${hops}次"
            )
            return current to body
        }
    }

    private fun httpGet(urlStr: String, timeoutMs: Int = 20000): String =
        httpGetFollow(urlStr, timeoutMs).second

    // ---------------------------------------------------------------- 解析

    /** 解析一条推文；fxtwitter 为主，vxtwitter 兜底。quality 决定视频选哪档 mp4 */
    fun fetch(id: String, quality: Quality = Quality.BEST): TweetResult {
        val errors = mutableListOf<String>()
        val t0 = System.currentTimeMillis()
        try {
            val r = parseFx(httpGet(FX_STATUS + id), quality)
            Log.i(
                "Parse",
                "id=$id fxtwitter 成功 画质=${quality.label} 媒体=${r.media.size} " +
                    "种类=${r.media.joinToString(",") { it.kind }} 用时=${System.currentTimeMillis() - t0}ms"
            )
            return r
        } catch (e: Exception) {
            errors += "fx: ${e.message}"
            Log.w("Parse", "id=$id fxtwitter 失败: ${e.message}")
        }
        try {
            val r = parseVx(httpGet(VX_STATUS + id))
            Log.i("Parse", "id=$id vxtwitter 兜底成功 媒体=${r.media.size}")
            return r
        } catch (e: Exception) {
            errors += "vx: ${e.message}"
            Log.e("Parse", "id=$id 两个解析源都失败 -> ${errors.joinToString(" | ")}")
        }
        throw ParseException("解析失败 → " + errors.joinToString(" | "))
    }

    private fun parseFx(body: String, quality: Quality): TweetResult {
        val root = JSONObject(body)
        val code = root.optInt("code", 0)
        if (code != 200) throw ParseException("接口 code=$code")
        val st = root.optJSONObject("status") ?: throw ParseException("缺少 status")
        val author = st.optJSONObject("author")

        val items = mutableListOf<MediaItem>()
        val arr = st.optJSONObject("media")?.optJSONArray("all")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val type = o.optString("type", "photo")
                var url = o.optString("url")
                if (url.isBlank()) continue
                val thumb = o.optString("thumbnail_url").ifBlank { null }
                val w = o.optInt("width")
                val h = o.optInt("height")
                val dur = (o.optDouble("duration", 0.0) * 1000).toLong()

                if (type == "video" || type == "gif") {
                    items += MediaItem(type, pickMp4(o, quality), thumb, w, h, dur)
                } else {
                    items += MediaItem("photo", origPhoto(url), thumb, w, h, 0)
                }
            }
        }

        return TweetResult(
            id = st.optString("id"),
            authorName = author?.optString("name").orEmpty(),
            handle = author?.optString("screen_name").orEmpty(),
            avatar = author?.optString("avatar_url")?.ifBlank { null },
            text = st.optString("text").orEmpty(),
            date = st.optString("created_at").orEmpty(),
            media = items
        )
    }

    /**
     * 从 formats[] 里挑一档 mp4：
     * 先按画质上限过滤（URL 里带 /1280x720/ 这种），再取码率最高的。
     * BEST 的上限是 Int.MAX_VALUE，等于不设限。
     */
    private fun pickMp4(o: JSONObject, quality: Quality): String {
        val fallback = o.optString("url")
        val fmts = o.optJSONArray("formats") ?: return fallback
        var bestUrl = ""
        var bestBitrate = -1L
        var bestHeight = -1
        for (j in 0 until fmts.length()) {
            val f = fmts.optJSONObject(j) ?: continue
            if (!"mp4".equals(f.optString("container"), true)) continue
            val u = f.optString("url")
            if (u.isBlank()) continue
            val h = HEIGHT_IN_URL.find(u)?.groupValues?.get(2)?.toIntOrNull() ?: 0
            if (quality.maxHeight != Int.MAX_VALUE && h > quality.maxHeight) continue
            val br = f.optLong("bitrate", 0L)
            if (br > bestBitrate || (br == bestBitrate && h > bestHeight)) {
                bestBitrate = br
                bestHeight = h
                bestUrl = u
            }
        }
        return if (bestUrl.isNotBlank()) bestUrl else fallback
    }

    private val HEIGHT_IN_URL = Regex("""/(\d+)x(\d+)/""")

    private fun parseVx(body: String): TweetResult {
        val o = JSONObject(body)
        val items = mutableListOf<MediaItem>()
        val ext = o.optJSONArray("media_extended")
        if (ext != null) {
            for (i in 0 until ext.length()) {
                val m = ext.optJSONObject(i) ?: continue
                val t = m.optString("type", "image").lowercase()
                val kind = when (t) {
                    "video" -> "video"
                    "gif" -> "gif"
                    else -> "photo"
                }
                var u = m.optString("url")
                if (u.isBlank()) continue
                if (kind == "photo") u = origPhoto(u)
                val thumb = m.optString("thumbnail_url").ifBlank { null }
                val size = m.optInt("size")
                val dur = m.optLong("duration_millis", 0L)
                items += MediaItem(kind, u, thumb, if (kind == "photo") size else 0, 0, dur)
            }
        } else {
            val urls = o.optJSONArray("mediaURLs")
            if (urls != null) {
                for (i in 0 until urls.length()) {
                    val u = urls.optString(i)
                    if (u.isNotBlank()) items += MediaItem("photo", origPhoto(u), null, 0, 0, 0)
                }
            }
        }
        return TweetResult(
            id = o.optString("tweetID"),
            authorName = o.optString("user_name"),
            handle = o.optString("user_screen_name"),
            avatar = o.optString("user_profile_image_url").ifBlank { null },
            text = o.optString("text"),
            date = o.optString("date"),
            media = items
        )
    }

    /** 图片统一取原图 */
    private fun origPhoto(u: String): String {
        val clean = u.substringBefore("?")
        return "$clean?name=orig"
    }
}
