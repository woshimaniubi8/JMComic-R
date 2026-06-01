package com.batsd.jmcomict.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.model.*
import com.batsd.jmcomict.utils.ImageDescrambler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/**
 * 下载的漫画数据
 */
@Serializable
data class DownloadedBook(
    val bookId: String = "",
    val title: String = "",
    val author: String = "",
    val coverLocalPath: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    val chapters: List<DownloadedChapter> = emptyList(),
    val downloadTime: Long = System.currentTimeMillis()
)

@Serializable
data class DownloadedChapter(
    val epsId: String = "",
    val epsName: String = "",
    val sort: String = "",
    val pageCount: Int = 0,
    val imageLocalPaths: List<String> = emptyList(),
    val scrambleId: Int = 0
)

/**
 * 下载任务状态
 */
enum class DownloadTaskStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

data class ChapterDownloadProgress(
    val epsId: String,
    val epsName: String,
    val currentPage: Int,
    val totalPages: Int,
    val status: DownloadTaskStatus,
    val error: String? = null
)

/**
 * 漫画下载管理器
 * 负责将漫画章节下载到本地存储，包括图片解密
 */
object DownloadManager {

    private lateinit var baseDir: File
    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val _downloadProgress = MutableStateFlow<Map<String, ChapterDownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, ChapterDownloadProgress>> = _downloadProgress

    /** 整体下载进度 Flow */
    private val _overallProgress = MutableStateFlow(Pair(0, 0))
    val overallProgress: StateFlow<Pair<Int, Int>> = _overallProgress

    private val _downloadedBooks = MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: StateFlow<List<DownloadedBook>> = _downloadedBooks

    private var jobs = mutableMapOf<String, Job>()

    /** 获取某本书的下载进度: (已下载页数, 总页数) */
    fun getBookProgress(bookId: String): Pair<Int, Int> {
        val chapters = _downloadedBooks.value.find { it.bookId == bookId }?.chapters ?: emptyList()
        val running = _downloadProgress.value.values.filter { it.status == DownloadTaskStatus.DOWNLOADING }
        var done = chapters.sumOf { it.pageCount }
        var total = done
        for (p in running) {
            total += p.totalPages
            done += p.currentPage
        }
        return done to total
    }

    /** 获取某个章节的本地图片路径列表 */
    fun getLocalChapterImages(bookId: String, epsId: String): List<String> {
        return _downloadedBooks.value
            .find { it.bookId == bookId }
            ?.chapters?.find { it.epsId == epsId }
            ?.imageLocalPaths ?: emptyList()
    }

    /** 获取已下载的漫画信息 */
    fun getDownloadedBook(bookId: String): DownloadedBook? =
        _downloadedBooks.value.find { it.bookId == bookId }

    /** 标记某个章节为下载中（用于立即触发UI动画） */
    fun markDownloading(epsId: String, epsName: String) {
        _downloadProgress.value = _downloadProgress.value + (epsId to
            ChapterDownloadProgress(epsId, epsName, 0, 1, DownloadTaskStatus.DOWNLOADING))
    }

    /** 检查已下载的漫画是否有新章节更新 */
    fun hasUpdates(bookId: String, serverEpsIds: List<String>): Boolean {
        val localChapters = _downloadedBooks.value.find { it.bookId == bookId }?.chapters ?: return false
        val localEpsIds = localChapters.map { it.epsId }.toSet()
        return serverEpsIds.any { it !in localEpsIds }
    }

    /** 获取本地已下载的 epsId 集合 */
    fun getLocalEpsIds(bookId: String): Set<String> {
        return _downloadedBooks.value.find { it.bookId == bookId }
            ?.chapters?.map { it.epsId }?.toSet() ?: emptySet()
    }

