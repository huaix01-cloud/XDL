package com.yg.xdl

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 网络 + 代理状态检测。
 *
 * 为什么不是「访问一个域名看看通不通」：
 * 那种做法有三个问题 —— 每次都要占用流量、慢（一个往返 RTT 起）、
 * 而且分不清「没网 / 代理没开 / 代理开了但走不通」这三种完全不同的故障。
 *
 * 这里分三层看，各用最合适的信号：
 *   1) 在线状态   → ConnectivityManager.NetworkCallback + NET_CAPABILITY_VALIDATED
 *                  系统级判定，零流量、被动实时，还能区分 WiFi / 移动网络
 *   2) 代理是否在跑 → 对代理端口做一次 TCP 握手（1200ms 超时）
 *                  毫秒级、不产生 HTTP 流量，代理软件挂了能立刻看出来
 *   3) 能不能真正解析 → 对 api.fxtwitter.com 发一次 GET，看有没有 HTTP 响应
 *                  只在下拉刷新 / 点检测时做，平时不跑
 */
object NetWatch {

    enum class Status {
        UNKNOWN, CHECKING, OFFLINE, PROXY_DOWN, OK_PROXY, OK_DIRECT, UNREACHABLE
    }

    private const val PROBE_URL = "https://api.fxtwitter.com/2/status/20" // 会 404，但有响应就算通

    var online by mutableStateOf(false)
        private set
    var validated by mutableStateOf(false)
        private set
    var onWifi by mutableStateOf(false)
        private set

    var checking by mutableStateOf(false)
        private set
    var proxyOpen by mutableStateOf<Boolean?>(null)
        private set
    var apiReachable by mutableStateOf<Boolean?>(null)
        private set
    var apiMillis by mutableStateOf(-1L)
        private set
    var checkedAt by mutableStateOf(0L)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private var cm: ConnectivityManager? = null
    private var callback: ConnectivityManager.NetworkCallback? = null

    val status: Status
        get() = when {
            checking -> Status.CHECKING
            !online -> Status.OFFLINE
            NetConfig.proxyInUse && proxyOpen == false -> Status.PROXY_DOWN
            apiReachable == true -> if (NetConfig.proxyInUse && proxyOpen == true) Status.OK_PROXY else Status.OK_DIRECT
            apiReachable == false -> Status.UNREACHABLE
            else -> Status.UNKNOWN
        }

    val statusText: String
        get() = when (status) {
            Status.UNKNOWN -> "未检测"
            Status.CHECKING -> "检测中…"
            Status.OFFLINE -> "无网络"
            Status.PROXY_DOWN -> "代理未响应"
            Status.OK_PROXY -> "代理已连接"
            Status.OK_DIRECT -> if (NetConfig.proxyInUse) "直连可用" else "网络正常"
            Status.UNREACHABLE -> "解析不可达"
        }

    fun start(ctx: Context) {
        if (cm != null) return
        val c = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        cm = c
        refreshFromActive()
        callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                onWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            }

            override fun onLost(network: Network) {
                online = false
                validated = false
                onWifi = false
            }

            override fun onUnavailable() {
                online = false
                validated = false
            }
        }
        runCatching { c.registerDefaultNetworkCallback(callback!!) }
            .onFailure { Log.w("NET", "注册网络回调失败: ${it.message}") }
    }

    fun refreshFromActive() {
        val c = cm ?: return
        val n = c.activeNetwork ?: run {
            online = false
            validated = false
            return
        }
        val caps = c.getNetworkCapabilities(n)
        online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    /** 全量探测：代理端口 + 解析接口。调用方应在协程里调 */
    suspend fun probe() {
        if (checking) return
        checking = true
        try {
            lastError = null
            refreshFromActive()

            val useProxy = NetConfig.proxyInUse
            val ph = NetConfig.proxyHost
            val pp = NetConfig.proxyPort

            var pOpen: Boolean? = null
            val t0 = System.currentTimeMillis()
            var reachable = false
            var err: String? = null

            withContext(Dispatchers.IO) {
                if (ph != null && pp in 1..65535) {
                    pOpen = try {
                        Socket().use { s ->
                            s.connect(InetSocketAddress(ph, pp), 1200)
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }

                try {
                    val conn = NetConfig.openForProbe(PROBE_URL, useProxy) as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", XParser.UA)
                    val code = conn.responseCode
                    runCatching { conn.inputStream?.use { it.readBytes() } }
                    conn.disconnect()
                    // 任何 HTTP 响应码都说明链路是通的（这个 URL 本来就返回 404）
                    reachable = code in 100..599
                    if (!reachable) err = "HTTP $code"
                } catch (e: Exception) {
                    reachable = false
                    err = "${e.javaClass.simpleName}: ${e.message}"
                }
            }

            proxyOpen = pOpen
            apiReachable = reachable
            apiMillis = System.currentTimeMillis() - t0
            lastError = err
            checkedAt = System.currentTimeMillis()

            Log.i(
                "NET",
                "探测完成 在线=$online 已联网验证=$validated WiFi=$onWifi " +
                    "代理=${if (useProxy) NetConfig.current else "关"} 端口通=${pOpen ?: "未测"} " +
                    "解析可达=$reachable 用时=${apiMillis}ms" + (err?.let { " 错误=$it" } ?: "")
            )
        } finally {
            // 协程被取消（比如 Activity 重建）也必须复位，否则界面永远停在「检测中」
            checking = false
        }
    }
}
