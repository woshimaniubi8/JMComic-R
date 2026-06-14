package com.batsd.jmcomict.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
    object Update : SubScreen()
    object CompatibilitySettings : SubScreen()
    data class Comment(val bookId: String) : SubScreen()
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
    var searchHistory by remember { mutableStateOf(prefs.getSearchHistory()) }
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
    val toast = LocalToast.current
    val scope = rememberCoroutineScope()
    val versionName = remember {
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        } catch (e: Exception) {
            ""
        }
    }
    val updateViewModel = remember { com.batsd.jmcomict.ui.viewmodel.UpdateViewModel(prefs = prefs) }
    val latestRelease by updateViewModel.release.collectAsState()
    val updateIsChecking by updateViewModel.isChecking.collectAsState()
    val updateError by updateViewModel.error.collectAsState()
    val userIsLoading by userViewModel.isLoading.collectAsState()
    val userError by userViewModel.error.collectAsState()

    // 低内存设备弹窗
    var showLowRamDialog by remember { mutableStateOf(false) }
    var pendingReaderEpsId by remember { mutableStateOf("") }
    var autoUpdateCheckStarted by remember { mutableStateOf(false) }
    var autoUpdateDialogConsumed by remember { mutableStateOf(false) }
    var showAutoUpdateDialog by remember { mutableStateOf(false) }

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
            is SubScreen.CompatibilitySettings -> subScreen = SubScreen.Settings
            is SubScreen.Comment -> subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None }
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
    var autoDailyCheckIn by remember { mutableStateOf(prefs.getAutoDailyCheckIn()) }
    // 启动时清理旧APK并自动检测版本更新
    LaunchedEffect(Unit) {
        updateViewModel.cleanupDownloadedApks(context)
        if (updateViewModel.autoCheckEnabled) {
            autoUpdateCheckStarted = true
            updateViewModel.checkForUpdates()
        }
    }
    LaunchedEffect(latestRelease, updateIsChecking, updateError) {
        val release = latestRelease
        if (
            autoUpdateCheckStarted &&
            !autoUpdateDialogConsumed &&
            !updateIsChecking &&
            updateError == null &&
            release != null &&
            hasNewVersion(release.tag_name, versionName)
        ) {
            showAutoUpdateDialog = true
            autoUpdateDialogConsumed = true
        }
    }
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            MainTab.Home -> if (bookList.isEmpty()) bookViewModel.getHomeSections()
            MainTab.Search -> searchHistory = prefs.getSearchHistory()
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
        if (user?.isLogin == true) {
            userViewModel.autoDailyCheckInIfNeeded(showSkippedResult = true) { _, msg ->
                scope.launch { toast(msg) }
            }
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
                (initialState is SubScreen.BookDetail && targetState !is SubScreen.Reader && targetState !is SubScreen.Comment) ||
                (initialState is SubScreen.Comment) ||
                (initialState is SubScreen.LineTest && targetState is SubScreen.Settings) ||
                (initialState is SubScreen.About && targetState is SubScreen.Settings) ||
                (initialState is SubScreen.CompatibilitySettings && targetState is SubScreen.Settings) ||
                (initialState is SubScreen.Update && targetState is SubScreen.Settings) ||
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
                        if (checkLowRamDevice(context) && !hasCompatEnabled(prefs)) {
                            pendingReaderEpsId = ep.epsId
                            showLowRamDialog = true
                        } else {
                            bookViewModel.getEpisodeDetail(ep.epsId)
                            previousSubScreen = subScreen
                            subScreen = SubScreen.Reader(ep.epsId)
                        }
                    },
                    onStartReading = { epsId ->
                        if (checkLowRamDevice(context) && !hasCompatEnabled(prefs)) {
                            pendingReaderEpsId = epsId
                            showLowRamDialog = true
                        } else {
                            bookViewModel.getEpisodeDetail(epsId)
                            previousSubScreen = subScreen
                            subScreen = SubScreen.Reader(epsId)
                        }
                    },
                    onFavoriteClick = { bookViewModel.toggleFavorite(id) },
                    onAddFavoriteClick = { cb -> bookViewModel.toggleFavorite(id, cb) },
                    onToggleLike = { cb -> bookViewModel.toggleLike(id, cb) },
                    onLoadComments = { bookViewModel.getComments(id) },
                    onLoadMoreComments = { page -> bookViewModel.getComments(id, page.toString()) },
                    onPostComment = { text, _ -> bookViewModel.postComment(id, text) },
                    commentResult = commentResult,
                    onCommentClick = {
                        previousSubScreen = subScreen
                        subScreen = SubScreen.Comment(id)
                    },
                    isDownloaded = dlBooks.any { it.bookId == id },
                    isDownloading = dlProgress.values.any {
                        it.bookId == id && it.status == com.batsd.jmcomict.data.download.DownloadTaskStatus.DOWNLOADING
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
                            val serverEpsIds = comicDetail.getEffectiveSeries().map { it.epsId }
                            com.batsd.jmcomict.data.download.DownloadManager.hasUpdates(id, serverEpsIds)
                        } else false
                    } ?: false,
                    onDownloadClick = {
                        android.util.Log.d("AppNav", "Download book: $id")
                        if (detail != null) {
                            com.batsd.jmcomict.data.download.DownloadManager.downloadBook(detail)
                        }
                    },
                    onRefresh = {
                        bookViewModel.getBookDetail(id)
                        bookViewModel.getComments(id)
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
                onAboutClick = { previousSubScreen = SubScreen.Settings; subScreen = SubScreen.About },
                onUpdateClick = { subScreen = SubScreen.Update },
                autoCheckUpdate = updateViewModel.autoCheckEnabled,
                onToggleAutoCheckUpdate = { updateViewModel.setAutoCheckEnabled(it) },
                autoCheckInEnabled = autoDailyCheckIn,
                onSetAutoCheckIn = { enabled ->
                    autoDailyCheckIn = enabled
                    prefs.setAutoDailyCheckIn(enabled)
                    if (enabled) {
                        userViewModel.autoDailyCheckInIfNeeded(force = true) { _, msg ->
                            scope.launch { toast(msg) }
                        }
                    } else {
                        scope.launch { toast("已关闭自动签到") }
                    }
                },
                onCompatibilityClick = { subScreen = SubScreen.CompatibilitySettings }
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
            is SubScreen.Update -> UpdateScreen(
                updateViewModel = updateViewModel,
                currentVersion = versionName,
                onBackClick = { subScreen = SubScreen.Settings }
            )
            is SubScreen.CompatibilitySettings -> CompatibilitySettingsScreen(
                compatDownsample = prefs.getCompatImageDownsample(),
                compatRGB565 = prefs.getCompatRGB565(),
                compatCacheLimit = prefs.getCompatCacheLimit(),
                onToggleCompatDownsample = { prefs.setCompatImageDownsample(it) },
                onToggleCompatRGB565 = { prefs.setCompatRGB565(it) },
                onToggleCompatCacheLimit = { prefs.setCompatCacheLimit(it) },
                onBackClick = { subScreen = SubScreen.Settings }
            )
            is SubScreen.Comment -> CommentScreen(
                comments = comments,
                commentCount = commentCount,
                isLoggedIn = user?.isLogin == true,
                isPosting = false,
                onBackClick = { subScreen = previousSubScreen.also { previousSubScreen = SubScreen.None } },
                onLoadComments = { bookViewModel.getComments((screen as SubScreen.Comment).bookId) },
                onLoadMoreComments = { page -> bookViewModel.getComments((screen as SubScreen.Comment).bookId, page.toString()) },
                onPostComment = { text, _ -> bookViewModel.postComment((screen as SubScreen.Comment).bookId, text) }
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
    val tabOrder = listOf(MainTab.Home, MainTab.Search, MainTab.Favorites, MainTab.Profile)
    val pagerState = rememberPagerState(
        pageCount = { tabOrder.size },
        initialPage = 0
    )
    val navSelectedTab = tabOrder.getOrNull(pagerState.targetPage) ?: selectedTab

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
                        selected = navSelectedTab == item.screen,
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
        // 使用 HorizontalPager 实现连续平滑滑动切换 Tab
        // selectedTab（底部导航栏点击）→ 同步 Pager
        LaunchedEffect(selectedTab) {
            val targetPage = tabOrder.indexOf(selectedTab).coerceAtLeast(0)
            if (targetPage != pagerState.settledPage || pagerState.currentPageOffsetFraction != 0f) {
                pagerState.animateScrollToPage(targetPage)
            }
        }
        // Pager 滑动完成后再同步 selectedTab，避免动画途中 currentPage 回写导致卡在中间页
        LaunchedEffect(pagerState.settledPage) {
            if (pagerState.settledPage in tabOrder.indices) {
                selectedTab = tabOrder[pagerState.settledPage]
            }
        }
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(colorScheme.background)
        ) { page ->
            when (tabOrder[page]) {
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
                    activeQuery = searchQuery,
                    hasMore = bookViewModel.searchHasMore,
                    onLoadMore = { bookViewModel.loadMoreSearch() },
                    onClearQuery = {
                        searchQuery = ""
                        bookViewModel.setBooks(emptyList())
                    },
                    onClearHistory = {
                        prefs.clearSearchHistory()
                        searchHistory = emptyList()
                    },
                    onDeleteHistory = { query ->
                        prefs.removeSearchHistory(query)
                        searchHistory = prefs.getSearchHistory()
                    },
                    onBackClick = { selectedTab = MainTab.Home; searchQuery = "" },
                    onSearchClick = { query ->
                        searchQuery = query.trim()
                        prefs.addSearchHistory(query)
                        searchHistory = prefs.getSearchHistory()
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
                    },
                    onRefresh = { if (searchQuery.isNotEmpty()) bookViewModel.searchBooks(searchQuery) }
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
                        onLoadMore = { bookViewModel.loadMoreFavorites() },
                        onRefresh = { bookViewModel.getFavorites() }
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

    // ===== 低内存设备提示弹窗 =====
    if (showLowRamDialog) {
        AlertDialog(
            onDismissRequest = { showLowRamDialog = false },
            icon = { Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("内存不足") },
            text = {
                Text("当前设备内存较低，浏览漫画图片可能导致闪退。\n\n建议前往 「设置 → 兼容设置」开启图片内存优化后再阅读。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showLowRamDialog = false
                    bookViewModel.getEpisodeDetail(pendingReaderEpsId)
                    previousSubScreen = subScreen
                    subScreen = SubScreen.Reader(pendingReaderEpsId)
                }) { Text("仍然进入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showLowRamDialog = false
                    subScreen = SubScreen.CompatibilitySettings
                }) { Text("去设置") }
            }
        )
    }

    val autoUpdateRelease = latestRelease
    if (showAutoUpdateDialog && autoUpdateRelease != null) {
        AlertDialog(
            onDismissRequest = { showAutoUpdateDialog = false },
            icon = { Icon(Icons.Default.NewReleases, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("发现新版本") },
            text = {
                Text("发现新版本 ${autoUpdateRelease.tag_name}，当前版本 v${versionName.ifEmpty { "?" }}。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showAutoUpdateDialog = false
                    subScreen = SubScreen.Update
                }) { Text("立即查看") }
            },
            dismissButton = {
                TextButton(onClick = { showAutoUpdateDialog = false }) { Text("稍后") }
            }
        )
    }

    } // close Box wrapper
}

