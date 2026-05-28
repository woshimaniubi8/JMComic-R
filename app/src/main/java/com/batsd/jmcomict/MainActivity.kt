package com.batsd.jmcomict

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
import com.batsd.jmcomict.ui.components.DisclaimerDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.batsd.jmcomict.R
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.batsd.jmcomict.data.api.ApiClientFactory
import com.batsd.jmcomict.data.local.PreferencesManager
import com.batsd.jmcomict.data.repository.BookRepository
import com.batsd.jmcomict.data.repository.CategoryRepository
import com.batsd.jmcomict.data.repository.UserRepository
import com.batsd.jmcomict.ui.navigation.AppNavigation
import com.batsd.jmcomict.ui.components.MaterialToastHost
import com.batsd.jmcomict.ui.theme.OpenJMTheme
import com.batsd.jmcomict.ui.viewmodel.BookViewModel
import com.batsd.jmcomict.ui.viewmodel.CategoryViewModel
import com.batsd.jmcomict.ui.viewmodel.UserViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val prefs = PreferencesManager(this)
        ApiClientFactory.init(this)
        val userRepository = UserRepository(prefs)
        val bookRepository = BookRepository()
        val categoryRepository = CategoryRepository()

        val userViewModel = UserViewModel(userRepository)
        val savedUser = prefs.getSavedUser()
        if (savedUser != null) {
            userViewModel.restoreUser(savedUser)
            val avsToken = prefs.getSessionToken()
            if (!avsToken.isNullOrEmpty()) {
                ApiClientFactory.saveAvsToken(avsToken)
            }
        }

        // 自动登录回调：cookie 过期时用保存的账号密码重登
        ApiClientFactory.autoLoginCallback = {
            val creds = prefs.getCredentials()
            if (creds == null) {
                android.util.Log.w("MainActivity", "No saved credentials for auto-login")
                false
            } else {
                android.util.Log.i("MainActivity", "Auto-login with saved credentials: ${creds.first}")
                userRepository.login(creds.first, creds.second).isSuccess
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
                    MaterialToastHost {
                        var showDisclaimer by remember { mutableStateOf(!prefs.hasAgreedDisclaimer()) }
                        DisclaimerDialog(
                            visible = showDisclaimer,
                            onAgree = {
                                prefs.setAgreedDisclaimer(true)
                                showDisclaimer = false
                            },
                            onDisagree = {
                                finish()
                            }
                        )

                        AppNavigation(
                        userViewModel = userViewModel,
                        bookViewModel = bookViewModel,
                        categoryViewModel = categoryViewModel,
                        prefs = prefs,
                        isDarkTheme = isDarkTheme,
                        themeMode = currentThemeMode,
                        onSetThemeMode = { mode ->
                            currentThemeMode = mode
                            prefs.setThemeMode(mode)
                        },
                        onShowDisclaimer = { showDisclaimer = true }
                    )
                }
                } // MaterialToastHost
            }
        }
    }
}
