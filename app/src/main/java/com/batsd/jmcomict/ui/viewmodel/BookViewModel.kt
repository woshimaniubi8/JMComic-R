package com.batsd.jmcomict.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batsd.jmcomict.data.local.PreferencesManager
import com.batsd.jmcomict.data.model.*
import com.batsd.jmcomict.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 漫画相关的 ViewModel
 */
class BookViewModel(
    private val bookRepository: BookRepository,
    private val prefsManager: PreferencesManager
) : ViewModel() {
    
    private val _bookList = MutableStateFlow<List<BookItem>>(emptyList())
    val bookList: StateFlow<List<BookItem>> = _bookList
    
    private val _bookDetail = MutableStateFlow<BookDetail?>(null)
    val bookDetail: StateFlow<BookDetail?> = _bookDetail
    
    private val _episodeDetail = MutableStateFlow<BookEps?>(null)
    val episodeDetail: StateFlow<BookEps?> = _episodeDetail
    
    private val _episodeImages = MutableStateFlow<List<String>>(emptyList())
    val episodeImages: StateFlow<List<String>> = _episodeImages
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _favorites = MutableStateFlow<List<BookItem>>(emptyList())
    val favorites: StateFlow<List<BookItem>> = _favorites

    private val _comments = MutableStateFlow<List<CommentInfo>>(emptyList())
    val comments: StateFlow<List<CommentInfo>> = _comments

    private val _commentCount = MutableStateFlow(0)
    val commentCount: StateFlow<Int> = _commentCount

    private val _history = MutableStateFlow<List<BookItem>>(emptyList())
    val history: StateFlow<List<BookItem>> = _history

    private val _homeSections = MutableStateFlow<List<Pair<String, List<BookItem>>>>(emptyList())
    val homeSections: StateFlow<List<Pair<String, List<BookItem>>>> = _homeSections

    /** 当前阅读页数 — 独立 StateFlow，确保 scrambleId/页码变更时触发 UI 更新 */
    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage

    /** 上次阅读的章节 ID */
    private val _lastReadChapterId = MutableStateFlow<String?>(null)
    val lastReadChapterId: StateFlow<String?> = _lastReadChapterId

    /** 评论发送结果 (success, message) */
    private val _commentResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val commentResult: StateFlow<Pair<Boolean, String>?> = _commentResult

    /** scramble_id — 独立 StateFlow，解决 BookEps.scrambleId 为 class body var 导致 StateFlow 不感知变化的问题
     *  初始值使用 220980 而非 0，确保即使 getChapterViewTemplate 尚未完成也能正确处理图片解密
     *  对照 Qt 项目 jm_config.py，220980 是最常见的 scrambleId 默认值
     */
    private val _scrambleId = MutableStateFlow(220980)
    val scrambleId: StateFlow<Int> = _scrambleId

    /** 当前正在查看的 bookId，用于保存/恢复阅读进度 */
    private var currentBookId: String = ""
    fun getCurrentBookId(): String = currentBookId
    /** 当前正在查看的 epsId */
    private var currentEpsId: String = ""
    
    fun searchBooks(query: String, page: Int = 1, sort: String = "mr") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            bookRepository.searchBooks(query, page, sort)
                .onSuccess { books ->
                    _bookList.value = books
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    fun getBookList(page: String = "0") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            bookRepository.getLatest(page)
                .onSuccess { books ->
                    _bookList.value = books
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    fun getBookDetail(bookId: String) {
        currentBookId = bookId
        _commentResult.value = null  // 清除上次评论结果
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // 清除旧数据，避免显示上一个漫画的详情（重要！）
            _bookDetail.value = null
            android.util.Log.d("BookVM", "============ 开始获取漫画详情: $bookId ============")

            // 已下载的漫画直接加载本地数据，无需网络
            val localDetail = com.batsd.jmcomict.data.download.DownloadManager.loadBookDetail(bookId)
            if (localDetail != null) {
                android.util.Log.i("BookVM", "✓ 从本地加载漫画详情: $bookId")
                _bookDetail.value = localDetail
                val series = localDetail.getEffectiveSeries()
                _lastReadChapterId.value = series.firstOrNull { eps ->
                    prefsManager.getReadingHistory(bookId, eps.epsId) > 0
                }?.epsId ?: series.firstOrNull()?.epsId
                _isLoading.value = false
                return@launch
            }

            bookRepository.getBookDetail(bookId)
                .onSuccess { detail ->
                    android.util.Log.d("BookVM", "✓ 漫画详情获取成功")
                    android.util.Log.d("BookVM", "  id=${detail.id}, name=${detail.name}")
                    android.util.Log.d("BookVM", "  likes='${detail.likes}' (totalLikes=${detail.totalLikes})")
                    android.util.Log.d("BookVM", "  totalViews='${detail.totalViews}'")
                    android.util.Log.d("BookVM", "  description='${detail.description?.take(50) ?: "null"}'")
                    android.util.Log.d("BookVM", "  series.size=${detail.series.size}")
                    _bookDetail.value = detail
                    // 查找上次阅读的章节
                    val series = detail.getEffectiveSeries()
                    _lastReadChapterId.value = series.firstOrNull { eps ->
                        prefsManager.getReadingHistory(bookId, eps.epsId) > 0
                    }?.epsId ?: series.firstOrNull()?.epsId
                }
                .onFailure { exception ->
                    android.util.Log.e("BookVM", "✗ 网络获取失败: ${exception.message}")
                    _error.value = exception.message
                }

            _isLoading.value = false
            android.util.Log.d("BookVM", "============ 漫画详情加载完成 ============")
        }
    }

    fun getEpisodeDetail(epsId: String) {
        currentEpsId = epsId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _scrambleId.value = 220980
            val imgBaseUrl = com.batsd.jmcomict.data.api.ApiClientFactory.getImageBaseUrl()

            // 检查是否有本地下载的图片 — 有则直接使用，跳过网络
            if (currentBookId.isNotEmpty()) {
                val localImages = com.batsd.jmcomict.data.download.DownloadManager.getLocalChapterImages(currentBookId, epsId)
                if (localImages.isNotEmpty()) {
                    android.util.Log.i("BookVM", "使用本地图片加载章节: $epsId, ${localImages.size}张")
                    val fallbackEps = com.batsd.jmcomict.data.model.BookEps(id = epsId).apply {
                        pictureUrl = localImages
                        pages = localImages.size
                    }
                    _episodeDetail.value = fallbackEps
                    val savedPage = if (currentBookId.isNotEmpty()) {
                        prefsManager.getReadingHistory(currentBookId, epsId)
                    } else 0
                    _currentPage.value = savedPage
                    _isLoading.value = false
                    return@launch
                }
            }

            // 1. 获取章节信息 (/chapter API 已包含 images 列表)
            bookRepository.getEpisodeDetail(epsId)
                .onSuccess { episode ->
                    // /chapter API 已返回 images 列表，直接使用
                    if (episode.images.isNotEmpty()) {
                        episode.pictureUrl = episode.images.map { img ->
                            "$imgBaseUrl/media/photos/$epsId/$img"
                        }
                        episode.pictureName = episode.images
                        episode.pages = episode.images.size
                        _episodeImages.value = episode.pictureUrl
                    }
                    // 检查是否有本地下载的图片
                    if (currentBookId.isNotEmpty()) {
                        val localImages = com.batsd.jmcomict.data.download.DownloadManager.getLocalChapterImages(currentBookId, epsId)
                        if (localImages.isNotEmpty()) {
                            episode.pictureUrl = localImages
                            episode.pages = localImages.size
                            _episodeImages.value = localImages
                            android.util.Log.i("BookVM", "使用本地图片: ${localImages.size}张")
                        }
                    }
                    // 先设置 episode
                    _episodeDetail.value = episode

                    // 恢复阅读进度
                    val savedPage = if (currentBookId.isNotEmpty()) {
                        prefsManager.getReadingHistory(currentBookId, epsId)
                    } else 0
                    _currentPage.value = savedPage
                    android.util.Log.d("BookVM", "恢复阅读进度: book=$currentBookId, eps=$epsId, page=$savedPage")

                    // 2. 获取 scramble_id (图片解密用) — chapter_view_template 返回 HTML
                    bookRepository.getChapterViewTemplate(epsId)
                        .onSuccess { scramble ->
                            val sid = scramble.scrambleId.toIntOrNull() ?: 220980
                            android.util.Log.d("BookVM", "scramble_id=$sid for epsId=$epsId")
                            // 使用独立 StateFlow 确保 UI 感知变化
                            _scrambleId.value = sid
                            // 同时更新 episode 中的 scrambleId（供下载等场景使用）
                            episode.scrambleId = sid
                            if (episode.images.isEmpty()) {
                                // 回退：从 chapter_view_template 的 images 构建图片 URL
                                val urls = scramble.images.map { img ->
                                    if (img.startsWith("http")) img
                                    else "$imgBaseUrl/$img"
                                }
                                episode.pictureUrl = urls
                                episode.pictureName = scramble.images.map { it.substringAfterLast("/") }
                                episode.pages = urls.size
                                _episodeImages.value = urls
                            }
                            _episodeDetail.value = episode
                        }
                        .onFailure { exception ->
                            android.util.Log.w("BookVM", "获取scramble_id失败(非关键): ${exception.message}")
                            // 即使获取失败，也使用默认 scrambleId 并触发 StateFlow
                            _scrambleId.value = 220980
                            episode.scrambleId = 220980
                            _episodeDetail.value = episode
                        }
                }
                .onFailure { exception ->
                    _error.value = exception.message
                    android.util.Log.e("BookVM", "获取章节详情失败: $epsId", exception)
                }

            _isLoading.value = false
        }
    }

    fun toggleFavorite(bookId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            bookRepository.toggleFavorite(bookId)
                .onSuccess { msg -> onResult(true, msg) }
                .onFailure { onResult(false, it.message ?: "操作失败") }
        }
    }

    /** 收藏（返回服务器消息） */
    private val _favResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val favResult: StateFlow<Pair<Boolean, String>?> = _favResult

    fun toggleFavoriteWithResult(bookId: String) {
        viewModelScope.launch {
            _favResult.value = null
            bookRepository.toggleFavorite(bookId)
                .onSuccess { _favResult.value = true to "操作成功" }
                .onFailure { _favResult.value = false to (it.message ?: "操作失败") }
        }
    }

    fun toggleLike(bookId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            bookRepository.toggleLike(bookId)
                .onSuccess { msg -> onResult(true, msg) }
                .onFailure { onResult(false, it.message ?: "点赞失败") }
        }
    }

    /** 收藏当前页码 */
    private var _favoritesPage = 1
    private var _favoritesHasMore = true
    private var _favoritesLoadingMore = false
    val favoritesHasMore: Boolean get() = _favoritesHasMore

    fun getFavorites(page: Int = 1, sort: String = "mr") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _favoritesPage = page
            bookRepository.getFavorites(page, sort)
                .onSuccess { books ->
                    _favorites.value = if (page == 1) books else _favorites.value + books
                    // 如果返回的条数少于20（每页预期条数），说明没有更多了
                    _favoritesHasMore = books.size >= 20
                    android.util.Log.d("BookVM", "getFavorites: page=$page, got=${books.size}, " +
                        "accumulated=${_favorites.value.size}, hasMore=$_favoritesHasMore")
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
            _favoritesLoadingMore = false
        }
    }

    fun loadMoreFavorites(sort: String = "mr") {
        if (!_favoritesHasMore || _favoritesLoadingMore) return
        _favoritesLoadingMore = true
        getFavorites(_favoritesPage + 1, sort)
    }

    fun setCurrentPage(page: Int) {
        _currentPage.value = page
        // 保存阅读进度（每个漫画独立）
        if (currentBookId.isNotEmpty() && currentEpsId.isNotEmpty()) {
            prefsManager.saveReadingHistory(currentBookId, currentEpsId, page)
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun getComments(bookId: String, page: String = "0") {
        android.util.Log.d("BookVM", "getComments: bookId=$bookId, page=$page")
        viewModelScope.launch {
            if (page == "0") {
                _isLoading.value = true
                _comments.value = emptyList()  // 切换漫画时清除旧评论
                _commentCount.value = 0
            }
            bookRepository.getComments(bookId, page)
                .onSuccess { data ->
                    val newList = if (page == "0") data.list else _comments.value + data.list
                    android.util.Log.d("BookVM", "getComments OK: page=$page, items=${data.list.size}, " +
                        "total=${data.total}, first=${data.list.firstOrNull()?.addtime ?: "N/A"}, " +
                        "newList.size=${newList.size}")
                    // 强制发射新值（即使空列表也要触发重组，解决无评论时加载动画不消失）
                    _comments.value = ArrayList(newList)
                    _commentCount.value = data.total
                }
                .onFailure { e ->
                    android.util.Log.e("BookVM", "getComments FAILED: page=$page, error=${e.message}")
                    _error.value = e.message
                }
            _isLoading.value = false
        }
    }

    fun postComment(bookId: String, content: String) {
        viewModelScope.launch {
            bookRepository.postComment(bookId, content)
                .onSuccess { msg ->
                    // 从当前CDN刷新评论列表（评论发在哪就从哪拉，确保能看到刚发的评论）
                    bookRepository.getCommentsFromCurrentCdn(bookId, "0")
                        .onSuccess { data ->
                            _comments.value = ArrayList(data.list)
                            _commentCount.value = data.total
                        }
                    _commentResult.value = true to msg
                }
                .onFailure { _commentResult.value = false to (it.message ?: "发送失败") }
        }
    }

    fun getHistory(page: Int = 1) {
        viewModelScope.launch {
            _isLoading.value = true
            bookRepository.getHistory(page)
                .onSuccess { books ->
                    val hidden = prefsManager.getHiddenHistoryItems()
                    _history.value = books.filter { it.id !in hidden }
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun hideHistoryItem(bookId: String) {
        prefsManager.hideHistoryItem(bookId)
        _history.value = _history.value.filter { it.id != bookId }
    }

    fun getWeekRecommend(page: Int = 0) {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            bookRepository.getWeekRecommend(page)
                .onSuccess { _bookList.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun getPromote(page: String = "0") {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            bookRepository.getPromote(page)
                .onSuccess { _bookList.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun getHomeSections() {
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            // 同时获取最新和分区
            bookRepository.getLatest("0")
                .onSuccess { latest -> _bookList.value = latest }
            bookRepository.getHomeSections()
                .onSuccess { sections ->
                    // 第一个标签="最新上传"，后续为服务器分区
                    val all = listOf("最新上传" to _bookList.value) + sections
                    _homeSections.value = all
                    _bookList.value = all.first().second
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    /** 直接设置漫画列表（供分类筛选等场景使用） */
    fun setBooks(books: List<BookItem>) {
        _bookList.value = books
    }
}
