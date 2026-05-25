package com.batsd.openjm.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * 漫画基本信息
 */
@Serializable
data class BookBaseInfo(
    @SerialName("book_id")
    val bookId: String = "",
    
    @SerialName("title")
    val title: String = "",
    
    @SerialName("book_url")
    val bookUrl: String = "",
    
    @SerialName("author")
    val author: String = "",
    
    @SerialName("cover")
    val cover: String = "",
    
    @SerialName("category")
    val category: List<String> = emptyList(),
    
    @SerialName("total_likes")
    val totalLikes: Int = 0,
    
    @SerialName("total_views")
    val totalViews: Int = 0
)

/**
 * 单个章节信息 (series 数组项 + /chapter API 响应)
 * 
 * /album API 的 series 项: {"id": "123", "name": "...", "sort": "1"}
 * /chapter API 的响应:   {"id": 1441524, "name": "...", "images": [...], "series_id": 0, ...}
 * 
 * 注意: /chapter API 返回的 id 是 int 类型，使用 StringOrIntSerializer 兼容
 */
@Serializable
data class BookEps(
    @Serializable(with = StringOrIntSerializer::class)
    val id: String = "",
    val name: String = "",
    val sort: String = "",
    // /chapter API 特有字段 (可选)
    val images: List<String> = emptyList(),
    @SerialName("series_id")
    val seriesIdRaw: kotlinx.serialization.json.JsonElement? = null,
    val tags: kotlinx.serialization.json.JsonElement? = null
) {
    val epsId: String get() = id
    val epsName: String get() = name
    /** 图片总数，从 chapter 或 chapter_view_template 填充 */
    var pages: Int = 0
    /** scramble_id, 从 chapter_view_template 获取，用于图片解密 */
    var scrambleId: Int = 0
    /** aid, 从 /chapter 的 series_id 或 chapter_view_template 获取 */
    var aid: Int get() = when (seriesIdRaw) {
        is kotlinx.serialization.json.JsonPrimitive -> seriesIdRaw.content.toIntOrNull() ?: 0
        else -> 0
    }
    set(value) { /* no-op, aid is computed */ }
    /** 图片URL列表 (已构建好的完整URL) */
    var pictureUrl: List<String> = emptyList()
    /** 图片文件名列表 */
    var pictureName: List<String> = emptyList()
}

/**
 * /chapter_view_template API 解密后的数据结构
 * 对应 Python 项目中 GetBookEpsScrambleReq2 的返回值
 */
@Serializable
data class ScrambleData(
    @kotlinx.serialization.SerialName("scramble_id")
    val scrambleId: String = "0",
    val aid: String = "0",
    val images: List<String> = emptyList(),
    @kotlinx.serialization.SerialName("img_type")
    val imgType: String = "jpg"
)

/**
 * 自定义序列化器：兼容 JSON 中的 int 和 string 类型，统一转为 String
 * 真实 API 中 id 字段可能是 int(1441531) 或 string("1441531")
 */
object StringOrIntSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("StringOrInt", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        return try {
            val element = (decoder as JsonDecoder).decodeJsonElement()
            when {
                element is JsonPrimitive -> element.content
                else -> element.toString()
            }
        } catch (e: Exception) {
            // 回退：如果 decoder 不是 JsonDecoder 或解析失败
            try {
                decoder.decodeString()
            } catch (_: Exception) {
                ""
            }
        }
    }
}

/** 安全字符串序列化器：兼容 Int/String/Null → String，用于 likes/total_views 等字段 */
object SafeStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("SafeString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        return try {
            val element = (decoder as JsonDecoder).decodeJsonElement()
            when {
                element is JsonPrimitive -> element.content
                element is JsonNull -> ""
                else -> element.toString()
            }
        } catch (e: Exception) {
            try { decoder.decodeString() } catch (_: Exception) { "" }
        }
    }
}

/**
 * 完整的漫画信息 (对应 /album API 解密后的 JSON)
 * 
 * 注意：真实 API 返回的字段类型不一致:
 * - id 可能是 Int 或 String → 使用 StringOrIntSerializer 兼容
 * - author 可能是 String 或 List<String> → 使用 JsonElement 兼容
 * - series 列表可能为空 → 代码中自动用 bookId 创建回退章节
 * - likes/totalViews 可能是 int/string/null → 使用 SafeStringSerializer 兼容
 */
