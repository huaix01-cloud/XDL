package com.yg.xdl

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.net.URLConnection

/**
 * 全局网络配置：只支持一条 HTTP 代理。
 * 留空 = 直连（手机开 Clash / V2Ray 的 VPN 模式时就用这个）。
 * 填 127.0.0.1:7890 这类 = 明确走代理（不开 VPN 也能下）。
 */
object NetConfig {

    private const val PREF = "xdl"
    private const val KEY_PROXY = "proxy"
    private const val KEY_ENABLED = "proxy_enabled"

    @Volatile
    private var host: String? = null

    @Volatile
    private var port: Int = 0

    /** 代理总开关（UI 上那个「代理」switch） */
    var enabled by mutableStateOf(true)
        private set

    val current: String
        get() = if (host != null && port > 0) "$host:$port" else ""

    val proxyHost: String?
        get() = host

    val proxyPort: Int
        get() = port

    /** 是否配了代理地址 */
    val proxyEnabled: Boolean
        get() = host != null && port > 0

    /** 是否真的会走代理（既配了地址，开关也开着） */
    val proxyInUse: Boolean
        get() = enabled && proxyEnabled

    /** 本次运行里，代理对这些域名已判定不可用（代理规则常按域名分流，所以按域名记） */
    private val brokenHosts = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun isProxyBrokenFor(url: String): Boolean {
        val h = runCatching { URL(url).host }.getOrNull() ?: return false
        return brokenHosts.contains(h)
    }

    fun markProxyBroken(url: String, reason: String) {
        val h = runCatching { URL(url).host }.getOrNull() ?: return
        if (brokenHosts.add(h)) {
            Log.w("NET", "代理对 $h 不可用（${reason.take(100)}），之后该域名一律直连")
        }
    }

    fun load(ctx: Context) {
        val sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        apply(sp.getString(KEY_PROXY, "").orEmpty())
        enabled = sp.getBoolean(KEY_ENABLED, true)
    }

    fun save(ctx: Context, raw: String) {
        apply(raw)
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY_PROXY, current).apply()
    }

    fun setEnabled(ctx: Context, v: Boolean) {
        enabled = v
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, v).apply()
        Log.i("NET", "代理开关 = ${if (v) "开" else "关（强制直连）"}")
    }

    private fun apply(raw: String) {
        // 中文输入法常把 . : 打成全角，统一纠正
        val s = raw.trim()
            .replace('\u3002', '.')
            .replace('\uFF0E', '.')
            .replace('\uFF1A', ':')
            .replace('\uFF0C', ',')
            .replace('\u00A0', ' ')
            .replace(Regex("\\s+"), "")
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("socks5://")
            .removePrefix("socks://")
            .trim()
            .trimEnd('/')
        if (s.isBlank()) {
            host = null
            port = 0
            return
        }
        val idx = s.lastIndexOf(':')
        if (idx <= 0) {
            host = null
            port = 0
            return
        }
        val h = s.substring(0, idx).trim()
        val p = s.substring(idx + 1).trim().toIntOrNull() ?: 0
        if (h.isNotBlank() && p in 1..65535) {
            host = h
            port = p
        } else {
            host = null
            port = 0
        }
    }

    fun open(url: String): URLConnection = open(url, useProxy = true)

    /**
     * useProxy=false 强制直连。
     * 代理分流把某些域名导到死节点时，调用方可以用它自动降级。
     */
    fun open(url: String, useProxy: Boolean): URLConnection {
        val h = host
        val p = port
        val viaProxy = useProxy && enabled && h != null && p in 1..65535 && !isProxyBrokenFor(url)
        return if (viaProxy) {
            URL(url).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(h!!, p)))
        } else {
            URL(url).openConnection()
        }
    }

    /** 探测专用：不看 brokenHosts 记忆，测的就是真实走代理的结果 */
    fun openForProbe(url: String, useProxy: Boolean): URLConnection {
        val h = host
        val p = port
        return if (useProxy && h != null && p in 1..65535) {
            URL(url).openConnection(Proxy(Proxy.Type.HTTP, InetSocketAddress(h, p)))
        } else {
            URL(url).openConnection()
        }
    }
}
