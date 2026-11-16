package com.yg.xdl.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yg.xdl.DownloadCenter
import com.yg.xdl.DownloadTask
import com.yg.xdl.R
import com.yg.xdl.TaskStatus
import kotlinx.coroutines.launch
import java.util.Locale

/** 新设计里的「下载」页：一页列表 + 顶部操作条，已完成项可展开 */
@Composable
fun TasksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var expandedKey by remember { mutableStateOf<String?>(null) }

    val all = DownloadCenter.tasks
    val running = all.filter {
        it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.PAUSED
    }
    val done = all.filter { it.status == TaskStatus.DONE }
    val failed = all.filter { it.status == TaskStatus.FAILED }
    val ordered = running + failed + done

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {

            // topAppBar
            Surface(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp),
                shape = RoundedCornerShape(
                    topStart = 40.dp, topEnd = 40.dp,
                    bottomStart = 7.dp, bottomEnd = 7.dp
                ),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "下载",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "共 ${all.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            // 操作条
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        if (DownloadCenter.paused) DownloadCenter.resumeAll() else DownloadCenter.pauseAll()
                        scope.launch {
                            snackbar.showSnackbar(if (DownloadCenter.paused) "已暂停" else "已继续")
                        }
                    },
                    label = {
                        Text(
                            if (DownloadCenter.paused) "继续全部" else "暂停全部",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
                AssistChip(
                    onClick = {
                        DownloadCenter.retryAllFailed()
                        scope.launch { snackbar.showSnackbar("已重试失败任务") }
                    },
                    label = { Text("重试全部", style = MaterialTheme.typography.labelMedium) }
                )
                AssistChip(
                    onClick = {
                        DownloadCenter.clearFailed()
                        DownloadCenter.clearDone()
                        scope.launch { snackbar.showSnackbar("已清空记录（文件保留）") }
                    },
                    label = { Text("删除全部", style = MaterialTheme.typography.labelMedium) }
                )
            }

            if (ordered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有下载过内容",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ordered, key = { it.key }) { task ->
                        TaskCard(
                            task = task,
                            expanded = expandedKey == task.key,
                            onToggleExpand = {
                                expandedKey = if (expandedKey == task.key) null else task.key
                            },
                            onRetry = { DownloadCenter.retry(task) },
                            onDelete = {
                                DownloadCenter.remove(task)
                                if (expandedKey == task.key) expandedKey = null
                            },
                            onOpen = { DownloadCenter.openFile(ctx, task) }
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
        )
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onOpen: () -> Unit
) {
    val isRunning = task.status == TaskStatus.RUNNING
    val isDone = task.status == TaskStatus.DONE
    val isFailed = task.status == TaskStatus.FAILED

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {

                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isFailed -> MaterialTheme.colorScheme.errorContainer
                                isDone -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.secondaryContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isFailed -> Text(
                            "!",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        isRunning -> RingProgress(
                            progress = task.progress,
                            size = 40.dp,
                            stroke = 4.dp,
                            textSize = 9
                        )
                        else -> Icon(
                            ImageVector.vectorResource(R.drawable.ic_download),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        task.savedName ?: "${task.title} · ${task.kindLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        taskSupporting(task),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isFailed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                when {
                    isFailed -> IconButton(onClick = onRetry, modifier = Modifier.size(40.dp)) {
                        Icon(
                            ImageVector.vectorResource(R.drawable.ic_arrow_downward),
                            contentDescription = "重试",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    isRunning -> IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "暂停",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    isDone -> IconButton(onClick = onToggleExpand, modifier = Modifier.size(40.dp)) {
                        Icon(
                            if (expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isRunning) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        buildString {
                            append("画质 ")
                            append(task.qualityLabel)
                            append(" · ")
                            append(task.mime)
                            if (task.bytes > 0) append(" · ${fmtSize(task.bytes)}")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (isDone) {
                            TextButton(onClick = onOpen) { Text("打开") }
                        }
                        TextButton(onClick = onDelete) {
                            Text("删除记录", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun fmtSize(b: Long): String = when {
    b >= 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", b / 1024.0 / 1024 / 1024)
    b >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", b / 1024.0 / 1024)
    b >= 1024 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
