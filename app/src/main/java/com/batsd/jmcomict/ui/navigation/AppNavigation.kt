package com.batsd.jmcomict.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.batsd.jmcomict.ui.screen.*
import com.batsd.jmcomict.ui.components.*
import com.batsd.jmcomict.ui.viewmodel.BookViewModel
import com.batsd.jmcomict.ui.viewmodel.CategoryViewModel
import com.batsd.jmcomict.ui.viewmodel.UserViewModel
import com.batsd.jmcomict.data.api.ApiClientFactory

data class NavItem(val label: String, val icon: ImageVector, val screen: MainTab)
enum class MainTab { Home, Search, Favorites, Profile }
sealed class SubScreen {
    object None : SubScreen()
    data class BookDetail(val bookId: String) : SubScreen()
    data class Reader(val epsId: String) : SubScreen()
    object Category : SubScreen()
    object Login : SubScreen()
    object Settings : SubScreen()
    object History : SubScreen()
}

val navItems = listOf(
    NavItem("主页", Icons.Filled.Home, MainTab.Home),
    NavItem("搜索", Icons.Filled.Search, MainTab.Search),
    NavItem("收藏", Icons.Filled.Favorite, MainTab.Favorites),
    NavItem("我的", Icons.Filled.Person, MainTab.Profile),
)

@Composable
fun AppNavigation(
    userViewModel: UserViewModel,
    bookViewModel: BookViewModel,
    categoryViewModel: CategoryViewModel,
    isDarkTheme: Boolean = false,
    themeMode: Int = 0,
    onSetThemeMode: (Int) -> Unit = {}
) {
    MainScreen(userViewModel, bookViewModel, categoryViewModel, isDarkTheme, themeMode, onSetThemeMode)
}

