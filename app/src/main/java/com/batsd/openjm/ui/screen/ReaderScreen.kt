package com.batsd.openjm.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.batsd.openjm.data.model.BookEps
import com.batsd.openjm.ui.components.DescrambledImage
import com.batsd.openjm.ui.components.*

/**
 * 阅读界面 — FlClash 风格翻新
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    episode: BookEps?,
    currentPage: Int,
    scrambleId: Int = 0,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onPreviousPage: () -> Unit,
    onNextPage: () -> Unit,
    onPageSelect: (Int) -> Unit
) {
    var showBar by remember { mutableStateOf(true) }
    val totalPages = episode?.pages ?: 0
    val imageUrls = episode?.pictureUrl ?: emptyList()
    val listState = rememberLazyListState()
    val colorScheme = MaterialTheme.colorScheme

    // 整体缩放状态
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    // 当前可见页码 — 直接在组合阶段计算（不依赖 derivedStateOf，因为 totalPages 非快照状态）
    val visiblePage = when {
        totalPages <= 0 -> 0
        else -> {
            val first = listState.firstVisibleItemIndex
            if (first < 0) {
                0
            } else if (!listState.canScrollForward) {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: first
                last.coerceIn(0, totalPages - 1)
            } else {
                first.coerceIn(0, totalPages - 1)
            }
        }
    }

    // 用户滚动停止后上报页码（仅追踪滚动状态）
    var isProgrammaticScroll by remember { mutableStateOf(false) }
    // 用 imageUrls.size 作 key，数据加载后重新启动协程
    LaunchedEffect(imageUrls.size) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
                if (!inProgress && !isProgrammaticScroll) {
                    kotlinx.coroutines.delay(300)
                    val idx = listState.firstVisibleItemIndex
                    if (idx in imageUrls.indices) {
                        onPageSelect(idx)
                    }
                }
            }
    }

    // 外部翻页（上一页/下一页按钮）驱动滚动
    LaunchedEffect(currentPage) {
        if (currentPage in imageUrls.indices && currentPage != visiblePage) {
            isProgrammaticScroll = true
            try {
                listState.animateScrollToItem(currentPage)
            } finally {
                // 等动画完成后再重置标记，防止 snapshotFlow 在动画期间误触发
                kotlinx.coroutines.delay(200)
                isProgrammaticScroll = false
            }
        }
    }

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 阅读内容
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else if (imageUrls.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "暂无图片数据",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .clickable { showBar = !showBar }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false)
                                    var prevPos = Offset.Zero
                                    var prevSpan = 0f
                                    var prevCentroid = Offset.Zero
                                    var pointerCount = 0
                                    var isZoomed = scale > 1.01f

                                    do {
                                        val event = awaitPointerEvent()
                                        val active = event.changes.filter { it.pressed }
                                        pointerCount = active.size

                                        if (active.size >= 2) {
                                            // 双指：缩放 + 平移
                                            val pts = active.map { it.position }
                                            val centroid = pts.reduce { a, b -> a + b } / pts.size.toFloat()
                                            val span = pts.map { (it - centroid).getDistance() }.average().toFloat()

                                            if (prevSpan > 0f) {
                                                val zoomChange = span / prevSpan
                                                scale = (scale * zoomChange).coerceIn(1f, 5f)
                                                offsetX += centroid.x - prevCentroid.x
                                                offsetY += centroid.y - prevCentroid.y
                                            }
                                            prevCentroid = centroid
                                            prevSpan = span
                                            active.forEach { it.consume() }
                                        } else if (active.size == 1 && isZoomed) {
                                            // 单指 + 已缩放：平移画面
                                            val pos = active.first().position
                                            if (prevPos != Offset.Zero) {
                                                offsetX += pos.x - prevPos.x
                                                offsetY += pos.y - prevPos.y
                                            }
                                            prevPos = pos
                                            active.forEach { it.consume() }
                                        } else {
                                            prevPos = Offset.Zero
                                        }
                                    } while (event.changes.any { it.pressed })

                                    // 缩放复位时清空偏移
                                    if (scale <= 1.01f) { offsetX = 0f; offsetY = 0f }
                                }
                            }
                    ) {
                        itemsIndexed(
                            items = imageUrls,
                            key = { index, _ -> "pg_$index" }
                        ) { _, url ->
                            DescrambledImage(
                                imageUrl = url,
                                scrambleId = scrambleId,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 缩放指示器
                    if (scale > 1.01f) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            shape = MaterialTheme.shapes.small,
                            color = colorScheme.inverseSurface.opacity80
                        ) {
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }

            // 顶部栏动画
            AnimatedVisibility(
                visible = showBar,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Surface(
                    color = colorScheme.surface.opacity80,
                    tonalElevation = 2.dp
                ) {
                    TopAppBar(
                        title = {
                            Text(
                                "${episode?.epsName ?: "阅读"} (${visiblePage + 1}/$totalPages)",
                                style = MaterialTheme.typography.titleSmall
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            // 底部进度指示器
            AnimatedVisibility(
                visible = showBar && totalPages > 1,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                Surface(
                    color = colorScheme.surface.opacity80,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        LinearProgressIndicator(
                            progress = { if (totalPages > 0) (visiblePage + 1).toFloat() / totalPages else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = colorScheme.primary,
                            trackColor = colorScheme.surfaceContainerHighest
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${visiblePage + 1} / $totalPages",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