    /** 保存完整 BookDetail JSON 到本地 */
    private fun saveBookDetailJson(bookId: String, detail: BookDetail) {
        try {
            val detailFile = File(bookDir(bookId), "detail.json")
            detailFile.writeText(Json { ignoreUnknownKeys = true; prettyPrint = true }.encodeToString(detail))
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "保存BookDetail失败", e)
        }
    }

    /** 从本地加载 BookDetail JSON */
    fun loadBookDetail(bookId: String): BookDetail? {
        return try {
            val detailFile = File(bookDir(bookId), "detail.json")
            if (detailFile.exists()) {
                Json { ignoreUnknownKeys = true }.decodeFromString<BookDetail>(detailFile.readText())
            } else null
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "加载BookDetail失败", e)
            null
        }
    }

    /** 更新整体下载进度 */
    private fun updateOverallProgress() {
        val chapters = _downloadedBooks.value.sumOf { it.chapters.sumOf { c -> c.pageCount } }
        val running = _downloadProgress.value.values.filter { it.status == DownloadTaskStatus.DOWNLOADING }
        var done = chapters
        var total = chapters
        for (p in running) {
            total += p.totalPages
            done += p.currentPage
        }
        _overallProgress.value = done to total
    }

    fun init(context: Context) {
        appContext = context.applicationContext
        baseDir = File(context.filesDir, "downloads").also { it.mkdirs() }
        loadIndex()
    }

    private fun getOkHttpClient(): okhttp3.OkHttpClient {
        return okhttp3.OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    /** 获取某本书的下载目录 */
    private fun bookDir(bookId: String): File = File(baseDir, bookId).also { it.mkdirs() }

    /** 获取章节图片目录 */
    private fun chapterDir(bookId: String, epsId: String): File =
        File(bookDir(bookId), epsId).also { it.mkdirs() }

    /** 索引文件路径 */
    private val indexFile: File get() = File(baseDir, "index.json")

    /** 保存索引 */
    private fun saveIndex() {
        try {
            indexFile.writeText(json.encodeToString(_downloadedBooks.value))
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "保存索引失败", e)
        }
    }

    /** 加载索引 */
    private fun loadIndex() {
        try {
            if (indexFile.exists()) {
                val text = indexFile.readText()
                _downloadedBooks.value = json.decodeFromString<List<DownloadedBook>>(text)
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "加载索引失败", e)
            _downloadedBooks.value = emptyList()
        }
    }

    /** 检查是否已下载 */
    fun isBookDownloaded(bookId: String): Boolean =
        _downloadedBooks.value.any { it.bookId == bookId }

    fun isChapterDownloaded(bookId: String, epsId: String): Boolean =
        _downloadedBooks.value.find { it.bookId == bookId }
            ?.chapters?.any { it.epsId == epsId } == true

    /** 删除下载 */
    fun deleteDownload(bookId: String) {
        bookDir(bookId).deleteRecursively()
        _downloadedBooks.value = _downloadedBooks.value.filter { it.bookId != bookId }
        saveIndex()
    }

    /**
     * 下载单个章节（需提前获取好图片URL）
     */
    fun downloadChapter(
        detail: BookDetail,
        episode: BookEps,
        scrambleId: Int,
        scope: CoroutineScope
    ) {
        val bookId = detail.id
        val epsId = episode.epsId
        if (bookId.isBlank() || epsId.isBlank()) return

        // 如果已在下载中则跳过
        val existing = _downloadProgress.value[epsId]
        if (existing?.status == DownloadTaskStatus.DOWNLOADING) return

        val job = scope.launch(Dispatchers.IO) {
            try {
                val images = episode.pictureUrl
                if (images.isEmpty()) {
                    _downloadProgress.value = _downloadProgress.value + (epsId to
                        ChapterDownloadProgress(epsId, episode.epsName, 0, 0, DownloadTaskStatus.FAILED, "无图片"))
                    return@launch
                }

                val totalPages = images.size
                _downloadProgress.value = _downloadProgress.value + (epsId to
                    ChapterDownloadProgress(epsId, episode.epsName, 0, totalPages, DownloadTaskStatus.DOWNLOADING))

                val dir = chapterDir(bookId, epsId)
                val localPaths = mutableListOf<String>()
                val client = getOkHttpClient()

                for ((index, url) in images.withIndex()) {
                    if (!isActive) break
                    try {
                        val request = Request.Builder().url(url).build()
                        val response = client.newCall(request).execute()
                        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                        val bytes = response.body?.bytes() ?: throw Exception("空响应体")
                        response.close()

                        // 解密图片
                        val opts = BitmapFactory.Options().apply {
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                        val num = ImageDescrambler.getNumFromUrl(scrambleId, url)
                        val descrambled = ImageDescrambler.descramble(src ?: continue, num)

                        // 保存为 PNG（无损）
                        val filename = "page_${(index + 1).toString().padStart(5, '0')}.png"
                        val file = File(dir, filename)
                        FileOutputStream(file).use { out ->
                            descrambled.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        descrambled.recycle()
                        src?.recycle()
                        localPaths.add(file.absolutePath)

                        _downloadProgress.value = _downloadProgress.value + (epsId to
                            ChapterDownloadProgress(epsId, episode.epsName, index + 1, totalPages, DownloadTaskStatus.DOWNLOADING))
                        updateOverallProgress()
                    } catch (e: Exception) {
                        android.util.Log.e("DownloadManager", "下载图片失败: $url", e)
                    }
                }

                if (localPaths.isNotEmpty()) {
                    // 保存封面
                    val coverLocalPath = downloadCover(detail)
                    // 更新索引
                    val downloadedChapter = DownloadedChapter(
                        epsId = epsId,
                        epsName = episode.epsName,
                        sort = episode.sort,
                        pageCount = localPaths.size,
                        imageLocalPaths = localPaths,
                        scrambleId = scrambleId
                    )

                    val existingBook = _downloadedBooks.value.find { it.bookId == bookId }
                    if (existingBook != null) {
                        val updatedChapters = existingBook.chapters.toMutableList()
                        updatedChapters.removeAll { it.epsId == epsId }
                        updatedChapters.add(downloadedChapter)
                        updatedChapters.sortBy { it.sort.toIntOrNull() ?: 0 }
                        _downloadedBooks.value = _downloadedBooks.value.map {
                            if (it.bookId == bookId) it.copy(chapters = updatedChapters)
                            else it
                        }
                    } else {
                        // 首次下载时保存完整 BookDetail
                        saveBookDetailJson(bookId, detail)
                        val newBook = DownloadedBook(
                            bookId = bookId,
                            title = detail.title,
                            author = detail.authorList.joinToString(", "),
                            coverLocalPath = coverLocalPath,
                            description = detail.description ?: "",
                            tags = detail.tags,
                            actors = detail.actors,
                            chapters = listOf(downloadedChapter)
                        )
                        _downloadedBooks.value = _downloadedBooks.value + newBook
                    }
                    saveIndex()
                    updateOverallProgress()

                    _downloadProgress.value = _downloadProgress.value + (epsId to
                        ChapterDownloadProgress(epsId, episode.epsName, totalPages, totalPages, DownloadTaskStatus.COMPLETED))
                } else {
                    _downloadProgress.value = _downloadProgress.value + (epsId to
                        ChapterDownloadProgress(epsId, episode.epsName, 0, totalPages, DownloadTaskStatus.FAILED, "所有图片下载失败"))
                }
            } catch (e: Exception) {
                android.util.Log.e("DownloadManager", "下载章节失败: $epsId", e)
                _downloadProgress.value = _downloadProgress.value + (epsId to
                    ChapterDownloadProgress(epsId, episode.epsName, 0, 0, DownloadTaskStatus.FAILED, e.message))
            }
        }
        jobs[epsId] = job
    }

    /** 下载并保存封面图片 */
    private fun downloadCover(detail: BookDetail): String {
        try {
            val coverUrl = ApiClientFactory.fullImageUrl(detail.cover)
            val request = Request.Builder().url(coverUrl).build()
            val response = getOkHttpClient().newCall(request).execute()
            val bytes = response.body?.bytes() ?: return ""
            response.close()
            val coverFile = File(bookDir(detail.id), "cover.jpg")
            coverFile.writeBytes(bytes)
            return coverFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "下载封面失败", e)
            return ""
        }
    }

    /** 取消下载 */
    fun cancelDownload(epsId: String) {
        jobs[epsId]?.cancel()
        jobs.remove(epsId)
        _downloadProgress.value = _downloadProgress.value - epsId
    }
}
