package com.yg.xdl

/**
 * 按链接**自动识别**平台并分发解析。
 * 用户不需要手动选平台——粘什么链接就认什么。
 *
 * 目前支持：X（FxTwitter）、哔哩哔哩（官方网页接口）。
 * 抖音 / 快手：接口还在调研，明确报「不支持」而不是静默失败。
 */
object Parser {

    private val BV_RE = Regex("""BV[0-9A-Za-z]{10}""")
    private val X_ID_RE = Regex("""^\d{15,25}$""")

    /** 只做判断，不联网 */
    fun detect(raw: String): Platform {
        val t = raw.trim()
        if (t.isEmpty()) throw ParseException("先粘贴一条链接")
        return when {
            t.contains("bilibili.com") || t.contains("b23.tv") || BV_RE.containsMatchIn(t) ->
                Platform.BILIBILI
            t.contains("x.com") || t.contains("twitter.com") || t.contains("t.co") ||
                X_ID_RE.matches(t) -> Platform.X
            t.contains("douyin.com") || t.contains("iesdouyin.com") -> Platform.DOUYIN
            t.contains("kuaishou.com") -> Platform.KUAISHOU
            else -> throw ParseException("认不出这个链接（目前支持 X 和哔哩哔哩）")
        }
    }

    fun parse(raw: String, quality: Quality): TweetResult {
        return when (val p = detect(raw)) {
            Platform.X -> XParser.fetch(XParser.extractId(raw), quality)
            Platform.BILIBILI -> BiliParser.fetch(BiliParser.extractId(raw), quality)
            else -> throw ParseException("${p.label} 还没接（接口调研中），先用 X 或哔哩哔哩")
        }
    }
}
