package com.batsd.openjm.data.api

import com.batsd.openjm.data.model.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.ResponseBody
import retrofit2.http.*

/**
 * 禁漫天堂 移动端 API 接口定义
 * 基于 JMComic-qt 原项目逆向的真实 API 端点
 *
 * 所有 API 返回统一的 ApiResponse，data 字段为 JsonElement 以兼容
 * 真实 API 中 data 类型不一致的问题 (成功时为对象{}, 失败时为数组[])
 */
interface JMComicApiService {

    // ============ 预登录 (获取 Cookie) ============

    /** 访问首页获取初始 Cookie */
    @GET(".")
    suspend fun getHomePage(): ResponseBody

    /** 访问登录页获取 Cookie */
    @GET("login")
    suspend fun getLoginPage(): ResponseBody

    // ============ 用户相关 ============

    /** 用户登录 (POST form: username, password) */
    @FormUrlEncoded
    @POST("login")
    suspend fun login(
        @Field("username") username: String,
        @Field("password") password: String
    ): ApiResponse

    /** 用户注册 */
    @FormUrlEncoded
    @POST("signup")
    suspend fun register(
        @Field("username") username: String,
        @Field("password") password: String,
        @Field("email") email: String
    ): ApiResponse

    /** 每日签到 */
    @GET("daily")
    suspend fun dailyCheckIn(
        @Query("user_id") userId: String
    ): ApiResponse

    // ============ 漫画列表 ============

    /** 首页推荐/推广 */
    @GET("promote")
    suspend fun getPromote(
        @Query("page") page: String = "0"
    ): ApiResponse

    /** 最新更新 */
    @GET("latest")
    suspend fun getLatest(
        @Query("page") page: String = "0"
    ): ApiResponse

    /** 搜索漫画 */
    @GET("search")
    suspend fun searchBooks(
        @Query("search_query") query: String,
        @Query("o") sort: String = "mr",
        @Query("page") page: Int = 1
    ): ApiResponse

    // ============ 漫画详情 ============

    /** 获取漫画详情 */
    @GET("album")
    suspend fun getBookDetail(
        @Query("id") bookId: String
    ): ApiResponse

    /** 获取章节详情 */
    @GET("chapter")
    suspend fun getEpisodeDetail(
        @Query("id") epsId: String
    ): ApiResponse

    /** 获取章节视图模板 (scramble_id, 图片URL列表) 
     *  这是 Qt 项目中的 GetBookEpsScrambleReq2
     *  需要特殊的 header (GetHeader2), 由 ApiClientFactory 的 ScrambleInterceptor 处理
     *  
     *  注意：此端点返回 HTML 而非 JSON，因此使用 ResponseBody 获取原始响应
     *  在 BookRepository 中使用正则提取 scramble_id
     */
    @GET("chapter_view_template")
    suspend fun getChapterViewTemplateRaw(
        @Query("id") epsId: String,
        @Query("mode") mode: String = "vertical",
        @Query("page") page: String = "0",
        @Query("app_img_shunt") appImgShunt: String = "NaN"
    ): okhttp3.ResponseBody

    /** [已弃用] 获取章节视图模板 — 此端点返回 HTML，JSON 解析必然失败
     *  请使用 getChapterViewTemplateRaw() 代替 */
    @Deprecated("端点返回 HTML 而非 JSON，请使用 getChapterViewTemplateRaw()")
    @GET("chapter_view_template")
    suspend fun getChapterViewTemplate(
        @Query("id") epsId: String,
        @Query("mode") mode: String = "vertical",
        @Query("page") page: String = "0",
        @Query("app_img_shunt") appImgShunt: String = "NaN"
    ): ApiResponse

    // ============ 分类相关 ============

    /** 获取所有分类 */
    @GET("categories")
    suspend fun getCategories(): ApiResponse

    /** 按分类筛选漫画 */
    @GET("categories/filter")
    suspend fun getBooksByCategory(
        @Query("c") categoryId: String,
        @Query("page") page: Int = 1,
        @Query("o") sort: String = "mr"
    ): ApiResponse

    // ============ 收藏相关 ============

    /** 获取收藏列表 */
    @GET("favorite")
    suspend fun getFavorites(
        @Query("page") page: Int = 1,
        @Query("o") sort: String = "mr",
        @Query("folder_id") folderId: String = "0"
    ): ApiResponse

    /** 添加/移除收藏 (toggle) */
    @FormUrlEncoded
    @POST("favorite")
    suspend fun toggleFavorite(
        @Field("aid") bookId: String
    ): ApiResponse

    // ============ 点赞 ============

    /** 点赞/取消点赞 (vote=likes)，需 X-Requested-With 头 */
    @FormUrlEncoded
    @Headers("X-Requested-With: XMLHttpRequest")
    @POST("ajax/vote_album")
    suspend fun toggleLike(
        @Field("album_id") bookId: String,
        @Field("vote") vote: String = "likes"
    ): ResponseBody

    // ============ 评论相关 ============

    /** 获取评论列表 */
    @GET("forum")
    suspend fun getComments(
        @Query("mode") mode: String = "manhua",
        @Query("aid") bookId: String,
        @Query("page") page: String = "1"
    ): ApiResponse

