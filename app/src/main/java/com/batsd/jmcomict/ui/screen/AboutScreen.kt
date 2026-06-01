package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    versionName: String = "",
    onBackClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val githubUrl = "https://github.com/woshimaniubi8/JMComic-R"

    Scaffold(
        containerColor = colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("关于") },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // ===== App 图标 =====
            Surface(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(20.dp),
                color = colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(com.batsd.jmcomict.R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp)
                            )
                        }
            }

            Spacer(Modifier.height(16.dp))

            // ===== App 名称 =====
            Text(
                "JMComic-R",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onBackground
            )

            Spacer(Modifier.height(4.dp))

            // ===== 版本号 =====
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colorScheme.surfaceContainerHighest
            ) {
                Text(
                    "v${versionName.ifEmpty { "?" }}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "一个第三方 JM 客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // ===== 信息卡片 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 功能介绍
                    InfoRow(
                        icon = Icons.Default.Description,
                        title = "功能特点",
                        description = "简洁清爽，界面美观，实现了原版的大部分功能，给你不一样的到管子体验"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    // 免责声明
                    InfoRow(
                        icon = Icons.Default.Shield,
                        title = "免责声明",
                        description = "本项目仅供研究学习使用。本软件与 JMComic、禁漫天堂无任何隶属关系，软件内的内容与开发者无关。"
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== GitHub 链接卡片 =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable {
                        val intent = android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(githubUrl)
                        )
                        context.startActivity(intent)
                    },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = colorScheme.surfaceContainerHigh
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(com.batsd.jmcomict.R.drawable.ic_github),
                                contentDescription = "GitHub",
                                modifier = Modifier.size(24.dp),
                                tint = colorScheme.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "GitHub 仓库",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            githubUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.primary,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Default.OpenInNew,
                        null,
                        Modifier.size(20.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ===== 版权信息 =====
            Text(
                "Copyright © 2024-2026 JMComic-R",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                "All rights reserved.",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    title: String,
    description: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            null,
            Modifier.size(20.dp),
            tint = colorScheme.primary.copy(alpha = 0.8f)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
        }
    }
}
