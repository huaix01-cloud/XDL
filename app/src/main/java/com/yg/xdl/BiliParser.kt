package com.yg.xdl

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 哔哩哔哩解析。
 *
 * 走的是网页公开接口，不需要登录：
 *   view     → 视频信息（标题 / 封面 / UP 主 / cid）
 *   playurl  → 播放地址，fnval=0 时返回**带音轨的整段 mp4**（durl），免去 DASH 合并
 *
 * 未登录最高只能拿到 720P（qn=80 会被服务端降到 64），这是 B 站的限制，不是这里的问题。
 */
object BiliParser {

    private const val REFERER = "https://www.bilibili.com"
    private const val VIEW = "https://api.bilibili.com/x/web-interface/view?bvid="
    private const val PLAY = "https://api.bilibili.com/x/player/playurl"

    private val BV_RE = Regex("""BV[0-9A-Za-z]{10}""")
    private val AV_RE = Regex("""av(\d+)""", RegexOption.IGNORE_CASE)
    private val SHORT_RE = Regex("""https?://b23\.tv/\w+""")

    /** 从分享文本 / 链接 / 纯 BV 号里取出 bvid */
    fun extractId(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) throw ParseException("先粘贴一条 B站链接")

        BV_RE.find(text)?.let { return it.value }

        // b23.tv 短链：跟随重定向后从最终地址里找
        SHORT_RE.find(text)?.let { m ->
            val finalUrl = resolveRedirect(m.value)
            BV_RE.find(finalUrl)?.let { return it.value }
        }

        // av 号：转成 bvid
        AV_RE.find(text)?.let { m ->
            val aid = m.groupValues[1]
            val bv = aidToBv(aid)
            if (bv != null) return bv
        }
        throw ParseException("没找到 BV 号（要形如 bilibili.com/video/BVxxx）")
    }

    private fun resolveRedirect(url: String): String {
        var current = url
        repeat(5) {
            val conn = (NetConfig.open(current) as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", XParser.UA)
            }
            val code = conn.responseCode
            val loc = conn.getHeaderField("Location")
            conn.disconnect()
            if (code in 300..399 && !loc.isNullOrBlank()) {
                current = if (loc.startsWith("http")) loc else URL(URL(current), loc).toString()
            } else {
                return current
            }
        }
        return current
    }

    fun fetch(bvid: String, quality: Quality): TweetResult {
        val viewJson = JSONObject(httpGet(VIEW + bvid))
        if (viewJson.optInt("code", -1) != 0) {
            throw ParseException("B站接口返回 ${viewJson.optInt("code")}: ${viewJson.optString("message")}")
        }
        val data = viewJson.getJSONObject("data")
        val cid = data.optLong("cid")
        val title = data.optString("title")
        val desc = data.optString("desc")
        // B站封面给的是 http://，App 禁止明文流量，统一换成 https（hdslb 支持）
        val pic = data.optString("pic").ifBlank { null }?.replace("http://", "https://")
        val duration = data.optLong("duration")
        val owner = data.optJSONObject("owner")
        val upName = owner?.optString("name").orEmpty()
        val upFace = owner?.optString("face")?.ifBlank { null }?.replace("http://", "https://")

        if (cid <= 0L) throw ParseException("拿不到 cid，可能是番剧或需要登录的内容")

        val qn = when (quality) {
            Quality.BEST, Quality.P1080 -> 80
            Quality.P720 -> 64
            Quality.P480 -> 32
        }
        val playUrl = "$PLAY?bvid=$bvid&cid=$cid&qn=$qn&fnval=0&fourk=1&platform=html5&high_quality=1"
        val playJson = JSONObject(httpGet(playUrl))
        if (playJson.optInt("code", -1) != 0) {
            throw ParseException("取播放地址失败 ${playJson.optInt("code")}: ${playJson.optString("message")}")
        }
        val pd = playJson.getJSONObject("data")
        val durl = pd.optJSONArray("durl")
        if (durl == null || durl.length() == 0) {
            throw ParseException("没有可下载的 mp4（可能需要登录或大会员）")
        }
        val first = durl.getJSONObject(0)
        val videoUrl = first.optString("url")
        if (videoUrl.isBlank()) throw ParseException("播放地址为空")

        val realQuality = pd.optInt("quality")
        Log.i(
            "BILI",
            "解析成功 bvid=$bvid cid=$cid qn=$qn→$realQuality 时长=${duration}s " +
                "大小=${first.optLong("size") / 1024 / 1024}MB"
        )

        val item = MediaItem(
            kind = "video",
            url = videoUrl,
            thumb = pic,
            width = 0,
            height = 0,
            durationMs = duration * 1000,
            referer = REFERER
        )

        return TweetResult(
            id = bvid,
            authorName = title,
            handle = upName.ifBlank { bvid },
            avatar = upFace,
            text = if (desc.isBlank() || desc.trim() == "-") title else "$title\n$desc",
            date = "",
            media = listOf(item),
            platform = Platform.BILIBILI
        )
    }

    private fun httpGet(url: String): String {
        val conn = (NetConfig.open(url) as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 15000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", XParser.UA)
            setRequestProperty("Referer", REFERER)
            setRequestProperty("Accept", "application/json,text/plain,*/*")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw ParseException("HTTP $code")
        }
        val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        conn.disconnect()
        return body
    }

    /** av123 → BV1xx411c7mD */
    private fun aidToBv(aidStr: String): String? {
        val aid = aidStr.toLongOrNull() ?: return null
        val table = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
        var x = (aid xor 177451812L) + 8728348608L
        val positions = intArrayOf(11, 10, 3, 8, 4, 6)
        val result = StringBuilder("BV1  4 1 7  ")
        for (i in positions.indices) {
            val idx = ((x / pow58(i)) % 58).toInt()
            result.setCharAt(positions[i], table[idx])
        }
        return result.toString().replace(" ", "")
    }

    private fun pow58(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 58 }
        return r
    }
}
