package com.batsd.openjm.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.batsd.openjm.data.api.ApiClientFactory
import com.batsd.openjm.data.model.BookItem
import com.batsd.openjm.ui.components.*

/**
/**
 * 主界面 — FlClash 风格重构
 */
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    bookList: List<BookItem>,
    isLoading: Boolean,
    sections: List<Pair<String, List<BookItem>>> = emptyList(),
    selectedSection: Int = 0,
    onBookClick: (String) -> Unit,
    onSearchClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onCategoryClick: () -> Unit,
    onSectionSelected: (Int) -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // ===== 椤堕儴鍖哄煙 =====
        Surface(
            color = colorScheme.surface,
            tonalElevation = 1.dp
        ) {
                Column {
                    // 顶部栏
                    TopAppBar(
                        title = {
                            Text(
                                "JMComic-R",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        },
                        actions = {
                            IconButton(onClick = onCategoryClick) {
                                Icon(Icons.Default.Apps, contentDescription = "分类")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = colorScheme.surface
                        )
                    )

                    // 搜索栏
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = colorScheme.surfaceContainerHigh,
                        onClick = {
                            // 点击跳转搜索页
                            onSearchClick("")
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.opacity60,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "搜索漫画名称或JM编号...",
                                color = colorScheme.onSurfaceVariant.opacity60,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // 分区标签栏
                    val tabs = if (sections.isNotEmpty()) sections.map { it.first }
                    else listOf("最新", "热门", "推荐")

                    ScrollableTabRow(
                        selectedTabIndex = selectedSection.coerceIn(0, tabs.lastIndex),
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = colorScheme.surface,
                        contentColor = colorScheme.primary,
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, name ->
                            val isSelected = selectedSection == index
                            Tab(
                                selected = isSelected,
                                onClick = { onSectionSelected(index) },
                                selectedContentColor = colorScheme.primary,
                                unselectedContentColor = colorScheme.onSurfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1
                                    )
                                    if (isSelected) {
                                        Spacer(Modifier.height(4.dp))
                                        Surface(
                                            modifier = Modifier
                                                .width(20.dp)
                                                .height(3.dp),
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = colorScheme.primary
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ===== 内容区域 =====
            if (isLoading && bookList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            } else if (bookList.isEmpty() && !isLoading) {
                EmptyState(
                    message = "鏆傛棤鏁版嵁",
                    icon = Icons.Default.MenuBook
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(bookList, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onBookClick(book.id) }
                        )
                    }
                    // 加载更多指示器
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
    }
}

/**
 * 漫画卡片 — FlClash CommonCard 风格
 */
@Composable
fun BookCard(
    book: BookItem,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onClick() }
    ) {
        // 灏侀潰
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f),
            shape = MaterialTheme.shapes.medium,
            color = colorScheme.surfaceContainerHighest
        ) {
            AsyncImage(
                model = ApiClientFactory.fullImageUrl(book.coverUrl),
                contentDescription = book.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(Modifier.height(6.dp))

        // 标题
        Text(
            text = book.name,
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )

        // 作者 & 点赞
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = book.author.ifEmpty { "浣氬悕" },
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant.opacity60,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = colorScheme.error.opacity60
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = "${book.likes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.opacity60
                )
            }
        }
    }
}