@Serializable
data class BookDetail(
    @Serializable(with = StringOrIntSerializer::class)
    val id: String = "",
    val name: String = "",
    val author: JsonElement? = null,  // 可能是 String 或 Array
    val images: List<String> = emptyList(),
    val description: String? = null,
    @SerialName("total_views")
    @Serializable(with = SafeStringSerializer::class)
    val totalViews: String = "0",  // 默认值改为 "0" 而非 ""
    @Serializable(with = SafeStringSerializer::class)
    val likes: String = "0",  // 默认值改为 "0" 而非 ""
    val series: List<BookEps> = emptyList(),
    @SerialName("series_id")
    val seriesId: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("comment_total")
    val commentTotal: kotlinx.serialization.json.JsonElement? = null,
    val tags: List<String> = emptyList(),
    val works: List<String> = emptyList(),
    val actors: List<String> = emptyList(),
    @SerialName("related_list")
    val relatedList: List<BookItem> = emptyList(),
    val liked: Boolean = false,
    @SerialName("is_favorite")
    val isFavorite: Boolean = false
) {
    val title: String get() = name
    val bookId: String get() = id
    
    /** 解析 author 字段，兼容 String 和 List 两种格式 */
    val authorList: List<String>
        get() = when (author) {
            is JsonPrimitive -> listOf(author.content)
            is JsonArray -> author.map { 
                (it as? JsonPrimitive)?.content ?: it.toString() 
            }
            else -> emptyList()
        }
    
    val cover: String get() {
        val img = images.firstOrNull()
        if (!img.isNullOrEmpty()) return "media/photos/$id/$img"
        return "media/albums/${id}_3x4.jpg"
    }
    val totalLikes: Int get() = likes.toIntOrNull() ?: 0
    
    /** 评论总数 (兼容 int/string) */
    val commentCount: String get() = when (commentTotal) {
        is JsonPrimitive -> commentTotal.content
        else -> "0"
    }
    
    /** 系列ID (兼容 int/string) */
    val seriesIdStr: String get() = when (seriesId) {
        is JsonPrimitive -> seriesId.content
        else -> "0"
    }

    /**
     * 获取有效的章节列表
     * 对应 Qt 项目 ParseBookInfo2: 当 series 为空时，用 bookId 创建单章回退
     */
    fun getEffectiveSeries(): List<BookEps> {
        if (series.isNotEmpty()) return series
        // 回退：将 bookId 作为章节 ID，创建单章
        val fallback = BookEps(
            id = bookId,
            name = name,
            sort = "1"
        )
        return listOf(fallback)
    }
}

/**
 * 漫画列表项（用于列表展示）
 * 真实 API 字段: id, name, author, image, likes, total_views
 */
@Serializable
data class BookItem(
    @Serializable(with = StringOrIntSerializer::class)
    val id: String = "",
    val name: String = "",
    val author: String = "",
    val image: String = "",
    val likes: String = "",
    @SerialName("total_views")
    val totalViews: String = "",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val category: CategoryBrief? = null,
    @SerialName("category_sub")
    val categorySub: CategoryBrief? = null
) {
    /** 封面完整 URL: /media/albums/{id}_3x4.jpg */
    val coverUrl: String get() {
        if (image.isNotEmpty()) return image
        return "media/albums/${id}_3x4.jpg"
    }
    val title: String get() = name
    val bookId: String get() = id
}

@Serializable
data class CategoryBrief(
    val id: String = "",
    val title: String = ""
)

/**
 * 漫画列表响应
 */
@Serializable
data class BookListResponse(
    @SerialName("code")
    val code: Int = 0,
    
    @SerialName("message")
    val message: String = "",
    
    @SerialName("count")
    val count: Int = 0,
    
    @SerialName("limit")
    val limit: Int = 0,
    
    @SerialName("offset")
    val offset: Int = 0,
    
    @SerialName("results")
    val results: List<BookItem> = emptyList()
)
