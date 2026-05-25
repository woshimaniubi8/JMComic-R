package com.batsd.openjm.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batsd.openjm.data.model.User
import com.batsd.openjm.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 用户相关的 ViewModel
 */
class UserViewModel(private val userRepository: UserRepository) : ViewModel() {
    
    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    
    fun login(username: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            userRepository.login(username, password)
                .onSuccess { user ->
                    _user.value = user
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }
            
            _isLoading.value = false
        }
    }
    
    fun register(username: String, password: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            
            userRepository.register(username, password, email)
                .onSuccess { user ->
                    _user.value = user
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }
            
            _isLoading.value = false
        }
    }
    
    fun restoreUser(user: User) {
        _user.value = user
    }

    fun dailyCheckIn(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val uid = _user.value?.uid ?: return
        viewModelScope.launch {
            userRepository.dailyCheckIn(uid)
                .onSuccess { msg -> onResult(true, msg) }
                .onFailure { e -> onResult(false, e.message ?: "签到失败") }
        }
    }

    fun logout() {
        android.util.Log.e("UserVM", "LOGOUT: clearing user state")
        userRepository.logout()
        _user.value = null
        _error.value = null
        android.util.Log.e("UserVM", "LOGOUT: done, prefs.isLogin=${userRepository.prefs.isLogin()}")
    }
}
