package com.batsd.jmcomict.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.batsd.jmcomict.data.model.User
import com.batsd.jmcomict.data.repository.UserRepository
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
        val uid = _user.value?.uid ?: run {
            onResult(false, "请先登录")
            return
        }
        viewModelScope.launch {
            userRepository.dailyCheckIn(uid)
                .onSuccess { msg -> onResult(true, msg) }
                .onFailure { e -> onResult(false, e.message ?: "签到失败") }
        }
    }

    fun autoDailyCheckInIfNeeded(
        force: Boolean = false,
        showSkippedResult: Boolean = false,
        onResult: ((Boolean, String) -> Unit)? = null
    ) {
        val uid = _user.value?.uid ?: run {
         //   onResult?.invoke(false, "请先登录")
            return
        }
        if (!userRepository.prefs.getAutoDailyCheckIn()) {
           // onResult?.invoke(false, "自动签到未开启")
            return
        }
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        if (userRepository.prefs.getLastAutoCheckInDate() == today) {
            if (showSkippedResult || force) {
              //  onResult?.invoke(true, "今天已自动签到")
            }
            return
        }

        viewModelScope.launch {
            userRepository.dailyCheckIn(uid)
                .onSuccess { msg ->
                    android.util.Log.i("UserVM", "Auto daily check-in result: $msg")
                    userRepository.prefs.setLastAutoCheckInDate(today)
                    onResult?.invoke(true, msg.ifBlank { "自动签到成功" })
                }
                .onFailure { e ->
                    val msg = e.message.orEmpty()
                    android.util.Log.w("UserVM", "Auto daily check-in failed: $msg")
                    if (msg.contains("已") || msg.contains("already", ignoreCase = true)) {
                        userRepository.prefs.setLastAutoCheckInDate(today)
                        onResult?.invoke(true, msg.ifBlank { "今天已签到" })
                    } else if (force || showSkippedResult) {
                        onResult?.invoke(false, "自动签到失败：${msg.ifBlank { "未知错误" }}")
                    }
                }
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
