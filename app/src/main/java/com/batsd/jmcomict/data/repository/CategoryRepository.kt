package com.batsd.jmcomict.data.repository

import com.batsd.jmcomict.data.api.ApiResponse
import com.batsd.jmcomict.data.api.BookListData
import com.batsd.jmcomict.data.api.CategoryWrapper
import com.batsd.jmcomict.data.api.JMComicApiService
import com.batsd.jmcomict.data.model.*

class CategoryRepository(private val apiService: JMComicApiService) {

    suspend fun getCategories(): Result<List<Category>> {
        return try {
            val response = apiService.getCategories()
            if (response.isSuccess()) {
                // categories API 返回 {categories: [...], blocks: [...]}
                val wrapper = response.decryptAndParse<CategoryWrapper>()
                if (wrapper != null) Result.success(wrapper.categories)
                else Result.failure(Exception("分类数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBooksByCategory(categoryId: String, page: Int = 1, sort: String = "mr"): Result<BookListData> {
        return try {
            val response = apiService.getBooksByCategory(categoryId, page, sort)
            if (response.isSuccess()) {
                val data = response.decryptAndParse<BookListData>()
                if (data != null) Result.success(data)
                else Result.failure(Exception("数据解析失败"))
            } else {
                Result.failure(Exception(response.errorMessage()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
