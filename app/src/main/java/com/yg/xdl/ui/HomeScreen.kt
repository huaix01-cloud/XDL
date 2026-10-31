package com.yg.xdl.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.yg.xdl.NetConfig
import com.yg.xdl.NetWatch
import com.yg.xdl.Platform
import com.yg.xdl.Quality
import com.yg.xdl.R
import com.yg.xdl.Settings
import com.yg.xdl.TweetResult
import com.yg.xdl.XParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 主页状态提升到 App 层：去「下载」「设置」再回来，解析结果还在。
 * 放在 HomeScreen 内部用 remember 的话，一切页面就没了。
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

    fun analyze(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) {
            st.error = "先粘贴一条推文链接"
            return
        }
        if (Settings.platform != Platform.X) {
            st.error = "${Settings.platform.label} 还没接，先选 X"
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
                withContext(Dispatchers.IO) {
                    XParser.fetch(XParser.extractId(text), Settings.effectiveQuality)
                }
            }
            res.onSuccess { r ->
                st.tweet = r
                st.selected = r.media.indices.toSet()
                st.parseProgress = 1f
                if (r.media.isEmpty()) {
                    st.error = "这条推文没有图片或视频"
                } else {
                    st.status = "共 ${r.media.size} 个媒体 · ${Settings.effectiveQuality.label}"
                }
            }.onFailure { e ->
                st.error = e.message ?: "解析失败"
            }
            st.loading = false
        }
    }

    fun downloadSelected() {
        val t = st.tweet ?: return
        val picks = st.selected.sorted()
        if (picks.isEmpty()) {
            st.error = "先选要下载的媒体"
            return
        }
        picks.forEach { i ->
            DownloadCenter.enqueue(
                platform = Settings.platform,
                handle = t.handle,
                tweetId = t.id,
                media = t.media[i],
                index = i,
                qualityLabel = Settings.effectiveQuality.label
            )
        }
        st.status = "已加入队列 ${picks.size} 个 → 点「查看下载」"
        toast("已加入下载队列，${picks.size} 个")
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
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ---------------- 顶栏（V5：只有 主题按钮 + XDL + vpn chip，没有代理开关）
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .size(40.dp)
                        .onGloballyPositioned { c -> onThemePos(c.boundsInWindow().center) }
                ) {
                    Icon(
                        if (dark) ImageVector.vectorResource(R.drawable.ic_light_mode)
                        else ImageVector.vectorResource(R.drawable.ic_dark_mode),
                        contentDescription = "切换深色模式",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(Modifier.width(10.dp))
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            "XDL",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Spacer(Modifier.weight(1f))
                NetChip()
            }

            Spacer(Modifier.height(10.dp))

            // ---------------- 解析结果卡（6canvas：常驻占位，避免下面内容跳动）
            Spacer(Modifier.height(12.dp))
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                val t = st.tweet
                if (t == null) {
                    Box(
                        Modifier.fillMaxWidth().height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "解析后在这里显示推文内容",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(Modifier.padding(14.dp)) {
                        if (t.media.isNotEmpty()) {
                            t.media.withIndex().toList().chunked(2).forEach { rowItems ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (i, m) ->
                                        MediaTile(
                                            modifier = Modifier.weight(1f),
                                            item = m,
                                            selected = st.selected.contains(i),
                                            onToggle = {
                                                st.selected = if (st.selected.contains(i)) {
                                                    st.selected - i
                                                } else {
                                                    st.selected + i
                                                }
                                            }
                                        )
                                    }
                                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Column(
                                Modifier.weight(1f),
                                horizontalAlignment = Alignment.End
                            ) {
                                if (t.text.isNotBlank()) {
                                    Text(
                                        t.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End
                                    )
                                }
                                Text(
                                    "@${t.handle}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Avatar(t.avatar)
                        }
                    }
                }
            }
            // ---------------- 链接输入（V5：框内只有图标，无占位文字）
            OutlinedTextField(
                value = st.url,
                onValueChange = { st.url = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_library_books),
                        contentDescription = null
                    )
                },
                supportingText = {
                    Text(
                        "粘贴x.com/xxx/status/123456或t.co短链",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                shape = MaterialTheme.shapes.medium,
                minLines = 1,
                maxLines = 3
            )

            Spacer(Modifier.height(10.dp))

            // ---------------- 操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        val t = clipboard.getText()?.text
                        if (!t.isNullOrBlank()) st.url = t
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_library_books),
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("粘贴", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                FilledTonalButton(
                    onClick = {
                        st.url = ""
                        st.tweet = null
                        st.selected = emptySet()
                        st.error = ""
                        st.status = ""
                        st.parseProgress = 0f
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("清空", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                Button(
                    onClick = { analyze(st.url) },
                    enabled = !st.loading && Settings.platform.enabled,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1.2f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)
                ) {
                    Icon(
                        ImageVector.vectorResource(R.drawable.ic_arrow_downward),
                        null,
                        Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("解析", style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
                // V5：设置入口在按钮行末尾
                FloatingActionButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.size(48.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(Icons.Outlined.Menu, contentDescription = "设置", Modifier.size(22.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ---------------- 视频画质 + 保持最佳（V5：画质 182 宽，label 在框外下方）
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

            Spacer(Modifier.height(4.dp))

            // ---------------- 下载按钮（6canvas：画质下方靠右，平台选择已移到设置页）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SplitActionButton(
                    label = "下载",
                    icon = Icons.Outlined.Send,
                    onClick = { downloadSelected() },
                    actions = listOf(
                        ImageVector.vectorResource(R.drawable.ic_done_all) to "全选",
                        Icons.Outlined.ArrowForward to "查看下载"
                    ),
                    onAction = { label ->
                        when (label) {
                            "全选" -> st.tweet?.let { tt -> st.selected = tt.media.indices.toSet() }
                            "查看下载" -> onOpenTasks()
                        }
                    },
                    modifier = Modifier.width(190.dp),
                    enabled = st.tweet != null,
                    tonal = st.tweet == null
                )
            }

            if (st.error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    st.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // ---------------- 解析中：环形百分比（6canvas：左下角）
            if (st.loading) {
                Spacer(Modifier.height(16.dp))
                RingProgress(progress = st.parseProgress, size = 60.dp, stroke = 5.dp)
            }

            if (st.status.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    st.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(96.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun Avatar(url: String?) {
    var bmp by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url != null) bmp = withContext(Dispatchers.IO) { ImageCache.load(url) }
    }
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        bmp?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun MediaTile(
    modifier: Modifier = Modifier,
    item: MediaItem,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val thumbUrl = item.thumb ?: if (item.isVideo) null else item.url.substringBefore("?") + "?name=small"
    var bmp by remember(thumbUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(thumbUrl) {
        var r = if (thumbUrl != null) withContext(Dispatchers.IO) { ImageCache.load(thumbUrl) } else null
        if (r == null) r = withContext(Dispatchers.IO) { ImageCache.load(item.url) }
        bmp = r
    }

    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onToggle() }
    ) {
        bmp?.let {
            Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        val badge = when {
            item.kind == "gif" -> "GIF"
            item.isVideo -> "视频"
            else -> "图片"
        }
        val extra = if (item.isVideo && item.durationMs > 0) {
            val s = item.durationMs / 1000
            String.format(Locale.US, "%d:%02d", s / 60, s % 60)
        } else if (item.width > 0) {
            "${item.width}×${item.height}"
        } else ""
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                if (extra.isBlank()) badge else "$badge $extra",
                color = Color.White,
                fontSize = 10.sp
            )
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp)
            }
        }
    }
}