@Composable
fun MainScreen(
    userViewModel: UserViewModel,
    bookViewModel: BookViewModel,
    categoryViewModel: CategoryViewModel,
    isDarkTheme: Boolean,
    themeMode: Int,
    onSetThemeMode: (Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var previousSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var searchHistory by remember { mutableStateOf(emptyList<String>()) }
    var selectedSection by remember { mutableIntStateOf(0) }
    var cdnName by remember { mutableStateOf(ApiClientFactory.getCurrentCdnName()) }
    val userIsLoading by userViewModel.isLoading.collectAsState()
    val userError by userViewModel.error.collectAsState()

    // 系统返回键处理
    BackHandler(enabled = subScreen !is SubScreen.None) {
        when (subScreen) {
            is SubScreen.Reader -> subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None }
            else -> subScreen = SubScreen.None
        }
    }

    val bookList by bookViewModel.bookList.collectAsState()
    val bookDetail by bookViewModel.bookDetail.collectAsState()
    val episodeDetail by bookViewModel.episodeDetail.collectAsState()
    val bookIsLoading by bookViewModel.isLoading.collectAsState()
    val currentPage by bookViewModel.currentPage.collectAsState()
    val scrambleId by bookViewModel.scrambleId.collectAsState()
    val categories by categoryViewModel.categories.collectAsState()
    val categoryIsLoading by categoryViewModel.isLoading.collectAsState()
    val comments by bookViewModel.comments.collectAsState()
    val commentCount by bookViewModel.commentCount.collectAsState()
    val commentResult by bookViewModel.commentResult.collectAsState()
    val history by bookViewModel.history.collectAsState()
    val homeSections by bookViewModel.homeSections.collectAsState()
    val user by userViewModel.user.collectAsState()
    val categoryBooks by categoryViewModel.categoryBooks.collectAsState()
    var pendingCategory by remember { mutableStateOf(false) }

    // 分类选择后同步书单到首页
    LaunchedEffect(categoryBooks) {
        if (pendingCategory && categoryBooks.isNotEmpty()) {
            bookViewModel.setBooks(categoryBooks)
            pendingCategory = false
        }
    }

    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            MainTab.Home -> if (bookList.isEmpty()) bookViewModel.getHomeSections()
            MainTab.Favorites -> bookViewModel.getFavorites()
            MainTab.Profile -> bookViewModel.getHistory()
            else -> {}
        }
    }

    LaunchedEffect(Unit) { categoryViewModel.getCategories() }

    LaunchedEffect(user?.isLogin) {
        if (user?.isLogin == true && subScreen is SubScreen.Login) {
            subScreen = SubScreen.None
        }
    }

    // ===== 子页面渲染（带动画）=====
    AnimatedContent(
        targetState = subScreen,
        transitionSpec = {
            if (targetState is SubScreen.None) {
                // 返回主屏：向右滑出
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 3 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 3 })
            } else {
                // 进入子屏：向左滑入
                (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 3 })
                    .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 3 })
            }
        },
        label = "subscreen_transition"
    ) { screen ->
        when (screen) {
            is SubScreen.BookDetail -> {
                val detail = bookDetail; val id = screen.bookId
                val lastChapId by bookViewModel.lastReadChapterId.collectAsState()
                androidx.compose.runtime.key(id) {
                BookDetailScreen(
                    bookDetail = detail,
                    isLoading = bookIsLoading,
                    comments = comments,
                    commentCount = commentCount,
                    isLoggedIn = user?.isLogin == true,
                    lastReadChapterId = lastChapId,
                    onBackClick = {
                        val prev = previousSubScreen
                        previousSubScreen = SubScreen.None
                        subScreen = prev.let { SubScreen.None }
                    },
                    onEpisodeClick = { ep ->
                        bookViewModel.getEpisodeDetail(ep.epsId)
                        previousSubScreen = subScreen
                        subScreen = SubScreen.Reader(ep.epsId)
                    },
                    onStartReading = { epsId ->
                        bookViewModel.getEpisodeDetail(epsId)
                        previousSubScreen = subScreen
                        subScreen = SubScreen.Reader(epsId)
                    },
                    onFavoriteClick = { bookViewModel.toggleFavorite(id) },
                    onAddFavoriteClick = { cb -> bookViewModel.toggleFavorite(id, cb) },
                    onToggleLike = { cb -> bookViewModel.toggleLike(id, cb) },
                    onLoadComments = { bookViewModel.getComments(id) },
                    onLoadMoreComments = { page -> bookViewModel.getComments(id, page.toString()) },
                    onPostComment = { text, _ -> bookViewModel.postComment(id, text) },
                    commentResult = commentResult
                )
                }
            }
            is SubScreen.Reader -> ReaderScreen(
                episode = episodeDetail, currentPage = currentPage,
                scrambleId = scrambleId, isLoading = bookIsLoading,
                onBackClick = { subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None } },
                onPreviousPage = { if (currentPage > 0) bookViewModel.setCurrentPage(currentPage - 1) },
                onNextPage = { bookViewModel.setCurrentPage(currentPage + 1) },
                onPageSelect = { bookViewModel.setCurrentPage(it) }
            )
            is SubScreen.Category -> CategoryScreen(
                categories = categories, isLoading = categoryIsLoading,
                onBackClick = { subScreen = SubScreen.None },
                onCategoryClick = { cid ->
                    pendingCategory = true
                    categoryViewModel.getBooksByCategory(cid)
                    subScreen = SubScreen.None
                    selectedTab = MainTab.Home
                }
            )
            is SubScreen.Login -> LoginScreen(
                onLoginClick = { u, p -> userViewModel.login(u, p) },
                onRegisterClick = {},
                isLoading = userIsLoading,
                error = userError
            )
            is SubScreen.Settings -> SettingsScreen(
                cdnName = cdnName,
                isDarkTheme = isDarkTheme,
                themeMode = themeMode,
                isLoggedIn = user?.isLogin == true,
                onBackClick = { subScreen = SubScreen.None },
                onSwitchCdn = { ApiClientFactory.switchCdn(); cdnName = ApiClientFactory.getCurrentCdnName() },
                onSetThemeMode = onSetThemeMode,
                onLogoutClick = {
                    userViewModel.logout()
                    subScreen = SubScreen.None
                    selectedTab = MainTab.Home
                }
            )
            is SubScreen.History -> HistoryScreen(
                history = history,
                isLoading = bookIsLoading,
                onBackClick = { subScreen = SubScreen.None },
                onBookClick = { id ->
                    bookViewModel.getBookDetail(id)
                    previousSubScreen = SubScreen.None
                    subScreen = SubScreen.BookDetail(id)
                },
                onHideHistoryItem = { bookViewModel.hideHistoryItem(it) }
            )
            is SubScreen.None -> { /* 主屏内容在下面渲染 */ }
        }
    }

    // 如果当前是主屏，渲染主屏内容
    if (subScreen is SubScreen.None) {
    val colorScheme = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
                contentColor = colorScheme.onSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                item.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        selected = selectedTab == item.screen,
                        onClick = { selectedTab = item.screen },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colorScheme.primary,
                            selectedTextColor = colorScheme.primary,
                            unselectedIconColor = colorScheme.onSurfaceVariant.opacity60,
                            unselectedTextColor = colorScheme.onSurfaceVariant.opacity60,
                            indicatorColor = colorScheme.primaryContainer.opacity50
                        )
                    )
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(padding),
            transitionSpec = {
                (fadeIn(animationSpec = tween(200)) + slideInHorizontally(animationSpec = tween(200)) { it / 4 })
                    .togetherWith(fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200)) { -it / 4 })
            },
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                MainTab.Home -> HomeScreen(
                    bookList = bookList, isLoading = bookIsLoading,
                    sections = homeSections,
                    selectedSection = selectedSection,
                    onBookClick = { id ->
                        bookViewModel.getBookDetail(id)
                        previousSubScreen = SubScreen.None
                        subScreen = SubScreen.BookDetail(id)
                    },
                    onSearchClick = { query ->
                        val jmCode = query.trim()
                        if (jmCode.all { it.isDigit() } && jmCode.isNotEmpty()) {
                            bookViewModel.getBookDetail(jmCode)
                            previousSubScreen = SubScreen.None
                            subScreen = SubScreen.BookDetail(jmCode)
                        } else {
                            selectedTab = MainTab.Search
                            bookViewModel.searchBooks(query)
                        }
                    },
                    onRefresh = { bookViewModel.getBookList() },
                    onCategoryClick = {
                        categoryViewModel.getCategories()
                        subScreen = SubScreen.Category
                    },
                    onSectionSelected = { index ->
                        selectedSection = index
                        if (index in homeSections.indices) {
                            bookViewModel.setBooks(homeSections[index].second)
                        }
                    }
                )
                MainTab.Search -> SearchScreen(
                    bookList = bookList, isLoading = bookIsLoading,
                    searchHistory = searchHistory,
                    onClearHistory = { searchHistory = emptyList() },
                    onBackClick = { selectedTab = MainTab.Home },
                    onSearchClick = { query ->
                        searchHistory = (listOf(query) + searchHistory.filter { it != query }).take(50)
                        // JM码识别：纯数字 或 JM/Jm/jm开头
                        val trimmed = query.trim()
                        if (trimmed.all { it.isDigit() } || trimmed.lowercase().startsWith("jm")) {
                            val code = if (trimmed.all { it.isDigit() }) trimmed
                                else trimmed.substring(2).trim()
                            if (code.isNotEmpty()) {
                                bookViewModel.getBookDetail(code)
                                previousSubScreen = SubScreen.None
                                subScreen = SubScreen.BookDetail(code)
                                return@SearchScreen
                            }
                        }
                        bookViewModel.searchBooks(query)
                    },
                    onBookClick = { id ->
                        bookViewModel.getBookDetail(id)
                        previousSubScreen = SubScreen.None
                        subScreen = SubScreen.BookDetail(id)
                    }
                )
                MainTab.Favorites -> {
                    val favs by bookViewModel.favorites.collectAsState()
                    FavoritesScreen(
                        favoriteBooks = favs, isLoading = bookIsLoading,
                        onBackClick = { selectedTab = MainTab.Home },
                        onBookClick = { id ->
                            bookViewModel.getBookDetail(id)
                            previousSubScreen = SubScreen.None
                            subScreen = SubScreen.BookDetail(id)
                        },
                        onRemoveFavorite = { id ->
                            bookViewModel.toggleFavorite(id)
                            bookViewModel.getFavorites()
                        }
                    )
                }
                MainTab.Profile -> {
                    LaunchedEffect(Unit) { bookViewModel.getHistory() }
                    UserProfileScreen(
                    userName = user?.userName, userId = user?.uid,
                    level = user?.levelName?.takeIf { it.isNotEmpty() } ?: user?.title ?: "",
                    coin = user?.coin ?: 0,
                    exp = user?.expStr ?: "",
                    expPercent = user?.expPercent ?: 0.0,
                    avatarUrl = user?.imgUrl ?: "",
                    isLoggedIn = user?.isLogin == true,
                    history = history,
                    onCheckInClick = { cb -> userViewModel.dailyCheckIn(cb) },
                    onLoginClick = { subScreen = SubScreen.Login },
                    onHistoryClick = { id ->
                        bookViewModel.getBookDetail(id)
                        previousSubScreen = SubScreen.None
                        subScreen = SubScreen.BookDetail(id)
                    },
                    onViewAllHistory = { subScreen = SubScreen.History },
                    onHideHistoryItem = { bookViewModel.hideHistoryItem(it) },
                    onLoadHistory = { bookViewModel.getHistory() },
                    onSettingsClick = { subScreen = SubScreen.Settings }
                )
                }
            }
        }
    }
    } // end if(subScreen is SubScreen.None)
}
