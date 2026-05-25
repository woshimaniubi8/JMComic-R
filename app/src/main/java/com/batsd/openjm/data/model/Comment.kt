package com.batsd.openjm.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 评论信息 — 对齐真实 API (/forum) 的字段名
 *
 * 真实 API 字段:
 *   username: 用户名 (显示用)
 *   content: 评论内容 (含HTML标签，需strip)
 *   photo: 头像文件名 (拼接 /media/users/{photo})
 *   addtime: 时间字符串 (如 "May 24, 2026")
 *   likes: 点赞数 (字符串)
 *   expinfo.level_name: 用户等级名
 *   spoiler: 是否剧透
 *   name: 关联的漫画JM编码 (非评论者名！)
 */
@Serializable
data class CommentInfo(
    val username: String = "",
    val nickname: String = "",
    val content: String = "",
    val photo: String = "",
    val addtime: String = "",
    val likes: String = "0",
    val spoiler: String = "0",
    val name: String = "",  // 关联漫画JM编码，非用户名
    @SerialName("expinfo")
    val expInfo: ExpInfo? = null,
    @SerialName("CID")
    val cid: String = "",
    @SerialName("UID")
    val uid: String = "",
    @SerialName("AID")
    val aid: String = "",
    @SerialName("parent_CID")
    val parentCid: String = "0"
) {
    /** 显示名：优先 username，其次 nickname */
    val displayName: String get() = username.ifEmpty { nickname.ifEmpty { "匿名" } }

    /** 等级名 */
    val levelName: String get() = expInfo?.levelName ?: ""

    /** 头像完整URL */
    val avatarUrl: String get() = if (photo.isNotEmpty()) "media/users/$photo" else ""

    /** 去掉HTML标签后的纯文本内容 */
    val contentText: String get() = content
        .replace(Regex("<[^>]*>"), "")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .trim()
}

@Serializable
data class ExpInfo(
    @SerialName("level_name")
    val levelName: String = "",
    val level: Int = 0
)
