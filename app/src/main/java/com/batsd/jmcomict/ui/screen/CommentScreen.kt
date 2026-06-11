package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.data.model.CommentInfo
import com.batsd.jmcomict.ui.components.CommentSheet

/**
 * 独立评论页 — 替代 ModalBottomSheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentScreen(
    comments: List<CommentInfo> = emptyList(),
    commentCount: Int = 0,
    isLoggedIn: Boolean = false,
    isPosting: Boolean = false,
    onBackClick: () -> Unit,
    onLoadComments: () -> Unit = {},
    onLoadMoreComments: (Int) -> Unit = {},
    onPostComment: (String, Boolean) -> Unit = { _, _ -> }
) {
    val colorScheme = MaterialTheme.colorScheme
    var commentsLoading by remember { mutableStateOf(true) }
    var loadStarted by remember { mutableStateOf(false) }
    var commentPage by remember { mutableIntStateOf(0) }

    // 初始加载
    LaunchedEffect(Unit) {
        commentsLoading = true
        loadStarted = true
        onLoadComments()
    }
    // 首次进入时 comments 通常是初始空列表，不应立刻显示“暂无评论”。
    LaunchedEffect(comments) {
        if (loadStarted && comments.isNotEmpty()) {
            commentsLoading = false
        }
    }
    // 超时保护：5秒后强制停止加载
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(5000)
        commentsLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("评论 (${commentCount})") },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            CommentSheet(
                comments = comments,
                isLoading = commentsLoading && comments.isEmpty(),
                totalCount = commentCount,
                hasMore = comments.size < commentCount && comments.isNotEmpty(),
                isPosting = isPosting,
                onLoadMore = {
                    val next = commentPage + 1
                    commentPage = next
                    onLoadMoreComments(next)
                },
                onPostComment = { text, spoiler -> onPostComment(text, spoiler) },
                onDismiss = onBackClick
            )
        }
    }
}
