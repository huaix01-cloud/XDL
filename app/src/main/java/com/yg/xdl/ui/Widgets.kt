package com.yg.xdl.ui

import androidx.compose.ui.res.vectorResource
import com.yg.xdl.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.xdl.DownloadTask
import com.yg.xdl.NetWatch
import com.yg.xdl.TaskStatus
import java.util.Locale
import kotlin.math.roundToInt

/** 环形进度：中间直接写百分比 */
@Composable
fun RingProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp,
    stroke: Dp = 6.dp,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = MaterialTheme.colorScheme.surfaceVariant,
    textSize: Int = 12
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxSize(),
            color = color,
            strokeWidth = stroke,
            trackColor = track
        )
        Text(
            text = label ?: "${(progress * 100).roundToInt()}%",
            fontSize = textSize.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * V5 的网络状态 chip：有 VPN / 系统代理时显示 "vpn 123ms"，
 * 没有代理时只显示延迟 —— 但宽度固定，避免布局跳动。
 */
@Composable
fun NetChip(modifier: Modifier = Modifier) {
    val hasProxy = NetWatch.hasProxy
    val ms = if (NetWatch.apiMillis > 0) "${NetWatch.apiMillis}ms" else "---"
    val color = when (NetWatch.status) {
        NetWatch.Status.OK -> Color(0xFF2E7D32)
        NetWatch.Status.UNREACHABLE -> MaterialTheme.colorScheme.error
        NetWatch.Status.OFFLINE -> MaterialTheme.colorScheme.outline
        NetWatch.Status.CHECKING -> MaterialTheme.colorScheme.tertiary
        NetWatch.Status.UNKNOWN -> MaterialTheme.colorScheme.outline
    }
    Surface(
        modifier = modifier.width(118.dp).heightIn(min = 32.dp),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                ImageVector.vectorResource(R.drawable.ic_language),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (hasProxy) "vpn $ms" else ms,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** 下拉选择（用 OutlinedButton + DropdownMenu 拼，避开实验性 API） */
@Composable
fun SelectField(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
    selectedIndex: Int = -1
) {
    var open by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.medium
    Box(modifier) {
        androidx.compose.material3.OutlinedButton(
            onClick = { open = true },
            enabled = enabled,
            shape = shape,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (leadingIcon != null) {
                Icon(leadingIcon, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            Column(Modifier.weight(1f)) {
                if (label.isNotBlank()) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            s,
                            fontWeight = if (i == selectedIndex) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(i)
                    }
                )
            }
        }
    }
}

/** 设计稿里的 splitButton：主按钮 + 右侧动作下拉 */
@Composable
fun SplitActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    actions: List<Pair<ImageVector, String>>,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tonal: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val leftShape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 4.dp, bottomEnd = 4.dp)
    val rightShape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp, topEnd = 26.dp, bottomEnd = 26.dp)

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (tonal) {
            FilledTonalButton(
                onClick = onClick,
                enabled = enabled,
                shape = leftShape,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        } else {
            Button(
                onClick = onClick,
                enabled = enabled,
                shape = leftShape,
                modifier = Modifier.weight(1f).height(52.dp)
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
        }
        Box {
            if (tonal) {
                FilledTonalButton(
                    onClick = { open = true },
                    enabled = enabled,
                    shape = rightShape,
                    modifier = Modifier.height(52.dp).width(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(24.dp)) }
            } else {
                Button(
                    onClick = { open = true },
                    enabled = enabled,
                    shape = rightShape,
                    modifier = Modifier.height(52.dp).width(52.dp),
                    contentPadding = PaddingValues(0.dp)
                ) { Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(24.dp)) }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                actions.forEach { (i, t) ->
                    DropdownMenuItem(
                        text = { Text(t) },
                        leadingIcon = { Icon(i, null, Modifier.size(20.dp)) },
                        onClick = {
                            open = false
                            onAction(t)
                        }
                    )
                }
            }
        }
    }
}

private fun bytesText(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / 1024.0 / 1024 / 1024)
    b >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

fun taskSupporting(t: DownloadTask): String = when (t.status) {
    TaskStatus.RUNNING -> buildString {
        append(t.qualityLabel)
        append(" · ")
        append(t.kindLabel)
        append(" · ")
        append("${(t.progress * 100).roundToInt()}%")
        if (t.speed > 0) append(" · ${String.format(Locale.US, "%.1f", t.speed / 1024.0 / 1024)} MB/s")
        if (t.total > 0) append(" · ${bytesText(t.total)}")
    }
    TaskStatus.QUEUED -> "${t.qualityLabel} · ${t.kindLabel} · 排队中"
    TaskStatus.PAUSED -> "${t.qualityLabel} · ${t.kindLabel} · 已暂停"
    TaskStatus.DONE -> "已完成 · ${t.mime.substringAfter('/').uppercase()} · ${bytesText(t.bytes)}"
    TaskStatus.FAILED -> "失败：${(t.error ?: "未知错误").take(90)}"
}

/** 下载列表里的一行 */
@Composable
fun TaskRow(
    task: DownloadTask,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${task.title} · ${task.kindLabel}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    taskSupporting(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == TaskStatus.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            when (task.status) {
                TaskStatus.RUNNING -> {
                    Spacer(Modifier.width(10.dp))
                    RingProgress(
                        progress = task.progress,
                        size = 44.dp,
                        stroke = 4.dp,
                        textSize = 10
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TaskStatus.DONE -> {
                    IconButton(onClick = onOpen, modifier = Modifier.size(40.dp)) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_open_in_new),
                            contentDescription = "打开",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {
                    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 设置页用的大圆角容器 */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(35.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) { content() }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
