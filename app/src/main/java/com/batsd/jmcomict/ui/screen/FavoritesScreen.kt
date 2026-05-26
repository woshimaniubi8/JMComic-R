package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.model.BookItem
import com.batsd.jmcomict.ui.components.*

/**
 * 收藏列表 — FlClash 列表项风格
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    favoriteBooks: List<BookItem>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onBookClick: (String) -> Unit,
    onRemoveFavorite: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的收藏") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colorScheme.surface
                )
            )
        },
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
            // 内容
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colorScheme.primary)
                        }
                    }
                    favoriteBooks.isEmpty() -> {
                        EmptyState(
                            message = "还没有收藏任何漫画",
                            icon = Icons.Default.FavoriteBorder
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(favoriteBooks, key = { it.id }) { book ->
                                FavoriteBookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) },
                                    onRemove = { onRemoveFavorite(book.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收藏列表项卡片 — CommonCard 风格
 */
@Composable
fun FavoriteBookCard(
    book: BookItem,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLow,
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.surfaceContainerHighest),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面
            Surface(
                modifier = Modifier
                    .width(72.dp)
                    .height(96.dp),
                shape = MaterialTheme.shapes.small,
                color = colorScheme.surfaceContainerHighest
            ) {
                AsyncImage(
                    model = ApiClientFactory.fullImageUrl(book.coverUrl),
                    contentDescription = book.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.width(12.dp))

            // 信息区域
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = book.author.ifEmpty { "佚名" },
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.opacity60,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colorScheme.error.opacity60
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${book.likes}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.opacity60
                    )
                }
            }

            // 移除按钮
            IconButton(
                onClick = onRemove,
                modifier = Modifier.padding(start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除收藏",
                    modifier = Modifier.size(20.dp),
                    tint = colorScheme.onSurfaceVariant.opacity50
                )
            }
        }
    }
}
