package com.yg.xdl.ui

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.yg.xdl.R

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yg.xdl.DownloadCenter
import com.yg.xdl.TaskStatus
import kotlinx.coroutines.launch

@Composable
fun TasksScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }

    val all = DownloadCenter.tasks
    val running = all.filter {
        it.status == TaskStatus.QUEUED || it.status == TaskStatus.RUNNING || it.status == TaskStatus.PAUSED
    }
    val done = all.filter { it.status == TaskStatus.DONE }
    val failed = all.filter { it.status == TaskStatus.FAILED }
    val current = when (tab) {
        0 -> running
        1 -> done
        else -> failed
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // 顶栏
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                }
                Text(
                    "下载",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f)
                )
                if (DownloadCenter.running) {
                    RingProgress(
                        progress = if (all.isEmpty()) 0f else done.size.toFloat() / all.size,
                        size = 34.dp,
                        stroke = 3.dp,
                        textSize = 9
                    )
                }
            }

            // 三个分页
            TabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 4.dp)) {
                listOf(
                    "下载中 (${running.size})",
                    "已完成 (${done.size})",
                    "失败 (${failed.size})"
                ).forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = {
                            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        }
                    )
                }
            }

            if (current.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (tab) {
                            0 -> "没有正在下载的任务"
                            1 -> "还没有下载过的文件"
                            else -> "没有失败的任务"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(current, key = { it.key }) { task ->
                        TaskRow(
                            task = task,
                            onOpen = { DownloadCenter.openFile(ctx, task) },
                            onDelete = {
                                DownloadCenter.remove(task)
                                scope.launch { snackbar.showSnackbar("已删除记录") }
                            }
                        )
                    }
                }
            }

            // 底部「处理」
            if (current.isNotEmpty()) {
                val actions = when (tab) {
                    0 -> listOf(
                        Icons.Outlined.Refresh to "重试",
                        ImageVector.vectorResource(R.drawable.ic_delete_forever) to "删除记录",
                        Icons.Outlined.Clear to "批量暂停",
                        Icons.Outlined.CheckCircle to "批量开始"
                    )
                    1 -> listOf(
                        ImageVector.vectorResource(R.drawable.ic_delete_forever) to "删除记录",
                        ImageVector.vectorResource(R.drawable.ic_folder_open) to "打开文件位置"
                    )
                    else -> listOf(
                        Icons.Outlined.Refresh to "重试",
                        ImageVector.vectorResource(R.drawable.ic_delete_forever) to "删除记录"
                    )
                }
                SplitActionButton(
                    label = "处理",
                    icon = Icons.Outlined.Settings,
                    onClick = {
                        scope.launch {
                            when (tab) {
                                0 -> {
                                    if (DownloadCenter.paused) {
                                        DownloadCenter.resumeAll()
                                        snackbar.showSnackbar("已继续下载")
                                    } else {
                                        DownloadCenter.pauseAll()
                                        snackbar.showSnackbar("已暂停未开始的任务")
                                    }
                                }
                                1 -> {
                                    DownloadCenter.clearDone()
                                    snackbar.showSnackbar("已清空完成记录")
                                }
                                else -> {
                                    DownloadCenter.retryAllFailed()
                                    snackbar.showSnackbar("已重新加入失败任务")
                                }
                            }
                        }
                    },
                    actions = actions,
                    onAction = { label ->
                        scope.launch {
                            when (label) {
                                "重试" -> {
                                    current.filter { it.status == TaskStatus.FAILED }
                                        .forEach { DownloadCenter.retry(it) }
                                    if (tab == 0) snackbar.showSnackbar("已重试当前失败项")
                                }
                                "删除记录" -> {
                                    when (tab) {
                                        1 -> DownloadCenter.clearDone()
                                        else -> DownloadCenter.clearFailed()
                                    }
                                    snackbar.showSnackbar("已删除记录（文件保留在相册）")
                                }
                                "批量暂停" -> {
                                    DownloadCenter.pauseAll()
                                    snackbar.showSnackbar("已暂停")
                                }
                                "批量开始" -> {
                                    DownloadCenter.resumeAll()
                                    snackbar.showSnackbar("已开始")
                                }
                                "打开文件位置" -> {
                                    snackbar.showSnackbar("视频在 Movies/XDL，图片在 Pictures/XDL")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    tonal = true
                )
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
        )
    }
}
