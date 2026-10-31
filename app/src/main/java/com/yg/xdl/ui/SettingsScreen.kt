package com.yg.xdl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.xdl.FolderOpener
import com.yg.xdl.Platform
import com.yg.xdl.R
import com.yg.xdl.Settings
import com.yg.xdl.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showMaxDialog by remember { mutableStateOf(false) }
    var showPlatformSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    // 主题切换也走 Circular Reveal：记录按钮位置，以它为圆心揭示
    var themePos by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val darkNow = when (Settings.theme) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
    }
    val applyTheme: (ThemeMode) -> Unit = { target ->
        val act = ctx as? com.yg.xdl.MainActivity
        val toDark = when (target) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemDark
        }
        if (act != null && themePos != androidx.compose.ui.geometry.Offset.Zero) {
            act.switchThemeWithReveal(themePos.x, themePos.y, target, darkNow, toDark)
        } else {
            Settings.updateTheme(target)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(6.dp))

            // 顶栏：只有「设置」chip（6canvas 里没有返回箭头，返回靠右下 FAB）
            AssistChip(
                onClick = { },
                label = { Text("设置", style = MaterialTheme.typography.titleMedium) },
                leadingIcon = {
                    Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                }
            )

            Spacer(Modifier.height(10.dp))

            // ---------------- 列表容器
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(35.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    SettingSwitchRow(
                        title = "下载通知",
                        subtitle = "需要通知保活后台下载",
                        checked = Settings.notifyDownload,
                        onCheckedChange = { Settings.updateNotifyDownload(it) },
                        icon = ImageVector.vectorResource(R.drawable.ic_notifications)
                    )
                    SettingSwitchRow(
                        title = "仅 WI FI 下载",
                        subtitle = null,
                        checked = Settings.wifiOnly,
                        onCheckedChange = { Settings.updateWifiOnly(it) },
                        icon = ImageVector.vectorResource(R.drawable.ic_wifi)
                    )
                    SettingListItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_folder_open),
                        title = "文件保存位置",
                        subtitle = "例：/storage/emulated/0/Movies/XDL",
                        onClick = { FolderOpener.open(ctx, "Movies/XDL") }
                    )
                    SettingListItem(
                        icon = Icons.Outlined.Menu,
                        title = "最大下载数量",
                        subtitle = "当前限制 ${Settings.maxConcurrent} 个",
                        onClick = { showMaxDialog = true }
                    )
                    SettingListItem(
                        icon = Icons.Outlined.MoreVert,
                        title = "下载平台选择",
                        subtitle = "当前 ${Settings.platform.label}",
                        onClick = { showPlatformSheet = true },
                        trailing = {
                            Icon(
                                Icons.Outlined.KeyboardArrowRight,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---------------- XDL + 版本 chip + 检查新版本
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("XDL", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(10.dp))
                AssistChip(
                    onClick = { },
                    label = {
                        Text("v${Settings.versionName}", style = MaterialTheme.typography.labelMedium)
                    }
                )
                Spacer(Modifier.weight(1f))
                Checkbox(
                    checked = Settings.autoUpdate,
                    onCheckedChange = { Settings.updateAutoUpdate(it) }
                )
                Text("检查新版本", style = MaterialTheme.typography.labelMedium)
            }

            Spacer(Modifier.height(4.dp))

            // ---------------- 主题（6canvas：splitButton，动态颜色并入其中）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SplitActionButton(
                    label = "主题",
                    icon = ImageVector.vectorResource(R.drawable.ic_light_mode),
                    onClick = {
                        applyTheme(
                            when (Settings.theme) {
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.SYSTEM
                                ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            }
                        )
                    },
                    actions = listOf(
                        ImageVector.vectorResource(R.drawable.ic_light_mode) to "浅色",
                        ImageVector.vectorResource(R.drawable.ic_dark_mode) to "深色",
                        Icons.Outlined.Settings to "跟随系统",
                        Icons.Outlined.Settings to "动态颜色"
                    ),
                    onAction = { label ->
                        when (label) {
                            "浅色" -> applyTheme(ThemeMode.LIGHT)
                            "深色" -> applyTheme(ThemeMode.DARK)
                            "跟随系统" -> applyTheme(ThemeMode.SYSTEM)
                            "动态颜色" -> Settings.updateDynamicColor(!Settings.dynamicColor)
                        }
                    },
                    modifier = Modifier
                        .width(190.dp)
                        .onGloballyPositioned { c -> themePos = c.boundsInWindow().center },
                    tonal = true
                )
            }

            // 主题和下面的介绍卡之间留白，别贴着
            Spacer(Modifier.height(22.dp))

            // ---------------- 介绍容器（6canvas：不是图片，放介绍、点击跳项目地址）
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(216.dp)
                    .clickable {
                        runCatching {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse("https://github.com/huaix01-cloud/XDL")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                // 6canvas 给的高度是 216dp；去掉顶部大标题后内容变少，
                // 改成垂直居中，避免中间空一大块
                Column(
                    Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "X（Twitter）图片 / 视频下载器\n解析公开接口，把原图与最高码率视频直接存进相册。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "点击查看项目主页",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Outlined.ArrowForward,
                            null,
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(150.dp))
        }

        // 返回 FAB（6canvas：右下角）
        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_arrow_downward),
                contentDescription = "返回",
                modifier = Modifier.size(24.dp).rotate(180f)
            )
        }
    }

    // ---------------- 最大下载数量
    if (showMaxDialog) {
        AlertDialog(
            onDismissRequest = { showMaxDialog = false },
            title = { Text("最大下载数量") },
            text = {
                Column {
                    Text(
                        "同时进行的下载任务数（1~5）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    (1..5).forEach { n ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Settings.updateMaxConcurrent(n)
                                    showMaxDialog = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = Settings.maxConcurrent == n,
                                onClick = {
                                    Settings.updateMaxConcurrent(n)
                                    showMaxDialog = false
                                }
                            )
                            Text("$n", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMaxDialog = false }) { Text("关闭") }
            }
        )
    }

    // ---------------- 下载平台选择（底部面板）
    if (showPlatformSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPlatformSheet = false },
            sheetState = sheetState
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Text(
                    "下载平台",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Platform.entries.forEach { p ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Settings.updatePlatform(p)
                                scope.launch {
                                    sheetState.hide()
                                    showPlatformSheet = false
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = Settings.platform == p,
                            onClick = {
                                Settings.updatePlatform(p)
                                scope.launch {
                                    sheetState.hide()
                                    showPlatformSheet = false
                                }
                            }
                        )
                        Text(
                            p.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (p.enabled) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

