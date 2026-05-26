package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.model.BookItem
import com.batsd.jmcomict.ui.components.*

/**
 * 个人中心 — FlClash 风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    userName: String?, userId: String?, level: String?, coin: Int = 0,
    exp: String = "", expPercent: Double = 0.0, avatarUrl: String = "", isLoggedIn: Boolean = false,
    history: List<BookItem> = emptyList(),
    onCheckInClick: (((Boolean, String) -> Unit) -> Unit) = {},
    onLoginClick: () -> Unit = {},
    onHistoryClick: (String) -> Unit = {},
    onViewAllHistory: () -> Unit = {},
    onHideHistoryItem: (String) -> Unit = {},
    onLoadHistory: () -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    var showCheckInDialog by remember { mutableStateOf(false) }
    var checkInMessage by remember { mutableStateOf("") }
    var isCheckingIn by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { onLoadHistory() }

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 未登录状态
            if (!isLoggedIn) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(40.dp))

                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = colorScheme.surfaceContainerHigh
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    null,
                                    modifier = Modifier.size(40.dp),
                                    tint = colorScheme.onSurfaceVariant.opacity38
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            "尚未登录",
                            style = MaterialTheme.typography.titleMedium,
                            color = colorScheme.onSurface
                        )

                        Text(
                            "登录以解锁更多功能",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.opacity60,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(Modifier.height(24.dp))

                        Button(
                            onClick = onLoginClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Login, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("登录 / 注册")
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Default.Settings, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("设置")
                        }
                    }
                }
                return@LazyColumn
            }

            // ===== 已登录：顶部信息卡片 =====
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLow,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp, colorScheme.surfaceContainerHighest
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 头像
                        Surface(
                            modifier = Modifier.size(88.dp),
                            shape = MaterialTheme.shapes.extraLarge,
                            color = colorScheme.primaryContainer
                        ) {
                            AsyncImage(
                                model = if (avatarUrl.isNotEmpty())
                                    ApiClientFactory.fullImageUrl(avatarUrl)
                                else null,
                                contentDescription = "头像",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // 名称
                        Text(
                            text = userName ?: "",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )

                        Text(
                            text = "UID: $userId",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant.opacity60,
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        Spacer(Modifier.height(16.dp))

                        // 统计信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            StatCard(
                                label = "等级",
                                value = level ?: "-",
                                icon = Icons.Default.Star,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "JCoin",
                                value = "$coin",
                                icon = Icons.Default.MonetizationOn,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "积分",
                                value = exp.ifEmpty { "0" },
                                icon = Icons.Default.TrendingUp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // ===== 操作按钮：一排两个 =====
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = {
                        isCheckingIn = true
                        onCheckInClick { ok, msg ->
                            isCheckingIn = false
                            checkInMessage = msg.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n"); showCheckInDialog = true
                        }
                    }, modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.medium, enabled = !isCheckingIn) {
                        if (isCheckingIn) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colorScheme.primary)
                        else Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("每日签到")
                    }
                    FilledTonalButton(onClick = onViewAllHistory,
                        modifier = Modifier.weight(1f).height(48.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Default.History, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("观看历史")
                    }
                }
            }

            // ===== 设置按钮：独占一排 =====
            item {
                Spacer(Modifier.height(2.dp))
                FilledTonalButton(onClick = onSettingsClick,
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Default.Settings, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("设置")
                }
            }

            // 设置按钮独占一排已在上方
        }
    }

    // ===== 签到结果弹窗 =====
    if (showCheckInDialog) {
        AlertDialog(
            onDismissRequest = { showCheckInDialog = false },
            confirmButton = { TextButton(onClick = { showCheckInDialog = false }) { Text("确定") } },
            text = { Text(checkInMessage) },
            containerColor = colorScheme.surfaceContainerHigh
        )
    }
}
