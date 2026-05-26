package com.batsd.jmcomict.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import com.batsd.jmcomict.data.model.CommentInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentSheet(
    comments: List<CommentInfo>,
    isLoading: Boolean,
    totalCount: Int = 0,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onPostComment: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var isSpoiler by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val topComments = comments.filter { it.parentCid == "0" || it.parentCid.isEmpty() }
    val replies = comments.filter { it.parentCid != "0" && it.parentCid.isNotEmpty() }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("评论 (${if (totalCount > 0) totalCount else comments.size})", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        if (isLoading && comments.isEmpty()) {
            Box(Modifier.height(200.dp).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (comments.isEmpty()) {
            Box(Modifier.height(100.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("暂无评论", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(topComments) { c -> CommentItem(c, replies.filter { it.parentCid == c.cid }) }
                if (hasMore || isLoading) {
                    item {
                        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
                            Text(if (isLoading) "加载中..." else "加载更多")
                        }
                    }
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = inputText, onValueChange = { inputText = it },
                placeholder = { Text("写评论...") }, modifier = Modifier.weight(1f), maxLines = 3)
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 4.dp)) {
                IconButton(onClick = {
                    if (inputText.isBlank()) { scope.launch { toast("请输入内容") }; return@IconButton }
                    onPostComment(inputText, isSpoiler); inputText = ""; isSpoiler = false
                }, enabled = inputText.isNotBlank(), modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Send, "发送", Modifier.size(20.dp))
                }
                Text("剧透", style = MaterialTheme.typography.labelSmall,
                    color = if (isSpoiler) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable { isSpoiler = !isSpoiler }.padding(vertical = 2.dp))
            }
        }
    }
}

@Composable
fun CommentItem(comment: CommentInfo, subReplies: List<CommentInfo>) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.avatarUrl.isNotEmpty())
                    AsyncImage(model = com.batsd.jmcomict.data.api.ApiClientFactory.fullImageUrl(comment.avatarUrl),
                        null, Modifier.size(32.dp).padding(end = 8.dp))
                Column {
                    Text(comment.displayName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (comment.levelName.isNotEmpty()) Text(comment.levelName, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(comment.addtime, style = MaterialTheme.typography.labelSmall)
                if (comment.likes != "0") Text("❤ ${comment.likes}", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (comment.contentText.isNotEmpty())
            Text(comment.contentText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), horizontalArrangement = Arrangement.End) {
            if (comment.name.isNotEmpty())
                Text(comment.name, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.opacity60)
        }
        subReplies.forEach { r ->
            Text("↳ ${r.displayName}: ${r.contentText}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 20.dp, top = 4.dp), maxLines = 3)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
