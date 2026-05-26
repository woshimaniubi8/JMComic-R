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

    /** scramble_id — 独立 StateFlow，解决 BookEps.scrambleId 为 class body var 导致 StateFlow 不感知变化的问题
     *  初始值使用 220980 而非 0，确保即使 getChapterViewTemplate 尚未完成也能正确处理图片解密
     *  对照 Qt 项目 jm_config.py，220980 是最常见的 scrambleId 默认值
     */
    private val _scrambleId = MutableStateFlow(220980)
    val scrambleId: StateFlow<Int> = _scrambleId

    /** 当前正在查看的 bookId，用于保存/恢复阅读进度 */
    private var currentBookId: String = ""
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
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            // 清除旧数据，避免显示上一个漫画的详情（重要！）
            _bookDetail.value = null
            android.util.Log.d("BookVM", "============ 开始获取漫画详情: $bookId ============")

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
                    _error.value = exception.message
                    android.util.Log.e("BookVM", "✗ 获取漫画详情失败: $bookId", exception)
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
            _scrambleId.value = 220980  // 重置为默认值，而非 0，确保初期显示时已有合理的解密参数
            val imgBaseUrl = com.batsd.jmcomict.data.api.ApiClientFactory.getImageBaseUrl()

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
                    // 先设置 episode（scrambleId 尚未获取）
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
                .onSuccess { onResult(true, "操作成功") }
                .onFailure { onResult(false, it.message ?: "操作失败") }
        }
    }

    fun toggleLike(bookId: String, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            bookRepository.toggleLike(bookId)
                .onSuccess { msg -> onResult(true, msg) }
                .onFailure { onResult(false, it.message ?: "点赞失败") }
        }
    }

    fun getFavorites(page: Int = 1, sort: String = "mr") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            bookRepository.getFavorites(page, sort)
                .onSuccess { books -> _favorites.value = books }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
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

    fun getComments(bookId: String, page: String = "1") {
        viewModelScope.launch {
            if (page == "1") _isLoading.value = true
            bookRepository.getComments(bookId, page)
                .onSuccess { data ->
                    _comments.value = if (page == "1") data.list else _comments.value + data.list
                    _commentCount.value = data.total
                }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun postComment(bookId: String, content: String) {
        viewModelScope.launch {
            bookRepository.postComment(bookId, content)
                .onSuccess { getComments(bookId) }
                .onFailure { _error.value = it.message }
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
