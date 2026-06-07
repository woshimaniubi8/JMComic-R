package com.batsd.jmcomict.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import com.batsd.jmcomict.data.local.PreferencesManager
import kotlinx.coroutines.launch

data class NavItem(val label: String, val icon: ImageVector, val screen: MainTab)
enum class MainTab { Home, Search, Favorites, Profile }
sealed class SubScreen {
    object None : SubScreen()
    data class BookDetail(val bookId: String) : SubScreen()
    data class Reader(val epsId: String) : SubScreen()
    object Category : SubScreen()
    data class CategoryBooks(val categoryId: String, val categoryName: String) : SubScreen()
    object Login : SubScreen()
    object Settings : SubScreen()
    object History : SubScreen()
    object LineTest : SubScreen()
    object About : SubScreen()
    object Downloads : SubScreen()
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
    prefs: PreferencesManager,
    isDarkTheme: Boolean = false,
    themeMode: Int = 0,
    onSetThemeMode: (Int) -> Unit = {},
    onShowDisclaimer: (() -> Unit)? = null
) {
    MainScreen(userViewModel, bookViewModel, categoryViewModel, prefs, isDarkTheme, themeMode, onSetThemeMode, onShowDisclaimer)
}

@Composable
fun MainScreen(
    userViewModel: UserViewModel,
    bookViewModel: BookViewModel,
    categoryViewModel: CategoryViewModel,
    prefs: PreferencesManager,
    isDarkTheme: Boolean,
    themeMode: Int,
    onSetThemeMode: (Int) -> Unit,
    onShowDisclaimer: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableStateOf(MainTab.Home) }
    var subScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var previousSubScreen by remember { mutableStateOf<SubScreen>(SubScreen.None) }
    var searchHistory by remember { mutableStateOf(emptyList<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSection by remember { mutableIntStateOf(0) }
    // 同步恢复 CDN 分流索引（API 和图片 CDN 独立恢复）
    val initialApiIndex = prefs.getApiUrlIndex().coerceIn(0, ApiClientFactory.getApiUrlCount() - 1)
    val initialImageIndex = prefs.getImageCdnIndex().coerceIn(0, ApiClientFactory.getCdnCount() - 1)
    ApiClientFactory.restoreIndices(initialApiIndex, initialImageIndex)
    var cdnName by remember { mutableStateOf(ApiClientFactory.getCurrentCdnName()) }
    var imageCdnName by remember { mutableStateOf(ApiClientFactory.getCurrentImageCdnName()) }
    var cdnIndex by remember { mutableIntStateOf(ApiClientFactory.getCurrentCdnIndex()) }
    var apiUrlIndex by remember { mutableIntStateOf(ApiClientFactory.getCurrentApiUrlIndex()) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    val userIsLoading by userViewModel.isLoading.collectAsState()
    val userError by userViewModel.error.collectAsState()

    // 双击返回退出
    var backPressedTime by remember { mutableLongStateOf(0L) }

    // 系统返回键处理（子页面）
    BackHandler(enabled = subScreen !is SubScreen.None) {
        when (subScreen) {
            is SubScreen.Reader -> subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None }
            is SubScreen.CategoryBooks -> subScreen = SubScreen.Category
            is SubScreen.BookDetail -> {
                val prev = previousSubScreen
                previousSubScreen = SubScreen.None
                subScreen = prev
            }
            is SubScreen.LineTest -> subScreen = SubScreen.Settings
            is SubScreen.Settings -> subScreen = SubScreen.None
            is SubScreen.About -> subScreen = SubScreen.Settings
            is SubScreen.Downloads -> subScreen = SubScreen.None
            else -> subScreen = SubScreen.None
        }
    }

    // 主页面双击返回退出
    BackHandler(enabled = subScreen is SubScreen.None) {
        val now = System.currentTimeMillis()
        if (now - backPressedTime > 2000) {
            backPressedTime = now
            android.widget.Toast.makeText(context, "再按一次返回退出", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            (context as? android.app.Activity)?.finish()
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
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            MainTab.Home -> if (bookList.isEmpty()) bookViewModel.getHomeSections()
            MainTab.Favorites -> if (bookViewModel.favorites.value.isEmpty()) bookViewModel.getFavorites()
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

    val bgColor = MaterialTheme.colorScheme.background

    // ===== 子页面渲染（带动画）=====
    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
    AnimatedContent(
        targetState = subScreen,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val isGoingBack = targetState is SubScreen.None ||
                (initialState is SubScreen.CategoryBooks && targetState is SubScreen.Category) ||
                (initialState is SubScreen.BookDetail && targetState !is SubScreen.Reader) ||
                (initialState is SubScreen.LineTest && targetState is SubScreen.Settings) ||
                (initialState is SubScreen.About && targetState is SubScreen.Settings) ||
                (initialState is SubScreen.Downloads) ||
                (initialState is SubScreen.Reader)
            if (isGoingBack) {
                // 返回：向右滑出
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
        // 响应式收集下载进度
        val dlProgress by com.batsd.jmcomict.data.download.DownloadManager.downloadProgress.collectAsState()
        val dlBooks by com.batsd.jmcomict.data.download.DownloadManager.downloadedBooks.collectAsState()
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
                        subScreen = prev
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
                    commentResult = commentResult,
                    isDownloaded = dlBooks.any { it.bookId == id },
                    isDownloading = dlProgress.values.any {
                        it.status == com.batsd.jmcomict.data.download.DownloadTaskStatus.DOWNLOADING
                    },
                    downloadProgress = com.batsd.jmcomict.data.download.DownloadManager.getBookProgress(id),
                    onSearchTagClick = { query ->
                        if (query.isNotBlank()) {
                            searchQuery = query
                            previousSubScreen = SubScreen.None
                            subScreen = SubScreen.None
                            selectedTab = MainTab.Search
                            bookViewModel.searchBooks(query)
                        }
                    },
                    hasUpdate = detail?.let { comicDetail ->
                        if (dlBooks.any { it.bookId == id }) {
                            val localEpsIds = com.batsd.jmcomict.data.download.DownloadManager.getLocalEpsIds(id)
                            val serverEpsIds = comicDetail.getEffectiveSeries().map { it.epsId }
                            serverEpsIds.any { it !in localEpsIds }
                        } else false
                    } ?: false,
                    onDownloadClick = {
                        android.util.Log.d("AppNav", "Download book: $id")
                        if (detail != null) {
                            // 立即显示加载动画
                            val firstEpsId = detail.getEffectiveSeries().firstOrNull()?.epsId ?: id
                            com.batsd.jmcomict.data.download.DownloadManager.markDownloading(firstEpsId, detail.title)
                            kotlinx.coroutines.MainScope().launch {
                                val repo = com.batsd.jmcomict.data.repository.BookRepository()
                                val episodes = detail.getEffectiveSeries()
                                val imgBaseUrl = com.batsd.jmcomict.data.api.ApiClientFactory.getImageBaseUrl()
                                episodes.forEach { eps ->
                                    var scrambleId = 220980
                                    var imageUrls = listOf<String>()
                                    repo.getChapterViewTemplate(eps.epsId).onSuccess { s ->
                                        scrambleId = s.scrambleId.toIntOrNull() ?: 220980
                                    }
                                    repo.getEpisodeDetail(eps.epsId).onSuccess { ep ->
                                        if (ep.images.isNotEmpty()) {
                                            imageUrls = ep.images.map { img ->
                                                "$imgBaseUrl/media/photos/${eps.epsId}/$img"
                                            }
                                        }
                                    }
                                    val fullEps = eps.copy().apply {
                                        pictureUrl = imageUrls
                                        pictureName = imageUrls.map { it.substringAfterLast("/") }
                                        pages = imageUrls.size
                                    }
                                    com.batsd.jmcomict.data.download.DownloadManager.downloadChapter(
                                        detail, fullEps, scrambleId, this
                                    )
                                }
                            }
                        }
                    }
                )
                }
            }
            is SubScreen.Reader -> {
                val readEpsId = (subScreen as? SubScreen.Reader)?.epsId ?: ""
                val localImages = com.batsd.jmcomict.data.download.DownloadManager.getLocalChapterImages(
                    bookViewModel.getCurrentBookId(), readEpsId
                )
                ReaderScreen(
                    episode = episodeDetail, currentPage = currentPage,
                    scrambleId = scrambleId, isLoading = bookIsLoading,
                    localImagePaths = localImages,
                    onBackClick = { subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None } },
                    onPreviousPage = { if (currentPage > 0) bookViewModel.setCurrentPage(currentPage - 1) },
                    onNextPage = { bookViewModel.setCurrentPage(currentPage + 1) },
                    onPageSelect = { bookViewModel.setCurrentPage(it) }
                )
            }
            is SubScreen.Category -> CategoryScreen(
                categories = categories, isLoading = categoryIsLoading,
                onBackClick = { subScreen = SubScreen.None },
                onCategoryClick = { cid, cname ->
                    categoryViewModel.clearBooks()
                    categoryViewModel.getBooksByCategory(cid)
                    subScreen = SubScreen.CategoryBooks(cid, cname)
                }
            )
            is SubScreen.CategoryBooks -> {
                val cid = screen.categoryId
                val cname = screen.categoryName
                CategoryBooksScreen(
                    categoryId = cid,
                    categoryName = cname,
                    categoryViewModel = categoryViewModel,
                    isLoading = categoryIsLoading,
                    onBackClick = { subScreen = SubScreen.Category },
                    onBookClick = { bookId ->
                        bookViewModel.getBookDetail(bookId)
                        previousSubScreen = subScreen
                        subScreen = SubScreen.BookDetail(bookId)
                    }
                )
            }
            is SubScreen.Login -> LoginScreen(
                onLoginClick = { u, p -> userViewModel.login(u, p) },
                onRegisterClick = {},
                isLoading = userIsLoading,
                error = userError
            )
            is SubScreen.Settings -> SettingsScreen(
                cdnName = cdnName,
                imageCdnName = imageCdnName,
                cdnIndex = apiUrlIndex,
                imageCdnIndex = cdnIndex,
                cdnCount = ApiClientFactory.getApiUrlCount(),
                imageCdnCount = ApiClientFactory.getCdnCount(),
                isDarkTheme = isDarkTheme,
                themeMode = themeMode,
                isLoggedIn = user?.isLogin == true,
                onBackClick = { subScreen = SubScreen.None },
                onSelectCdn = { index ->
                    ApiClientFactory.setApiUrlIndex(index)
                    cdnName = ApiClientFactory.getCurrentCdnName()
                    apiUrlIndex = index
                    prefs.setApiUrlIndex(index)
                },
                onSelectImageCdn = { index ->
                    ApiClientFactory.setImageCdnIndex(index)
                    imageCdnName = ApiClientFactory.getCurrentImageCdnName()
                    cdnIndex = index
                    prefs.setImageCdnIndex(index)
                },
                onSetThemeMode = onSetThemeMode,
                onLogoutClick = {
                    userViewModel.logout()
                    subScreen = SubScreen.None
                    selectedTab = MainTab.Home
                },
                onShowDisclaimer = onShowDisclaimer,
                onLineTestClick = { previousSubScreen = SubScreen.Settings; subScreen = SubScreen.LineTest },
                onAboutClick = { previousSubScreen = SubScreen.Settings; subScreen = SubScreen.About }
            )
            is SubScreen.LineTest -> LineTestScreen(
                currentApiUrlIndex = apiUrlIndex,
                currentImageCdnIndex = cdnIndex,
                onBackClick = { subScreen = SubScreen.Settings },
                onSelectApiCdn = { index ->
                    ApiClientFactory.setApiUrlIndex(index)
                    cdnName = ApiClientFactory.getCurrentCdnName()
                    apiUrlIndex = index
                    prefs.setApiUrlIndex(index)
                },
                onSelectImageCdn = { index ->
                    ApiClientFactory.setImageCdnIndex(index)
                    imageCdnName = ApiClientFactory.getCurrentImageCdnName()
                    cdnIndex = index
                    prefs.setImageCdnIndex(index)
                }
            )
            is SubScreen.About -> AboutScreen(
                versionName = versionName,
                onBackClick = { subScreen = SubScreen.Settings }
            )
            is SubScreen.Downloads -> DownloadsScreen(
                onBackClick = { subScreen = SubScreen.None },
                onBookClick = { bookId ->
                    bookViewModel.getBookDetail(bookId)
                    previousSubScreen = SubScreen.None
                    subScreen = SubScreen.BookDetail(bookId)
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
            is SubScreen.None -> {
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
        // Tab 方向：根据切换方向决定动画
        val tabOrder = listOf(MainTab.Home, MainTab.Search, MainTab.Favorites, MainTab.Profile)
        val previousTabIndex = remember { mutableIntStateOf(0) }
        LaunchedEffect(selectedTab) {
            previousTabIndex.intValue = tabOrder.indexOf(selectedTab).coerceAtLeast(0)
        }
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .padding(padding)
                .background(colorScheme.background),
            transitionSpec = {
                val currentIdx = tabOrder.indexOf(targetState).coerceAtLeast(0)
                val prevIdx = tabOrder.indexOf(initialState).coerceAtLeast(0)
                val slideRight = currentIdx > prevIdx
                if (slideRight) {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { -it / 4 })
                } else {
                    (fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutHorizontally(tween(200)) { it / 4 })
                }
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
                    initialQuery = searchQuery,
                    onClearHistory = { searchHistory = emptyList() },
                    onBackClick = { selectedTab = MainTab.Home; searchQuery = "" },
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
                        hasMore = bookViewModel.favoritesHasMore,
                        onBackClick = { selectedTab = MainTab.Home },
                        onBookClick = { id ->
                            bookViewModel.getBookDetail(id)
                            previousSubScreen = SubScreen.None
                            subScreen = SubScreen.BookDetail(id)
                        },
                        onRemoveFavorite = { id ->
                            bookViewModel.toggleFavorite(id)
                            bookViewModel.getFavorites()
                        },
                        onLoadMore = { bookViewModel.loadMoreFavorites() }
                    )
                }
                MainTab.Profile -> {
                    LaunchedEffect(Unit) { bookViewModel.getHistory() }
                    UserProfileScreen(
                    userName = user?.userName, userId = user?.uid,
                    level = user?.levelName?.takeIf { it.isNotEmpty() } ?: user?.title ?: "",
                    levelNumber = user?.level?.let { "LV.${it}" } ?: "",
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
                    onSettingsClick = { subScreen = SubScreen.Settings },
                    onDownloadsClick = { subScreen = SubScreen.Downloads }
                )
                }
            }
        }
    }
    } // close SubScreen.None
    } // close when(screen)
    } // close AnimatedContent
    } // close Box wrapper
}
