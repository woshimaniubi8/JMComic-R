package com.batsd.openjm.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * 分类信息
 * 注意: 真实 API 中 id 可能是 int(0) 或 string("1"), total_albums 可能是 int 或 string
 */
@Serializable
data class Category(
    @Serializable(with = StringOrIntSerializer::class)
    val id: String = "",
    
    val name: String = "",
    val slug: String = "",
    val type: String = "",
    
    /** API 返回 total_albums，可能是 int 或 string */
    @SerialName("total_albums")
    val totalAlbums: JsonElement? = null,
    
    /** 子分类(可选) */
    @SerialName("sub_categories")
    val subCategories: List<JsonElement> = emptyList()
) {
    /** 作品总数（兼容 int/string） */
    val total: Int
        get() = when (totalAlbums) {
            is JsonPrimitive -> totalAlbums.content.toIntOrNull() ?: 0
            else -> 0
        }
}

/**
 * 分类列表响应
 */
@Serializable
data class CategoryListResponse(
    @SerialName("code")
    val code: Int = 0,
    
    @SerialName("results")
    val results: List<Category> = emptyList()
)