private fun hasNewVersion(latestTag: String, currentVersion: String): Boolean {
    val latest = latestTag.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
    val current = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
    if (latest.isEmpty() || current.isEmpty()) return latestTag != "v$currentVersion"
    return compareVersionList(latest, current) > 0
}

private fun compareVersionList(a: List<Int>, b: List<Int>): Int {
    val maxLen = maxOf(a.size, b.size)
    for (i in 0 until maxLen) {
        val va = a.getOrElse(i) { 0 }
        val vb = b.getOrElse(i) { 0 }
        if (va != vb) return va - vb
    }
    return 0
}

/**
 * 检测是否为低内存设备（总 RAM ≤ 2GB 或可用 ≤ 200MB）
 */
private fun checkLowRamDevice(context: android.content.Context): Boolean {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        ?: return false
    val memInfo = android.app.ActivityManager.MemoryInfo()
    am.getMemoryInfo(memInfo)
    return memInfo.totalMem <= 1536L * 1024 * 1024
            || memInfo.availMem <= 200L * 1024 * 1024
}

/**
 * 检查是否已开启任一兼容优化
 */
private fun hasCompatEnabled(prefs: PreferencesManager): Boolean {
    return prefs.getCompatImageDownsample()
            || prefs.getCompatRGB565()
            || prefs.getCompatCacheLimit()
}
