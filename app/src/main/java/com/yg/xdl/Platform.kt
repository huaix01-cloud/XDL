package com.yg.xdl

/** 平台区分：目前只做 UI 区分，只有 X 真正可用 */
enum class Platform(val label: String, val short: String, val enabled: Boolean) {
    X("X", "X", true),
    BILIBILI("哔哩哔哩", "B站", true),
    DOUYIN("抖音（搁置）", "抖音", false),
    KUAISHOU("快手（搁置）", "快手", false);

    companion object {
        fun fromName(n: String?): Platform = entries.firstOrNull { it.name == n } ?: X
    }
}
