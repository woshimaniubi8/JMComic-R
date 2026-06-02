package com.batsd.jmcomict.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.data.download.DownloadManager
import com.batsd.jmcomict.data.download.DownloadedBook
import com.batsd.jmcomict.data.download.DownloadedChapter
import com.batsd.jmcomict.ui.components.CommonCard
import com.batsd.jmcomict.ui.components.CardVariant
import com.batsd.jmcomict.ui.components.InfoHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val downloadedBooks by DownloadManager.downloadedBooks.collectAsState()
    var showDeleteConfirm by remember { mutableStateOf<DownloadedBook?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本地下载") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        if (downloadedBooks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudDownload,
                        null,
                        Modifier.size(64.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "暂无下载",
                        style = MaterialTheme.typography.titleMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "在漫画详情页点击下载按钮即可下载",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    InfoHeader(
                        title = "已下载 ${downloadedBooks.size} 本",
                        icon = Icons.Default.CloudDownload
                    )
                }
                items(downloadedBooks, key = { it.bookId }) { book ->
                    DownloadedBookCard(
                        book = book,
                        onClick = { onBookClick(book.bookId) },
                        onDelete = { showDeleteConfirm = book }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (showDeleteConfirm != null) {
        val bookToDelete = showDeleteConfirm!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDeleteConfirm = null }) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            DownloadManager.deleteDownload(bookToDelete.bookId)
                            showDeleteConfirm = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("确认删除", color = MaterialTheme.colorScheme.onError)
                    }
                }
            },
            title = { Text("确认删除") },
            text = {
                Column {
                    Text("确定要删除「${bookToDelete.title}」的所有本地数据吗？")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "将删除 ${bookToDelete.chapters.sumOf { it.pageCount }} 页图片和漫画信息，此操作不可恢复。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }
}

@Composable
private fun DownloadedBookCard(
    book: DownloadedBook,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var coverBitmap by remember(book.coverLocalPath) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(book.coverLocalPath) {
        if (book.coverLocalPath.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    coverBitmap = android.graphics.BitmapFactory.decodeFile(book.coverLocalPath)
                } catch (_: Exception) {}
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            // 封面
            Surface(
                modifier = Modifier.size(width = 80.dp, height = 110.dp),
                shape = MaterialTheme.shapes.small,
                color = colorScheme.surfaceContainerHighest
            ) {
                if (coverBitmap != null) {
                    Image(
                        bitmap = coverBitmap!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Image, null, Modifier.size(32.dp),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (book.author.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        book.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, null, Modifier.size(14.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${book.chapters.size} 章 · ${book.chapters.sumOf { it.pageCount }} 页",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            // 删除按钮
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp).align(Alignment.Top)
            ) {
                Icon(Icons.Default.Delete, "删除", Modifier.size(18.dp),
                    tint = colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}
