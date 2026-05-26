package com.batsd.jmcomict.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ==================== InfoHeader ====================

/**
 * 信息头部组件 — 图标 + 标题 + 右侧操作
 * 参考 FlClash InfoHeader 设计
 */
@Composable
fun InfoHeader(
    title: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

// ==================== CommonCard ====================

/**
 * 通用卡片组件 — 参考 FlClash CommonCard
 * @param isSelected 是否选中态
 * @param variant 卡片样式变体：outlined / filled
 * @param info 顶部的 InfoHeader 信息
 */
@Composable
fun CommonCard(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    variant: CardVariant = CardVariant.Outlined,
    info: CardInfo? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium // 14dp 圆角

    // 背景色
    val backgroundColor = when {
        variant == CardVariant.Filled && isSelected -> colorScheme.secondaryContainer.opacity80
        variant == CardVariant.Filled -> colorScheme.surfaceContainerHigh
        isSelected -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceContainerLow
    }

    // 边框色
    val borderColor = when {
        isSelected -> colorScheme.primary
        variant == CardVariant.Filled -> Color.Transparent
        else -> colorScheme.surfaceContainerHighest
    }

    Surface(
        modifier = modifier,
        shape = shape,
        color = backgroundColor,
        tonalElevation = if (variant == CardVariant.Filled) 0.dp else 0.dp,
        shadowElevation = 0.dp,
        border = if (variant == CardVariant.Filled) null
        else androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        onClick = onClick ?: {}
    ) {
        Column {
            if (info != null) {
                InfoHeader(
                    title = info.title,
                    icon = info.icon,
                    modifier = Modifier.padding(bottom = 0.dp)
                )
            }
            content()
        }
    }
}

enum class CardVariant { Outlined, Filled }

data class CardInfo(
    val title: String,
    val icon: ImageVector? = null
)

// ==================== CommonScaffold ====================

/**
 * 通用脚手架 — 参考 FlClash CommonScaffold
 * 统一处理 TopAppBar、加载状态、搜索状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonScaffold(
    title: String,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { onBackClick?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            content(padding)
            // Loading overlay
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.opacity60),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

// ==================== ListItem ====================

/**
 * 通用列表项 — 参考 FlClash ListItem 设计模式
 */
@Composable
fun CommonListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorScheme.surface,
        onClick = { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
            if (showChevron) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = colorScheme.onSurfaceVariant.opacity50,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== CommonChip ====================

/**
 * 通用标签组件 — 参考 FlClash CommonChip
 */
@Composable
fun CommonChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null
) {
    val colorScheme = MaterialTheme.colorScheme

    if (onClick != null) {
        AssistChip(
            onClick = onClick,
            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
            leadingIcon = leadingIcon?.let {
                { Icon(it, null, modifier = Modifier.size(16.dp)) }
            },
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = if (selected) colorScheme.secondaryContainer
                else colorScheme.surfaceContainerHigh,
                labelColor = if (selected) colorScheme.onSecondaryContainer
                else colorScheme.onSurfaceVariant
            ),
            border = AssistChipDefaults.assistChipBorder(
                borderColor = if (selected) colorScheme.primary.opacity60
                else colorScheme.outlineVariant,
                enabled = true
            )
        )
    } else {
        // 纯展示标签
        Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.small,
            color = colorScheme.surfaceContainerHigh,
            border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leadingIcon != null) {
                    Icon(leadingIcon, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

// ==================== SearchBar ====================

/**
 * 通用搜索栏 — 参考 FlClash AppBar 搜索模式
 */
@Composable
fun CommonSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String = "搜索...",
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                placeholder,
                color = colorScheme.onSurfaceVariant.opacity60
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = colorScheme.onSurfaceVariant.opacity60
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "清除")
                }
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = colorScheme.surfaceContainerHigh,
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant
        )
    )
}

// ==================== EmptyState ====================

/**
 * 空状态占位
 */
@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.opacity38
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.opacity60
            )
        }
    }
}

// ==================== StatCard ====================

/**
 * 统计卡片 — 数值 + 标签
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    autoShrink: Boolean = false
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (icon != null) {
                Icon(
                    icon, null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = !autoShrink,
                fontSize = if (autoShrink) 14.sp else TextUnit.Unspecified
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
                maxLines = 1
            )
        }
    }
}

// ==================== Utility: Color Extensions ====================

/** 80% opacity */
val Color.opacity80: Color get() = copy(alpha = 0.8f)
/** 60% opacity */
val Color.opacity60: Color get() = copy(alpha = 0.6f)
/** 50% opacity */
val Color.opacity50: Color get() = copy(alpha = 0.5f)
/** 38% opacity */
val Color.opacity38: Color get() = copy(alpha = 0.38f)
/** 12% opacity */
val Color.opacity12: Color get() = copy(alpha = 0.12f)
