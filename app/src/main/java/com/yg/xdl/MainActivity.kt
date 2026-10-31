package com.yg.xdl

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Bundle
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.view.ViewAnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.OvershootInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
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
                "画质=${Settings.effectiveQuality.label}"
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

        // 预置两层揭示层（都常驻）：
        // - 纯色层给「深色覆盖」用，颜色就是目标色；
        // - 截图层给「浅色收回」用，内容 = 当前界面，收缩时才不会把文字盖成一片纯色。
        // 两层分开是为了避免来回换背景 —— 换背景要整层重绘，实测能卡到 112ms。
        // 平时 alpha≈0（不挡触摸、肉眼不可辨），切换时只改裁剪半径。
        findViewById<ViewGroup>(android.R.id.content)?.let { c ->
            fun makeLayer(): View = View(this).apply {
                alpha = IDLE_ALPHA
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            val dark = makeLayer().apply { setBackgroundColor(0xFF141218.toInt()) }
            val snap = HoleRevealView(this).apply {
                alpha = IDLE_ALPHA
                isClickable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            }
            c.addView(dark, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            c.addView(snap, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            revealOverlayDark = dark
            revealOverlaySnap = snap
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

    /** 常驻的主题揭示层：纯色层兜底，截图层主用（深色走挖洞、浅色走裁剪） */
    private var revealOverlayDark: View? = null
    private var revealOverlaySnap: HoleRevealView? = null

    private companion object {
        /** 覆盖层闲置时的透明度：约 1/255，肉眼不可辨，但能让硬件层常驻不被回收 */
        const val IDLE_ALPHA = 0.004f

        /** 截图层分辨率比例：压低纹理上传成本 */
        const val SNAPSHOT_SCALE = 0.6f
    }

    /**
     * 主题切换的 Circular Reveal（6canvas：以点击点为圆心、500~700ms、深浅双向）。
     *
     * 两个方向刻意做成不同动作（用户要求）：
     *  - **切深色 = 深色覆盖**：深色从点击点向外扩张，铺满屏幕后落地主题，带过冲回弹。
     *  - **切浅色 = 浅色收回**：先把状态落地成浅色，再把旧深色从满屏收回到点击点。
     *
     * 注意半径的坑（见 4.12）：扩张 0→max 可以用过冲插值器；收回 max→0 不行，
     * 过冲会把半径算成负数导致动画被中断，只能用减速插值器。
     * 另外收回方向先落地状态，所以即使中断也不会停在旧主题。
     */
    fun switchThemeWithReveal(
        cx: Float,
        cy: Float,
        target: ThemeMode,
        fromDark: Boolean,
        toDark: Boolean
    ) {
        if (revealBusy) return
        val content = findViewById<ViewGroup>(android.R.id.content)
        if (content == null || content.width == 0 || content.height == 0) {
            Settings.updateTheme(target)
            return
        }

        // 有些组合视觉上没变化（比如跟随系统时解析出来还是同一个色），直接写状态
        if (fromDark == toDark) {
            Settings.updateTheme(target)
            Log.i("UI", "主题切换 同色跳过（from=${if (fromDark) "dark" else "light"}）→ $target")
            return
        }

        val darkLayer = revealOverlayDark
        val snapLayer = revealOverlaySnap
        if (darkLayer == null || snapLayer == null) {
            Settings.updateTheme(target)
            return
        }

        revealBusy = true
        val maxRadius = kotlin.math.hypot(
            kotlin.math.max(cx, content.width - cx).toDouble(),
            kotlin.math.max(cy, content.height - cy).toDouble()
        ).toFloat().coerceAtLeast(1f)

        // 两个方向都用「当前界面的截图」当覆盖层，组件全程都在，不会被纯色抹掉：
        // - 切深色 = **覆盖**：先落地深色主题，再让截图中间挖个洞、洞从点击点扩大，
        //   洞里露出深色新界面 → 深色以点击点为中心向外铺开。
        // - 切浅色 = **收回**：先落地浅色主题，再把整张截图从满屏裁到点击点。
        // 截图层用 0.6 倍分辨率，压低纹理上传成本；纯色层只在截图拿不到时兜底。
        val sw = (content.width * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)
        val sh = (content.height * SNAPSHOT_SCALE).toInt().coerceAtLeast(1)
        val bmp = snapshotBitmap(sw, sh)
        if (bmp == null) {
            playReveal(darkLayer, cx, cy, target, fallbackExpand = true, maxRadius = maxRadius)
            return
        }
        runCatching {
            PixelCopy.request(
                window, bmp,
                { res ->
                    if (res == PixelCopy.SUCCESS) {
                        // 先把截图交给层（此刻层几乎不可见，重绘用户看不到），
                        // 隔几帧再进动画，避免上传/重组挤在动画首帧
                        snapLayer.setSnapshot(bmp, cx, cy)
                        snapLayer.postDelayed({
                            if (toDark) {
                                playHoleReveal(snapLayer, cx, cy, target, maxRadius)
                            } else {
                                playReveal(snapLayer, cx, cy, target, fallbackExpand = false, maxRadius = maxRadius)
                            }
                        }, 64L)
                    } else {
                        playReveal(darkLayer, cx, cy, target, fallbackExpand = true, maxRadius = maxRadius)
                    }
                },
                android.os.Handler(android.os.Looper.getMainLooper())
            )
        }.onFailure {
            playReveal(darkLayer, cx, cy, target, fallbackExpand = true, maxRadius = maxRadius)
        }
    }

    /**
     * 深色覆盖：整层是旧界面截图，中间挖洞并让洞从 0 扩到全屏。
     * 洞里是已经切好的深色新界面，所以视觉是「深色从点击点铺开」，
     * 而不是「浅色往回缩」—— 这是两个方向动作不一样的关键。
     */
    private fun playHoleReveal(
        layer: HoleRevealView,
        cx: Float,
        cy: Float,
        target: ThemeMode,
        maxRadius: Float
    ) {
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            layer.alpha = IDLE_ALPHA
            layer.isClickable = false
            layer.clearSnapshot()
            revealBusy = false
        }

        layer.isClickable = true
        layer.alpha = 1f
        // 先把深色主题落地（此刻整屏被旧截图盖住，看不出跳变）
        Settings.updateTheme(target)
        Log.i("UI", "主题切换 深色覆盖(挖洞) 圆心=(${cx.toInt()},${cy.toInt()}) → $target")

        // 等重组画完再开始扩洞
        layer.postDelayed({
            val va = android.animation.ValueAnimator.ofFloat(0f, maxRadius)
            va.duration = 560
            // 半径是从 0 铺到全屏，跨度很大：Expressive 那条曲线前段太猛，
            // 100ms 就能铺到 80%，看起来像"闪一下就没了"。这里用缓一点的减速曲线。
            va.interpolator = DecelerateInterpolator(1.9f)
            va.addUpdateListener { layer.holeRadius = it.animatedValue as Float }
            va.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = finish()
            })
            va.start()
            layer.postDelayed({ if (!finished) finish() }, 1400)
        }, 64L)
    }

    /** M3 Expressive 的空间曲线；个别 ROM 对非单调控制点会抛异常，兜底回退 */
    private fun expressiveInterpolator(): Interpolator =
        runCatching {
            android.view.animation.PathInterpolator(0.42f, 1.67f, 0.21f, 0.90f) as Interpolator
        }.getOrElse { OvershootInterpolator(0.92f) }

    /** 复用同一张截屏 bitmap，避免每次切换都分配十几 MB */
    private var captureBitmap: Bitmap? = null

    private fun snapshotBitmap(w: Int, h: Int): Bitmap? {
        if (w <= 0 || h <= 0) return null
        val cached = captureBitmap
        if (cached != null && !cached.isRecycled && cached.width == w && cached.height == h) return cached
        runCatching { cached?.recycle() }
        return runCatching { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
            .onSuccess { captureBitmap = it }
            .getOrNull()
    }

    private fun playReveal(
        ov: View,
        cx: Float,
        cy: Float,
        target: ThemeMode,
        fallbackExpand: Boolean,
        maxRadius: Float
    ) {
        var finished = false
        fun finish(hideDelay: Long) {
            if (finished) return
            finished = true
            // 只收起，不改背景：换背景会整层重绘
            val hide = {
                ov.alpha = IDLE_ALPHA
                ov.isClickable = false
                revealBusy = false
            }
            if (hideDelay > 0) ov.postDelayed(hide, hideDelay) else hide()
        }

        // 主路径（截图）：旧界面从点击点收拢，露出已经换好色的新界面
        // 兜底（纯色）：从点击点扩张铺满，然后再切主题
        val startR = if (fallbackExpand) 0f else maxRadius
        val endR = if (fallbackExpand) maxRadius else 0f
        val anim = ViewAnimationUtils.createCircularReveal(ov, cx.toInt(), cy.toInt(), startR, endR)
        anim.duration = 520
        anim.interpolator = if (fallbackExpand) {
            expressiveInterpolator()
        } else {
            // 半径 max→0 不能用过冲曲线，会算成负半径；用强减速保证起步够快
            DecelerateInterpolator(2.2f)
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (fallbackExpand) {
                    // 纯色铺满之后才改主题，这次重组整帧都被盖住
                    Settings.updateTheme(target)
                    finish(hideDelay = 48L)
                } else {
                    finish(hideDelay = 0L)
                }
            }
        })

        Log.i(
            "UI",
            "主题切换 ${if (fallbackExpand) "覆盖(纯色兜底)" else "揭示(截图)"} " +
                "圆心=(${cx.toInt()},${cy.toInt()}) → $target"
        )

        ov.isClickable = true   // 动画期间吞点击
        ov.alpha = 1f
        if (fallbackExpand) {
            anim.start()
        } else {
            // 覆盖层此刻就是当前界面（截图），看不出变化，
            // 正好用来消化主题重组：先落地状态，等两帧画完再收拢
            Settings.updateTheme(target)
            ov.postDelayed({ anim.start() }, 32L)
        }

        // 兜底：动画万一不回调，1.4s 后强制收尾，别把按钮卡死
        ov.postDelayed({
            if (!finished) {
                Settings.updateTheme(target)
                finish(0L)
            }
        }, 1400)
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

        // 平台由 Parser 按链接自动识别：能认出平台就把原文交出去（X / B站都走这条）
        return runCatching {
            Parser.detect(raw)
            raw.trim()
        }.getOrNull()
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
                    // 设置页：从按钮位置"弹"出来，缩放 + 轻微上浮，阻尼低才看得出回弹
                    "settings" -> (
                        scaleIn(
                            initialScale = 0.82f,
                            animationSpec = spring(dampingRatio = 0.60f, stiffness = 420f)
                        ) + slideInVertically(
                            animationSpec = spring(dampingRatio = 0.62f, stiffness = 420f)
                        ) { it / 14 } + fadeIn(tween(150))
                    ).togetherWith(fadeOut(tween(110)))
                    // 下载页：从右滑入，弹簧会滑过头再回位
                    "tasks" -> (
                        slideInHorizontally(
                            animationSpec = spring(dampingRatio = 0.66f, stiffness = 400f)
                        ) { it } + fadeIn(tween(140))
                    ).togetherWith(
                        slideOutHorizontally(
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) { -it / 6 } + fadeOut(tween(110))
                    )
                    // 回主页：轻放大 + 淡入，尾巴收得软一点
                    else -> (
                        fadeIn(tween(170)) + scaleIn(
                            initialScale = 0.90f,
                            animationSpec = spring(dampingRatio = 0.58f, stiffness = 360f)
                        )
                    ).togetherWith(fadeOut(tween(130)))
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
                            activity.switchThemeWithReveal(
                                themeBtnPos.x, themeBtnPos.y, target,
                                fromDark = dark, toDark = target == ThemeMode.DARK
                            )
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
