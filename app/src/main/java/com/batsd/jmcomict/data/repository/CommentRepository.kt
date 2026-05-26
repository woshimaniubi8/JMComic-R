package com.batsd.jmcomict.data.repository

import com.batsd.jmcomict.data.api.CommentListData
import com.batsd.jmcomict.data.api.JMComicApiService

class CommentRepository(private val apiService: JMComicApiService) {

    suspend fun getComments(bookId: String, page: String = "1"): Result<CommentListData> {
        return try {
            val response = apiService.getComments(bookId = bookId, page = page)
            if (response.isSuccess()) {
                val data = response.decryptAndParse<CommentListData>()
                if (data != null) Result.success(data)
                else Result.failure(Exception("评论数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun postComment(bookId: String, content: String, replyTo: String = ""): Result<String> {
        return try {
            val response = apiService.postComment(content, bookId, replyTo)
            if (response.isSuccess()) {
                Result.success("ok")
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
