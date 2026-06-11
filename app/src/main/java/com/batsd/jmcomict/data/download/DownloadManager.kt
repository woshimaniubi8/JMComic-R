package com.batsd.jmcomict.data.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.model.BookDetail
import com.batsd.jmcomict.data.model.BookEps
import com.batsd.jmcomict.data.repository.BookRepository
import com.batsd.jmcomict.utils.ImageDescrambler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    val scrambleId: Int = 0,
    val totalPages: Int = pageCount,
    val sourceImageUrls: List<String> = emptyList(),
    val status: DownloadTaskStatus = DownloadTaskStatus.COMPLETED,
    val error: String? = null
)

/**
 * 下载任务状态
 */
@Serializable
enum class DownloadTaskStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED
}

data class ChapterDownloadProgress(
    val epsId: String,
    val epsName: String,
    val currentPage: Int,
    val totalPages: Int,
    val status: DownloadTaskStatus,
    val error: String? = null,
    val bookId: String = ""
)

/**
 * 漫画下载管理器
 * 负责将漫画章节下载到本地存储，包括图片解密和断点续传。
 */
object DownloadManager {

    private lateinit var baseDir: File
    private var appContext: Context? = null
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = kotlinx.coroutines.flow.MutableStateFlow<Map<String, ChapterDownloadProgress>>(emptyMap())
    val downloadProgress: kotlinx.coroutines.flow.StateFlow<Map<String, ChapterDownloadProgress>> = _downloadProgress

    /** 整体下载进度 Flow */
    private val _overallProgress = kotlinx.coroutines.flow.MutableStateFlow(Pair(0, 0))
    val overallProgress: kotlinx.coroutines.flow.StateFlow<Pair<Int, Int>> = _overallProgress

    private val _downloadedBooks = kotlinx.coroutines.flow.MutableStateFlow<List<DownloadedBook>>(emptyList())
    val downloadedBooks: kotlinx.coroutines.flow.StateFlow<List<DownloadedBook>> = _downloadedBooks

    private val jobs = mutableMapOf<String, Job>()

    fun init(context: Context) {
        appContext = context.applicationContext
        baseDir = File(context.filesDir, "downloads").also { it.mkdirs() }
        loadIndex()
    }

    /** 获取某本书的下载进度: (已下载页数, 总页数) */
    fun getBookProgress(bookId: String): Pair<Int, Int> {
        val book = _downloadedBooks.value.find { it.bookId == bookId }
        val progressByEps = _downloadProgress.value.values
            .filter { it.bookId == bookId }
            .associateBy { it.epsId }

        var done = 0
        var total = 0
        val counted = mutableSetOf<String>()

        book?.chapters.orEmpty().forEach { chapter ->
            val progress = progressByEps[chapter.epsId]
            done += progress?.currentPage ?: chapter.pageCount
            total += progress?.totalPages?.takeIf { it > 0 }
                ?: chapter.totalPages.takeIf { it > 0 }
                ?: chapter.pageCount
            counted += chapter.epsId
        }

        progressByEps.values.filter { it.epsId !in counted }.forEach { progress ->
            done += progress.currentPage
            total += progress.totalPages
        }

        return done to total
    }

    /** 获取某个章节的本地图片路径列表 */
    fun getLocalChapterImages(bookId: String, epsId: String): List<String> {
        return _downloadedBooks.value
            .find { it.bookId == bookId }
            ?.chapters?.find { it.epsId == epsId }
            ?.imageLocalPaths
            ?.filter { File(it).exists() }
            ?: emptyList()
    }

    /** 获取已下载的漫画信息 */
    fun getDownloadedBook(bookId: String): DownloadedBook? =
        _downloadedBooks.value.find { it.bookId == bookId }

    /** 标记某个章节为下载中（用于立即触发 UI 动画） */
    fun markDownloading(epsId: String, epsName: String, bookId: String = "", totalPages: Int = 0) {
        _downloadProgress.value = _downloadProgress.value + (taskKey(bookId, epsId) to
            ChapterDownloadProgress(epsId, epsName, 0, totalPages, DownloadTaskStatus.DOWNLOADING, bookId = bookId))
        updateOverallProgress()
    }

