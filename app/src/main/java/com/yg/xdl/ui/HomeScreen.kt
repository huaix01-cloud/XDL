package com.yg.xdl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.xdl.DownloadCenter
import com.yg.xdl.ImageCache
import com.yg.xdl.MediaItem
import com.yg.xdl.NetWatch
import com.yg.xdl.Parser
import com.yg.xdl.Quality
import com.yg.xdl.R
import com.yg.xdl.Settings
import com.yg.xdl.TweetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 主页状态提升到 App 层：去「下载」「设置」再回来，解析结果还在。
 */
class HomeState {
    var url by mutableStateOf("")
    var loading by mutableStateOf(false)
    var parseProgress by mutableStateOf(0f)
    var tweet by mutableStateOf<TweetResult?>(null)
    var selected by mutableStateOf<Set<Int>>(emptySet())
    var error by mutableStateOf("")
    var status by mutableStateOf("")
}

@Composable
fun HomeScreen(
    st: HomeState,
    incoming: String?,
    onConsumedIncoming: () -> Unit,
    dark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    onThemePos: (Offset) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }

    fun toast(msg: String) {
        scope.launch { snackbar.showSnackbar(msg) }
    }

    /** 把当前结果里选中的媒体全部入队 */
    fun enqueueSelected(): Boolean {
        val t = st.tweet ?: return false
        val picks = st.selected.sorted()
        if (picks.isEmpty()) return false
        picks.forEach { i ->
            DownloadCenter.enqueue(
                platform = t.platform,
                handle = t.handle,
                tweetId = t.id,
                media = t.media[i],
                index = i,
                qualityLabel = Settings.effectiveQuality.label
            )
        }
        st.status = "已加入队列 ${picks.size} 个，去「下载」查看"
        return true
    }

    fun analyze(raw: String, thenDownload: Boolean = false) {
        val text = raw.trim()
        if (text.isEmpty()) {
            st.error = "先粘贴一条链接"
            return
        }
        if (st.loading) return
        st.loading = true
        st.error = ""
        st.status = ""
        st.tweet = null
        st.selected = emptySet()
        st.parseProgress = 0f
        scope.launch {
            val res = runCatching {
                withContext(Dispatchers.IO) { Parser.parse(text, Settings.effectiveQuality) }
            }
            res.onSuccess { r ->
                st.tweet = r
                st.selected = r.media.indices.toSet()
                st.parseProgress = 1f
                if (r.media.isEmpty()) {
                    st.error = "这条链接没有可下载的媒体"
                } else {
                    st.status = "共 ${r.media.size} 个媒体 · ${Settings.effectiveQuality.label}"
                    if (thenDownload) {
                        if (enqueueSelected()) toast("开始下载 ${r.media.size} 个")
                    }
                }
            }.onFailure { e ->
                st.error = e.message ?: "解析失败"
            }
            st.loading = false
        }
    }

    /** 新设计里「解析 / 下载」是一个按钮：没结果就解析并下载，有结果就继续下 */
    fun analyzeOrDownload() {
        if (st.loading) return
        val t = st.tweet
        if (t != null && t.media.isNotEmpty()) {
            if (DownloadCenter.running) {
                toast("正在下载，去「下载」查看进度")
                return
            }
            if (enqueueSelected()) toast("已加入下载队列")
            return
        }
        analyze(st.url, thenDownload = true)
    }

    // 解析进度：逐步逼近 92%，完成后跳 100%
    LaunchedEffect(st.loading) {
        if (st.loading) {
            st.parseProgress = 0f
            while (st.loading && st.parseProgress < 0.92f) {
                delay(110)
                st.parseProgress += (0.92f - st.parseProgress) * 0.18f
            }
        }
    }

    LaunchedEffect(incoming) {
        val v = incoming ?: return@LaunchedEffect
        st.url = v
        analyze(v)
        onConsumedIncoming()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // ---------------- TopAppBar（新标准：大圆角顶栏，标题 XDL + 网络状态）
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(0.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .height(64.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(44.dp)
                            .onGloballyPositioned { c -> onThemePos(c.boundsInWindow().center) }
                    ) {
                        Icon(
                            if (dark) ImageVector.vectorResource(R.drawable.ic_light_mode)
                            else ImageVector.vectorResource(R.drawable.ic_dark_mode),
                            contentDescription = "切换主题",
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "XDL",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    TopBarNetChip()
                }
            }

            // ---------------- 内容
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {

                // carousel：多图/多视频横向滑动，全屏宽卡片
                val media = st.tweet?.media.orEmpty()
                val pageCount = if (media.isEmpty()) 1 else media.size
                val pagerState = rememberPagerState(pageCount = { pageCount })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    pageSpacing = 10.dp
                ) { page ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(28.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    ) {
                        if (media.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    if (st.loading) {
                                        RingProgress(
                                            progress = st.parseProgress,
                                            size = 48.dp,
                                            stroke = 4.dp
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Text(
                                            "解析中…",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        Text(
                                            "解析后显示",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            CarouselPage(
                                item = media[page],
                                index = page,
                                total = media.size,
                                tweet = st.tweet
                            )
                        }
                    }
                }

                // 页码指示
                if (media.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        media.indices.forEach { i ->
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (i == pagerState.currentPage) 7.dp else 5.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i == pagerState.currentPage)
                                            MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 链接输入
                OutlinedTextField(
                    value = st.url,
                    onValueChange = { st.url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("粘贴在此处") },
                    leadingIcon = {
                        Icon(ImageVector.vectorResource(R.drawable.ic_article), contentDescription = null)
                    },
                    shape = MaterialTheme.shapes.medium,
                    minLines = 1,
                    maxLines = 3
                )

                Spacer(Modifier.height(10.dp))

                // 粘贴 / 清空 / 解析·下载
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            val t = clipboard.getText()?.text
                            if (!t.isNullOrBlank()) st.url = t
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)
                    ) {
                        Text("粘贴", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = {
                            st.url = ""
                            st.tweet = null
                            st.error = ""
                            st.status = ""
                            st.parseProgress = 0f
                        },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp)
                    ) {
                        Text("清空", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                    }
                    Button(
                        onClick = { analyzeOrDownload() },
                        enabled = !st.loading,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1.5f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_arrow_downward),
                            null,
                            Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (st.loading) "解析中" else "解析/下载",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 视频画质 + 保持最佳
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectField(
                        label = "",
                        value = "",
                        options = Quality.entries.map { it.label },
                        onSelect = { i -> Settings.updateQuality(Quality.entries[i]) },
                        selectedIndex = Quality.entries.indexOf(Settings.quality),
                        modifier = Modifier.width(157.dp),
                        enabled = !Settings.keepBest,
                        leadingIcon = Icons.Filled.PlayArrow,
                        supporting = "视频画质"
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).padding(top = 6.dp)
                    ) {
                        Checkbox(
                            checked = Settings.keepBest,
                            onCheckedChange = { Settings.updateKeepBest(it) }
                        )
                        Text(
                            "保持选择最佳画质",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            lineHeight = 14.sp
                        )
                    }
                }

                if (st.error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        st.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (st.status.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        st.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

/** topAppBar 里的网络状态 chip（跟 primaryContainer 底配合） */
@Composable
private fun TopBarNetChip() {
    val hasProxy = NetWatch.hasProxy
    val ms = if (NetWatch.apiMillis > 0) "${NetWatch.apiMillis}ms" else "---"
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = Modifier.width(112.dp).height(32.dp),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, fg.copy(alpha = 0.5f))
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_language),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = fg
            )
            Spacer(Modifier.width(4.dp))
            Text(
                if (hasProxy) "vpn $ms" else ms,
                style = MaterialTheme.typography.labelSmall,
                color = fg,
                maxLines = 1
            )
        }
    }
}

/** carousel 单页：大图 + 角标 + 标题/作者 */
@Composable
private fun CarouselPage(
    item: MediaItem,
    index: Int,
    total: Int,
    tweet: TweetResult?
) {
    val thumbUrl = item.thumb ?: if (item.isVideo) null else item.url.substringBefore("?") + "?name=small"
    var bmp by remember(thumbUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(thumbUrl) {
        var r = if (thumbUrl != null) withContext(Dispatchers.IO) { ImageCache.load(thumbUrl, item.referer) } else null
        if (r == null) r = withContext(Dispatchers.IO) { ImageCache.load(item.url, item.referer) }
        bmp = r
    }

    Box(Modifier.fillMaxSize()) {
        bmp?.let {
            Image(
                it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 左下角：媒体类型 + 尺寸/时长
        val badge = when {
            item.kind == "gif" -> "GIF"
            item.isVideo -> "视频"
            else -> "图片"
        }
        val extra = if (item.isVideo && item.durationMs > 0) {
            val s = item.durationMs / 1000
            String.format(Locale.US, "%d:%02d", s / 60, s % 60)
        } else if (item.width > 0) "${item.width}×${item.height}" else ""
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
                .background(Color(0x99000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                if (extra.isBlank()) badge else "$badge $extra",
                color = Color.White,
                fontSize = 11.sp
            )
        }

        // 右上角：页码
        if (total > 1) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .background(Color(0x99000000), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("${index + 1}/$total", color = Color.White, fontSize = 11.sp)
            }
        }

        // 底部渐隐条上的标题
        tweet?.let { t ->
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    t.text.lineSequence().firstOrNull().orEmpty().take(40),
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "@${t.handle}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
