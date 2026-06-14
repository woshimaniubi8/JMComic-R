package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.data.model.BookItem
import com.batsd.jmcomict.ui.components.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

/**
 * 搜索界面 — FlClash 搜索体验
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SearchScreen(
    bookList: List<BookItem>,
    isLoading: Boolean,
    searchHistory: List<String> = emptyList(),
    onBackClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    onBookClick: (String) -> Unit,
    onClearHistory: () -> Unit = {},
    onDeleteHistory: (String) -> Unit = {},
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onClearQuery: () -> Unit = {},
    activeQuery: String = "",
    resultTotal: Int = 0,
    initialQuery: String = "",
    onRefresh: () -> Unit = {}
) {
    var searchQuery by remember(initialQuery) { mutableStateOf(initialQuery) }
    var searchFocused by remember { mutableStateOf(false) }
    var historyDeleteMode by remember { mutableStateOf(false) }
    // 用户手动输入后清除 initialQuery 影响
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            searchQuery = initialQuery
        }
    }
    LaunchedEffect(searchHistory) {
        if (searchHistory.isEmpty()) historyDeleteMode = false
    }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme

    // 仅在从外部传入搜索词时自动聚焦（标签/角色点击）
    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotEmpty()) {
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索栏区域
            Surface(
                color = colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = colorScheme.onSurface
                        )
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "搜索漫画名称或 JM 编号...",
                                color = colorScheme.onSurfaceVariant.opacity60
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { searchFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchClick(searchQuery)
                                searchFocused = false
                                focusManager.clearFocus()
                            }
                        }),
                        trailingIcon = {
                            Row {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        onClearQuery()
                                    }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除")
                                    }
                                }
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        onSearchClick(searchQuery)
                                        searchFocused = false
                                        focusManager.clearFocus()
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = colorScheme.surfaceContainerHigh,
                            focusedBorderColor = colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )
                }
            }

            // 搜索结果
            Box(modifier = Modifier.fillMaxSize()) {
                val hasSubmittedSearch = activeQuery.isNotBlank()
                val showHistory = searchFocused && searchHistory.isNotEmpty() && !isLoading && !hasSubmittedSearch
                when {
                    showHistory -> {
                        SearchHistoryList(
                            searchHistory = searchHistory,
                            deleteMode = historyDeleteMode,
                            onDeleteModeChange = { historyDeleteMode = it },
                            onClearHistory = {
                                historyDeleteMode = false
                                onClearHistory()
                            },
                            onSearchHistoryClick = { query ->
                                if (historyDeleteMode) return@SearchHistoryList
                                searchQuery = query
                                onSearchClick(query)
                                focusManager.clearFocus()
                            },
                            onDeleteHistory = onDeleteHistory
                        )
                    }
                    isLoading && hasSubmittedSearch && bookList.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colorScheme.primary)
                        }
                    }
                    hasSubmittedSearch && bookList.isEmpty() -> {
                        EmptyState(
                            message = "未找到相关漫画",
                            icon = Icons.Default.SearchOff
                        )
                    }
                    hasSubmittedSearch && bookList.isNotEmpty() -> {
                        val gridState = rememberLazyGridState()
                        val displayTotal = if (resultTotal > 0) resultTotal else bookList.size
                        val shouldLoadMore by remember {
                            derivedStateOf {
                                val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                lastVisible >= bookList.size - 6 && bookList.isNotEmpty()
                            }
                        }
                        LaunchedEffect(shouldLoadMore, isLoading, hasMore) {
                            if (shouldLoadMore && hasMore && !isLoading) {
                                onLoadMore()
                            }
                        }
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    "搜索 \"${activeQuery}\" 共 ${displayTotal} 结果",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            items(bookList, key = { it.id }) { book ->
                                BookCard(
                                    book = book,
                                    onClick = { onBookClick(book.id) }
                                )
                            }
                            if (isLoading) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
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
                    else -> {
                        if (searchHistory.isNotEmpty()) {
                            SearchHistoryList(
                                searchHistory = searchHistory,
                                deleteMode = historyDeleteMode,
                                onDeleteModeChange = { historyDeleteMode = it },
                                onClearHistory = {
                                    historyDeleteMode = false
                                    onClearHistory()
                                },
                                onSearchHistoryClick = { query ->
                                    if (historyDeleteMode) return@SearchHistoryList
                                    searchQuery = query
                                    onSearchClick(query)
                                    focusManager.clearFocus()
                                },
                                onDeleteHistory = onDeleteHistory
                            )
                        } else {
                            EmptyState(message = "输入关键词搜索漫画", icon = Icons.Default.Search)
                        }
                    }
                }
            }
        }
    }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SearchHistoryList(
    searchHistory: List<String>,
    deleteMode: Boolean,
    onDeleteModeChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onSearchHistoryClick: (String) -> Unit,
    onDeleteHistory: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                enabled = deleteMode,
                interactionSource = interactionSource,
                indication = null
            ) { onDeleteModeChange(false) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("搜索历史", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onClearHistory) { Text("清除") }
                }
            }
            lazyItems(searchHistory, key = { it }) { query ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .combinedClickable(
                            onClick = { onSearchHistoryClick(query) },
                            onLongClick = { onDeleteModeChange(true) }
                        ),
                    shape = MaterialTheme.shapes.small,
                    color = colorScheme.surfaceContainerLow
                ) {
                    Row(
                        Modifier.padding(start = 12.dp, top = 8.dp, end = 4.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.History, null, Modifier.size(16.dp), tint = colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            query,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (deleteMode) {
                            IconButton(
                                onClick = { onDeleteHistory(query) },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "删除",
                                    modifier = Modifier.size(18.dp),
                                    tint = colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
