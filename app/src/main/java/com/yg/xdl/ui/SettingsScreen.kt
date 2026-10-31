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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                    IconListItem(
                        icon = ImageVector.vectorResource(R.drawable.ic_folder_open),
                        title = "文件保存位置",
                        subtitle = "例：/storage/emulated/0/Movies/XDL",
                        onClick = { FolderOpener.open(ctx, "Movies/XDL") }
                    )
                    IconListItem(
                        icon = Icons.Outlined.Menu,
                        title = "最大下载数量",
                        subtitle = "当前限制 ${Settings.maxConcurrent} 个",
                        onClick = { showMaxDialog = true }
                    )
                    IconListItem(
                        icon = Icons.Outlined.MoreVert,
                        title = "下载平台选择",
                        subtitle = "当前 ${Settings.platform.label}",
                        trailing = Icons.Outlined.KeyboardArrowRight,
                        onClick = { showPlatformSheet = true }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

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

            Spacer(Modifier.height(10.dp))

            // ---------------- 主题（6canvas：splitButton，动态颜色并入其中）
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                SplitActionButton(
                    label = "主题",
                    icon = ImageVector.vectorResource(R.drawable.ic_light_mode),
                    onClick = {
                        Settings.updateTheme(
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
                            "浅色" -> Settings.updateTheme(ThemeMode.LIGHT)
                            "深色" -> Settings.updateTheme(ThemeMode.DARK)
                            "跟随系统" -> Settings.updateTheme(ThemeMode.SYSTEM)
                            "动态颜色" -> Settings.updateDynamicColor(!Settings.dynamicColor)
                        }
                    },
                    modifier = Modifier.width(190.dp),
                    tonal = true
                )
            }

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
                                    android.net.Uri.parse("https://github.com/CYQawa/YunX")
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        "XDL",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "X（Twitter）图片 / 视频下载器\n解析公开接口，把原图与最高码率视频直接存进相册。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
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

/** 设置页带圆形图标的列表项 */
@Composable
private fun IconListItem(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: ImageVector? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Icon(
                trailing,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
