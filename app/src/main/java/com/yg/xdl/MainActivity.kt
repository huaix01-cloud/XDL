package com.yg.xdl

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.ViewAnimationUtils
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
            // 状态栏/导航栏融入界面：透明 + 图标颜色跟随主题
            LaunchedEffect(useDark) {
                val bg = if (useDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()
                window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(bg))
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDark
                    isAppearanceLightNavigationBars = !useDark
                }
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

    // ---------------------------------------------------------------- 主题切换动画

    /** 动画进行中，避免重复点击叠出多层覆盖视图 */
    private var revealBusy = false

    /**
     * V5 要求的 Circular Reveal 主题切换：
     * 用一层纯色 View 盖住界面，从点击点向外扩散（切深色）或收缩（切浅色），
     * 动画结束再真正切主题并移除覆盖层，避免闪烁与布局跳动。
     */
    fun switchThemeWithReveal(cx: Float, cy: Float, target: ThemeMode) {
        if (revealBusy) return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: run {
            Settings.updateTheme(target)
            return
        }
        if (content.width == 0 || content.height == 0) {
            Settings.updateTheme(target)
            return
        }

        val goDark = target == ThemeMode.DARK
        // 目标主题的背景色（与 Theme.kt 的 background 一致）
        val overlayColor = if (goDark) 0xFF141218.toInt() else 0xFFFEF7FF.toInt()

        val overlay = View(this).apply { setBackgroundColor(overlayColor) }
        content.addView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val w = content.width.toFloat()
        val h = content.height.toFloat()
        val maxRadius = kotlin.math.hypot(
            kotlin.math.max(cx, w - cx).toDouble(),
            kotlin.math.max(cy, h - cy).toDouble()
        ).toFloat().coerceAtLeast(1f)

        revealBusy = true
        val startR = if (goDark) 0f else maxRadius
        val endR = if (goDark) maxRadius else 0f

        val anim = ViewAnimationUtils.createCircularReveal(
            overlay, cx.toInt(), cy.toInt(), startR, endR
        )
        anim.duration = 620
        anim.interpolator = AnimationUtils.loadInterpolator(this, android.R.interpolator.fast_out_slow_in)
        anim.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false

            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) {
                    Settings.updateTheme(target)
                }
                // 等一帧让 Compose 完成重组，再摘掉覆盖层
                overlay.postDelayed({
                    runCatching { content.removeView(overlay) }
                    revealBusy = false
                }, if (cancelled) 0 else 32)
            }
        })
        Log.i("UI", "主题切换 Circular Reveal 圆心=(${cx.toInt()},${cy.toInt()}) 半径=${maxRadius.toInt()} → $target")
        anim.start()
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
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val activity = ctx as? MainActivity
    var themeBtnPos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var route by rememberSaveable { mutableStateOf("home") }
    // 放在 App 里，切页面不会丢解析结果
    val homeState = remember { HomeState() }

    val dark = when (Settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    BackHandler(enabled = route != "home") { route = "home" }

    Surface(
        // 背景铺满整块屏幕（含状态栏/导航栏后面），内容各自加 systemBarsPadding，
        // 这样浅色主题下状态栏区域也是浅色，不会露出窗口底色
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                when (targetState) {
                    // 设置页：从 FAB 处放大展开
                    "settings" -> (
                        scaleIn(
                            initialScale = 0.88f,
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) + fadeIn(tween(220))
                    ).togetherWith(fadeOut(tween(150)))
                    // 下载页：从右滑入
                    "tasks" -> (
                        slideInHorizontally(
                            animationSpec = tween(380, easing = FastOutSlowInEasing)
                        ) { it } + fadeIn(tween(200))
                    ).togetherWith(
                        slideOutHorizontally(animationSpec = tween(380)) { -it / 5 } + fadeOut(tween(150))
                    )
                    // 回主页：轻微放大 + 淡入
                    else -> (
                        fadeIn(tween(260)) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(340, easing = FastOutSlowInEasing)
                        )
                    ).togetherWith(fadeOut(tween(170)))
                }
            },
            label = "screen"
        ) { r ->
            when (r) {
                "home" -> HomeScreen(
                    st = homeState,
                    incoming = incoming.value,
                    onConsumedIncoming = { incoming.value = null },
                    dark = dark,
                    onToggleTheme = {
                        val target = when {
                            Settings.theme == ThemeMode.SYSTEM ->
                                if (dark) ThemeMode.LIGHT else ThemeMode.DARK
                            Settings.theme == ThemeMode.LIGHT -> ThemeMode.DARK
                            else -> ThemeMode.LIGHT
                        }
                        if (activity != null && themeBtnPos != androidx.compose.ui.geometry.Offset.Zero) {
                            activity.switchThemeWithReveal(themeBtnPos.x, themeBtnPos.y, target)
                        } else {
                            Settings.updateTheme(target)
                        }
                    },
                    onOpenTasks = { route = "tasks" },
                    onOpenSettings = { route = "settings" },
                    onThemePos = { themeBtnPos = it }
                )
                "tasks" -> TasksScreen(onBack = { route = "home" })
                else -> SettingsScreen(onBack = { route = "home" })
            }
        }
    }
}
