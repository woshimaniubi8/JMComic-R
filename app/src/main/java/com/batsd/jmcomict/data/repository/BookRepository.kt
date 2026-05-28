package com.batsd.jmcomict.data.repository

import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.api.ApiResponse
import com.batsd.jmcomict.data.api.BookListData
import com.batsd.jmcomict.data.api.CommentListData
import com.batsd.jmcomict.data.api.FavoriteListData
import com.batsd.jmcomict.data.api.PromoteSection
import com.batsd.jmcomict.data.api.JMComicApiService
import com.batsd.jmcomict.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BookRepository {
    private val apiService: JMComicApiService get() = ApiClientFactory.getApiService()

    suspend fun getLatest(page: String = "0"): Result<List<BookItem>> {
        return apiCall({ apiService.getLatest(page) }) { it.decryptAndParseList<BookItem>() }
    }

    suspend fun searchBooks(query: String, page: Int = 1, sort: String = "mr"): Result<List<BookItem>> {
        // 搜索 API 返回 {search_query, total, content: [...]}，不是裸数组
        return try {
            val response = apiService.searchBooks(query, sort, page)
            if (response.isSuccess()) {
                val data = response.decryptAndParse<BookListData>()
                if (data != null) Result.success(data.content)
                else Result.failure(Exception("搜索数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookDetail(bookId: String): Result<BookDetail> {
        return apiCall({ apiService.getBookDetail(bookId) }) { it.decryptAndParse<BookDetail>() }
    }

    suspend fun getEpisodeDetail(epsId: String): Result<BookEps> {
        return apiCall({ apiService.getEpisodeDetail(epsId) }) { it.decryptAndParse<BookEps>() }
    }

    /** 获取章节的 scramble_id — 从 chapter_view_template HTML 响应中用正则提取
     *  
     *  /chapter_view_template 端点返回 HTML (非 JSON)，其中包含 JavaScript:
     *    var scramble_id = 220980;
     * 
     *  对照 Qt 项目 ToolUtil.ParseBookEpsScramble():
     *    成功时提取 scramble_id，失败时默认返回 220980 (非 0)
     */
    suspend fun getChapterViewTemplate(epsId: String): Result<ScrambleData> {
        return try {
            val responseBody = apiService.getChapterViewTemplateRaw(epsId)
            val html = responseBody.string()
            android.util.Log.d("BookRepo", "chapter_view_template HTML length: ${html.length}")
            
            // 正则提取 scramble_id (对照 Qt 项目 ParseBookEpsScramble)
            val regex = Regex("""var scramble_id = (\w+)""")
            val matchResult = regex.find(html)
            val scrambleId = matchResult?.groupValues?.getOrNull(1) ?: "220980"
            android.util.Log.d("BookRepo", "Extracted scramble_id: $scrambleId")
            
            Result.success(ScrambleData(scrambleId = scrambleId))
        } catch (e: Exception) {
            android.util.Log.e("BookRepo", "Failed to get scramble_id, using fallback 220980", e)
            // 对照 Qt: except 时默认返回 220980
            Result.success(ScrambleData(scrambleId = "220980"))
        }
    }

    suspend fun toggleFavorite(bookId: String): Result<Unit> {
        return apiCallSimple { apiService.toggleFavorite(bookId) }
    }

    suspend fun toggleLike(bookId: String): Result<String> {
        return try {
            val client = ApiClientFactory.getOkHttpClientForWeb()
            val body = okhttp3.FormBody.Builder()
                .add("album_id", bookId)
                .add("vote", "likes")
                .build()
            val request = okhttp3.Request.Builder()
                .url("${ApiClientFactory.WEB_BASE_URL}ajax/vote_album")
                .post(body)
                .header("Origin", ApiClientFactory.WEB_BASE_URL.trimEnd('/'))
                .header("Referer", "${ApiClientFactory.WEB_BASE_URL.trimEnd('/')}/album/$bookId/")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .build()
            val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }
            val raw = response.body?.string() ?: ""
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val obj = json.decodeFromString<kotlinx.serialization.json.JsonObject>(raw)
            val msg = obj["msg"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            if (!msg.isNullOrEmpty()) Result.success(msg)
            else {
                val err = obj["errorMsg"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                Result.failure(Exception(err ?: "点赞失败"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFavorites(page: Int = 1, sort: String = "mr"): Result<List<BookItem>> {
        return try {
            val response = apiService.getFavorites(page, sort)
            if (response.isSuccess()) {
                val wrapper = response.decryptAndParse<FavoriteListData>()
                if (wrapper != null) Result.success(wrapper.list)
                else Result.failure(Exception("收藏数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getComments(bookId: String, page: String = "1"): Result<CommentListData> {
        return apiCall({ apiService.getComments(bookId = bookId, page = page) }) { it.decryptAndParse<CommentListData>() }
    }

    suspend fun postComment(bookId: String, content: String, replyTo: String = ""): Result<String> {
        return try {
            val response = apiService.postComment(bookId = bookId, content = content, replyTo = replyTo)
            if (!response.isSuccess()) return Result.failure(Exception(response.errorMessage()))
            val decrypted = response.decryptDataField()
            if (!decrypted.isNullOrEmpty()) {
                try {
                    val obj = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                        .decodeFromString<kotlinx.serialization.json.JsonObject>(decrypted)
                    val msg = obj["msg"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                        ?: obj["message"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                    if (!msg.isNullOrEmpty()) return Result.success(msg)
                } catch (_: Exception) {}
            }
            Result.success(response.message.ifEmpty { "评论已发送" })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getHistory(page: Int = 1): Result<List<BookItem>> {
        return try {
            val response = apiService.getHistory(page)
            if (response.isSuccess()) {
                // history API 返回 {list: [BookItem], total: N}
                val wrapper = response.decryptAndParse<FavoriteListData>()
                if (wrapper != null) Result.success(wrapper.list)
                else Result.failure(Exception("历史数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getWeekRecommend(page: Int = 0): Result<List<BookItem>> {
        return try {
            val response = apiService.getWeekRecommend(page)
            if (response.isSuccess()) {
                val sections = response.decryptAndParseList<PromoteSection>()
                if (sections != null && sections.isNotEmpty()) Result.success(sections.flatMap { it.content })
                else {
                    val data = response.decryptAndParse<BookListData>()
                    if (data != null) Result.success(data.content)
                    else Result.failure(Exception("解析失败"))
                }
            } else Result.failure(Exception(response.errorMessage()))
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun getPromote(page: String = "0"): Result<List<BookItem>> {
        return try {
            val response = apiService.getPromote(page)
            if (response.isSuccess()) {
                val sections = response.decryptAndParseList<PromoteSection>()
                if (sections != null) Result.success(sections.flatMap { it.content })
                else Result.failure(Exception("解析失败"))
            } else Result.failure(Exception(response.errorMessage()))
        } catch (e: Exception) { Result.failure(e) }
    }

    /** 获取首页分区（动态标签）+ 内容 */
    suspend fun getHomeSections(): Result<List<Pair<String, List<BookItem>>>> {
        return try {
            val response = apiService.getPromote("0")
            if (!response.isSuccess()) return Result.failure(Exception(response.errorMessage()))
            // 解析为数组 [{id, title, content: [BookItem]}]
            val sections = response.decryptAndParseList<PromoteSection>()
            if (sections != null && sections.isNotEmpty())
                Result.success(sections.map { it.title.ifEmpty { "最新" } to it.content })
            else Result.failure(Exception("数据为空"))
        } catch (e: Exception) { Result.failure(e) }
    }

    // === 辅助方法 ===

    private suspend fun <T> apiCall(
        call: suspend () -> ApiResponse,
        parse: (ApiResponse) -> T?
    ): Result<T> {
        return try {
            val response = call()
            if (response.isSuccess()) {
                val data = parse(response)
                if (data != null) {
                    Result.success(data)
                } else {
                    Result.failure(Exception("数据解析失败: ${response.data}".take(100)))
                }
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            android.util.Log.e("BookRepository", "API error", e)
            Result.failure(e)
        }
    }

    private suspend fun apiCallSimple(call: suspend () -> ApiResponse): Result<Unit> {
        return try {
            val response = call()
            if (response.isSuccess()) Result.success(Unit)
            else Result.failure(Exception(response.errorMessage()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