    /** 发表评论 */
    @FormUrlEncoded
    @POST("comment")
    suspend fun postComment(
        @Field("comment") content: String,
        @Field("aid") bookId: String,
        @Field("comment_id") replyTo: String = ""
    ): ApiResponse

    // ============ 历史记录 ============

    /** 获取观看历史 */
    @GET("watch_list")
    suspend fun getHistory(
        @Query("page") page: Int = 1
    ): ApiResponse

    // ============ 周推荐 ============

    /** 获取周推荐列表 */
    @GET("week")
    suspend fun getWeekRecommend(
        @Query("page") page: Int = 0
    ): ApiResponse
}

// ==================== 响应数据模型 ====================

/** 内部 JSON 实例，用于解析 data 字段 */
@PublishedApi
internal val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

/**
 * 通用 API 响应包装
 * data 字段使用 JsonElement 以兼容真实 API 的不一致类型:
 * - 成功: {"code":200, "data":{...}}
 * - 失败: {"code":401, "data":[], "errorMsg":"..."} 
 */
@Serializable
data class ApiResponse(
    val code: Int = 0,
    val message: String = "",
    val errorMsg: String = "",
    val data: JsonElement? = null
) {
    fun isSuccess(): Boolean = code == 200

    /** 解密 data 为对象类型 T */
    inline fun <reified T> decryptAndParse(): T? {
        return try {
            val decrypted = decryptDataField() ?: return null
            android.util.Log.d("ApiResponse", "Parsing ${T::class.simpleName}: ${decrypted.take(300)}")
            apiJson.decodeFromString<T>(decrypted)
        } catch (e: Exception) {
            android.util.Log.e("ApiResponse", "Parse error for ${T::class.simpleName}", e)
            null
        }
    }

    /** 解密 data 为列表 */
    inline fun <reified T> decryptAndParseList(): List<T> {
        return try {
            val decrypted = decryptDataField() ?: return emptyList()
            android.util.Log.d("ApiResponse", "Parsing list of ${T::class.simpleName}: ${decrypted.take(300)}")
            apiJson.decodeFromString<List<T>>(decrypted)
        } catch (e: Exception) {
            android.util.Log.e("ApiResponse", "Parse list error for ${T::class.simpleName}", e)
            emptyList()
        }
    }

    /** 提取并解密 data 字段为 JSON 字符串 */
    @PublishedApi
    internal fun decryptDataField(): String? {
        if (data == null) return null
        return when (data) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                if (data.isString) {
                    ApiClientFactory.decryptData(data.content).ifEmpty { null }
                } else data.content
            }
            is kotlinx.serialization.json.JsonArray, is kotlinx.serialization.json.JsonObject -> {
                data.toString()
            }
            else -> null
        }
    }

    fun errorMessage(): String = errorMsg.ifEmpty { message.ifEmpty { "未知错误" } }
}

/** 登录成功后 data 字段的结构 */
@Serializable
data class LoginData(
    val uid: String = "",
    val username: String = "",
    val name: String = "",
    val token: String = ""
)

/** 解密后的登录响应 JSON (对应 Python ParseLogin2 返回值) */
@Serializable
data class LoginResult(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val emailverified: String = "",
    val photo: String = "",
    val fname: String = "",
    val gender: String = "",
    val message: String = "",
    val coin: Int = 0,
    @kotlinx.serialization.SerialName("album_favorites")
    val albumFavorites: Int = 0,
    val s: String = "",  // session token (AVS cookie)
    @kotlinx.serialization.SerialName("level_name")
    val levelName: String = "",
    val level: Int = 0,  // 整数类型!
    @kotlinx.serialization.SerialName("nextLevelExp")
    val nextLevelExp: Int = 0,
    val exp: String = "",  // 字符串类型
    val expPercent: Double = 0.0,
    val badges: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("album_favorites_max")
    val albumFavoritesMax: Int = 0,
    val jwttoken: String = ""
)

/** 漫画列表 data 结构 (真实 API: total + content) */
@Serializable
data class BookListData(
    val total: Int = 0,
    val content: List<BookItem> = emptyList()
)

/** 首页推广/排行响应: 可能是数组 [{id, title, content}] 或对象 {categories:[], content:[]} */
@Serializable
data class PromoteSection(
    val id: String = "",
    val title: String = "",
    val content: List<BookItem> = emptyList(),
    val categories: List<JsonElement>? = null
)

/** 评论列表 data 结构 */
@Serializable
data class CommentListData(
    val list: List<CommentInfo> = emptyList(),
    val total: Int = 0
)

/** /favorite API 解密后结构: {list, folder_list, total} */
@Serializable
data class FavoriteListData(
    val list: List<BookItem> = emptyList(),
    val total: String = "",
    val count: Int = 0,
    @kotlinx.serialization.SerialName("folder_list")
    val folderList: List<kotlinx.serialization.json.JsonElement> = emptyList()
)

/** /categories API 解密后的包装结构 */
@Serializable
data class CategoryWrapper(
    val categories: List<Category> = emptyList(),
    val blocks: List<kotlinx.serialization.json.JsonElement> = emptyList()
)
