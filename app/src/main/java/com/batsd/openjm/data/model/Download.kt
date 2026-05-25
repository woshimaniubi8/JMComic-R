package com.batsd.openjm.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 下载任务信息
 */
@Serializable
data class DownloadTask(
    @SerialName("id")
    val id: String = "",
    
    @SerialName("book_id")
    val bookId: String = "",
    
    @SerialName("book_title")
    val bookTitle: String = "",
    
    @SerialName("eps_id")
    val epsId: String = "",
    
    @SerialName("eps_name")
    val epsName: String = "",
    
    val progress: Int = 0,
    
    val status: DownloadStatus = DownloadStatus.PENDING,
    
    val createdAt: Long = System.currentTimeMillis()
) {
    override fun hashCode(): Int = id.hashCode()
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DownloadTask) return false
        return id == other.id
    }
}

/**
 * 下载状态
 */
enum class DownloadStatus {
    PENDING,      // 等待中
    DOWNLOADING,  // 下载中
    PAUSED,       // 暂停
    COMPLETED,    // 已完成
    FAILED        // 失败
}
