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
import java.net.Proxy

/**
 * 网络状态检测（**只读**，App 里不配置任何代理）。
 *
 * 三层信号：
 *   1) 在线状态  → ConnectivityManager.NetworkCallback + NET_CAPABILITY_VALIDATED
 *   2) 是否有代理 → 系统 VPN（TRANSPORT_VPN）或 ConnectivityManager.defaultProxy
 *   3) 能不能解析 → 对 api.fxtwitter.com 发一次 GET（8s 超时）
 */
object NetWatch {

    enum class Status { UNKNOWN, CHECKING, OFFLINE, OK, UNREACHABLE }

    private const val PROBE_URL = "https://api.fxtwitter.com/2/status/20" // 会 404，但有响应就算通

    var online by mutableStateOf(false)
        private set
    var validated by mutableStateOf(false)
        private set
    var onWifi by mutableStateOf(false)
        private set

    /** 系统层是否有 VPN 在跑 */
    var vpnActive by mutableStateOf(false)
        private set
    var httpProxy by mutableStateOf<String?>(null)
        private set

    var checking by mutableStateOf(false)
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

    /** 有 VPN 或系统 HTTP 代理 */
    val hasProxy: Boolean
        get() = vpnActive || httpProxy != null

    val status: Status
        get() = when {
            checking -> Status.CHECKING
            !online -> Status.OFFLINE
            apiReachable == true -> Status.OK
            apiReachable == false -> Status.UNREACHABLE
            else -> Status.UNKNOWN
        }

    val statusText: String
        get() = when (status) {
            Status.UNKNOWN -> "未检测"
            Status.CHECKING -> "检测中"
            Status.OFFLINE -> "无网络"
            Status.OK -> "已连接"
            Status.UNREACHABLE -> "不可达"
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
                vpnActive = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }

            override fun onLost(network: Network) {
                online = false
                validated = false
                onWifi = false
                vpnActive = false
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
            vpnActive = false
            return
        }
        val caps = c.getNetworkCapabilities(n)
        online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        vpnActive = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        httpProxy = runCatching {
            val p = c.defaultProxy
            if (p == null || p == Proxy.NO_PROXY) null else p.toString()
        }.getOrNull()
    }

    suspend fun probe() {
        if (checking) return
        checking = true
        try {
            lastError = null
            refreshFromActive()

            val t0 = System.currentTimeMillis()
            var reachable = false
            var err: String? = null

            withContext(Dispatchers.IO) {
                try {
                    val conn = NetConfig.openForProbe(PROBE_URL, false) as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    conn.setRequestProperty("User-Agent", XParser.UA)
                    val code = conn.responseCode
                    runCatching { conn.inputStream?.use { it.readBytes() } }
                    conn.disconnect()
                    reachable = code in 100..599
                    if (!reachable) err = "HTTP $code"
                } catch (e: Exception) {
                    reachable = false
                    err = "${e.javaClass.simpleName}: ${e.message}"
                }
            }

            apiReachable = reachable
            apiMillis = System.currentTimeMillis() - t0
            lastError = err
            checkedAt = System.currentTimeMillis()

            Log.i(
                "NET",
                "探测完成 在线=$online 验证=$validated WiFi=$onWifi VPN=$vpnActive " +
                    "系统代理=${httpProxy ?: "无"} 解析可达=$reachable 用时=${apiMillis}ms" +
                    (err?.let { " 错误=$it" } ?: "")
            )
        } finally {
            checking = false
        }
    }
}