    /** 检查已下载的漫画是否有新章节更新 */
    fun hasUpdates(bookId: String, serverEpsIds: List<String>): Boolean {
        val localEpsIds = getLocalEpsIds(bookId)
        return serverEpsIds.any { it !in localEpsIds }
    }

    /** 获取本地已完整下载的 epsId 集合 */
    fun getLocalEpsIds(bookId: String): Set<String> {
        return _downloadedBooks.value.find { it.bookId == bookId }
            ?.chapters
            ?.filter { it.status == DownloadTaskStatus.COMPLETED }
            ?.map { it.epsId }
            ?.toSet()
            ?: emptySet()
    }

    /** 保存完整 BookDetail JSON 到本地 */
    private fun saveBookDetailJson(bookId: String, detail: BookDetail) {
        try {
            val detailFile = File(bookDir(bookId), "detail.json")
            detailFile.writeText(json.encodeToString(detail))
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "保存BookDetail失败", e)
        }
    }

    /** 从本地加载 BookDetail JSON */
    fun loadBookDetail(bookId: String): BookDetail? {
        return try {
            val detailFile = File(bookDir(bookId), "detail.json")
            if (detailFile.exists()) {
                json.decodeFromString<BookDetail>(detailFile.readText())
            } else null
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "加载BookDetail失败", e)
            null
        }
    }

    /** 后台下载整本漫画。已完成章节会跳过，未完成章节会从本地已有图片后继续。 */
    fun downloadBook(detail: BookDetail) {
        val bookId = detail.id
        if (bookId.isBlank()) return
        if (jobs[bookId]?.isActive == true) return

        markDownloading(
            epsId = detail.getEffectiveSeries().firstOrNull()?.epsId ?: bookId,
            epsName = detail.title,
            bookId = bookId
        )

        val job = managerScope.launch {
            saveBookDetailJson(bookId, detail)
            ensureBookRecord(detail)

            val repo = BookRepository()
            val episodes = detail.getEffectiveSeries()
            if (episodes.isEmpty()) {
                failBook(bookId, "没有可下载章节")
                return@launch
            }

            for (episode in episodes) {
                if (!isActive) break
                if (isChapterDownloaded(bookId, episode.epsId)) continue

                val prepared = prepareEpisode(repo, episode)
                if (prepared == null) {
                    val failed = DownloadedChapter(
                        epsId = episode.epsId,
                        epsName = episode.epsName.ifEmpty { detail.title },
                        sort = episode.sort,
                        status = DownloadTaskStatus.FAILED,
                        error = "章节信息获取失败"
                    )
                    upsertChapter(bookId, failed)
                    continue
                }

                downloadChapterInternal(detail, prepared.first, prepared.second)
            }

            jobs.remove(bookId)
            updateOverallProgress()
        }
        jobs[bookId] = job
    }

    /** 继续下载本地未完成任务。 */
    fun resumeBookDownload(bookId: String) {
        val detail = loadBookDetail(bookId)
        if (detail != null) {
            downloadBook(detail)
        } else {
            failBook(bookId, "缺少本地漫画详情，无法继续下载")
        }
    }

    /**
     * 下载单个章节（兼容旧调用；实际任务由全局下载作用域托管）。
     */
    fun downloadChapter(
        detail: BookDetail,
        episode: BookEps,
        scrambleId: Int,
        scope: CoroutineScope? = null
    ) {
        val bookId = detail.id
        val epsId = episode.epsId
        if (bookId.isBlank() || epsId.isBlank()) return
        if (jobs[taskKey(bookId, epsId)]?.isActive == true) return

        val job = managerScope.launch {
            saveBookDetailJson(bookId, detail)
            ensureBookRecord(detail)
            downloadChapterInternal(detail, episode, scrambleId)
            jobs.remove(taskKey(bookId, epsId))
        }
        jobs[taskKey(bookId, epsId)] = job
    }

    /** 检查是否已下载任意本地数据 */
    fun isBookDownloaded(bookId: String): Boolean =
        _downloadedBooks.value.any { it.bookId == bookId }

    fun isChapterDownloaded(bookId: String, epsId: String): Boolean =
        _downloadedBooks.value.find { it.bookId == bookId }
            ?.chapters?.any { it.epsId == epsId && it.status == DownloadTaskStatus.COMPLETED } == true

    /** 删除下载 */
    fun deleteDownload(bookId: String) {
        jobs[bookId]?.cancel()
        jobs.remove(bookId)
        jobs.filterKeys { it.startsWith("$bookId:") }.values.forEach { it.cancel() }
        jobs.keys.removeAll { it.startsWith("$bookId:") }

        val dir = bookDir(bookId)
        val deleted = dir.deleteRecursively()
        android.util.Log.i("DownloadManager", "删除下载: bookId=$bookId, dir=${dir.absolutePath}, success=$deleted")
        _downloadedBooks.value = _downloadedBooks.value.filter { it.bookId != bookId }
        _downloadProgress.value = _downloadProgress.value.filterValues { it.bookId != bookId }
        saveIndex()
        updateOverallProgress()
    }

    /** 取消下载 */
    fun cancelDownload(epsId: String) {
        jobs.filterKeys { it.endsWith(":$epsId") || it == epsId }.values.forEach { it.cancel() }
        jobs.keys.removeAll { it.endsWith(":$epsId") || it == epsId }
        _downloadProgress.value = _downloadProgress.value.filterValues { it.epsId != epsId }
        updateOverallProgress()
    }

    private suspend fun prepareEpisode(repo: BookRepository, episode: BookEps): Pair<BookEps, Int>? {
        val epsId = episode.epsId
        if (epsId.isBlank()) return null

        val scrambleId = repo.getChapterViewTemplate(epsId)
            .getOrNull()
            ?.scrambleId
            ?.toIntOrNull()
            ?: 220980

        val chapter = repo.getEpisodeDetail(epsId).getOrNull() ?: return null
        val imageUrls = chapter.images.mapNotNull { imageName ->
            buildImageUrl(epsId, imageName)
        }
        if (imageUrls.isEmpty()) return null

        val prepared = episode.copy(
            id = episode.epsId.ifEmpty { chapter.epsId },
            name = episode.epsName.ifEmpty { chapter.epsName },
            sort = episode.sort.ifEmpty { chapter.sort }
        ).apply {
            pictureUrl = imageUrls
            pictureName = imageUrls.map { it.substringAfterLast("/") }
            pages = imageUrls.size
            this.scrambleId = scrambleId
        }
        return prepared to scrambleId
    }

    private fun buildImageUrl(epsId: String, imageName: String): String? {
        val raw = imageName.trim()
        if (raw.isEmpty()) return null
        if (raw.startsWith("http")) return raw

        val base = ApiClientFactory.getImageBaseUrl().trimEnd('/')
        val path = raw.trimStart('/')
        return if (path.startsWith("media/")) {
            "$base/$path"
        } else {
            "$base/media/photos/$epsId/$path"
        }
    }

    private suspend fun downloadChapterInternal(
        detail: BookDetail,
        episode: BookEps,
        scrambleId: Int
    ) = withContext(Dispatchers.IO) {
        val bookId = detail.id
        val epsId = episode.epsId
        val images = episode.pictureUrl
        val totalPages = images.size
        val taskId = taskKey(bookId, epsId)

        if (images.isEmpty()) {
            upsertChapter(
                bookId,
                DownloadedChapter(
                    epsId = epsId,
                    epsName = episode.epsName,
                    sort = episode.sort,
                    status = DownloadTaskStatus.FAILED,
                    error = "无图片"
                )
            )
            _downloadProgress.value = _downloadProgress.value + (taskId to
                ChapterDownloadProgress(epsId, episode.epsName, 0, 0, DownloadTaskStatus.FAILED, "无图片", bookId))
            return@withContext
        }

        val dir = chapterDir(bookId, epsId)
        val client = getOkHttpClient()
        var lastError: String? = null

        updateChapterProgress(bookId, episode, scrambleId, totalPages, images, DownloadTaskStatus.DOWNLOADING)

        for ((index, url) in images.withIndex()) {
            if (!kotlin.coroutines.coroutineContext.isActive) break

            val file = pageFile(dir, index)
            if (file.exists() && file.length() > 0) {
                updateChapterProgress(bookId, episode, scrambleId, totalPages, images, DownloadTaskStatus.DOWNLOADING)
                continue
            }

            try {
                val request = Request.Builder().url(url).build()
                val bytes = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                    response.body?.bytes() ?: throw Exception("空响应体")
                }

                val opts = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    ?: throw Exception("图片解码失败")
                val num = ImageDescrambler.getNumFromUrl(scrambleId, url)
                val descrambled = ImageDescrambler.descramble(src, num)

                val tempFile = File(file.parentFile, "${file.name}.tmp")
                FileOutputStream(tempFile).use { out ->
                    descrambled.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                if (tempFile.exists()) {
                    if (file.exists()) file.delete()
                    tempFile.renameTo(file)
                }

                if (descrambled !== src) descrambled.recycle()
                src.recycle()

                updateChapterProgress(bookId, episode, scrambleId, totalPages, images, DownloadTaskStatus.DOWNLOADING)
            } catch (e: Exception) {
                lastError = "第${index + 1}页下载失败: ${e.message ?: "未知错误"}"
                android.util.Log.e("DownloadManager", "下载图片失败: $url", e)
            }
        }

        val localPaths = collectLocalPagePaths(dir, totalPages)
        val status = if (localPaths.size == totalPages) DownloadTaskStatus.COMPLETED else DownloadTaskStatus.FAILED
        val error = if (status == DownloadTaskStatus.COMPLETED) null
            else lastError ?: "已下载 ${localPaths.size}/$totalPages 页，可继续下载"

        val coverLocalPath = getDownloadedBook(bookId)?.coverLocalPath?.takeIf { it.isNotEmpty() }
            ?: downloadCover(detail)
        if (coverLocalPath.isNotEmpty()) updateCover(bookId, coverLocalPath)

        val chapter = DownloadedChapter(
            epsId = epsId,
            epsName = episode.epsName,
            sort = episode.sort,
            pageCount = localPaths.size,
            imageLocalPaths = localPaths,
            scrambleId = scrambleId,
            totalPages = totalPages,
            sourceImageUrls = images,
            status = status,
            error = error
        )
        upsertChapter(bookId, chapter)

        _downloadProgress.value = _downloadProgress.value + (taskId to
            ChapterDownloadProgress(epsId, episode.epsName, localPaths.size, totalPages, status, error, bookId))
        updateOverallProgress()
    }

    private fun updateChapterProgress(
        bookId: String,
        episode: BookEps,
        scrambleId: Int,
        totalPages: Int,
        sourceImageUrls: List<String>,
        status: DownloadTaskStatus
    ) {
        val dir = chapterDir(bookId, episode.epsId)
        val localPaths = collectLocalPagePaths(dir, totalPages)
        val chapter = DownloadedChapter(
            epsId = episode.epsId,
            epsName = episode.epsName,
            sort = episode.sort,
            pageCount = localPaths.size,
            imageLocalPaths = localPaths,
            scrambleId = scrambleId,
            totalPages = totalPages,
            sourceImageUrls = sourceImageUrls,
            status = status
        )
        upsertChapter(bookId, chapter)
        _downloadProgress.value = _downloadProgress.value + (taskKey(bookId, episode.epsId) to
            ChapterDownloadProgress(episode.epsId, episode.epsName, localPaths.size, totalPages, status, bookId = bookId))
        updateOverallProgress()
    }

    private fun ensureBookRecord(detail: BookDetail) {
        val existing = getDownloadedBook(detail.id)
        if (existing != null) return
        _downloadedBooks.value = _downloadedBooks.value + DownloadedBook(
            bookId = detail.id,
            title = detail.title,
            author = detail.authorList.joinToString(", "),
            coverLocalPath = "",
            description = detail.description ?: "",
            tags = detail.tags,
            actors = detail.actors,
            chapters = emptyList()
        )
        saveIndex()
    }

    private fun updateCover(bookId: String, coverLocalPath: String) {
        _downloadedBooks.value = _downloadedBooks.value.map { book ->
            if (book.bookId == bookId && book.coverLocalPath.isEmpty()) {
                book.copy(coverLocalPath = coverLocalPath)
            } else book
        }
        saveIndex()
    }

    private fun upsertChapter(bookId: String, chapter: DownloadedChapter) {
        _downloadedBooks.value = _downloadedBooks.value.map { book ->
            if (book.bookId != bookId) return@map book
            val chapters = book.chapters.toMutableList()
            chapters.removeAll { it.epsId == chapter.epsId }
            chapters.add(chapter)
            chapters.sortBy { it.sort.toIntOrNull() ?: Int.MAX_VALUE }
            book.copy(chapters = chapters)
        }
        saveIndex()
    }

    private fun failBook(bookId: String, message: String) {
        _downloadProgress.value = _downloadProgress.value + (taskKey(bookId, bookId) to
            ChapterDownloadProgress(bookId, "", 0, 0, DownloadTaskStatus.FAILED, message, bookId))
        updateOverallProgress()
    }

    /** 更新整体下载进度 */
    private fun updateOverallProgress() {
        var done = 0
        var total = 0
        _downloadedBooks.value.forEach { book ->
            val progress = getBookProgress(book.bookId)
            done += progress.first
            total += progress.second
        }
        _overallProgress.value = done to total
    }

    private fun getOkHttpClient(): okhttp3.OkHttpClient =
        appContext?.let { ApiClientFactory.getOkHttpClient(it) } ?: okhttp3.OkHttpClient()

    /** 获取某本书的下载目录 */
    private fun bookDir(bookId: String): File = File(baseDir, bookId).also { it.mkdirs() }

    /** 获取章节图片目录 */
    private fun chapterDir(bookId: String, epsId: String): File =
        File(bookDir(bookId), epsId).also { it.mkdirs() }

    private fun pageFile(dir: File, index: Int): File =
        File(dir, "page_${(index + 1).toString().padStart(5, '0')}.png")

    private fun collectLocalPagePaths(dir: File, totalPages: Int): List<String> {
        return (0 until totalPages).mapNotNull { index ->
            pageFile(dir, index).takeIf { it.exists() && it.length() > 0 }?.absolutePath
        }
    }

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
                val books = json.decodeFromString<List<DownloadedBook>>(text)
                _downloadedBooks.value = books.map { book ->
                    book.copy(chapters = book.chapters.map { chapter ->
                        if (chapter.status == DownloadTaskStatus.DOWNLOADING) {
                            chapter.copy(
                                status = DownloadTaskStatus.FAILED,
                                error = "上次下载中断，可继续下载"
                            )
                        } else {
                            chapter
                        }
                    })
                }
                saveIndex()
            }
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "加载索引失败", e)
            _downloadedBooks.value = emptyList()
        }
        updateOverallProgress()
    }

    /** 下载并保存封面图片 */
    private fun downloadCover(detail: BookDetail): String {
        try {
            val coverUrl = ApiClientFactory.fullImageUrl(detail.cover)
            val request = Request.Builder().url(coverUrl).build()
            val bytes = getOkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                response.body?.bytes() ?: return ""
            }
            val coverFile = File(bookDir(detail.id), "cover.jpg")
            coverFile.writeBytes(bytes)
            return coverFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("DownloadManager", "下载封面失败", e)
            return ""
        }
    }

    private fun taskKey(bookId: String, epsId: String): String =
        if (bookId.isBlank()) epsId else "$bookId:$epsId"
}
