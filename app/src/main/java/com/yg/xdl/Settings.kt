package com.yg.xdl

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ThemeMode(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }

/** 视频画质档位 */
enum class Quality(val label: String, val maxHeight: Int) {
    BEST("最佳画质", Int.MAX_VALUE),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480);
}

/**
 * 全局设置。全部用 Compose 的 mutableStateOf 存，改完 UI 自动重组；
 * 同时落盘到 SharedPreferences。
 */
object Settings {

    private const val PREF = "xdl_settings"

    private var sp: SharedPreferences? = null

    var theme by mutableStateOf(ThemeMode.SYSTEM)
        private set
    var dynamicColor by mutableStateOf(false)
        private set
    var platform by mutableStateOf(Platform.X)
        private set
    var quality by mutableStateOf(Quality.BEST)
        private set
    var keepBest by mutableStateOf(true)
        private set
    var notifyDownload by mutableStateOf(false)
        private set
    var wifiOnly by mutableStateOf(false)
        private set
    var maxConcurrent by mutableStateOf(2)
        private set
    var autoUpdate by mutableStateOf(true)
        private set
    var saveRoot by mutableStateOf("Movies/XDL")
        private set

    val versionName: String = "1.2.4"

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        sp = p
        theme = runCatching { ThemeMode.valueOf(p.getString("theme", "SYSTEM")!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        dynamicColor = p.getBoolean("dynamicColor", false)
        platform = Platform.fromName(p.getString("platform", "X"))
        quality = runCatching { Quality.valueOf(p.getString("quality", "BEST")!!) }
            .getOrDefault(Quality.BEST)
        keepBest = p.getBoolean("keepBest", true)
        notifyDownload = p.getBoolean("notifyDownload", false)
        wifiOnly = p.getBoolean("wifiOnly", false)
        maxConcurrent = p.getInt("maxConcurrent", 2).coerceIn(1, 4)
        autoUpdate = p.getBoolean("autoUpdate", true)
        saveRoot = p.getString("saveRoot", "Movies/XDL") ?: "Movies/XDL"
    }

    private fun put(block: SharedPreferences.Editor.() -> Unit) {
        val e = sp?.edit() ?: return
        e.block()
        e.apply()
    }

    fun updateTheme(v: ThemeMode) {
        theme = v
        put { putString("theme", v.name) }
    }

    fun updateDynamicColor(v: Boolean) {
        dynamicColor = v
        put { putBoolean("dynamicColor", v) }
    }

    fun updatePlatform(v: Platform) {
        platform = v
        put { putString("platform", v.name) }
    }

    fun updateQuality(v: Quality) {
        quality = v
        put { putString("quality", v.name) }
    }

    fun updateKeepBest(v: Boolean) {
        keepBest = v
        put { putBoolean("keepBest", v) }
    }

    fun updateNotifyDownload(v: Boolean) {
        notifyDownload = v
        put { putBoolean("notifyDownload", v) }
    }

    fun updateWifiOnly(v: Boolean) {
        wifiOnly = v
        put { putBoolean("wifiOnly", v) }
    }

    fun updateMaxConcurrent(v: Int) {
        maxConcurrent = v.coerceIn(1, 4)
        put { putInt("maxConcurrent", maxConcurrent) }
    }

    fun updateAutoUpdate(v: Boolean) {
        autoUpdate = v
        put { putBoolean("autoUpdate", v) }
    }

    fun updateSaveRoot(v: String) {
        saveRoot = v
        put { putString("saveRoot", v) }
    }

    /** 实际生效的画质（勾了「保持最佳」就强制 BEST） */
    val effectiveQuality: Quality
        get() = if (keepBest) Quality.BEST else quality
}
