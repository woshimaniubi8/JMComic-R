package com.batsd.openjm.data.local

import android.content.Context
import android.content.SharedPreferences
import com.batsd.openjm.data.model.User
import kotlinx.serialization.json.JsonPrimitive

class PreferencesManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("jmcomic_prefs", Context.MODE_PRIVATE)

    fun saveUser(userId: String, userName: String, token: String) {
        sharedPreferences.edit().apply {
            putString("user_id", userId)
            putString("user_name", userName)
            putString("token", token)
            putBoolean("is_login", true)
            apply()
        }
    }

    /** 保存完整用户信息（登录成功后调用） */
    fun saveLoginUser(user: User) {
        sharedPreferences.edit().apply {
            putString("user_id", user.uid)
            putString("user_name", user.userName)
            putString("level_name", user.levelName.ifEmpty { user.title })
            putString("level", user.level)
            putInt("coin", user.coin)
            putInt("favorites", user.favorites)
            putString("exp", user.expStr)
            putFloat("exp_percent", user.expPercent.toFloat())
            putBoolean("is_login", true)
            apply()
        }
    }

    /** 获取已保存的用户信息（用于恢复登录） */
    fun getSavedUser(): User? {
        if (!isLogin()) return null
        val savedExp = sharedPreferences.getString("exp", "") ?: ""
        return User(
            uid = getUserId() ?: "",
            userName = getUserName() ?: "",
            title = sharedPreferences.getString("level_name", "") ?: "",
            levelName = sharedPreferences.getString("level_name", "") ?: "",
            level = sharedPreferences.getString("level", "") ?: "",
            coin = sharedPreferences.getInt("coin", 0),
            favorites = sharedPreferences.getInt("favorites", 0),
            exp = if (savedExp.isNotEmpty()) JsonPrimitive(savedExp) else null,
            expPercent = sharedPreferences.getFloat("exp_percent", 0f).toDouble(),
            isLogin = true
        )
    }

    fun getToken(): String? = sharedPreferences.getString("token", null)
    fun getUserId(): String? = sharedPreferences.getString("user_id", null)
    fun getUserName(): String? = sharedPreferences.getString("user_name", null)
    fun isLogin(): Boolean = sharedPreferences.getBoolean("is_login", false)

    fun saveSessionToken(token: String) {
        sharedPreferences.edit().putString("avs_token", token).apply()
    }

    fun getSessionToken(): String? = sharedPreferences.getString("avs_token", null)

    /** 仅清除登录相关数据，保留设置（主题、CDN等） */
    fun logout() {
        sharedPreferences.edit().apply {
            remove("user_id")
            remove("user_name")
            remove("token")
            remove("level_name")
            remove("level")
            remove("coin")
            remove("favorites")
            remove("exp")
            remove("avs_token")
            putBoolean("is_login", false)
            commit()
        }
    }
    
    fun saveReadingHistory(bookId: String, epsId: String, page: Int) {
        val key = "history_${bookId}_${epsId}"
        sharedPreferences.edit().putInt(key, page).apply()
    }
    
    fun getReadingHistory(bookId: String, epsId: String): Int {
        val key = "history_${bookId}_${epsId}"
        return sharedPreferences.getInt(key, 0)
    }
    
    fun saveFavoriteBooks(bookIds: Set<String>) {
        sharedPreferences.edit().putStringSet("favorite_books", bookIds).apply()
    }
    
    fun getFavoriteBooks(): Set<String> {
        return sharedPreferences.getStringSet("favorite_books", emptySet()) ?: emptySet()
    }
    
    fun addFavoriteBook(bookId: String) {
        val favorites = getFavoriteBooks().toMutableSet()
        favorites.add(bookId)
        saveFavoriteBooks(favorites)
    }
    
    fun removeFavoriteBook(bookId: String) {
        val favorites = getFavoriteBooks().toMutableSet()
        favorites.remove(bookId)
        saveFavoriteBooks(favorites)
    }
    
    fun isFavorited(bookId: String): Boolean {
        return getFavoriteBooks().contains(bookId)
    }

    // CDN/分流切换 (0=默认, 1/2/3=备用)
    fun getCdnIndex(): Int = sharedPreferences.getInt("cdn_index", 0)
    fun setCdnIndex(index: Int) { sharedPreferences.edit().putInt("cdn_index", index).apply() }

    // 主题切换
    fun isDarkTheme(): Boolean = sharedPreferences.getBoolean("dark_theme", false)
    fun setDarkTheme(isDark: Boolean) { sharedPreferences.edit().putBoolean("dark_theme", isDark).apply() }
    
    /** 主题模式: 0=跟随系统, 1=浅色, 2=深色 */
    fun getThemeMode(): Int = sharedPreferences.getInt("theme_mode", 0)
    fun setThemeMode(mode: Int) { sharedPreferences.edit().putInt("theme_mode", mode).apply() }

    /** 隐藏的历史记录 ID（本地删除） */
    fun hideHistoryItem(bookId: String) {
        val hidden = getHiddenHistoryItems().toMutableSet()
        hidden.add(bookId)
        sharedPreferences.edit().putStringSet("hidden_history", hidden).apply()
    }
    fun getHiddenHistoryItems(): Set<String> =
        sharedPreferences.getStringSet("hidden_history", emptySet()) ?: emptySet()

    // 搜索历史
    fun getSearchHistory(): List<String> = sharedPreferences.getString("search_history", "")?.split("|||")?.filter { it.isNotEmpty() } ?: emptyList()
    fun addSearchHistory(query: String) {
        val list = getSearchHistory().toMutableList()
        list.remove(query)
        list.add(0, query)
        if (list.size > 20) list.removeAt(list.lastIndex)
        sharedPreferences.edit().putString("search_history", list.joinToString("|||")).apply()
    }
    fun clearSearchHistory() { sharedPreferences.edit().remove("search_history").apply() }
}
