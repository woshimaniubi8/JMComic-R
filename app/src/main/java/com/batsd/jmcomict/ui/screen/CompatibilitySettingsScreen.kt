package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompatibilitySettingsScreen(
    compatDownsample: Boolean,
    compatRGB565: Boolean,
    compatCacheLimit: Boolean,
    onToggleCompatDownsample: (Boolean) -> Unit,
    onToggleCompatRGB565: (Boolean) -> Unit,
    onToggleCompatCacheLimit: (Boolean) -> Unit,
    onBackClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var downsample by remember { mutableStateOf(compatDownsample) }
    var rgb565 by remember { mutableStateOf(compatRGB565) }
    var cacheLimit by remember { mutableStateOf(compatCacheLimit) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("兼容设置") },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "以下选项仅建议低内存设备开启，正常设备无需启用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                ToggleCard(
                    icon = Icons.Default.PhotoSizeSelectLarge,
                    title = "图片降采样",
                    subtitle = "按屏幕宽度缩放，大幅降低内存占用",
                    checked = downsample,
                    onToggle = { downsample = it; onToggleCompatDownsample(it) }
                )
            }

            item {
                ToggleCard(
                    icon = Icons.Default.Palette,
                    title = "RGB_565 色深",
                    subtitle = "每像素 2 字节（原始格式 4 字节），内存减半",
                    checked = rgb565,
                    onToggle = { rgb565 = it; onToggleCompatRGB565(it) }
                )
            }

            item {
                ToggleCard(
                    icon = Icons.Default.Storage,
                    title = "限制图片缓存",
                    subtitle = "缓存数量上限从 30 降至 8，降低峰值内存",
                    checked = cacheLimit,
                    onToggle = { cacheLimit = it; onToggleCompatCacheLimit(it) }
                )
            }

            item { Spacer(Modifier.height(16.dp)) }

            item {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, null, Modifier.size(20.dp), tint = colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("注意", style = MaterialTheme.typography.titleSmall, color = colorScheme.error)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "开启降采样或 RGB_565 会降低图片质量。如果设备内存充足（≥3GB），建议保持所有选项关闭以获得最佳画质。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
