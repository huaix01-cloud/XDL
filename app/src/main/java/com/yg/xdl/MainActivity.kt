package com.yg.xdl

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.yg.xdl.ui.HomeScreen
import com.yg.xdl.ui.HomeState
import com.yg.xdl.ui.SettingsScreen
import com.yg.xdl.ui.TasksScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val incoming = mutableStateOf<String?>(null)

    // 独立于 Activity 生命周期的 scope：重建时别把正在跑的探测一起取消
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Settings.init(this)
        NetConfig.load(this)
        Log.init(this)
        DownloadCenter.init(this)
        NetWatch.start(this)
        requestNotificationPermission()

        Log.i(
            "App",
            "启动 v${Settings.versionName} sdk=${Build.VERSION.SDK_INT} " +
                "代理=${NetConfig.current.ifBlank { "未设置" }} 开关=${NetConfig.enabled} " +
                "画质=${Settings.effectiveQuality.label} 平台=${Settings.platform.label}"
        )

        incoming.value = extract(intent)

        setContent {
            val systemDark = isSystemInDarkTheme()
            val useDark = when (Settings.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }
            XdlTheme(dark = useDark) {
                App(incoming)
            }
        }

        appScope.launch {
            delay(400)
            NetWatch.probe()
        }
    }

    override fun onResume() {
        super.onResume()
        // 回到前台顺手刷一次网络状态（不阻塞）
        appScope.launch { NetWatch.probe() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming.value = extract(intent)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                runCatching {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9001)
                }
            }
        }
    }

    /** 从分享 / 点开链接 / 文本处理 里取出推文链接 */
    private fun extract(i: Intent?): String? {
        if (i == null) return null
        val raw = when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> i.dataString
            Intent.ACTION_PROCESS_TEXT ->
                i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        } ?: return null
        val found = XParser.findTweetUrl(raw)
        if (found != null) return found
        val t = raw.trim()
        return if (t.matches(Regex("\\d{15,25}")) || t.contains("x.com") ||
            t.contains("twitter.com") || t.contains("t.co")
        ) t else null
    }
}

@Composable
fun App(incoming: MutableState<String?>) {
    val ctx = LocalContext.current
    var route by rememberSaveable { mutableStateOf("home") }
    var showProxy by remember { mutableStateOf(false) }
    var proxyText by remember { mutableStateOf(NetConfig.current) }
    // 放在 App 里，切页面不会丢解析结果
    val homeState = remember { HomeState() }

    val dark = when (Settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    BackHandler(enabled = route != "home") { route = "home" }

    Surface(
        modifier = Modifier.fillMaxSize().systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (route) {
            "home" -> HomeScreen(
                st = homeState,
                incoming = incoming.value,
                onConsumedIncoming = { incoming.value = null },
                dark = dark,
                onToggleTheme = {
                    Settings.updateTheme(
                        when {
                            Settings.theme == ThemeMode.SYSTEM ->
                                if (dark) ThemeMode.LIGHT else ThemeMode.DARK
                            Settings.theme == ThemeMode.LIGHT -> ThemeMode.DARK
                            else -> ThemeMode.LIGHT
                        }
                    )
                },
                onOpenTasks = { route = "tasks" },
                onOpenSettings = { route = "settings" },
                onOpenProxyDialog = {
                    proxyText = NetConfig.current
                    showProxy = true
                }
            )
            "tasks" -> TasksScreen(onBack = { route = "home" })
            else -> SettingsScreen(
                onBack = { route = "home" },
                onOpenProxyDialog = {
                    proxyText = NetConfig.current
                    showProxy = true
                }
            )
        }
    }

    if (showProxy) {
        AlertDialog(
            onDismissRequest = { showProxy = false },
            title = { Text("HTTP 代理") },
            text = {
                Column {
                    Text(
                        "填 host:port，比如 127.0.0.1:7890（Clash 的 HTTP / 混合端口）。" +
                            "留空则直连 —— 手机开了 VPN 模式时留空就行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = proxyText,
                        onValueChange = { proxyText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("127.0.0.1:7890") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    NetConfig.save(ctx, proxyText)
                    proxyText = NetConfig.current
                    showProxy = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = {
                    proxyText = ""
                    NetConfig.save(ctx, "")
                    showProxy = false
                }) { Text("清除") }
            }
        )
    }
}
