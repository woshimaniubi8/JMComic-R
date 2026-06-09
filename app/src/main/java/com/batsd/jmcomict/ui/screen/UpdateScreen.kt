package com.batsd.jmcomict.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.batsd.jmcomict.ui.components.*
import com.batsd.jmcomict.ui.viewmodel.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(
    updateViewModel: UpdateViewModel,
    currentVersion: String,
    onBackClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val release by updateViewModel.release.collectAsState()
    val isChecking by updateViewModel.isChecking.collectAsState()
    val isDownloading by updateViewModel.isDownloading.collectAsState()
    val downloadProgress by updateViewModel.downloadProgress.collectAsState()
    val error by updateViewModel.error.collectAsState()
    val downloadStarted by updateViewModel.downloadStarted.collectAsState()

    var showChangelog by remember { mutableStateOf(false) }

    // 进入页面时清理旧APK并检查更新
    LaunchedEffect(Unit) {
        updateViewModel.cleanupDownloadedApks(context)
        updateViewModel.checkForUpdates()
    }

    // 下载完成提示
    LaunchedEffect(downloadStarted) {
        if (downloadStarted) {
            showChangelog = false
            updateViewModel.clearDownloadState()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("版本更新") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colorScheme.surface)
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // 版本图标
            Surface(
                modifier = Modifier.size(72.dp),
                shape = MaterialTheme.shapes.large,
                color = colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.SystemUpdate, null, Modifier.size(36.dp), tint = colorScheme.primary)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("当前版本: v$currentVersion", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.height(24.dp))

            when {
                isChecking -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("正在检查更新...", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurfaceVariant)
                }
                error != null -> {
                    Icon(Icons.Default.ErrorOutline, null, Modifier.size(48.dp), tint = colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { updateViewModel.checkForUpdates() }) {
                        Text("重试")
                    }
                }
                release != null -> {
                    val rel = release!!
                    val hasUpdate = ReleaseRepositoryHasNewVersion(rel.tag_name, currentVersion)

                    if (hasUpdate) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.NewReleases, null, Modifier.size(32.dp), tint = colorScheme.error)
                                Spacer(Modifier.height(8.dp))
                                Text("发现新版本 ${rel.tag_name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("发布于 ${rel.published_at.take(10)}", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = { updateViewModel.downloadRelease(context, rel) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                            enabled = !isDownloading
                        ) {
                            if (isDownloading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isDownloading) "下载中..."
                                else "下载最新版 (${rel.tag_name})"
                            )
                        }
                        // 下载进度条
                        if (isDownloading && downloadProgress != null) {
                            Spacer(Modifier.height(8.dp))
                            val (downloaded, total) = downloadProgress!!
                            val progress = if (total > 0) downloaded.toFloat() / total else 0f
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = colorScheme.primary,
                                    trackColor = colorScheme.surfaceContainerHighest,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${downloaded / 1024 / 1024}MB / ${total / 1024 / 1024}MB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(rel.html_url))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Text("在 GitHub 中查看")
                        }
                    } else {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.CheckCircle, null, Modifier.size(32.dp), tint = colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Text("已是最新版本", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // 更新日志
                    if (rel.body.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showChangelog = !showChangelog },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        ) {
                            Icon(
                                if (showChangelog) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                null, Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (showChangelog) "收起更新日志" else "查看更新日志")
                        }
                        if (showChangelog) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                            ) {
                                Text(
                                    rel.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { updateViewModel.checkForUpdates() }) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("重新检查")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            // 自动检测开关
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("自动检测更新", style = MaterialTheme.typography.bodyMedium, color = colorScheme.onSurface)
                    Text("启动时自动检查新版本", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                }
                Switch(
                    checked = updateViewModel.autoCheckEnabled,
                    onCheckedChange = { updateViewModel.setAutoCheckEnabled(it) }
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 版本号比较 */
private fun ReleaseRepositoryHasNewVersion(latestTag: String, currentVersion: String): Boolean {
    val latest = latestTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    val current = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
    if (latest.isEmpty() || current.isEmpty()) return latestTag != "v$currentVersion"
    return compareVersionList(latest, current) > 0
}

private fun compareVersionList(a: List<Int>, b: List<Int>): Int {
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until maxLen) {
        val va = a.getOrElse(i) { 0 }
        val vb = b.getOrElse(i) { 0 }
        if (va != vb) return va - vb
    }
    return 0
}
