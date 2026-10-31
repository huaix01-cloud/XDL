package com.yg.xdl

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.URL
import java.net.URLConnection

/**
 * 1.2.1 起 App **不再提供代理配置**。
 *
 * 原因：手机开 Clash / V2Ray 的 VPN（TUN）模式时，流量在系统层就被接管，
 * Java 的 HttpURLConnection 一样走 VPN，App 内再配一份 HTTP 代理没有收益。
 * 保留这个对象只是为了收口「建连接」这件事，并让老调用点不用改。
 *
 * 想看网络状态请用 [NetWatch]（只读检测，不配置）。
 */
object NetConfig {

    /** 恒为 true：没有可配置的开关了 */
    var enabled by mutableStateOf(true)
        private set

    /** 没有可配置的代理地址 */
    val current: String
        get() = ""

    val proxyEnabled: Boolean
        get() = false

    val proxyInUse: Boolean
        get() = false

    fun open(url: String): URLConnection = URL(url).openConnection()

    /** 兼容老签名，useProxy 已无意义 */
    fun open(url: String, useProxy: Boolean): URLConnection = URL(url).openConnection()

    fun openForProbe(url: String, useProxy: Boolean): URLConnection = URL(url).openConnection()

    fun isProxyBrokenFor(url: String): Boolean = false

    fun markProxyBroken(url: String, reason: String) {
        // 已无代理，保留空实现以兼容调用点
    }

    fun load(ctx: android.content.Context) = Unit

    fun setEnabled(ctx: android.content.Context, v: Boolean) = Unit
}
