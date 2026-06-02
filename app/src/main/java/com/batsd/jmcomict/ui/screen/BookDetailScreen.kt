package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
    commentResult: Pair<Boolean, String>? = null,
    isDownloaded: Boolean = false,
    hasUpdate: Boolean = false,
    onDownloadClick: () -> Unit = {},
    onSearchTagClick: (String) -> Unit = {},
    isDownloading: Boolean = false,
    downloadProgress: Pair<Int, Int> = 0 to 0
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
    var commentsLoading by remember { mutableStateOf(false) }
    var isPosting by remember { mutableStateOf(false) }
    var isFavLoading by remember { mutableStateOf(false) }
    var isLikeLoading by remember { mutableStateOf(false) }
    var commentPage by remember { mutableIntStateOf(1) }
    val sheetState = rememberModalBottomSheetState()

    // 评论加载完成后重置加载状态
    LaunchedEffect(comments) {
        if (!showComments) return@LaunchedEffect
        commentsLoading = false
    }

    // 评论加载失败也重置（通过 isLoading 变化检测）
    LaunchedEffect(isLoading) {
        if (!isLoading && showComments && comments.isNotEmpty()) {
            commentsLoading = false
        }
    }
    // 当评论列表为空但加载完成时也重置
    LaunchedEffect(commentsLoading, isLoading) {
        if (!isLoading && !commentsLoading && comments.isEmpty() && showComments) {
            // 已加载完成，图片显示暂无评论
        }
    }

    // 评论发送结果 → Dialog（消费后清空，避免重复弹窗）
    LaunchedEffect(commentResult) {
        commentResult?.let { (ok, msg) ->
            resultMessage = msg.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n")
            showResultDialog = true
            isPosting = false
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
                                            color = colorScheme.onSurfaceVariant.opacity60,
                                            modifier = Modifier.clickable { onSearchTagClick(detail.authorList.firstOrNull() ?: "") }
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
                                    CommonChip(label = tag, onClick = { onSearchTagClick(tag) })
                                }
                            }
                        }
                    }

                    // ===== 出场角色 =====
                    if (detail.actors.isNotEmpty()) {
                        item {
                            InfoHeader(title = "出场角色", icon = Icons.Default.Person)
                            FlowRow(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                detail.actors.forEach { actor ->
                                    CommonChip(label = actor, onClick = { onSearchTagClick(actor) })
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
                                    if (isFavLoading) return@IconButton
                                    isFavLoading = true
                                    onAddFavoriteClick { ok, msg ->
                                        if (ok) localFav = !localFav
                                        resultMessage = msg; showResultDialog = true
                                        isFavLoading = false
                                    }
                                }, modifier = Modifier.size(48.dp)) {
                                    AnimatedContent(
                                        targetState = isFavLoading,
                                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                        label = "fav_loading"
                                    ) { loading ->
                                        if (loading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        } else {
                                            Icon(if (localFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null, Modifier.size(22.dp),
                                                tint = if (localFav) colorScheme.error else colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text(if (localFav) "已收藏" else "收藏", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            // 点赞
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (!isLoggedIn) { scope.launch { toast("请先登录") }; return@IconButton }
                                    if (isLikeLoading) return@IconButton
                                    isLikeLoading = true
                                    onToggleLike { ok, msg ->
                                        if (ok) localLiked = !localLiked
                                        resultMessage = msg; showResultDialog = true
                                        isLikeLoading = false
                                    }
                                }, modifier = Modifier.size(48.dp)) {
                                    AnimatedContent(
                                        targetState = isLikeLoading,
                                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                        label = "like_loading"
                                    ) { loading ->
                                        if (loading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        } else {
                                            Icon(Icons.Default.ThumbUp, null, Modifier.size(22.dp),
                                                tint = if (localLiked) colorScheme.primary else colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text("点赞", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            // 评论
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (commentsLoading) return@IconButton
                                    commentsLoading = true
                                    showComments = true
                                    commentPage = 1
                                    onLoadComments()
                                }, modifier = Modifier.size(48.dp)) {
                                    AnimatedContent(
                                        targetState = commentsLoading,
                                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                        label = "comment_loading"
                                    ) { loading ->
                                        if (loading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = colorScheme.primary
                                            )
                                        } else {
                                            Icon(Icons.Default.ChatBubbleOutline, "评论", Modifier.size(22.dp),
                                                tint = colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Text("评论", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                            }
                            // 下载
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                IconButton(onClick = {
                                    if (isDownloading) return@IconButton
                                    onDownloadClick()
                                }, modifier = Modifier.size(48.dp)) {
                                    Box {
                                        AnimatedContent(
                                            targetState = if (isDownloading) 1 else if (isDownloaded) 2 else 0,
                                            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
                                            label = "download_loading"
                                        ) { state ->
                                            when (state) {
                                                1 -> CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = colorScheme.primary
                                                )
                                                2 -> Icon(Icons.Default.CloudDone, null, Modifier.size(22.dp),
                                                    tint = colorScheme.primary)
                                                else -> Icon(Icons.Default.CloudDownload, null, Modifier.size(22.dp),
                                                    tint = colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        // 更新徽章
                                        if (isDownloaded && hasUpdate) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(10.dp)
                                                    .background(colorScheme.error, shape = androidx.compose.foundation.shape.CircleShape)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    if (isDownloading) "下载中" else if (isDownloaded) if (hasUpdate) "有更新" else "已下载" else "下载",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasUpdate) colorScheme.error else colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ===== 下载进度条 =====
                    if (isDownloading) {
                        item {
                            val (done, total) = downloadProgress
                            val progress = if (total > 0) done.toFloat() / total else 0f
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier.fillMaxWidth().height(6.dp),
                                    color = colorScheme.primary,
                                    trackColor = colorScheme.surfaceContainerHighest,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "下载中 $done/$total 页",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
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
                isLoading = commentsLoading && comments.isEmpty(),
                totalCount = commentCount,
                hasMore = comments.size < commentCount && comments.isNotEmpty(),
                isPosting = isPosting,
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
                    if (bookDetail?.id?.isEmpty() != false) return@CommentSheet
                    isPosting = true
                    postCommentAction(text, spoiler)
                },
                onDismiss = { showComments = false; commentPage = 1; isPosting = false }
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
