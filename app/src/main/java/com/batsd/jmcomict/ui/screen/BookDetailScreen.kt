package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.model.BookDetail
import com.batsd.jmcomict.data.model.BookEps
import com.batsd.jmcomict.data.model.CommentInfo
import com.batsd.jmcomict.ui.components.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    bookDetail: BookDetail?,
    isLoading: Boolean,
    comments: List<CommentInfo> = emptyList(),
    commentCount: Int = 0,
    isLoggedIn: Boolean = false,
    lastReadChapterId: String? = null,
    onBackClick: () -> Unit,
    onEpisodeClick: (BookEps) -> Unit,
    onStartReading: (String) -> Unit = {},
    onFavoriteClick: () -> Unit,
    onAddFavoriteClick: (((Boolean, String) -> Unit) -> Unit) = {},
    onToggleLike: (((Boolean, String) -> Unit) -> Unit) = {},
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: (Int) -> Unit = {},
    onPostComment: (String, Boolean) -> Unit = { _, _ -> },
    commentResult: Pair<Boolean, String>? = null
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    var localFav by remember { mutableStateOf(false) }
    LaunchedEffect(bookDetail?.isFavorite) { bookDetail?.isFavorite?.let { localFav = it } }
    var localLiked by remember { mutableStateOf(false) }
    LaunchedEffect(bookDetail?.liked) { bookDetail?.liked?.let { localLiked = it } }
    var showResultDialog by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf("") }
    var showComments by remember { mutableStateOf(false) }
    var commentPage by remember { mutableIntStateOf(1) }
    val sheetState = rememberModalBottomSheetState()

    // 评论发送结果 → Dialog（消费后清空，避免重复弹窗）
    LaunchedEffect(commentResult) {
        commentResult?.let { (ok, msg) ->
            resultMessage = msg.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            showResultDialog = true
        }
    }

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            // 浮动开始阅读按钮
            val detail = bookDetail
            if (detail != null && detail.getEffectiveSeries().isNotEmpty()) {
                val startChapId = lastReadChapterId ?: detail.getEffectiveSeries().first().epsId
                val chapIndex = detail.getEffectiveSeries().indexOfFirst { it.epsId == startChapId }
                val label = if (lastReadChapterId != null && chapIndex >= 0) "继续阅读 第${chapIndex + 1}话" else "开始阅读"
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 8.dp
                ) {
                    Button(
                        onClick = { onStartReading(startChapId) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.MenuBook, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TopAppBar(
                title = { Text("漫画详情", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )

            if (isLoading && bookDetail == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else if (bookDetail != null) {
                val detail = bookDetail
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ===== 封面 + 基本信息 =====
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.surfaceContainerLow,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, colorScheme.surfaceContainerHighest
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp)
                            ) {
                                // 封面
                                Surface(
                                    modifier = Modifier.width(120.dp).height(160.dp),
                                    shape = MaterialTheme.shapes.small,
                                    color = colorScheme.surfaceContainerHighest
                                ) {
                                    AsyncImage(
                                        model = ApiClientFactory.fullImageUrl(detail.cover),
                                        contentDescription = detail.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(
                                    modifier = Modifier.weight(1f).height(160.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            detail.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (detail.authorList.isNotEmpty()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                "作者: ${detail.authorList.joinToString(", ")}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant.opacity60
                                            )
                                        }
                                    }

                                    // JM 编号
                                    Surface(
                                        modifier = Modifier
                                            .clip(MaterialTheme.shapes.small)
                                            .clickable {
                                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cm.setPrimaryClip(ClipData.newPlainText("JM", "JM${detail.id}"))
                                                scope.launch { toast("已复制 JM${detail.id}") }
                                            },
                                        shape = MaterialTheme.shapes.small,
                                        color = colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            "JM${detail.id}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ===== 简介 =====
                    item {
                        InfoHeader(title = "简介", icon = Icons.Default.Description)
                        Text(
                            detail.description?.takeIf { it.isNotBlank() } ?: "暂无简介",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // ===== 标签 =====
                    if (detail.tags.isNotEmpty()) {
                        item {
                            InfoHeader(title = "标签", icon = Icons.Default.Label)
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                detail.tags.forEach { tag ->
                                    CommonChip(label = tag)
                                }
                            }
                        }
                    }

                    // ===== 统计 =====
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                label = "浏览",
                                value = detail.totalViews,
                                icon = Icons.Default.Visibility,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "章节",
                                value = "${detail.getEffectiveSeries().size}",
                                icon = Icons.Default.MenuBook,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                label = "点赞",
                                value = detail.totalLikes.toString(),
                                icon = Icons.Default.ThumbUp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    // ===== 操作按钮 =====
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // 收藏
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (!isLoggedIn) { scope.launch { toast("请先登录") }; return@IconButton }
                                    onAddFavoriteClick { ok, msg ->
                                        if (ok) localFav = !localFav
                                        resultMessage = msg; showResultDialog = true
                                    }
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(if (localFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(22.dp),
                                        tint = if (localFav) colorScheme.error else colorScheme.onSurfaceVariant)
                                }
                                Text(if (localFav) "已收藏" else "收藏", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            // 点赞
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (!isLoggedIn) { scope.launch { toast("请先登录") }; return@IconButton }
                                    onToggleLike { ok, msg ->
                                        if (ok) localLiked = !localLiked
                                        resultMessage = msg; showResultDialog = true
                                    }
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.ThumbUp, null, Modifier.size(22.dp),
                                        tint = if (localLiked) colorScheme.primary else colorScheme.onSurfaceVariant)
                                }
                                Text("点赞", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            // 评论
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = { showComments = true; onLoadComments() }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Default.ChatBubbleOutline, null, Modifier.size(22.dp), tint = colorScheme.onSurfaceVariant)
                                }
                                Text("评论", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // ===== 章节列表 =====
                    item {
                        InfoHeader(title = "章节列表", icon = Icons.Default.List)
                    }

                    items(detail.getEffectiveSeries()) { episode ->
                        EpisodeCard(
                            episode = episode,
                            onClick = { onEpisodeClick(episode) }
                        )
                    }
                }
            }
        }
    }

    // ===== 评论 BottomSheet =====
    if (showComments) {
        val postCommentAction = onPostComment
        val currentBookId = bookDetail?.id ?: ""
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            sheetState = sheetState,
            containerColor = colorScheme.surface
        ) {
            CommentSheet(
                comments = comments,
                isLoading = isLoading,
                totalCount = commentCount,
                hasMore = comments.size < commentCount && comments.isNotEmpty(),
                onLoadMore = {
                    val next = commentPage + 1
                    commentPage = next
                    bookDetail?.let { onLoadMoreComments(next) }
                },
                onPostComment = { text, spoiler ->
                    if (!isLoggedIn) {
                        scope.launch { toast("请先登录") }
                        return@CommentSheet
                    }
                    if (currentBookId.isEmpty()) return@CommentSheet
                    postCommentAction(text, spoiler)
                    // 不立即弹 toast，让 onPostComment 内部处理结果反馈
                },
                onDismiss = { showComments = false; commentPage = 1 }
            )
        }
    }

    // ===== 操作结果提示 =====
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            confirmButton = { TextButton(onClick = { showResultDialog = false }) { Text("确定") } },
            text = { Text(resultMessage) },
            containerColor = colorScheme.surfaceContainerHigh
        )
    }
}

/**
 * 章节卡片 — CommonCard 风格
 */
@Composable
fun EpisodeCard(
    episode: BookEps,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val title = when {
        episode.epsName.isNotEmpty() -> episode.epsName
        episode.sort.isNotEmpty() -> "第${episode.sort}话"
        else -> "开始阅读"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.surfaceContainerHighest),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    null,
                    modifier = Modifier.size(18.dp),
                    tint = colorScheme.primary.opacity60
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariant.opacity38
            )
        }
    }
}
