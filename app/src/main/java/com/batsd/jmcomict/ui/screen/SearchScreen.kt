package com.batsd.jmcomict.ui.screen

import androidx.compose.animation.Crossfade
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.data.model.BookItem
import com.batsd.jmcomict.ui.components.*

/**
 * 搜索界面 — FlClash 搜索体验
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    bookList: List<BookItem>,
    isLoading: Boolean,
    searchHistory: List<String> = emptyList(),
    onBackClick: () -> Unit,
    onSearchClick: (String) -> Unit,
    onBookClick: (String) -> Unit,
    onClearHistory: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
        ) {
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
                            .focusRequester(focusRequester),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (searchQuery.isNotEmpty()) {
                                onSearchClick(searchQuery)
                                focusManager.clearFocus()
                            }
                        }),
                        trailingIcon = {
                            Row {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除")
                                    }
                                }
                                IconButton(onClick = {
                                    if (searchQuery.isNotEmpty()) {
                                        onSearchClick(searchQuery)
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
                when {
                    isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = colorScheme.primary)
                        }
                    }
                    bookList.isEmpty() && searchQuery.isNotEmpty() -> {
                        EmptyState(
                            message = "未找到相关漫画",
                            icon = Icons.Default.SearchOff
                        )
                    }
                    bookList.isNotEmpty() -> {
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
                        }
                    }
                    else -> {
                        if (searchHistory.isNotEmpty()) {
                            Column(Modifier.padding(16.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("搜索历史", style = MaterialTheme.typography.titleSmall)
                                    TextButton(onClick = onClearHistory) { Text("清除") }
                                }
                                searchHistory.take(10).forEach { query ->
                                    Surface(onClick = {
                                        searchQuery = query
                                        onSearchClick(query)
                                    }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = colorScheme.surfaceContainerLow) {
                                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.History, null, Modifier.size(16.dp), tint = colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(8.dp))
                                            Text(query, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                            EmptyState(message = "输入关键词搜索漫画", icon = Icons.Default.Search)
                        }
                    }
                }
            }
        }
    }
}
