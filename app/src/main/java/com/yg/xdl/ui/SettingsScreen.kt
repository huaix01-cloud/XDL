package com.yg.xdl.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.yg.xdl.R

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yg.xdl.FolderOpener
import com.yg.xdl.Settings
import com.yg.xdl.ThemeMode

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
            }
            Text("设置", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            AssistChip(
                onClick = { },
                label = { Text("v${Settings.versionName}", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(Icons.Outlined.Info, null, Modifier.size(16.dp)) }
            )
        }

        Spacer(Modifier.height(10.dp))

        // ---------------- 下载
        SectionCard {
            Text(
                "下载",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            SettingSwitchRow(
                title = "下载通知",
                subtitle = "需要通知保活后台下载",
                checked = Settings.notifyDownload,
                onCheckedChange = { Settings.updateNotifyDownload(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitchRow(
                title = "仅 WiFi 下载",
                subtitle = if (Settings.wifiOnly) "当前移动网络下会提示" else null,
                checked = Settings.wifiOnly,
                onCheckedChange = { Settings.updateWifiOnly(it) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.padding(vertical = 12.dp)) {
                SelectField(
                    label = "最多同时下载数量",
                    value = "${Settings.maxConcurrent}",
                    options = listOf("1", "2", "3", "4"),
                    onSelect = { Settings.updateMaxConcurrent(it + 1) },
                    selectedIndex = Settings.maxConcurrent - 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { FolderOpener.open(ctx, "Movies/XDL") }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    ImageVector.vectorResource(R.drawable.ic_folder_open),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("文件保存位置", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "/storage/emulated/0/Pictures/XDL · Movies/XDL",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AssistChip(
                    onClick = { FolderOpener.open(ctx, "Movies/XDL") },
                    label = { Text("打开", style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ---------------- 外观
        SectionCard {
            Text(
                "外观",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Column(Modifier.padding(vertical = 12.dp)) {
                SelectField(
                    label = "主题",
                    value = Settings.theme.label,
                    options = ThemeMode.entries.map { it.label },
                    onSelect = { Settings.updateTheme(ThemeMode.entries[it]) },
                    selectedIndex = ThemeMode.entries.indexOf(Settings.theme),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitchRow(
                title = "动态颜色",
                subtitle = "Android 12 以上支持，跟随壁纸取色",
                checked = Settings.dynamicColor,
                onCheckedChange = { Settings.updateDynamicColor(it) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ---------------- 关于
        SectionCard {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .height(72.dp)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("XDL", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "X 图片 / 视频下载器 · 自用",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = { },
                            label = { Text("v${Settings.versionName}", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                        AssistChip(
                            onClick = { },
                            label = { Text("Android 10+", style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors()
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingSwitchRow(
                title = "更新",
                subtitle = "检查是否有新版本",
                checked = Settings.autoUpdate,
                onCheckedChange = { Settings.updateAutoUpdate(it) }
            )
        }

        Spacer(Modifier.height(28.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "XDL ${Settings.versionName} · 仅供个人使用",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}
