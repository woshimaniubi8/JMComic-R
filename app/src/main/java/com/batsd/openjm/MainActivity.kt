package com.batsd.openjm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.batsd.openjm.data.api.ApiClientFactory
import com.batsd.openjm.data.local.PreferencesManager
import com.batsd.openjm.data.repository.BookRepository
import com.batsd.openjm.data.repository.CategoryRepository
import com.batsd.openjm.data.repository.UserRepository
import com.batsd.openjm.ui.navigation.AppNavigation
import com.batsd.openjm.ui.theme.OpenJMTheme
import com.batsd.openjm.ui.viewmodel.BookViewModel
import com.batsd.openjm.ui.viewmodel.CategoryViewModel
import com.batsd.openjm.ui.viewmodel.UserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)
        val apiService = ApiClientFactory.getInstance(this)
        val userRepository = UserRepository(apiService, prefs)
        val bookRepository = BookRepository(apiService)
        val categoryRepository = CategoryRepository(apiService)

        val userViewModel = UserViewModel(userRepository)
        val savedUser = prefs.getSavedUser()
        if (savedUser != null) {
            userViewModel.restoreUser(savedUser)
            val avsToken = prefs.getSessionToken()
            if (!avsToken.isNullOrEmpty()) {
                ApiClientFactory.saveAvsToken(avsToken)
            }
        }

        val bookViewModel = BookViewModel(bookRepository, prefs)
        val categoryViewModel = CategoryViewModel(categoryRepository)

        setContent {
            val themeMode = prefs.getThemeMode()
            var currentThemeMode by remember { mutableIntStateOf(themeMode) }
            val isDarkTheme = when (currentThemeMode) {
                1 -> false  // 浅色
                2 -> true   // 深色
                else -> isSystemInDarkTheme()  // 0=跟随系统
            }
            val imageLoader = ImageLoader.Builder(this@MainActivity)
                .okHttpClient { ApiClientFactory.getOkHttpClient(this@MainActivity) }
                .crossfade(true)
                .build()
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                OpenJMTheme(darkTheme = isDarkTheme) {
                    AppNavigation(
                        userViewModel = userViewModel,
                        bookViewModel = bookViewModel,
                        categoryViewModel = categoryViewModel,
                        isDarkTheme = isDarkTheme,
                        themeMode = currentThemeMode,
                        onSetThemeMode = { mode ->
                            currentThemeMode = mode
                            prefs.setThemeMode(mode)
                        }
                    )
                }
            }
        }
    }
}
