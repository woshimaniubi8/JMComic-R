package com.batsd.openjm.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.batsd.openjm.ui.components.*

/**
 * 设置界面 — FlClash ListItem 风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    cdnName: String = "主线路",
    isDarkTheme: Boolean = false,
    themeMode: Int = 0,
    isLoggedIn: Boolean = false,
    onBackClick: () -> Unit,
    onSwitchCdn: () -> Unit,
    onSetThemeMode: (Int) -> Unit,
    onLogoutClick: () -> Unit
) {
    var showCdnDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 顶部栏
            item {
                TopAppBar(
                    title = { Text("设置") },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = colorScheme.surface
                    )
                )
            }

            // 分流切换
            item {
                CommonListItem(
                    title = "分流切换",
                    subtitle = "当前: $cdnName",
                    leading = {
                        Icon(
                            Icons.Default.Language,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.primary
                        )
                    },
                    showChevron = true,
                    onClick = { showCdnDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorScheme.outlineVariant.opacity50
                )
            }

            // 主题模式
            item {
                val modeNames = listOf("跟随系统", "浅色", "深色")
                CommonListItem(
                    title = "主题模式",
                    subtitle = modeNames[themeMode.coerceIn(0, 2)],
                    leading = {
                        Icon(
                            if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.primary
                        )
                    },
                    showChevron = true,
                    onClick = { showThemeDialog = true }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = colorScheme.outlineVariant.opacity50
                )
            }

            // 退出登录 (仅登录后显示)
            if (isLoggedIn) {
            item {
                CommonListItem(
                    title = "退出登录",
                    leading = {
                        Icon(
                            Icons.Default.Logout,
                            null,
                            modifier = Modifier.size(22.dp),
                            tint = colorScheme.error.copy(alpha = 0.7f)
                        )
                    },
                    onClick = onLogoutClick
                )
            }
            } // end if(isLoggedIn)
        }
    }

    // ===== CDN 选择对话框 =====
    if (showCdnDialog) {
        AlertDialog(
            onDismissRequest = { showCdnDialog = false },
            confirmButton = {
                TextButton(onClick = { showCdnDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("选择线路") },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
            text = {
                Column {
                    listOf("主线路", "备用线路1", "备用线路2").forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSwitchCdn()
                                    showCdnDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = cdnName == name,
                                onClick = null
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        )
    }

    // ===== 主题选择对话框 =====
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消")
                }
            },
            title = { Text("选择主题") },
            shape = MaterialTheme.shapes.medium,
            containerColor = colorScheme.surfaceContainerHigh,
            text = {
            Column {
                listOf("跟随系统", "浅色", "深色").forEachIndexed { index, name ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetThemeMode(index)
                                showThemeDialog = false
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == index,
                            onClick = null
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            }
        )
    }
}


