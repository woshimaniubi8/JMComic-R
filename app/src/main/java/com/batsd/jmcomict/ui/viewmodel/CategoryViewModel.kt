package com.batsd.jmcomict.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batsd.jmcomict.data.model.Category
import com.batsd.jmcomict.data.model.BookItem
import com.batsd.jmcomict.data.api.BookListData
import com.batsd.jmcomict.data.repository.CategoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 分类相关的 ViewModel
 */
class CategoryViewModel(private val categoryRepository: CategoryRepository) : ViewModel() {
    
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    private val _categoryBooks = MutableStateFlow<List<BookItem>>(emptyList())
    val categoryBooks: StateFlow<List<BookItem>> = _categoryBooks
    
    fun getCategories() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            categoryRepository.getCategories()
                .onSuccess { categories ->
                    _categories.value = categories
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }
            
            _isLoading.value = false
        }
    }
    
    fun clearError() {
        _error.value = null
    }

    /** 清除分类筛选的书籍列表（恢复显示首页列表） */
    fun clearBooks() {
        _categoryBooks.value = emptyList()
    }
    
    fun getBooksByCategory(categoryId: String, page: Int = 1, sort: String = "mr") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            categoryRepository.getBooksByCategory(categoryId, page, sort)
                .onSuccess { data ->
                    _categoryBooks.value = data.content
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }
            _isLoading.value = false
        }
    }
}
